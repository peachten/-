package com.peachten.shortlink.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短链分页出参
 */
@Data
public class ShortLinkPageRespVO {

    private Long id;

    private String domain;

    private String shortUri;

    private String fullShortUrl;

    private String originUrl;

    private String gid;

    private Integer clickNum;

    /** 0 启用 1 禁用 */
    private Integer enableStatus;

    /** 0 永久 1 自定义 */
    private Integer validDateType;

    private LocalDateTime validDate;

    private String description;

    private LocalDateTime createTime;
}
