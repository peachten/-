package com.peachten.shortlink.service;

import com.peachten.shortlink.dto.ShortLinkCreateReqDTO;
import com.peachten.shortlink.vo.ShortLinkCreateRespVO;

public interface ShortLinkService {

    /**
     * 创建短链（幂等）
     * <p>
     * 同一分组下同一长链重复提交只会有一条记录，重复提交返回已存在的短链且 idempotent=true。
     */
    ShortLinkCreateRespVO create(ShortLinkCreateReqDTO req);

    /**
     * 解析短链对应的原始链接（跳转用，对外无鉴权）
     * <p>
     * 顺序：格式预校验 → 布隆过滤器 → 缓存 / 空值缓存 → 查库 → 回写缓存。
     *
     * @return 原始链接；短链不存在 / 已删除 / 已禁用 / 已过期时返回 null
     */
    String restoreUrl(String gid, String shortUri);
}
