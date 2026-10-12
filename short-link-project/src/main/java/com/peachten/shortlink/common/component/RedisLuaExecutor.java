package com.peachten.shortlink.common.component;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;

/**
 * Redis Lua 脚本执行器
 */
@Component
@RequiredArgsConstructor
public class RedisLuaExecutor {

    private static final long MILLIS_PER_SECOND = 1000L;

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    /**
     * 尝试获取一次通行资格（滑动窗口）
     *
     * @param key           限流 key
     * @param windowSeconds 窗口大小（秒）
     * @param limit         窗口内允许次数
     * @return true 放行，false 超限
     */
    public boolean tryAcquire(String key, int windowSeconds, int limit) {
        // member 必须唯一：若用时间戳，同一毫秒的并发请求会覆盖成同一个成员导致计数偏小
        String member = UUID.randomUUID().toString();
        Long result = stringRedisTemplate.execute(rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(windowSeconds * MILLIS_PER_SECOND),
                String.valueOf(limit),
                member);
        return result != null && result == 1L;
    }
}
