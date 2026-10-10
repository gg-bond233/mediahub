-- 1.参数列表  ARGV 是 Redis 传给 Lua 脚本的参数数组，从 1 开始。
-- 1.1.优惠券id
local collectibleId = ARGV[1]
-- 1.2.用户id
local userId = ARGV[2]
-- 1.3.订单id
local orderId = ARGV[3]

-- 2.数据key  .. 是 Lua 的字符串拼接。
-- 2.1.库存key
local stockKey = 'seckill:stock:' .. collectibleId
-- 2.2.订单key
local orderKey = 'seckill:order:' .. collectibleId

--[[ redis.call('get', stockKey)：执行 Redis 的 GET 命令，获取库存值。
     tonumber(...)：将字符串转为数字。 ]]
-- 3.脚本业务
-- 3.1.判断库存是否充足 get stockKey
if(tonumber(redis.call('get', stockKey)) <= 0) then
    -- 3.2.库存不足，返回1
    return 1
end
-- 3.2.判断用户是否下单 SISMEMBER orderKey userId
if(redis.call('sismember', orderKey, userId) == 1) then
    -- 3.3.存在，说明是重复下单，返回2
    return 2
end
-- 3.4.扣库存 incrby stockKey -1
redis.call('incrby', stockKey, -1)
-- 3.5.下单（保存用户）sadd orderKey userId
redis.call('sadd', orderKey, userId)
-- 3.6.发送消息到队列中， XADD stream.orders * k1 v1 k2 v2 ...  *：让 Redis 自动生成消息 ID（时间戳-序号）
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'collectibleId', collectibleId, 'id', orderId)
return 0