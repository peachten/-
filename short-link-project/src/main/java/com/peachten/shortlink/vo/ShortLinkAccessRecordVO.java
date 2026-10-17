package com.peachten.shortlink.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 访问明细出参
 */
@Data
public class ShortLinkAccessRecordVO {

    private String fullShortUrl;

    private String ip;

    private String browser;

    private String os;

    private String deviceType;

    private String locale;

    private LocalDateTime accessTime;
}
