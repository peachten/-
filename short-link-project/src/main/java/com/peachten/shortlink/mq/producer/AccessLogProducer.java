package com.peachten.shortlink.mq.producer;

import com.peachten.shortlink.common.constant.MQConstant;
import com.peachten.shortlink.mq.message.AccessLogMessage;
import com.peachten.shortlink.service.AccessLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 访问日志生产者
 * <p>
 * 核心原则：**发消息失败绝不能影响跳转**，失败时降级为同步落库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogProducer {

    private final RabbitTemplate rabbitTemplate;
    private final AccessLogService accessLogService;

    public void send(AccessLogMessage message) {
        if (message.getMsgId() == null) {
            message.setMsgId(UUID.randomUUID().toString());
        }
        try {
            rabbitTemplate.convertAndSend(
                    MQConstant.EXCHANGE,
                    MQConstant.ACCESS_LOG_ROUTING_KEY,
                    message,
                    msg -> {
                        // 消息级持久化：MQ 重启后消息不丢
                        msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return msg;
                    });
        } catch (Exception e) {
            log.error("访问日志消息发送失败，降级同步落库, msgId={}", message.getMsgId(), e);
            accessLogService.saveSync(message);
        }
    }
}
