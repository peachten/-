package com.peachten.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 操作系统维度统计（按天）
 */
@Data
@TableName("t_link_os_stats")
public class TLinkOsStats {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fullShortUrl;

    private LocalDate date;

    private String os;

    private Integer cnt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
