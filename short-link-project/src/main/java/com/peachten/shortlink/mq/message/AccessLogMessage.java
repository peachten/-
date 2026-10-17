package com.peachten.shortlink.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 访问日志消息体
 * <p>
 * accessTime 用 Long 毫秒时间戳而不是 LocalDateTime：
 * JSON 序列化不需要额外注册 JavaTimeModule，消费端也不会有「Java 8 date/time not supported」的坑。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessLogMessage {

    /** 消息唯一标识，消费端幂等用 */
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

    /** 访问时间（毫秒时间戳） */
    private Long accessTime;
}
