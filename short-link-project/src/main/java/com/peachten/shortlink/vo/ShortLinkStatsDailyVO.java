package com.peachten.shortlink.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 每日统计出参
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsDailyVO {

    private LocalDate date;

    private Integer pv;

    private Integer uv;

    private Integer uip;
}
