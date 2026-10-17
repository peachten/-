package com.peachten.shortlink.mq.producer;

import com.peachten.shortlink.mq.message.AccessLogMessage;
import com.peachten.shortlink.service.AccessLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccessLogProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private AccessLogService accessLogService;

    @InjectMocks
    private AccessLogProducer accessLogProducer;

    private AccessLogMessage buildMessage() {
        return AccessLogMessage.builder().gid("g1").shortUri("abc123").build();
    }

    @Test
    void 发送成功_不降级() {
        AccessLogMessage message = buildMessage();

        accessLogProducer.send(message);

        // msgId 为空时自动补齐
        assertNotNull(message.getMsgId());
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), eq(message), any(MessagePostProcessor.class));
        verify(accessLogService, never()).saveSync(any());
    }

    @Test
    void 发送失败_降级同步落库且不抛异常() {
        AccessLogMessage message = buildMessage();
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));

        assertDoesNotThrow(() -> accessLogProducer.send(message));

        verify(accessLogService).saveSync(message);
    }
}
