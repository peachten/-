package com.peachten.shortlink.common.annotation;

import com.peachten.shortlink.common.enums.LimitType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流（Redis + Lua 滑动窗口）
 * <p>
 * 注意：Spring AOP 只能拦截「外部调用」的方法，加在同类内部调用的方法上不生效，
 * 建议加在 Controller 方法上。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流维度 */
    LimitType type() default LimitType.IP;

    /** 窗口大小（秒） */
    int window() default 60;

    /** 窗口内允许的请求次数 */
    int limit() default 100;

    /** 超限提示 */
    String message() default "访问过于频繁，请稍后重试";
}
