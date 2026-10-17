package com.peachten.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 24 小时访问分布（按天）
 */
@Data
@TableName("t_link_access_hour_stats")
public class TLinkAccessHourStats {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fullShortUrl;

    private LocalDate date;

    @TableField("hour_00")
    private Integer hour00;
    @TableField("hour_01")
    private Integer hour01;
    @TableField("hour_02")
    private Integer hour02;
    @TableField("hour_03")
    private Integer hour03;
    @TableField("hour_04")
    private Integer hour04;
    @TableField("hour_05")
    private Integer hour05;
    @TableField("hour_06")
    private Integer hour06;
    @TableField("hour_07")
    private Integer hour07;
    @TableField("hour_08")
    private Integer hour08;
    @TableField("hour_09")
    private Integer hour09;
    @TableField("hour_10")
    private Integer hour10;
    @TableField("hour_11")
    private Integer hour11;
    @TableField("hour_12")
    private Integer hour12;
    @TableField("hour_13")
    private Integer hour13;
    @TableField("hour_14")
    private Integer hour14;
    @TableField("hour_15")
    private Integer hour15;
    @TableField("hour_16")
    private Integer hour16;
    @TableField("hour_17")
    private Integer hour17;
    @TableField("hour_18")
    private Integer hour18;
    @TableField("hour_19")
    private Integer hour19;
    @TableField("hour_20")
    private Integer hour20;
    @TableField("hour_21")
    private Integer hour21;
    @TableField("hour_22")
    private Integer hour22;
    @TableField("hour_23")
    private Integer hour23;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
