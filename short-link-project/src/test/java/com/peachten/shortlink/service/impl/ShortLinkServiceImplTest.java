package com.peachten.shortlink.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.peachten.shortlink.common.constant.RedisKeyConstant;
import com.peachten.shortlink.common.exception.BizException;
import com.peachten.shortlink.common.result.ResultCode;
import com.peachten.shortlink.component.DelayedCacheDeleter;
import com.peachten.shortlink.dao.LinkMapper;
import com.peachten.shortlink.dto.ShortLinkCreateReqDTO;
import com.peachten.shortlink.dto.ShortLinkPageReqDTO;
import com.peachten.shortlink.dto.ShortLinkUpdateReqDTO;
import com.peachten.shortlink.mq.producer.AccessLogProducer;
import com.peachten.shortlink.entity.TGroup;
import com.peachten.shortlink.entity.TLink;
import com.peachten.shortlink.service.BloomFilterService;
import com.peachten.shortlink.service.GroupService;
import com.peachten.shortlink.util.ShortUriGenerator;
import com.peachten.shortlink.vo.ShortLinkCreateRespVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 创建短链单测：纯 Mockito，覆盖幂等（三段式）、过期、禁用/启用默认值
 */
@ExtendWith(MockitoExtension.class)
class ShortLinkServiceImplTest {

    private static final String GID = "Ab3xK9";
    private static final String DOMAIN = "http://localhost:8000";
    private static final String SHORT_URI = "9aK2mZ";
    private static final String ORIGIN_URL = "https://example.com/very/long/path?x=1";

    @Mock
    private LinkMapper linkMapper;
    @Mock
    private GroupService groupService;
    @Mock
    private ShortUriGenerator shortUriGenerator;
    @Mock
    private BloomFilterService bloomFilterService;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private SetOperations<String, String> setOperations;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private DelayedCacheDeleter delayedCacheDeleter;
    @Mock
    private AccessLogProducer accessLogProducer;

