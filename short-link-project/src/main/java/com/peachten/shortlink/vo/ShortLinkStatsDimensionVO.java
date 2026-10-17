package com.peachten.shortlink.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 维度统计出参（浏览器 / OS / 设备 / 地区），name-value 结构直接给 ECharts 饼图
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsDimensionVO {

    private String name;

    private Long value;
}
