package com.peachten.shortlink.common.enums;

/**
 * 限流维度
 */
public enum LimitType {

    /** 按客户端 IP 限流（跳转等对外接口） */
    IP,

    /** 按登录用户限流（需要登录的管理端接口） */
    USER;

    /** Redis key 中的维度标识，如 short-link:rate-limit:user:zhangsan */
    public String keyType() {
        return name().toLowerCase();
    }
}
