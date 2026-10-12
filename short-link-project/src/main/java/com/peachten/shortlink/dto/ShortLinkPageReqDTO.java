package com.peachten.shortlink.dto;

import lombok.Data;

/**
 * 短链分页查询入参
 */
@Data
public class ShortLinkPageReqDTO {

    /** 分组标识，不传则查询当前用户所有分组 */
    private String gid;

    /** 关键词：匹配备注或原始链接 */
    private String keyword;

    /** 排序字段白名单：createTime / clickNum */
    private String orderTag;

    private Long current = 1L;

    private Long size = 10L;
}
