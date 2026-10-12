package com.peachten.shortlink.common.component;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisLuaExecutorTest {

    private static final String KEY = "short-link:rate-limit:user:zhangsan";

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private DefaultRedisScript<Long> rateLimitScript;

    @InjectMocks
    private RedisLuaExecutor redisLuaExecutor;

    @Test
    void 脚本返回1_放行() {
        when(stringRedisTemplate.execute(any(), anyList(), any(), any(), any(), any())).thenReturn(1L);

        assertTrue(redisLuaExecutor.tryAcquire(KEY, 60, 30));

        // 窗口秒数必须换算成毫秒传给 Lua
        verify(stringRedisTemplate).execute(eq(rateLimitScript), eq(Collections.singletonList(KEY)),
                anyString(), eq("60000"), eq("30"), anyString());
    }

    @Test
    void 脚本返回0_拒绝() {
        when(stringRedisTemplate.execute(any(), anyList(), any(), any(), any(), any())).thenReturn(0L);

        assertFalse(redisLuaExecutor.tryAcquire(KEY, 60, 30));
    }

    @Test
    void 脚本返回null_按拒绝处理() {
        when(stringRedisTemplate.execute(any(), anyList(), any(), any(), any(), any())).thenReturn(null);

        assertFalse(redisLuaExecutor.tryAcquire(KEY, 60, 30));
    }
}
