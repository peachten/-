package com.peachten.shortlink.common.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.peachten.shortlink.common.annotation.RateLimit;
import com.peachten.shortlink.common.component.RedisLuaExecutor;
import com.peachten.shortlink.common.constant.RedisKeyConstant;
import com.peachten.shortlink.common.enums.LimitType;
import com.peachten.shortlink.common.exception.BizException;
import com.peachten.shortlink.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 限流切面
 * <p>
 * 用 {@code @Order(HIGHEST_PRECEDENCE)} 保证限流在业务逻辑（以及事务）之前执行，
 * 被限流的请求不会产生任何业务副作用。
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedisLuaExecutor redisLuaExecutor;

    /**
     * 注：这里刻意不用 {@code @annotation(rateLimit)} 的参数绑定形式
     * （代理场景下会报 "Required to bind 2 arguments, but only bound 1"），
     * 改为从方法签名上反射取注解，更稳。
     */
    @Around("@annotation(com.peachten.shortlink.common.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        RateLimit rateLimit = resolveAnnotation(joinPoint);
        if (rateLimit == null) {
            return joinPoint.proceed();
        }

        String key = RedisKeyConstant.rateLimit(rateLimit.type().keyType(), resolveDimension(rateLimit.type()));
        if (!redisLuaExecutor.tryAcquire(key, rateLimit.window(), rateLimit.limit())) {
            log.warn("触发限流, key={}, window={}s, limit={}", key, rateLimit.window(), rateLimit.limit());
            throw new BizException(rateLimit.message());
        }
        return joinPoint.proceed();
    }

    private RateLimit resolveAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        return AnnotatedElementUtils.findMergedAnnotation(method, RateLimit.class);
    }

    /** 按维度取限流标识：IP 取真实 IP，USER 取当前登录用户名 */
    private String resolveDimension(LimitType type) {
        if (LimitType.USER == type) {
            return StpUtil.getLoginIdAsString();
        }
        return IpUtil.getRealIp(currentRequest());
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new BizException("无法获取请求上下文，限流失败");
        }
        return attributes.getRequest();
    }
}
