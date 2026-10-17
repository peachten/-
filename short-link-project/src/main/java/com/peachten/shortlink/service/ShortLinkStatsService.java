package com.peachten.shortlink.service;

import com.peachten.shortlink.mq.message.AccessLogMessage;

public interface ShortLinkStatsService {

    /** 记录一次访问的实时统计（PV / UV / UIP / 维度 / 小时分布），全部写 Redis */
    void recordAccess(AccessLogMessage message);
}
