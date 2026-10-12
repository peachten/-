package com.peachten.shortlink.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 延迟删除缓存（延迟双删的第二次删除）
 * <p>
 * 场景：更新 DB 前先删了缓存，但可能有一个读线程在「删缓存之后、更新 DB 之前」
 * 把旧值又回填进缓存 —— 延迟再删一次即可覆盖这个时间窗。
 * <p>
 * 用 Spring 的 @Async（Boot 自动装配的 ThreadPoolTaskExecutor），
 * 不要用 ForkJoinPool.commonPool，避免占满公共池影响其他任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelayedCacheDeleter {

    private final StringRedisTemplate stringRedisTemplate;

    @Async
    public void deleteAfter(String key, long delayMillis) {
        try {
            Thread.sleep(delayMillis);
            stringRedisTemplate.delete(key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // 缓存删除失败不阻断主流程，靠 TTL 兜底
            log.warn("延迟删除缓存失败, key={}", key, e);
        }
    }
}
