package com.peachten.shortlink.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 访问日志明细
 */
@Data
@TableName("t_link_access_logs")
public class TLinkAccessLogs {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** MQ 消息唯一 ID，唯一索引 uk_msg_id 保证消费幂等 */
    private String msgId;

    private Long linkId;

    private String fullShortUrl;

    private String gid;

    private String shortUri;

    /** UV 去重键：md5(ip + "|" + userAgent) */
    private String user;

    private String ip;

    private String browser;

    private String os;

    private String deviceType;

    private String locale;

    private LocalDateTime accessTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
