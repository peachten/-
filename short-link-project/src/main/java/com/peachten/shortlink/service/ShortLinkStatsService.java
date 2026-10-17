package com.peachten.shortlink.service;

import com.peachten.shortlink.common.result.PageResult;
import com.peachten.shortlink.dto.ShortLinkStatsAccessRecordReqDTO;
import com.peachten.shortlink.dto.ShortLinkStatsReqDTO;
import com.peachten.shortlink.mq.message.AccessLogMessage;
import com.peachten.shortlink.vo.ShortLinkAccessRecordVO;
import com.peachten.shortlink.vo.ShortLinkStatsDimensionVO;
import com.peachten.shortlink.vo.ShortLinkStatsRespVO;

import java.util.List;

public interface ShortLinkStatsService {

    /** 记录一次访问的实时统计（PV / UV / UIP / 维度 / 小时分布），全部写 Redis */
    void recordAccess(AccessLogMessage message);

    /** 汇总统计：PV / UV / UIP + 24 小时分布 + 按天趋势（今天读 Redis，历史读 DB） */
    ShortLinkStatsRespVO getGroupStats(ShortLinkStatsReqDTO req);

    /** 维度占比：dimension 取值 browser / os / device / locale */
    List<ShortLinkStatsDimensionVO> getDimensionStats(ShortLinkStatsReqDTO req, String dimension);

    /** 访问明细分页，按访问时间倒序 */
    PageResult<ShortLinkAccessRecordVO> pageAccessRecords(ShortLinkStatsAccessRecordReqDTO req);
}
