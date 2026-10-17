package com.peachten.shortlink.common.config;

import com.peachten.shortlink.common.constant.MQConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑
 * <pre>
 * short-link.topic (topic) --short-link.access-log--> short-link.access-log.queue
 *                                                          | 消费失败/超时
 *                                                          v
 * short-link.dlx (direct) --short-link.access-log.dlq--> short-link.access-log.dlq
 * </pre>
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    // ---------- 业务交换机与队列 ----------

    @Bean
    public TopicExchange shortLinkExchange() {
        return new TopicExchange(MQConstant.EXCHANGE, true, false);
    }

    @Bean
    public Queue accessLogQueue() {
        return QueueBuilder.durable(MQConstant.ACCESS_LOG_QUEUE)
                // 死信出口：消息被 reject/nack 不重回队列、或超过 TTL 时转到这里
                .deadLetterExchange(MQConstant.DLX_EXCHANGE)
                .deadLetterRoutingKey(MQConstant.DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding accessLogBinding() {
        return BindingBuilder.bind(accessLogQueue()).to(shortLinkExchange())
                .with(MQConstant.ACCESS_LOG_ROUTING_KEY);
    }

    // ---------- 死信交换机与队列 ----------

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(MQConstant.DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(MQConstant.DLX_QUEUE).build();
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(MQConstant.DLX_ROUTING_KEY);
    }

    // ---------- 消息转换与模板 ----------

    /**
     * 必须配 JSON 转换器，否则消息体是 Java 序列化二进制，Console 里看不到内容。
     * 自动设为容器与 RabbitTemplate 的唯一 MessageConverter。
     */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        // 自动带 messageId，消费端可用于幂等
        converter.setCreateMessageIds(true);
        return converter;
    }

    /**
     * mandatory=true：消息投递到交换机但路由不到队列时，会触发 ReturnsCallback 而不是被静默丢弃。
     * <p>
     * ConfirmCallback：消息到达交换机后回调（ack=false 表示没到交换机）；
     * ReturnsCallback：到了交换机但路由不到队列。
     */
    @Bean
    public RabbitTemplateCustomizer rabbitTemplateCustomizer() {
        return template -> {
            template.setMandatory(true);

            template.setConfirmCallback((correlationData, ack, cause) -> {
                if (ack) {
                    log.debug("消息已到达交换机, correlationData={}", correlationData);
                } else {
                    log.error("消息未到达交换机, correlationData={}, cause={}", correlationData, cause);
                }
            });

            template.setReturnsCallback(returned -> log.error(
                    "消息路由失败, exchange={}, routingKey={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        };
    }

    /**
     * 启动时主动声明一次拓扑。
     * <p>
     * RabbitAdmin 默认是「连接建立时」才声明队列/交换机；本模块在没有监听器、也没发过消息之前
     * 不会建立连接（实测 connections=0），控制台里就看不到拓扑。这里启动时强制初始化一次。
     * <p>
     * 注意必须按 {@link AmqpAdmin} 注入：Boot 自动配置的 Bean 声明类型是 AmqpAdmin，
     * 按具体类 RabbitAdmin 注入会报 No qualifying bean。
     * <p>
     * 声明失败不阻断启动：MQ 暂时不可用时应用照常起，等恢复后首次连接会自动声明。
     */
    @Bean
    public ApplicationRunner rabbitTopologyInitializer(AmqpAdmin amqpAdmin) {
        return args -> {
            if (amqpAdmin instanceof RabbitAdmin rabbitAdmin) {
                try {
                    rabbitAdmin.initialize();
                } catch (Exception e) {
                    log.warn("RabbitMQ 拓扑初始化失败，将在连接恢复后自动声明", e);
                }
            }
        };
    }
}
