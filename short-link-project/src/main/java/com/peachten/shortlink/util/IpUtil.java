package com.peachten.shortlink.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端真实 IP 工具
 */
public final class IpUtil {

    private static final String UNKNOWN = "unknown";
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";

    private IpUtil() {
    }

    /**
     * 优先 X-Forwarded-For 里第一个非 unknown 的 IP，其次 X-Real-IP，最后 remoteAddr。
     * <p>
     * 本机直连时拿到的就是 127.0.0.1，属正常。
     */
    public static String getRealIp(HttpServletRequest request) {
        String forwarded = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (isValid(forwarded)) {
            // 多级代理时是一串 IP，取最左边的客户端 IP
            for (String part : forwarded.split(",")) {
                String candidate = part.trim();
                if (isValid(candidate)) {
                    return candidate;
                }
            }
        }
        String realIp = request.getHeader(HEADER_X_REAL_IP);
        if (isValid(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static boolean isValid(String ip) {
        return ip != null && !ip.isBlank() && !UNKNOWN.equalsIgnoreCase(ip.trim());
    }
}