    @InjectMocks
    private ShortLinkServiceImpl shortLinkService;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // MyBatis-Plus 的 LambdaWrapper 需要 TableInfo 缓存（正常由 MP 启动时初始化）。
        // 纯单测没有 MP 上下文，这里手动初始化一次，避免用例之间依赖执行顺序。
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TLink.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(shortLinkService, "defaultDomain", DOMAIN);
        // 创建链路用；跳转（restoreUrl）不经过归属校验，故用 lenient 避免多余存根告警
        lenient().when(groupService.checkOwnership(GID)).thenReturn(new TGroup());
    }

    /** ── 幂等：两次创建同一长链，只落一条 ── */
    @Test
    void create_同一分组同一长链_两次创建只落一条且第二次幂等() throws InterruptedException {
        stubLock();
        when(shortUriGenerator.generateUniqueShortUri(GID)).thenReturn(SHORT_URI);

        AtomicReference<TLink> saved = new AtomicReference<>();
        AtomicInteger selectTimes = new AtomicInteger();
        // 第 1 次创建：快路径 null、双重检查 null；第 2 次创建：快路径直接命中
        when(linkMapper.selectOne(any())).thenAnswer(inv ->
                selectTimes.incrementAndGet() <= 2 ? null : saved.get());
        when(linkMapper.insert(any(TLink.class))).thenAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return 1;
        });
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        ShortLinkCreateRespVO first = shortLinkService.create(buildReq());
        ShortLinkCreateRespVO second = shortLinkService.create(buildReq());

        assertFalse(first.getIdempotent());
        assertTrue(second.getIdempotent());
        assertEquals(DOMAIN + "/" + GID + "/" + SHORT_URI, first.getFullShortUrl());
        assertEquals(first.getFullShortUrl(), second.getFullShortUrl());
        // 两条请求，只插入了一条
        verify(linkMapper, times(1)).insert(any(TLink.class));
    }

    /** ── 幂等：锁内双重检查命中（并发下被其他线程抢先创建） ── */
    @Test
    void create_锁内双重检查命中_不再插入() throws InterruptedException {
        stubLock();
        TLink exist = existLink();
        when(linkMapper.selectOne(any())).thenReturn(null, exist);

        ShortLinkCreateRespVO resp = shortLinkService.create(buildReq());

        assertTrue(resp.getIdempotent());
        verify(linkMapper, never()).insert(any(TLink.class));
    }

    /** ── 幂等：唯一索引兜底，insert 撞唯一索引后转幂等返回 ── */
    @Test
    void create_插入撞唯一索引_转幂等返回不抛异常() throws InterruptedException {
        stubLock();
        when(shortUriGenerator.generateUniqueShortUri(GID)).thenReturn(SHORT_URI);
        when(linkMapper.selectOne(any())).thenReturn(null, null, existLink());
        when(linkMapper.insert(any(TLink.class))).thenThrow(new DuplicateKeyException("dup"));

        ShortLinkCreateRespVO resp = shortLinkService.create(buildReq());

        assertTrue(resp.getIdempotent());
    }

    /** ── 过期：自定义有效期早于当前时间，直接拒绝 ── */
    @Test
    void create_失效时间早于当前时间_抛业务异常() {
        ShortLinkCreateReqDTO req = buildReq();
        req.setValidDateType(1);
        req.setValidDate(LocalDateTime.now().minusDays(1));

        BizException ex = assertThrows(BizException.class, () -> shortLinkService.create(req));

        assertTrue(ex.getMessage().contains("晚于当前时间"));
        verify(linkMapper, never()).insert(any(TLink.class));
        verify(shortUriGenerator, never()).generateUniqueShortUri(anyString());
    }

    /** ── 过期：自定义有效期晚于当前时间，正常创建并写入失效时间 ── */
    @Test
    void create_自定义有效期_落库写入失效时间() throws InterruptedException {
        stubLock();
        LocalDateTime validDate = LocalDateTime.now().plusDays(30);
        stubInsertOk();

        ShortLinkCreateReqDTO req = buildReq();
        req.setValidDateType(1);
        req.setValidDate(validDate);

        shortLinkService.create(req);

        TLink link = captureInserted();
        assertEquals(1, link.getValidDateType().intValue());
        assertEquals(validDate, link.getValidDate());
    }

    /** ── 禁用：新建短链默认是启用状态（enable_status = 0）且永久有效 ── */
    @Test
    void create_新建短链默认启用且永久有效() throws InterruptedException {
        stubLock();
        stubInsertOk();

        ShortLinkCreateRespVO resp = shortLinkService.create(buildReq());

        assertFalse(resp.getIdempotent());
        TLink link = captureInserted();
        assertEquals(0, link.getEnableStatus().intValue());     // 0 启用（未禁用）
        assertEquals(0, link.getValidDateType().intValue());    // 0 永久
        assertNull(link.getValidDate());
        assertEquals(0, link.getCreatedType().intValue());
        assertEquals(0, link.getClickNum().intValue());
        assertEquals(DOMAIN, link.getDomain());
        assertEquals(GID, link.getGid());
        verify(setOperations).add(anyString(), eq(SHORT_URI));
    }

    /** ── 跳转：缓存命中，直接返回且不查库 ── */
    @Test
    void restoreUrl_缓存命中_直接返回不查库() {
        when(bloomFilterService.mightContain(GID, SHORT_URI)).thenReturn(true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey())).thenReturn(ORIGIN_URL);

        assertEquals(ORIGIN_URL, shortLinkService.restoreUrl(GID, SHORT_URI));

        verify(linkMapper, never()).selectOne(any());
    }

    /** ── 跳转：命中空值缓存，直接 404 且不查库 ── */
    @Test
    void restoreUrl_空值缓存_返回null不查库() {
        when(bloomFilterService.mightContain(GID, SHORT_URI)).thenReturn(true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey())).thenReturn("");

        assertNull(shortLinkService.restoreUrl(GID, SHORT_URI));

        verify(linkMapper, never()).selectOne(any());
    }

    /** ── 跳转：布隆判 false，连缓存都不查（防缓存穿透关键路径） ── */
    @Test
    void restoreUrl_布隆判定不存在_返回null且不查缓存() {
        when(bloomFilterService.mightContain(GID, "zzzzzz")).thenReturn(false);

        assertNull(shortLinkService.restoreUrl(GID, "zzzzzz"));

        verify(stringRedisTemplate, never()).opsForValue();
        verify(linkMapper, never()).selectOne(any());
    }

    /** ── 跳转：后缀格式非法，连布隆都不查 ── */
    @Test
    void restoreUrl_后缀格式非法_返回null且不查布隆() {
        assertNull(shortLinkService.restoreUrl(GID, "abc"));

        verify(bloomFilterService, never()).mightContain(anyString(), anyString());
        verify(linkMapper, never()).selectOne(any());
    }

    /** ── 跳转：库里不存在，写空值缓存 ── */
    @Test
    void restoreUrl_库中不存在_写空值缓存并返回null() {
        stubCacheMiss();
        when(linkMapper.selectOne(any())).thenReturn(null);

        assertNull(shortLinkService.restoreUrl(GID, SHORT_URI));

        verify(valueOperations).set(cacheKey(), "", 60L, TimeUnit.SECONDS);
    }

    /** ── 跳转：短链被禁用，写空值缓存 ── */
    @Test
    void restoreUrl_短链已禁用_写空值缓存并返回null() {
        stubCacheMiss();
        TLink link = availableLink();
        link.setEnableStatus(1);
        when(linkMapper.selectOne(any())).thenReturn(link);

        assertNull(shortLinkService.restoreUrl(GID, SHORT_URI));

        verify(valueOperations).set(cacheKey(), "", 60L, TimeUnit.SECONDS);
    }

    /** ── 跳转：短链已过期，写空值缓存 ── */
    @Test
    void restoreUrl_短链已过期_写空值缓存并返回null() {
        stubCacheMiss();
        TLink link = availableLink();
        link.setValidDateType(1);
        link.setValidDate(LocalDateTime.now().minusDays(1));
        when(linkMapper.selectOne(any())).thenReturn(link);

        assertNull(shortLinkService.restoreUrl(GID, SHORT_URI));

        verify(valueOperations).set(cacheKey(), "", 60L, TimeUnit.SECONDS);
    }

    /** ── 跳转：正常回源，回写缓存且 TTL 带随机（防雪崩） ── */
    @Test
    void restoreUrl_正常回源_回写缓存且TTL为1天加随机值() {
        stubCacheMiss();
        when(linkMapper.selectOne(any())).thenReturn(availableLink());

        assertEquals(ORIGIN_URL, shortLinkService.restoreUrl(GID, SHORT_URI));

        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(valueOperations).set(eq(cacheKey()), eq(ORIGIN_URL), ttlCaptor.capture(), eq(TimeUnit.SECONDS));
        long ttl = ttlCaptor.getValue();
        assertTrue(ttl >= 86400 && ttl <= 90000, "TTL 应在 1 天 ~ 1 天 1 小时之间，实际=" + ttl);
    }

    /** ── 分页：排序字段必须走白名单，禁止 SQL 注入 ── */
    @Test
    void pageLink_非法排序字段_抛参数错误() {
        ShortLinkPageReqDTO req = new ShortLinkPageReqDTO();
        req.setGid(GID);
        req.setOrderTag("create_time;DROP TABLE t_link");

        BizException ex = assertThrows(BizException.class, () -> shortLinkService.pageLink(req));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(linkMapper, never()).selectPage(any(), any());
    }

    /** ── 修改：更新 DB 后清理缓存（延迟双删） ── */
    @Test
    void updateLink_修改后删除缓存() {
        TLink link = ownedLink();
        when(linkMapper.selectOne(any())).thenReturn(link);

        ShortLinkUpdateReqDTO req = new ShortLinkUpdateReqDTO();
        req.setFullShortUrl(link.getFullShortUrl());
        req.setDescribe("新的备注");
        shortLinkService.updateLink(req);

        verify(linkMapper).update(any(), any());
        verify(stringRedisTemplate).delete(cacheKey());
        verify(delayedCacheDeleter).deleteAfter(cacheKey(), 500L);
    }

    /** ── 禁用：更新状态并清理缓存 ── */
    @Test
    void enableLink_更新状态并删除缓存() {
        TLink link = ownedLink();
        when(linkMapper.selectOne(any())).thenReturn(link);

        shortLinkService.enableLink(link.getFullShortUrl(), 1);

        ArgumentCaptor<TLink> captor = ArgumentCaptor.forClass(TLink.class);
        verify(linkMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getEnableStatus().intValue());
        verify(stringRedisTemplate).delete(cacheKey());
    }

    /** ── 删除：逻辑删除 + 改写 origin_url_hash 腾出唯一索引位 ── */
    @Test
    void deleteLink_逻辑删除_改写hash腾出唯一索引并删除缓存() {
        String oldHash = "0123456789abcdef0123456789abcdef";
        TLink link = ownedLink();
        link.setId(9L);
        link.setOriginUrlHash(oldHash);
        when(linkMapper.selectOne(any())).thenReturn(link);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        shortLinkService.deleteLink(List.of(link.getFullShortUrl()));

        ArgumentCaptor<Wrapper<TLink>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(linkMapper).update(any(), captor.capture());
        Collection<Object> values = ((LambdaUpdateWrapper<TLink>) captor.getValue()).getParamNameValuePairs().values();
        assertTrue(values.contains(1), "del_flag 应被置为 1");
        assertTrue(values.stream().anyMatch(v -> v instanceof String s && s.length() == 32 && !s.equals(oldHash)),
                "origin_url_hash 应被改写成新的 32 位值，否则唯一索引位不释放");

        verify(stringRedisTemplate).delete(cacheKey());
        verify(setOperations).remove(anyString(), eq(SHORT_URI));
    }

    // ---------- helpers ----------

    private ShortLinkCreateReqDTO buildReq() {
        ShortLinkCreateReqDTO req = new ShortLinkCreateReqDTO();
        req.setOriginUrl(ORIGIN_URL);
        req.setGid(GID);
        return req;
    }

    private TLink existLink() {
        TLink link = new TLink();
        link.setId(1L);
        link.setGid(GID);
        link.setShortUri(SHORT_URI);
        link.setOriginUrl(ORIGIN_URL);
        link.setFullShortUrl(DOMAIN + "/" + GID + "/" + SHORT_URI);
        return link;
    }

    private void stubLock() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(3, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    private void stubInsertOk() {
        when(shortUriGenerator.generateUniqueShortUri(GID)).thenReturn(SHORT_URI);
        when(linkMapper.selectOne(any())).thenReturn(null);
        when(linkMapper.insert(any(TLink.class))).thenReturn(1);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
    }

    private TLink captureInserted() {
        ArgumentCaptor<TLink> captor = ArgumentCaptor.forClass(TLink.class);
        verify(linkMapper).insert(captor.capture());
        return captor.getValue();
    }

    /** 一条属于当前用户、可管理的短链（带 id 与完整短链） */
    private TLink ownedLink() {
        TLink link = availableLink();
        link.setId(9L);
        link.setFullShortUrl(DOMAIN + "/" + GID + "/" + SHORT_URI);
        link.setOriginUrlHash("0123456789abcdef0123456789abcdef");
        return link;
    }

    private String cacheKey() {
        return RedisKeyConstant.gotoCache(DOMAIN, GID, SHORT_URI);
    }

    /** 跳转缓存未命中（布隆 true + Redis get 返回 null） */
    private void stubCacheMiss() {
        when(bloomFilterService.mightContain(GID, SHORT_URI)).thenReturn(true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey())).thenReturn(null);
    }

    /** 一条可用短链：启用 + 永久有效 */
    private TLink availableLink() {
        TLink link = new TLink();
        link.setGid(GID);
        link.setShortUri(SHORT_URI);
        link.setOriginUrl(ORIGIN_URL);
        link.setEnableStatus(0);
        link.setValidDateType(0);
        return link;
    }
}
