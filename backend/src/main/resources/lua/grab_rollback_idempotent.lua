--!nocheck
-- 幂等回补：同一个 token 只归还一次库存
-- KEYS[1] = ticket:stock:{ticketId}
-- KEYS[2] = ticket:buyers:{ticketId}
-- KEYS[3] = ticket:rollback:{token}
-- ARGV[1] = userId
-- ARGV[2] = count
-- ARGV[3] = 标记保留秒数

if redis.call('SETNX', KEYS[3], '1') == 0 then
    return 0
end

redis.call('EXPIRE', KEYS[3], ARGV[3])
redis.call('INCRBY', KEYS[1], ARGV[2])
redis.call('SREM', KEYS[2], ARGV[1])
return 1
