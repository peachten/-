package com.peachten.shortlink.service.impl;

import com.peachten.shortlink.common.constant.RedisKeyConstant;
import com.peachten.shortlink.mq.message.AccessLogMessage;
import com.peachten.shortlink.service.ShortLinkStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 实时统计：PV / UV(HLL) / UIP(HLL) / 各维度 Hash / 小时分布 Hash
 * <p>
 * 用 Pipeline 把 6 类 key 的「写入 + 过期」共 12 条命令合并成 **一次 RTT**，
 * 避免在跳转热路径上产生 12 次网络往返。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkStatsServiceImpl implements ShortLinkStatsService {

    /** 统计 key 统一保留 2 天（够定时任务当天/次日聚合，又不至于无限膨胀） */
    private static final long STATS_TTL_SECONDS = TimeUnit.DAYS.toSeconds(2);

    private final StringRedisTemplate stringRedisTemplate;

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
