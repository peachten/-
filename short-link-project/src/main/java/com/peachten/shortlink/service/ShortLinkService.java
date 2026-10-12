package com.peachten.shortlink.service;

import com.peachten.shortlink.common.result.PageResult;
import com.peachten.shortlink.dto.ShortLinkCreateReqDTO;
import com.peachten.shortlink.dto.ShortLinkPageReqDTO;
import com.peachten.shortlink.dto.ShortLinkUpdateReqDTO;
import com.peachten.shortlink.vo.ShortLinkCreateRespVO;
import com.peachten.shortlink.vo.ShortLinkPageRespVO;

import java.util.List;

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

    /** 分页查询当前用户的短链（gid / 关键词 / 排序） */
    PageResult<ShortLinkPageRespVO> pageLink(ShortLinkPageReqDTO req);

    /** 修改短链（备注 / 有效期 / 分组），并清理缓存 */
    void updateLink(ShortLinkUpdateReqDTO req);

    /** 批量修改短链 */
    void updateBatch(List<ShortLinkUpdateReqDTO> reqList);

    /** 启用 / 禁用短链，并清理缓存 */
    void enableLink(String fullShortUrl, Integer enableStatus);

    /** 删除短链（逻辑删除），并清理缓存 */
    void deleteLink(List<String> fullShortUrls);

    /** 分组内短链数量 */
    long countLink(String gid);
}
