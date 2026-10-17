package com.peachten.shortlink.common.constant;

/**
 * RabbitMQ 常量
 * <p>
 * 队列/交换机名统一在这里定义，配置类与 @RabbitListener 一律引用，避免两处不一致导致监听不到。
 */
public final class MQConstant {

    /** 业务交换机（topic） */
    public static final String EXCHANGE = "short-link.topic";

    /** 访问日志队列 */
    public static final String ACCESS_LOG_QUEUE = "short-link.access-log.queue";

    /** 访问日志 routing key */
    public static final String ACCESS_LOG_ROUTING_KEY = "short-link.access-log";

    /** 死信交换机（direct） */
    public static final String DLX_EXCHANGE = "short-link.dlx";

    /** 死信队列 */
    public static final String DLX_QUEUE = "short-link.access-log.dlq";

    /** 死信 routing key */
    public static final String DLX_ROUTING_KEY = "short-link.access-log.dlq";

    private MQConstant() {
    }
}
