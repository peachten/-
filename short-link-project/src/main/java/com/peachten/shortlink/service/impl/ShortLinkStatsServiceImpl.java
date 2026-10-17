package com.peachten.shortlink.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.peachten.shortlink.common.constant.RedisKeyConstant;
import com.peachten.shortlink.common.exception.BizException;
import com.peachten.shortlink.common.result.PageResult;
import com.peachten.shortlink.common.result.ResultCode;
import com.peachten.shortlink.dao.AccessLogMapper;
import com.peachten.shortlink.dao.LinkAccessHourStatsMapper;
import com.peachten.shortlink.dao.LinkAccessStatsMapper;
import com.peachten.shortlink.dao.LinkBrowserStatsMapper;
import com.peachten.shortlink.dao.LinkDeviceStatsMapper;
import com.peachten.shortlink.dao.LinkLocaleStatsMapper;
import com.peachten.shortlink.dao.LinkMapper;
import com.peachten.shortlink.dao.LinkOsStatsMapper;
import com.peachten.shortlink.dto.ShortLinkStatsAccessRecordReqDTO;
import com.peachten.shortlink.dto.ShortLinkStatsReqDTO;
import com.peachten.shortlink.entity.TLink;
import com.peachten.shortlink.entity.TLinkAccessHourStats;
import com.peachten.shortlink.entity.TLinkAccessLogs;
import com.peachten.shortlink.entity.TLinkAccessStats;
import com.peachten.shortlink.entity.TLinkBrowserStats;
import com.peachten.shortlink.entity.TLinkDeviceStats;
import com.peachten.shortlink.entity.TLinkLocaleStats;
import com.peachten.shortlink.entity.TLinkOsStats;
import com.peachten.shortlink.mq.message.AccessLogMessage;
import com.peachten.shortlink.service.GroupService;
import com.peachten.shortlink.service.ShortLinkStatsService;
import com.peachten.shortlink.vo.ShortLinkAccessRecordVO;
import com.peachten.shortlink.vo.ShortLinkStatsDailyVO;
import com.peachten.shortlink.vo.ShortLinkStatsDimensionVO;
import com.peachten.shortlink.vo.ShortLinkStatsRespVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * 实时统计：PV / UV(HLL) / UIP(HLL) / 各维度 Hash / 小时分布 Hash
 * <p>
 * 用 Pipeline 把 6 类 key 的「写入 + 过期」共 12 条命令合并成 **一次 RTT**，
 * 避免在跳转热路径上产生 12 次网络往返。
 * <p>
 * 查询规则（最容易出 bug 的地方）：**今天只读 Redis，历史只读 DB**。
 * 判断条件只看「日期是否等于今天」，与聚合任务是否已经执行过无关。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkStatsServiceImpl implements ShortLinkStatsService {

    /** 统计 key 统一保留 2 天（够定时任务当天/次日聚合，又不至于无限膨胀） */
    private static final long STATS_TTL_SECONDS = TimeUnit.DAYS.toSeconds(2);

    /** 单次查询的最大时间跨度 */
    private static final int MAX_RANGE_DAYS = 90;

    private static final String GID_ALL = "all";

    private static final Set<String> DIMENSIONS = Set.of("browser", "os", "device", "locale");

    private final StringRedisTemplate stringRedisTemplate;
    private final GroupService groupService;
    private final LinkMapper linkMapper;
    private final LinkAccessStatsMapper accessStatsMapper;
    private final LinkAccessHourStatsMapper hourStatsMapper;
    private final LinkBrowserStatsMapper browserStatsMapper;
    private final LinkOsStatsMapper osStatsMapper;
    private final LinkDeviceStatsMapper deviceStatsMapper;
    private final LinkLocaleStatsMapper localeStatsMapper;
    private final AccessLogMapper accessLogMapper;

    @Override
    public void recordAccess(AccessLogMessage message) {
        String url = message.getFullShortUrl();
        if (url == null || message.getGid() == null) {
            return;
        }
        long timestamp = message.getAccessTime() == null ? System.currentTimeMillis() : message.getAccessTime();
        LocalDateTime accessTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        String date = accessTime.toLocalDate().format(DateTimeFormatter.ISO_DATE);
        String hour = String.valueOf(accessTime.getHour());

        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisSerializer serializer = new StringRedisSerializer();

            // PV：每次访问 +1
            byte[] pvKey = serializer.serialize(RedisKeyConstant.statsPv(date, url));
            connection.stringCommands().incr(pvKey);
            expire(connection, pvKey);

            // UV：按「IP + UA」的摘要去重（HyperLogLog，标准误差 0.81%，省内存）
            byte[] uvKey = serializer.serialize(RedisKeyConstant.statsUv(date, url));
            connection.hyperLogLogCommands().pfAdd(uvKey, serializer.serialize(message.getUser()));
            expire(connection, uvKey);

            // UIP：按 IP 去重
            byte[] uipKey = serializer.serialize(RedisKeyConstant.statsUip(date, url));
            connection.hyperLogLogCommands().pfAdd(uipKey, serializer.serialize(message.getIp()));
            expire(connection, uipKey);

            // 维度：浏览器 / 操作系统 / 设备 / 地区
            hIncrByDimension(connection, serializer, "browser", date, url, message.getBrowser());
            hIncrByDimension(connection, serializer, "os", date, url, message.getOs());
            hIncrByDimension(connection, serializer, "device", date, url, message.getDeviceType());
            hIncrByDimension(connection, serializer, "locale", date, url, message.getLocale());

            // 小时分布
            byte[] hourKey = serializer.serialize(RedisKeyConstant.statsHour(date, url));
            connection.hashCommands().hIncrBy(hourKey, serializer.serialize(hour), 1L);
            expire(connection, hourKey);

            return null;
        });
    }

    @Override
    public ShortLinkStatsRespVO getGroupStats(ShortLinkStatsReqDTO req) {
        validateRange(req.getStartDate(), req.getEndDate());
        List<String> urls = resolveUrls(req.getGid(), req.getFullShortUrl());
        LocalDate today = LocalDate.now();

        int[] hours = new int[24];
        Map<LocalDate, int[]> dailyMap = new TreeMap<>();

        // 1. 历史：只读 DB（最多到昨天）
        LocalDate historyEnd = req.getEndDate().isBefore(today) ? req.getEndDate() : today.minusDays(1);
        if (!urls.isEmpty() && !req.getStartDate().isAfter(historyEnd)) {
            List<TLinkAccessStats> rows = accessStatsMapper.selectList(Wrappers.lambdaQuery(TLinkAccessStats.class)
                    .in(TLinkAccessStats::getFullShortUrl, urls)
                    .between(TLinkAccessStats::getDate, req.getStartDate(), historyEnd));
            for (TLinkAccessStats row : rows) {
                int[] values = dailyMap.computeIfAbsent(row.getDate(), k -> new int[3]);
                values[0] += nvl(row.getPv());
                values[1] += nvl(row.getUv());
                values[2] += nvl(row.getUip());
            }

            List<TLinkAccessHourStats> hourRows = hourStatsMapper.selectList(
                    Wrappers.lambdaQuery(TLinkAccessHourStats.class)
                            .in(TLinkAccessHourStats::getFullShortUrl, urls)
                            .between(TLinkAccessHourStats::getDate, req.getStartDate(), historyEnd));
            for (TLinkAccessHourStats row : hourRows) {
                addHours(hours, row);
            }
        }

        // 2. 今天：只读 Redis
        if (!urls.isEmpty() && !req.getStartDate().isAfter(today) && !req.getEndDate().isBefore(today)) {
            String todayStr = today.format(DateTimeFormatter.ISO_DATE);
            int todayPv = 0;
            for (String url : urls) {
                todayPv += toInt(stringRedisTemplate.opsForValue().get(RedisKeyConstant.statsPv(todayStr, url)));
                Map<Object, Object> hourMap = stringRedisTemplate.opsForHash()
                        .entries(RedisKeyConstant.statsHour(todayStr, url));
                for (Map.Entry<Object, Object> e : hourMap.entrySet()) {
                    int hour = toInt(e.getKey());
                    if (hour >= 0 && hour < 24) {
                        hours[hour] += toInt(e.getValue());
                    }
                }
            }
            int todayUv = (int) unionHll(urls, todayStr, true);
            int todayUip = (int) unionHll(urls, todayStr, false);
            dailyMap.put(today, new int[]{todayPv, todayUv, todayUip});
        }

        // 3. 合并
        int totalPv = 0;
        int totalUv = 0;
        int totalUip = 0;
        for (int[] values : dailyMap.values()) {
            totalPv += values[0];
            totalUv += values[1];
            totalUip += values[2];
        }

        ShortLinkStatsRespVO resp = new ShortLinkStatsRespVO();
        resp.setPv(totalPv);
        resp.setUv(totalUv);
        resp.setUip(totalUip);
        resp.setHourStats(java.util.Arrays.stream(hours).boxed().toList());
        resp.setDaily(dailyMap.entrySet().stream()
                .map(e -> new ShortLinkStatsDailyVO(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList());
        return resp;
    }

    @Override
    public List<ShortLinkStatsDimensionVO> getDimensionStats(ShortLinkStatsReqDTO req, String dimension) {
        validateRange(req.getStartDate(), req.getEndDate());
        if (!DIMENSIONS.contains(dimension)) {
            throw new BizException(ResultCode.PARAM_ERROR.getCode(), "不支持的统计维度: " + dimension);
        }
        List<String> urls = resolveUrls(req.getGid(), req.getFullShortUrl());
        Map<String, Long> counts = new HashMap<>();
        LocalDate today = LocalDate.now();

        // 历史：DB
        LocalDate historyEnd = req.getEndDate().isBefore(today) ? req.getEndDate() : today.minusDays(1);
        if (!urls.isEmpty() && !req.getStartDate().isAfter(historyEnd)) {
            switch (dimension) {
                case "browser" -> browserStatsMapper.selectList(Wrappers.lambdaQuery(TLinkBrowserStats.class)
                                .in(TLinkBrowserStats::getFullShortUrl, urls)
                                .between(TLinkBrowserStats::getDate, req.getStartDate(), historyEnd))
                        .forEach(s -> counts.merge(s.getBrowser(), (long) nvl(s.getCnt()), Long::sum));
                case "os" -> osStatsMapper.selectList(Wrappers.lambdaQuery(TLinkOsStats.class)
                                .in(TLinkOsStats::getFullShortUrl, urls)
                                .between(TLinkOsStats::getDate, req.getStartDate(), historyEnd))
                        .forEach(s -> counts.merge(s.getOs(), (long) nvl(s.getCnt()), Long::sum));
                case "device" -> deviceStatsMapper.selectList(Wrappers.lambdaQuery(TLinkDeviceStats.class)
                                .in(TLinkDeviceStats::getFullShortUrl, urls)
                                .between(TLinkDeviceStats::getDate, req.getStartDate(), historyEnd))
                        .forEach(s -> counts.merge(s.getDevice(), (long) nvl(s.getCnt()), Long::sum));
                default -> localeStatsMapper.selectList(Wrappers.lambdaQuery(TLinkLocaleStats.class)
                                .in(TLinkLocaleStats::getFullShortUrl, urls)
                                .between(TLinkLocaleStats::getDate, req.getStartDate(), historyEnd))
                        .forEach(s -> counts.merge(s.getLocale(), (long) nvl(s.getCnt()), Long::sum));
            }
        }

        // 今天：Redis Hash
        if (!urls.isEmpty() && !req.getStartDate().isAfter(today) && !req.getEndDate().isBefore(today)) {
            String todayStr = today.format(DateTimeFormatter.ISO_DATE);
            for (String url : urls) {
                Map<Object, Object> hash = stringRedisTemplate.opsForHash()
                        .entries(RedisKeyConstant.statsDimension(dimension, todayStr, url));
                hash.forEach((k, v) -> counts.merge(String.valueOf(k), (long) toInt(v), Long::sum));
            }
        }

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new ShortLinkStatsDimensionVO(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public PageResult<ShortLinkAccessRecordVO> pageAccessRecords(ShortLinkStatsAccessRecordReqDTO req) {
        validateRange(req.getStartDate(), req.getEndDate());
        List<String> urls = resolveUrls(req.getGid(), req.getFullShortUrl());
        if (urls.isEmpty()) {
            return new PageResult<>(0, req.getCurrent(), req.getSize(), List.of());
        }

        Page<TLinkAccessLogs> page = accessLogMapper.selectPage(
                new Page<>(req.getCurrent(), req.getSize()),
                Wrappers.lambdaQuery(TLinkAccessLogs.class)
                        .in(TLinkAccessLogs::getFullShortUrl, urls)
                        .between(TLinkAccessLogs::getAccessTime,
                                req.getStartDate().atStartOfDay(),
                                req.getEndDate().atTime(LocalTime.MAX))
                        .orderByDesc(TLinkAccessLogs::getAccessTime));

        List<ShortLinkAccessRecordVO> records = page.getRecords().stream().map(this::toRecordVO).toList();
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /**
     * 解析查询范围：返回允许查询的完整短链列表。
     * gid=all 时限定为当前用户的全部分组；指定 fullShortUrl 时校验它属于该范围，防止越权。
     */
    private List<String> resolveUrls(String gid, String fullShortUrl) {
        List<String> gids;
        if (GID_ALL.equals(gid)) {
            gids = groupService.listGids();
        } else {
            groupService.checkOwnership(gid);
            gids = List.of(gid);
        }
        if (gids.isEmpty()) {
            return List.of();
        }

        if (StringUtils.hasText(fullShortUrl)) {
            TLink link = linkMapper.selectOne(Wrappers.lambdaQuery(TLink.class)
                    .eq(TLink::getFullShortUrl, fullShortUrl));
            if (link == null || !gids.contains(link.getGid())) {
                throw new BizException(ResultCode.PARAM_ERROR.getCode(), "短链不存在或无权访问");
            }
            return List.of(fullShortUrl);
        }

        return linkMapper.selectList(Wrappers.lambdaQuery(TLink.class)
                        .select(TLink::getFullShortUrl)
                        .in(TLink::getGid, gids))
                .stream()
                .map(TLink::getFullShortUrl)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BizException(ResultCode.PARAM_ERROR.getCode(), "开始/结束日期不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw new BizException(ResultCode.PARAM_ERROR.getCode(), "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_RANGE_DAYS) {
            throw new BizException(ResultCode.PARAM_ERROR.getCode(), "查询时间范围不能超过 " + MAX_RANGE_DAYS + " 天");
        }
    }

    /** 多个短链的 UV / UIP 用 PFCOUNT 多 key 求并集（不是相加） */
    private long unionHll(List<String> urls, String dateStr, boolean uv) {
        byte[][] keys = urls.stream()
                .map(url -> (uv ? RedisKeyConstant.statsUv(dateStr, url) : RedisKeyConstant.statsUip(dateStr, url))
                        .getBytes(StandardCharsets.UTF_8))
                .toArray(byte[][]::new);
        if (keys.length == 0) {
            return 0;
        }
        Long count = stringRedisTemplate.execute((RedisCallback<Long>) connection ->
                connection.hyperLogLogCommands().pfCount(keys));
        return count == null ? 0 : count;
    }

    private void addHours(int[] hours, TLinkAccessHourStats row) {
        hours[0] += nvl(row.getHour00());
        hours[1] += nvl(row.getHour01());
        hours[2] += nvl(row.getHour02());
        hours[3] += nvl(row.getHour03());
        hours[4] += nvl(row.getHour04());
        hours[5] += nvl(row.getHour05());
        hours[6] += nvl(row.getHour06());
        hours[7] += nvl(row.getHour07());
        hours[8] += nvl(row.getHour08());
        hours[9] += nvl(row.getHour09());
        hours[10] += nvl(row.getHour10());
        hours[11] += nvl(row.getHour11());
        hours[12] += nvl(row.getHour12());
        hours[13] += nvl(row.getHour13());
        hours[14] += nvl(row.getHour14());
        hours[15] += nvl(row.getHour15());
        hours[16] += nvl(row.getHour16());
        hours[17] += nvl(row.getHour17());
        hours[18] += nvl(row.getHour18());
        hours[19] += nvl(row.getHour19());
        hours[20] += nvl(row.getHour20());
        hours[21] += nvl(row.getHour21());
        hours[22] += nvl(row.getHour22());
        hours[23] += nvl(row.getHour23());
    }

    private ShortLinkAccessRecordVO toRecordVO(TLinkAccessLogs log) {
        ShortLinkAccessRecordVO vo = new ShortLinkAccessRecordVO();
        vo.setFullShortUrl(log.getFullShortUrl());
        vo.setIp(log.getIp());
        vo.setBrowser(log.getBrowser());
        vo.setOs(log.getOs());
        vo.setDeviceType(log.getDeviceType());
        vo.setLocale(log.getLocale());
        vo.setAccessTime(log.getAccessTime());
        return vo;
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
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

    private void hIncrByDimension(RedisConnection connection, StringRedisSerializer serializer,
                                  String dimension, String date, String url, String field) {
        if (field == null) {
            return;
        }
        byte[] key = serializer.serialize(RedisKeyConstant.statsDimension(dimension, date, url));
        connection.hashCommands().hIncrBy(key, serializer.serialize(field), 1L);
        expire(connection, key);
    }

    /** 统一设置 TTL，避免每个方法都漏了 expire 导致 key 永不过期 */
    private void expire(RedisConnection connection, byte[] key) {
        connection.keyCommands().expire(key, STATS_TTL_SECONDS);
    }
}
