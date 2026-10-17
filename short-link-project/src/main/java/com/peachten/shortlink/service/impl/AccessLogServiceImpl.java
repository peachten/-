package com.peachten.shortlink.service.impl;

import com.peachten.shortlink.dao.AccessLogMapper;
import com.peachten.shortlink.entity.TLinkAccessLogs;
import com.peachten.shortlink.mq.message.AccessLogMessage;
import com.peachten.shortlink.service.AccessLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessLogServiceImpl implements AccessLogService {

    private final AccessLogMapper accessLogMapper;

    @Override
    public void saveIdempotent(AccessLogMessage message) {
        TLinkAccessLogs entity = convert(message);
        try {
            accessLogMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 同一 msgId 被重复消费：不是错误，直接当作成功
            log.debug("访问日志重复消费，已忽略, msgId={}", message.getMsgId());
        }
    }

    @Override
    public void saveSync(AccessLogMessage message) {
        saveIdempotent(message);
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
