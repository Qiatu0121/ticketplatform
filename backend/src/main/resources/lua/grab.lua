--!nocheck
-- 抢票预扣脚本
-- KEYS[1] = ticket:stock:{ticketId}   库存
-- KEYS[2] = ticket:buyers:{ticketId}  已购买用户集合
-- ARGV[1] = userId
-- ARGV[2] = count
-- 返回值：>=0 剩余库存；-1 库存不足；-2 该用户已购买

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -2
end

-- 库存 key 不存在时按 -1 处理，避免未初始化库存的票被卖出
local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
if stock < tonumber(ARGV[2]) then
    return -1
end

redis.call('DECRBY', KEYS[1], ARGV[2])
redis.call('SADD', KEYS[2], ARGV[1])

return stock - tonumber(ARGV[2])
