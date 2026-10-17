package com.peachten.shortlink.vo;

import lombok.Data;

import java.util.List;

/**
 * 短链汇总统计出参（可直接喂给 ECharts）
 */
@Data
public class ShortLinkStatsRespVO {

    private Integer pv;

    private Integer uv;

    private Integer uip;

    /** 24 小时分布，索引 0~23 对应 0~23 点 */
    private List<Integer> hourStats;

    /** 按天趋势（历史来自 DB，今天来自 Redis） */
    private List<ShortLinkStatsDailyVO> daily;
}
