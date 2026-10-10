package com.mediahub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mediahub.result.Result;
import com.mediahub.entity.Collectible;
import com.mediahub.entity.CollectibleOrder;
import com.mediahub.entity.Media;
import com.mediahub.entity.SeckillCollectible;
import com.mediahub.mapper.CollectibleOrderMapper;
import com.mediahub.service.ICollectibleOrderService;
import com.mediahub.service.ICollectibleService;
import com.mediahub.service.IMediaService;
import com.mediahub.service.ISeckillCollectibleService;
import com.mediahub.utils.RedisIdWorker;
import com.mediahub.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 限量典藏抢购订单服务
 * <p>
 * 整体链路：Lua 脚本原子判定（库存 + 一人一单）→ 通过 Redis Stream 消息队列异步落库 → 消费端用 Redisson 分布式锁兜底。
 */
@Slf4j
@Service
public class  CollectibleOrderServiceImpl extends ServiceImpl<CollectibleOrderMapper, CollectibleOrder> implements ICollectibleOrderService {

    /**
     * 消息队列与消费组
     */
    private static final String STREAM_KEY = "stream.orders";
    private static final String STREAM_GROUP = "g1";
    private static final String STREAM_CONSUMER = "c1";

    @Resource
    private ISeckillCollectibleService seckillCollectibleService;

    @Resource
    private ICollectibleService collectibleService;

    @Resource
    private IMediaService mediaService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    //静态代码块：初始化 Lua 脚本
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //创建一个单线程的线程池。用于启动订单消费线程 CollectibleOrderHandler。
    //保证订单落库操作串行执行，避免多线程同时扣库存、插入订单导致并发问题。
    //但单线程也可能成为性能瓶颈，如果订单量极大，需要考虑多消费者或分区。
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //@PostConstruct 表示在 Bean 初始化完成后执行。创建 Stream 消费组
    @PostConstruct
    private void init() {
        // 确保消费组存在（幂等，重复创建会抛 BUSYGROUP，直接忽略）
        // 注意：XGROUP CREATE 默认要求 stream 键已存在，首次启动时键还不存在，
        // 必须带 MKSTREAM 才能建组，否则消费端会一直报 NOGROUP，订单消息永远无人消费、无法落库
        try {
            stringRedisTemplate.execute((RedisCallback<String>) connection ->
                    connection.streamCommands().xGroupCreate(
                            STREAM_KEY.getBytes(), STREAM_GROUP, ReadOffset.from("0"), true));
        } catch (Exception e) {
            // 组已存在（BUSYGROUP），忽略
            log.debug("订单 Stream 消费组已存在：{}", e.getMessage());
        }
    }

