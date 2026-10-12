package com.peachten.shortlink.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IpUtilTest {

    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void XForwardedFor_取第一个非unknown的IP() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown, 1.2.3.4, 5.6.7.8");

        assertEquals("1.2.3.4", IpUtil.getRealIp(request));
    }

    @Test
    void XForwardedFor_全是unknown时回落到XRealIP() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("X-Real-IP")).thenReturn("9.9.9.9");

        assertEquals("9.9.9.9", IpUtil.getRealIp(request));
    }

    @Test
    void 代理头都没有_取remoteAddr() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertEquals("127.0.0.1", IpUtil.getRealIp(request));
    }
}
