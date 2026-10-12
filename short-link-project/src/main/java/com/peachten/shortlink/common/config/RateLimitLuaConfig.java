package com.peachten.shortlink.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 限流 Lua 脚本配置
 * <p>
 * 脚本在启动时加载一次并作为 Bean 复用，避免每次请求都读文件。
 */
@Configuration
public class RateLimitLuaConfig {

    @Bean
    public DefaultRedisScript<Long> rateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/rate_limit_sliding_window.lua"));
        // Redis 的 Lua number 映射为 Java Long，类型必须声明为 Long
        script.setResultType(Long.class);
        return script;
    }
}
