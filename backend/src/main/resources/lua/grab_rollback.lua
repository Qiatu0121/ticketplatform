--!nocheck
-- 回滚抢票预扣：归还库存并移除购买标记
-- KEYS[1] = ticket:stock:{ticketId}
-- KEYS[2] = ticket:buyers:{ticketId}
-- ARGV[1] = userId
-- ARGV[2] = count

redis.call('INCRBY', KEYS[1], ARGV[2])
redis.call('SREM', KEYS[2], ARGV[1])
return 1
