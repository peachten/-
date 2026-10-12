package com.peachten.shortlink.common.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.peachten.shortlink.common.annotation.RateLimit;
import com.peachten.shortlink.common.component.RedisLuaExecutor;
import com.peachten.shortlink.common.enums.LimitType;
import com.peachten.shortlink.common.exception.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    /** 借一个带注解的真实方法，供切面反射取注解 */
    static class Sample {
        @RateLimit(type = LimitType.USER, window = 60, limit = 30)
        public String limited() {
            return "ok";
        }
    }

    @Mock
    private RedisLuaExecutor redisLuaExecutor;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature methodSignature;

    @InjectMocks
    private RateLimitAspect rateLimitAspect;

    private void stubJoinPoint() throws NoSuchMethodException {
        Method method = Sample.class.getMethod("limited");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new Sample());
    }

    @Test
    void 超过阈值_抛业务异常且不执行目标方法() throws Throwable {
        stubJoinPoint();
        when(redisLuaExecutor.tryAcquire(anyString(), eq(60), eq(30))).thenReturn(false);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("zhangsan");

            BizException ex = assertThrows(BizException.class, () -> rateLimitAspect.around(joinPoint));

            assertEquals("访问过于频繁，请稍后重试", ex.getMessage());
            verify(joinPoint, never()).proceed();
            // 限流 key 必须是「维度类型 + 维度值」
            verify(redisLuaExecutor).tryAcquire("short-link:rate-limit:user:zhangsan", 60, 30);
        }
    }

    @Test
    void 未超阈值_放行并执行目标方法() throws Throwable {
        stubJoinPoint();
        when(redisLuaExecutor.tryAcquire(anyString(), eq(60), eq(30))).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("zhangsan");

            assertEquals("ok", rateLimitAspect.around(joinPoint));
            verify(joinPoint).proceed();
        }
    }
}
