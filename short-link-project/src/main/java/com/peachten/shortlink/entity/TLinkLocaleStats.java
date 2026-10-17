package com.peachten.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 地区维度统计（按天）
 */
@Data
@TableName("t_link_locale_stats")
public class TLinkLocaleStats {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fullShortUrl;

    private LocalDate date;

    private String locale;

    private Integer cnt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
