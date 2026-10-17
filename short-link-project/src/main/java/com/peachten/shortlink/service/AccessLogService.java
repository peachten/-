package com.peachten.shortlink.service;

import com.peachten.shortlink.mq.message.AccessLogMessage;

public interface AccessLogService {

    /**
     * 落访问日志，按 msg_id 幂等：
     * 重复消息撞唯一索引时视为成功（消费端可直接 ACK）。
     */
    void saveIdempotent(AccessLogMessage message);

    /** MQ 发送失败时的降级路径：同步落库（复用同一套幂等写入） */
    void saveSync(AccessLogMessage message);
}
