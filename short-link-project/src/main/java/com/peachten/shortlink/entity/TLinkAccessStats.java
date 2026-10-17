package com.peachten.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 短链访问统计（按天）
 */
@Data
@TableName("t_link_access_stats")
public class TLinkAccessStats {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fullShortUrl;

    private LocalDate date;

    private Integer pv;

    private Integer uv;

    private Integer uip;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
