package com.peachten.shortlink.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 鉴权拦截器
 * <p>
 * 默认全部接口都要登录，白名单里的放行。跳转接口（/{shortUri}）在白名单里随 W2 一起补。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                // 只拦截管理端接口；跳转接口 /{shortUri}、/{gid}/{shortUri}、404 页面、静态资源天然放行
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/short-link/admin/v1/user/login",
                        "/api/short-link/admin/v1/user/register",
                        "/api/short-link/admin/v1/user/has-username",
                        "/api/short-link/admin/v1/health"
                );
    }
}
