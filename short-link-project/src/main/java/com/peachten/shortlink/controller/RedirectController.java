package com.peachten.shortlink.controller;

import com.peachten.shortlink.common.annotation.RateLimit;
import com.peachten.shortlink.common.enums.LimitType;
import com.peachten.shortlink.service.ShortLinkService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;

/**
 * 短链跳转（对外，无鉴权）
 * <p>
 * 路径变量都用正则约束：既避免和静态资源 / 固定路径冲突，
 * 也让非法后缀在路由层就被挡掉。
 */
@Controller
@RequiredArgsConstructor
public class RedirectController {

    private static final String NOT_FOUND_PAGE = "/page/notfound.html";

    private final ShortLinkService shortLinkService;

    @Value("${short-link.default-gid:default}")
    private String defaultGid;

    @GetMapping("/page/notfound")
    public String notFoundPage() {
        return "forward:" + NOT_FOUND_PAGE;
    }

    /** 不带分组：使用默认分组的短链 */
    @RateLimit(type = LimitType.IP, window = 60, limit = 300)
    @GetMapping("/{shortUri:[0-9a-zA-Z]{6}}")
    public void redirectByDefaultGid(@PathVariable String shortUri,
                                     HttpServletRequest request,
                                     HttpServletResponse response) throws IOException, ServletException {
        doRedirect(defaultGid, shortUri, request, response);
    }

    /** 带分组：/{gid}/{shortUri} */
    @RateLimit(type = LimitType.IP, window = 60, limit = 300)
    @GetMapping("/{gid:[0-9a-zA-Z]+}/{shortUri:[0-9a-zA-Z]{6}}")
    public void redirectByGid(@PathVariable String gid,
                              @PathVariable String shortUri,
                              HttpServletRequest request,
                              HttpServletResponse response) throws IOException, ServletException {
        doRedirect(gid, shortUri, request, response);
    }

    private void doRedirect(String gid, String shortUri,
                           HttpServletRequest request,
                           HttpServletResponse response) throws IOException, ServletException {
        String originUrl = shortLinkService.restoreUrl(gid, shortUri);
        if (originUrl == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            request.getRequestDispatcher(NOT_FOUND_PAGE).forward(request, response);
            return;
        }
        // 必须 302：301 会被浏览器长期缓存，后续访问不再经过服务端，统计不到数据
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", originUrl);
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
    }
}
