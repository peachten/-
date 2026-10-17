package com.peachten.shortlink.job;

import com.peachten.shortlink.common.constant.RedisKeyConstant;
import com.peachten.shortlink.dao.LinkMapper;
import com.peachten.shortlink.entity.TLinkAccessHourStats;
import com.peachten.shortlink.entity.TLinkAccessStats;
import com.peachten.shortlink.entity.TLinkBrowserStats;
import com.peachten.shortlink.entity.TLinkDeviceStats;
import com.peachten.shortlink.entity.TLinkLocaleStats;
import com.peachten.shortlink.entity.TLinkOsStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 统计聚合定时任务
 * <p>
 * Redis 里的实时统计是「当天全量」，所以落库统一用 upsert 覆盖写 ——
 * 任务重复执行、多实例重复执行都不会让数值翻倍。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortLinkStatsJob {

    private static final String JOB_AGGREGATE = "aggregate-stats";
    private static final String JOB_CLEANUP = "daily-cleanup";
    private static final String STATS_KEY_PREFIX = "short-link:stats:";
    private static final int SCAN_BATCH = 500;
    /** 访问明细保留天数 */
    private static final int LOG_RETAIN_DAYS = 90;

    private final StringRedisTemplate stringRedisTemplate;
    private final LinkMapper linkMapper;
    private final RedissonClient redissonClient;

    /**
     * 每小时聚合一次。
     * 同时补聚合昨天：跨过 0 点后，昨天 23:00~24:00 的数据还需要落库。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void aggregateToday() {
        LocalDate today = LocalDate.now();
        aggregate(today);
        aggregate(today.minusDays(1));
    }

    /**
     * 聚合指定日期。显式传日期，避免跨天时依赖「当前时间」隐式推断。
     */
    public void aggregate(LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ISO_DATE);
        RLock lock = redissonClient.getLock(RedisKeyConstant.lockJob(JOB_AGGREGATE));
        boolean locked = false;
        try {
            // 二参 tryLock，看门狗自动续期；waitTime=0 表示抢不到直接跳过，不排队
            locked = lock.tryLock(0, TimeUnit.SECONDS);
            if (!locked) {
                log.info("聚合任务未抢到锁，跳过, date={}", dateStr);
                return;
            }
            aggregateDate(date, dateStr);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("聚合任务被中断, date={}", dateStr);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 每天凌晨 3 点：清理 90 天前的访问明细 + 兜底清理无 TTL 的统计 key */
    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyCleanup() {
        RLock lock = redissonClient.getLock(RedisKeyConstant.lockJob(JOB_CLEANUP));
        boolean locked = false;
        try {
            locked = lock.tryLock(0, TimeUnit.SECONDS);
            if (!locked) {
                log.info("清理任务未抢到锁，跳过");
                return;
            }

            LocalDateTime deadline = LocalDateTime.now().minusDays(LOG_RETAIN_DAYS);
            int deleted = linkMapper.deleteAccessLogsBefore(deadline);
            log.info("清理访问明细完成, deadline={}, deleted={}", deadline, deleted);

            int expired = cleanupNoTtlStatsKeys();
            log.info("兜底清理无 TTL 统计 key 完成, deleted={}", expired);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("清理任务被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void aggregateDate(LocalDate date, String dateStr) {
        // 以 PV key 为入口发现「当天有访问的短链」，其余 key 与它同源
        String pvPattern = RedisKeyConstant.statsPv(dateStr, "") + "*";
        Set<String> urls = scanKeys(pvPattern, RedisKeyConstant.statsPv(dateStr, "").length());
        log.info("开始聚合统计, date={}, 短链数={}", dateStr, urls.size());

        int ok = 0;
        int fail = 0;
        for (String url : urls) {
            try {
                upsertOne(date, dateStr, url);
                ok++;
            } catch (Exception e) {
                // 单条失败不影响整批
                fail++;
                log.error("聚合失败, date={}, url={}", dateStr, url, e);
            }
        }
        log.info("聚合统计完成, date={}, 成功={}, 失败={}", dateStr, ok, fail);
    }

    /** 读一条短链当天的全部 Redis 指标，覆盖写入 6 张统计表 */
    private void upsertOne(LocalDate date, String dateStr, String url) {
        String pvValue = stringRedisTemplate.opsForValue().get(RedisKeyConstant.statsPv(dateStr, url));
        if (pvValue == null) {
            // SCAN 之后 key 恰好被 TTL 清掉，跳过
            return;
        }

        TLinkAccessStats access = new TLinkAccessStats();
        access.setFullShortUrl(url);
        access.setDate(date);
        access.setPv(toInt(pvValue));
        access.setUv(toInt(stringRedisTemplate.opsForHyperLogLog().size(RedisKeyConstant.statsUv(dateStr, url))));
        access.setUip(toInt(stringRedisTemplate.opsForHyperLogLog().size(RedisKeyConstant.statsUip(dateStr, url))));
        linkMapper.upsertAccessStats(access);

        Map<Object, Object> hourMap = stringRedisTemplate.opsForHash()
                .entries(RedisKeyConstant.statsHour(dateStr, url));
        TLinkAccessHourStats hourStats = new TLinkAccessHourStats();
        hourStats.setFullShortUrl(url);
        hourStats.setDate(date);
        for (int i = 0; i < 24; i++) {
            setHour(hourStats, i, toInt(hourMap.get(String.valueOf(i))));
        }
        linkMapper.upsertHourStats(hourStats);

        Map<Object, Object> browserMap = stringRedisTemplate.opsForHash()
                .entries(RedisKeyConstant.statsDimension("browser", dateStr, url));
        for (Map.Entry<Object, Object> e : browserMap.entrySet()) {
            TLinkBrowserStats s = new TLinkBrowserStats();
            s.setFullShortUrl(url);
            s.setDate(date);
            s.setBrowser(String.valueOf(e.getKey()));
            s.setCnt(toInt(e.getValue()));
            linkMapper.upsertBrowserStats(s);
        }

        Map<Object, Object> osMap = stringRedisTemplate.opsForHash()
                .entries(RedisKeyConstant.statsDimension("os", dateStr, url));
        for (Map.Entry<Object, Object> e : osMap.entrySet()) {
            TLinkOsStats s = new TLinkOsStats();
            s.setFullShortUrl(url);
            s.setDate(date);
            s.setOs(String.valueOf(e.getKey()));
            s.setCnt(toInt(e.getValue()));
            linkMapper.upsertOsStats(s);
        }

        Map<Object, Object> deviceMap = stringRedisTemplate.opsForHash()
                .entries(RedisKeyConstant.statsDimension("device", dateStr, url));
        for (Map.Entry<Object, Object> e : deviceMap.entrySet()) {
            TLinkDeviceStats s = new TLinkDeviceStats();
            s.setFullShortUrl(url);
            s.setDate(date);
            s.setDevice(String.valueOf(e.getKey()));
            s.setCnt(toInt(e.getValue()));
            linkMapper.upsertDeviceStats(s);
        }

        Map<Object, Object> localeMap = stringRedisTemplate.opsForHash()
                .entries(RedisKeyConstant.statsDimension("locale", dateStr, url));
        for (Map.Entry<Object, Object> e : localeMap.entrySet()) {
            TLinkLocaleStats s = new TLinkLocaleStats();
            s.setFullShortUrl(url);
            s.setDate(date);
            s.setLocale(String.valueOf(e.getKey()));
            s.setCnt(toInt(e.getValue()));
            linkMapper.upsertLocaleStats(s);
        }
    }

    /**
     * SCAN 游标遍历（不要用 KEYS，生产环境会阻塞 Redis），返回去掉前缀后的短链列表。
     */
    private Set<String> scanKeys(String pattern, int prefixLength) {
        Set<String> keys = new HashSet<>();
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH).build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    String key = new String(cursor.next(), StandardCharsets.UTF_8);
                    keys.add(key.substring(prefixLength));
                }
            }
            return null;
        });
        return keys;
    }

    /** 统计 key 都设了 TTL，这里只兜底清理「意外没有过期时间」的 key */
    private int cleanupNoTtlStatsKeys() {
        List<String> noTtlKeys = new ArrayList<>();
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match(STATS_KEY_PREFIX + "*").count(SCAN_BATCH).build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    byte[] rawKey = cursor.next();
                    Long ttl = connection.keyCommands().ttl(rawKey);
                    if (ttl != null && ttl == -1) {
                        noTtlKeys.add(new String(rawKey, StandardCharsets.UTF_8));
                    }
                }
            }
            return null;
        });
        if (noTtlKeys.isEmpty()) {
            return 0;
        }
        Long deleted = stringRedisTemplate.delete(noTtlKeys);
        return deleted == null ? 0 : deleted.intValue();
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void setHour(TLinkAccessHourStats stats, int hour, int cnt) {
        switch (hour) {
            case 0 -> stats.setHour00(cnt);
            case 1 -> stats.setHour01(cnt);
            case 2 -> stats.setHour02(cnt);
            case 3 -> stats.setHour03(cnt);
            case 4 -> stats.setHour04(cnt);
            case 5 -> stats.setHour05(cnt);
            case 6 -> stats.setHour06(cnt);
            case 7 -> stats.setHour07(cnt);
            case 8 -> stats.setHour08(cnt);
            case 9 -> stats.setHour09(cnt);
            case 10 -> stats.setHour10(cnt);
            case 11 -> stats.setHour11(cnt);
            case 12 -> stats.setHour12(cnt);
            case 13 -> stats.setHour13(cnt);
            case 14 -> stats.setHour14(cnt);
            case 15 -> stats.setHour15(cnt);
            case 16 -> stats.setHour16(cnt);
            case 17 -> stats.setHour17(cnt);
            case 18 -> stats.setHour18(cnt);
            case 19 -> stats.setHour19(cnt);
            case 20 -> stats.setHour20(cnt);
            case 21 -> stats.setHour21(cnt);
            case 22 -> stats.setHour22(cnt);
            case 23 -> stats.setHour23(cnt);
            default -> {
            }
        }
    }
}
