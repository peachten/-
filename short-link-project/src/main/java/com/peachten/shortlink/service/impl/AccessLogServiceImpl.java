package com.peachten.shortlink.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.peachten.shortlink.dao.AccessLogMapper;
import com.peachten.shortlink.dao.LinkMapper;
import com.peachten.shortlink.entity.TLink;
import com.peachten.shortlink.entity.TLinkAccessLogs;
import com.peachten.shortlink.mq.message.AccessLogMessage;
import com.peachten.shortlink.service.AccessLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessLogServiceImpl implements AccessLogService {

    private final AccessLogMapper accessLogMapper;
    private final LinkMapper linkMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveIdempotent(AccessLogMessage message) {
        try {
            accessLogMapper.insert(convert(message));
        } catch (DuplicateKeyException e) {
            // MQ 至少投递一次：重复消息撞 uk_msg_id 不是错误，跳过即可（也不再累加点击）
            log.debug("访问日志重复消费，已忽略, msgId={}", message.getMsgId());
            return;
        }
        // 与明细插入同一事务：要么都成功，要么都回滚
        incrementClickNum(message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSync(AccessLogMessage message) {
        saveIdempotent(message);
    }

    /** 缓存命中路径拿不到 linkId，所以按 (gid, short_uri) 累加，uk_short_uri 走索引 */
    private void incrementClickNum(AccessLogMessage message) {
        if (message.getGid() == null || message.getShortUri() == null) {
            return;
        }
        linkMapper.update(null, Wrappers.lambdaUpdate(TLink.class)
                .eq(TLink::getGid, message.getGid())
                .eq(TLink::getShortUri, message.getShortUri())
                .setSql("click_num = click_num + 1"));
    }

    private TLinkAccessLogs convert(AccessLogMessage message) {
        TLinkAccessLogs entity = new TLinkAccessLogs();
        entity.setMsgId(message.getMsgId());
        entity.setLinkId(message.getLinkId());
        entity.setFullShortUrl(message.getFullShortUrl());
        entity.setGid(message.getGid());
        entity.setShortUri(message.getShortUri());
        entity.setUser(message.getUser());
        entity.setIp(message.getIp());
        entity.setBrowser(message.getBrowser());
        entity.setOs(message.getOs());
        entity.setDeviceType(message.getDeviceType());
        entity.setLocale(message.getLocale());
        entity.setAccessTime(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(message.getAccessTime()), ZoneId.systemDefault()));
        return entity;
    }
}
