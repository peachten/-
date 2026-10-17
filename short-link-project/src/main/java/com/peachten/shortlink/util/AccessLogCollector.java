package com.peachten.shortlink.util;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.peachten.shortlink.entity.TLink;
import com.peachten.shortlink.mq.message.AccessLogMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * 访问日志采集：从请求里取 IP / UA / 语言，组装成消息体
 */
public final class AccessLogCollector {

    private static final String UNKNOWN = "unknown";
    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final String DEVICE_MOBILE = "mobile";
    private static final String DEVICE_PC = "pc";

    private AccessLogCollector() {
    }

    /**
     * @param link      命中的短链记录；缓存命中时没有查库，可为 null
     * @param gid       分组
     * @param shortUri  短链后缀
     * @param domain    短链域名，用于在 link 为空时拼出完整短链
     */
    public static AccessLogMessage build(TLink link, String gid, String shortUri, String domain) {
        HttpServletRequest request = currentRequest();
        String ip = request == null ? UNKNOWN : IpUtil.getRealIp(request);
        String userAgent = request == null ? UNKNOWN : nullToUnknown(request.getHeader(HEADER_USER_AGENT));
        UserAgent ua = parseUserAgent(userAgent);

        return AccessLogMessage.builder()
                .msgId(UUID.randomUUID().toString())
                .linkId(link == null ? null : link.getId())
                .fullShortUrl(link == null
                        ? LinkUtil.buildFullShortUrl(domain, gid, shortUri)
                        : link.getFullShortUrl())
                .gid(gid)
                .shortUri(shortUri)
                // UV 去重键：同一 IP + 同一 UA 视为同一访客
                .user(DigestUtil.md5Hex(ip + "|" + userAgent))
                .ip(ip)
                .browser(ua == null ? UNKNOWN : nullToUnknown(ua.getBrowser().getName()))
                .os(ua == null ? UNKNOWN : nullToUnknown(ua.getOs().getName()))
                .deviceType(ua == null ? UNKNOWN : (ua.isMobile() ? DEVICE_MOBILE : DEVICE_PC))
                .locale(request == null ? UNKNOWN : String.valueOf(request.getLocale()))
                .accessTime(System.currentTimeMillis())
                .build();
    }

    /** 畸形 UA 不能让跳转链路报错 */
    private static UserAgent parseUserAgent(String userAgent) {
        try {
            return UserAgentUtil.parse(userAgent);
        } catch (Exception e) {
            return null;
        }
    }

    private static String nullToUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }
}