    /**
     * 启动消费线程
     * 应用完全就绪后再启动订单消费线程：
     * 若在 @PostConstruct 阶段启动，此时 Lettuce 连接池尚未就绪，
     * 消费线程的疯狂重试会导致 "Event executor group is terminated"，拖死整个启动流程。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startOrderHandler() {
        SECKILL_ORDER_EXECUTOR.submit(new CollectibleOrderHandler());
    }

    /**
     * 后台线程：从 Stream 消费订单消息并落库，异常消息进入 pending-list 重试
     */
    private class CollectibleOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            //构造一个消费者对象。
                            //STREAM_GROUP：消费者组名称。
                            //STREAM_CONSUMER：消费者名称（同一个组内可以有多个消费者，这里固定一个）。
                            Consumer.from(STREAM_GROUP, STREAM_CONSUMER),
                            //一次读一条。阻塞 2 秒，没有消息则返回空。
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            //ReadOffset.lastConsumed()：读取尚未被该消费者组消费的最新消息
                            StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    // 3.解析并处理
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    //这是 Hutool 工具类的方法。作用：将 Map 中的键值对填充到 CollectibleOrder 对象的属性中。
                    //第三个参数 true 表示忽略大小写，支持驼峰与下划线的自动转换。
                    //例如 Map 中的 user_id 可以映射到 userId 字段。最终得到一个 CollectibleOrder 实体对象。
                    CollectibleOrder collectibleOrder = BeanUtil.fillBeanWithMap(value, new CollectibleOrder(), true);
                    createCollectibleOrder(collectibleOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge(STREAM_KEY, STREAM_GROUP, record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(STREAM_GROUP, STREAM_CONSUMER),
                            StreamReadOptions.empty().count(1),
                            //ReadOffset.from("0")：读取当前消费者组中已投递但未确认的消息（pending-list）。
                            StreamOffset.create(STREAM_KEY, ReadOffset.from("0"))
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        break;
                    }
                    // 3.解析并处理
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    CollectibleOrder collectibleOrder = BeanUtil.fillBeanWithMap(value, new CollectibleOrder(), true);
                    createCollectibleOrder(collectibleOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge(STREAM_KEY, STREAM_GROUP, record.getId());
                } catch (Exception e) {
                    log.error("处理pending-list订单异常", e);
                }
            }
        }
    }

    /**
     * 落库创建订单：Redisson 锁 + 一人一单校验 + 乐观扣库存（stock > 0 条件更新）
     */
    private void createCollectibleOrder(CollectibleOrder collectibleOrder) {
        Long userId = collectibleOrder.getUserId();
        Long collectibleId = collectibleOrder.getCollectibleId();
        // 创建锁对象 锁的是“用户”，目的是防止同一个用户重复下单。
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取锁
        //tryLock()：无参版本，非阻塞尝试获取锁，立即返回 true 或 false。
        //底层使用 Redisson 的看门狗机制：默认 leaseTime = -1，锁会自动续期（默认 30 秒一次），直到显式 unlock() 或客户端宕机。
        boolean isLock = redisLock.tryLock();
        if (!isLock) {
            // 获取锁失败，直接返回
            log.error("不允许重复下单！");
            return;
        }

        try {
            // 1.查询订单，判断是否重复下单
            long count = query().eq("user_id", userId).eq("collectible_id", collectibleId).count();
            if (count > 0) {
                log.error("不允许重复下单！");
                return;
            }

            // 2.扣减库存（乐观锁：where stock > 0）
            //虽然 UPDATE 语句本身在数据库层面会加行锁，但这属于数据库内部的机制，
            // 从应用层角度看，我们并没有显式地使用 SELECT ... FOR UPDATE 这样的悲观锁，而是通过条件更新实现了乐观锁的效果。
            boolean success = seckillCollectibleService.update()
                    .setSql("stock = stock - 1")
                    //.gt() 是 MyBatis-Plus 中条件构造器提供的一个方法，全称 greater than，意思是“大于”。
                    .eq("collectible_id", collectibleId).gt("stock", 0)
                    .update();
            if (!success) {
                log.error("库存不足！");
                return;
            }

            // 3.创建订单
            save(collectibleOrder);
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }

    @Override
    public Result seckillCollectible(Long collectibleId) {
        Long userId = UserHolder.getUser().getId();
        // 1.校验抢购时间窗口
        SeckillCollectible seckill = seckillCollectibleService.getById(collectibleId);
        if (seckill == null) {
            return Result.fail("抢购活动不存在！");
        }
        LocalDateTime now = LocalDateTime.now();
        if (seckill.getBeginTime() != null && now.isBefore(seckill.getBeginTime())) {
            return Result.fail("抢购尚未开始！");
        }
        if (seckill.getEndTime() != null && now.isAfter(seckill.getEndTime())) {
            return Result.fail("抢购已经结束！");
        }

        // 2.生成全局唯一订单id（Redis自增序列 + 时间戳位运算）
        long orderId = redisIdWorker.nextId("order");
        // 3.执行lua脚本（原子判定库存、一人一单，并发消息入队）
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                collectibleId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        // 4.判断结果是否为0
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 5.返回订单id（转字符串，订单id为时间戳位运算生成的long，超出JS安全整数2^53，返回数字会被前端截断精度）
        return Result.ok(String.valueOf(orderId));
    }

    /**
     * 查询当前用户的典藏订单列表（个人主页「我的典藏」）
     */
    @Override
    public Result queryMyOrders() {
        Long userId = UserHolder.getUser().getId();
        // 1.查我的订单，按下单时间倒序
        List<CollectibleOrder> orders = query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .list();
        if (orders.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2.批量查典藏信息
        //.distinct()：去重，避免重复查询同一个藏品。
        List<Long> collectibleIds = orders.stream()
                .map(CollectibleOrder::getCollectibleId).distinct().collect(Collectors.toList());
        Map<Long, Collectible> collectibleMap = collectibleService.listByIds(collectibleIds)
                .stream().collect(Collectors.toMap(Collectible::getId, Function.identity()));
        // 3.批量查条目名称
        List<Long> mediaIds = collectibleMap.values().stream()
                .map(Collectible::getMediaId).distinct().collect(Collectors.toList());
        Map<Long, String> mediaNameMap = mediaService.listByIds(mediaIds).stream()
                .collect(Collectors.toMap(Media::getId, Media::getName));
        // 4.组装返回
        //LinkedHashMap 会保留插入顺序，put 进去的顺序就是遍历时的顺序；
        List<Map<String, Object>> list = orders.stream().map(order -> {
            Collectible collectible = collectibleMap.get(order.getCollectibleId());
            Map<String, Object> vo = new LinkedHashMap<>();
            // 订单id转字符串，避免超长long被前端JSON解析丢失精度
            vo.put("orderId", order.getId().toString());
            vo.put("collectibleId", order.getCollectibleId());
            vo.put("status", order.getStatus());
            vo.put("createTime", order.getCreateTime());
            if (collectible != null) {
                vo.put("title", collectible.getTitle());
                vo.put("subTitle", collectible.getSubTitle());
                vo.put("payValue", collectible.getPayValue());
                vo.put("actualValue", collectible.getActualValue());
                vo.put("mediaName", mediaNameMap.get(collectible.getMediaId()));
            }
            return vo;
        }).collect(Collectors.toList());
        return Result.ok(list);
    }
}
