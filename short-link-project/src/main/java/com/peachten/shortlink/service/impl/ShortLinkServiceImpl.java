package com.peachten.shortlink.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.peachten.shortlink.common.constant.RedisKeyConstant;
import com.peachten.shortlink.common.exception.BizException;
import com.peachten.shortlink.common.result.ResultCode;
import com.peachten.shortlink.dao.LinkMapper;
import com.peachten.shortlink.dto.ShortLinkCreateReqDTO;
import com.peachten.shortlink.entity.TLink;
import com.peachten.shortlink.service.BloomFilterService;
import com.peachten.shortlink.service.GroupService;
import com.peachten.shortlink.service.ShortLinkService;
import com.peachten.shortlink.util.HashUtil;
import com.peachten.shortlink.util.LinkUtil;
import com.peachten.shortlink.util.ShortUriGenerator;
import com.peachten.shortlink.vo.ShortLinkCreateRespVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    /** 等锁时间：拿不到锁说明并发创建同一长链，直接提示稍后重试 */
    private static final long LOCK_WAIT_SECONDS = 3;

    /** 短链后缀格式：6 位 Base62 */
    private static final Pattern SHORT_URI_PATTERN = Pattern.compile("^[0-9a-zA-Z]{6}$");
    /** 跳转缓存基础 TTL：1 天，再叠加随机值防雪崩 */
    private static final long CACHE_BASE_SECONDS = Duration.ofDays(1).getSeconds();
    private static final int CACHE_RANDOM_BOUND = 3600;
    /** 空值缓存 TTL：60 秒 */
    private static final long NULL_CACHE_SECONDS = 60;

    private final LinkMapper linkMapper;
    private final GroupService groupService;
    private final ShortUriGenerator shortUriGenerator;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final BloomFilterService bloomFilterService;

    @Value("${short-link.domain.default:http://localhost:8000}")
    private String defaultDomain;

    @Override
    public ShortLinkCreateRespVO create(ShortLinkCreateReqDTO req) {
        // 0. 基础校验：分组归属 + 有效期/后缀等参数
        groupService.checkOwnership(req.getGid());
        validCreateParam(req);

        String originUrlHash = HashUtil.md5(req.getOriginUrl());

        // 1. 快路径：同一分组同一长链已存在，直接返回（幂等）
        TLink exist = findExist(req.getGid(), originUrlHash);
        if (exist != null) {
            return buildResp(exist, true);
        }

        // 2. 慢路径：Redisson 分布式锁 + 锁内双重检查
        RLock lock = redissonClient.getLock(RedisKeyConstant.lockCreate(req.getGid(), originUrlHash));
        boolean locked = false;
        try {
            // 二参版本，不指定 leaseTime，交给看门狗自动续期
            locked = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BizException("创建请求过于频繁，请稍后重试");
            }

            exist = findExist(req.getGid(), originUrlHash);   // 双重检查
            if (exist != null) {
                log.info("锁内双重检查命中，幂等返回, gid={}, originUrlHash={}", req.getGid(), originUrlHash);
                return buildResp(exist, true);
            }

            return doCreate(req, originUrlHash);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("创建被中断，请重试");
        } finally {
            // 必须判断持有者，避免业务超时后释放了别人的锁
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 真正落库。唯一索引 (gid, origin_url_hash) / (gid, short_uri) 是最后的兜底。
     */
    private ShortLinkCreateRespVO doCreate(ShortLinkCreateReqDTO req, String originUrlHash) {
        boolean custom = StringUtils.hasText(req.getCustomShortUri());
        String shortUri = custom
                ? req.getCustomShortUri()
                : shortUriGenerator.generateUniqueShortUri(req.getGid());

        int validDateType = req.getValidDateType() == null ? 0 : req.getValidDateType();

        TLink link = new TLink();
        link.setDomain(defaultDomain);
        link.setShortUri(shortUri);
        link.setFullShortUrl(LinkUtil.buildFullShortUrl(defaultDomain, req.getGid(), shortUri));
        link.setOriginUrl(req.getOriginUrl());
        link.setOriginUrlHash(originUrlHash);
        link.setGid(req.getGid());
        link.setEnableStatus(0);                                            // 0 启用
        link.setCreatedType(req.getCreatedType() == null ? 0 : req.getCreatedType());
        link.setValidDateType(validDateType);
        link.setValidDate(validDateType == 1 ? req.getValidDate() : null);  // 0 永久 1 自定义
        link.setDescription(req.getDescribe());
        link.setClickNum(0);

        try {
            linkMapper.insert(link);
        } catch (DuplicateKeyException e) {
            // 并发下被其他请求抢先插入：按幂等键查回，转为幂等返回
            TLink dup = findExist(req.getGid(), originUrlHash);
            if (dup != null) {
                return buildResp(dup, true);
            }
            // 幂等键查不到，说明撞的是 uk_short_uri：自定义后缀已被占用
            if (custom) {
                throw new BizException(ResultCode.SHORT_URI_EXIST);
            }
            throw new BizException("短链创建失败，请重试");
        }

        // 写入分组 Set（1 天过期），供后续后缀查重使用
        String setKey = RedisKeyConstant.gidSet(req.getGid());
        stringRedisTemplate.opsForSet().add(setKey, shortUri);
        stringRedisTemplate.expire(setKey, Duration.ofDays(1));

        // 写入布隆过滤器，跳转时用于拦截不存在的短链（防缓存穿透）
        bloomFilterService.add(req.getGid(), shortUri);

        return buildResp(link, false);
    }

    /**
     * 参数校验：自定义有效期必须晚于当前时间。
     * URL 协议、后缀字符集等已由 DTO 上的校验注解拦截。
     */
    private void validCreateParam(ShortLinkCreateReqDTO req) {
        if (Integer.valueOf(1).equals(req.getValidDateType())) {
            if (req.getValidDate() == null) {
                throw new BizException("自定义有效期必须填写失效时间");
            }
            if (!req.getValidDate().isAfter(LocalDateTime.now())) {
                throw new BizException("失效时间必须晚于当前时间");
            }
        }
    }

    @Override
    public String restoreUrl(String gid, String shortUri) {
        // 1. 格式预校验：连 Redis 都不查
        if (!isValidShortUri(shortUri)) {
            return null;
        }

        // 2. 布隆过滤器：false 表示一定不存在，直接结束（这是防缓存穿透的第一道防线）
        if (!bloomFilterService.mightContain(gid, shortUri)) {
            return null;
        }

        String cacheKey = RedisKeyConstant.gotoCache(defaultDomain, gid, shortUri);

        // 3. 查缓存：miss / 空值缓存 / 命中
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (cached.isEmpty()) {
                // 空值缓存：之前查过且不存在，直接 404
                return null;
            }
            logAccess(gid, shortUri);
            return cached;
        }

        // 4. 缓存未命中：查库
        TLink link = linkMapper.selectOne(Wrappers.lambdaQuery(TLink.class)
                .eq(TLink::getGid, gid)
                .eq(TLink::getShortUri, shortUri));
        // 逻辑删除的记录查不到；这里再挡掉「不存在 / 已禁用 / 已过期」
        if (link == null || Integer.valueOf(1).equals(link.getEnableStatus()) || isExpired(link)) {
            cacheNull(gid, shortUri);
            return null;
        }

        // 5. 回源：写缓存（随机 TTL 防雪崩）
        cacheOriginUrl(gid, shortUri, link.getOriginUrl());
        logAccess(gid, shortUri);
        return link.getOriginUrl();
    }

    /** 6 位 Base62 校验，长度或字符集不合法直接 404 */
    private boolean isValidShortUri(String shortUri) {
        return shortUri != null && SHORT_URI_PATTERN.matcher(shortUri).matches();
    }

    /** 有效期类型为自定义且已过期 */
    private boolean isExpired(TLink link) {
        return Integer.valueOf(1).equals(link.getValidDateType())
                && link.getValidDate() != null
                && link.getValidDate().isBefore(LocalDateTime.now());
    }

    /** 回写原始链接：TTL = 1 天 + 随机 0~3600 秒，避免大量 key 同时过期造成雪崩 */
    private void cacheOriginUrl(String gid, String shortUri, String originUrl) {
        long ttl = CACHE_BASE_SECONDS + ThreadLocalRandom.current().nextInt(CACHE_RANDOM_BOUND);
        stringRedisTemplate.opsForValue().set(
                RedisKeyConstant.gotoCache(defaultDomain, gid, shortUri), originUrl, ttl, TimeUnit.SECONDS);
    }

    /** 空值缓存：TTL 60 秒，挡住对同一个不存在短链的重复穿透 */
    private void cacheNull(String gid, String shortUri) {
        stringRedisTemplate.opsForValue().set(
                RedisKeyConstant.gotoCache(defaultDomain, gid, shortUri), "", NULL_CACHE_SECONDS, TimeUnit.SECONDS);
    }

    /** W2D4 先打印日志，第 3 周改为发 MQ 异步落库 */
    private void logAccess(String gid, String shortUri) {
        log.info("短链访问, gid={}, shortUri={}", gid, shortUri);
    }

    private TLink findExist(String gid, String originUrlHash) {
        return linkMapper.selectOne(Wrappers.lambdaQuery(TLink.class)
                .eq(TLink::getGid, gid)
                .eq(TLink::getOriginUrlHash, originUrlHash));
    }

    private ShortLinkCreateRespVO buildResp(TLink link, boolean idempotent) {
        ShortLinkCreateRespVO resp = new ShortLinkCreateRespVO();
        resp.setFullShortUrl(link.getFullShortUrl());
        resp.setShortUri(link.getShortUri());
        resp.setOriginUrl(link.getOriginUrl());
        resp.setIdempotent(idempotent);
        return resp;
    }
}
