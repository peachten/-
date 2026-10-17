package com.peachten.shortlink.mq.consumer;

import com.peachten.shortlink.common.constant.MQConstant;
import com.peachten.shortlink.mq.message.AccessLogMessage;
import com.peachten.shortlink.service.AccessLogService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 访问日志消费者
 * <p>
 * 手动 ACK：只有「业务成功」才 ack；异常一律 nack 且 requeue=false，交给死信队列，
 * 避免失败消息反复回队把队列和 CPU 打满。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogConsumer {

    private final AccessLogService accessLogService;

    @RabbitListener(queues = MQConstant.ACCESS_LOG_QUEUE)
    public void consume(AccessLogMessage message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            // 方法返回时事务已提交（saveIdempotent 上的 @Transactional），之后才 ACK
            accessLogService.saveIdempotent(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("访问日志落库失败，进入死信队列, msgId={}", message.getMsgId(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
