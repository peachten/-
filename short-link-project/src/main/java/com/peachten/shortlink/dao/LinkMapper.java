package com.peachten.shortlink.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.peachten.shortlink.entity.TLink;
import com.peachten.shortlink.entity.TLinkAccessHourStats;
import com.peachten.shortlink.entity.TLinkAccessStats;
import com.peachten.shortlink.entity.TLinkBrowserStats;
import com.peachten.shortlink.entity.TLinkDeviceStats;
import com.peachten.shortlink.entity.TLinkLocaleStats;
import com.peachten.shortlink.entity.TLinkOsStats;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 短链表 Mapper
 */
public interface LinkMapper extends BaseMapper<TLink> {

    /** 所有存在有效短链的分组标识，用于布隆过滤器预热 */
    List<String> selectAllValidGids();

    /** 按 gid 分批扫描有效短链（未删除且未禁用），用于布隆过滤器重建 */
    List<TLink> selectValidByGid(@Param("gid") String gid,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    /* ---------- 统计聚合：统一 INSERT ... ON DUPLICATE KEY UPDATE（覆盖写，重跑不翻倍） ---------- */

    int upsertAccessStats(TLinkAccessStats stats);

    int upsertHourStats(TLinkAccessHourStats stats);

    int upsertBrowserStats(TLinkBrowserStats stats);

    int upsertOsStats(TLinkOsStats stats);

    int upsertDeviceStats(TLinkDeviceStats stats);

    int upsertLocaleStats(TLinkLocaleStats stats);

    /** 清理指定时间之前的访问日志 */
    int deleteAccessLogsBefore(@Param("deadline") LocalDateTime deadline);
}
