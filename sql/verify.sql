-- =============================================================
-- W3D7 数据一致性校验
-- 用法：把 @full_short_url 改成要校验的短链后整体执行
-- 说明：排期文档里的示例带了 l.del_flag = 0，但 t_link_access_logs
--       实际上没有 del_flag 字段（明细只做定期物理清理），这里去掉。
-- =============================================================
SET @full_short_url = 'http://localhost:8000/testGp/HXnhmS';

-- 1. 明细条数 vs 统计表 PV（按天对比）
-- 期望：查询结果为空
SELECT
    l.full_short_url,
    DATE(l.access_time) AS stat_date,
    COUNT(*)            AS log_count,
    s.pv                AS stats_pv,
    COUNT(*) - IFNULL(s.pv, 0) AS diff
FROM t_link_access_logs l
LEFT JOIN t_link_access_stats s
       ON s.full_short_url = l.full_short_url
      AND s.date = DATE(l.access_time)
WHERE l.full_short_url = @full_short_url
GROUP BY l.full_short_url, DATE(l.access_time), s.pv
HAVING diff <> 0;

-- 2. 维度之和 vs 总 PV（同一天）
-- 期望：查询结果为空（浏览器维度之和 == 当天 PV）
SELECT
    s.full_short_url,
    s.date,
    s.pv,
    SUM(b.cnt)          AS browser_sum,
    s.pv - SUM(b.cnt)   AS diff
FROM t_link_access_stats s
JOIN t_link_browser_stats b
  ON b.full_short_url = s.full_short_url
 AND b.date = s.date
WHERE s.full_short_url = @full_short_url
GROUP BY s.full_short_url, s.date, s.pv
HAVING diff <> 0;
