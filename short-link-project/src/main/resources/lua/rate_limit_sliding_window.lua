-- 滑动窗口限流
-- KEYS[1] 限流 key（short-link:rate-limit:{type}:{维度值}）
-- ARGV[1] 当前时间戳(ms)  ARGV[2] 窗口大小(ms)  ARGV[3] 阈值  ARGV[4] 请求唯一标识
--
-- 为什么必须用 Lua：ZREMRANGEBYSCORE + ZCARD + ZADD 三条命令若分开执行，
-- 并发下会「先读后写」产生竞态导致超发，Lua 在 Redis 单线程内原子执行。
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1] - ARGV[2])
local count = redis.call('ZCARD', KEYS[1])
if count < tonumber(ARGV[3]) then
    redis.call('ZADD', KEYS[1], ARGV[1], ARGV[4])
    redis.call('PEXPIRE', KEYS[1], ARGV[2])
    return 1
else
    return 0
end
