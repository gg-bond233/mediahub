package com.mediahub.task;

import com.mediahub.entity.SeckillCollectible;
import com.mediahub.service.ISeckillCollectibleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

import static com.mediahub.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * 启动时预热秒杀库存：
 * 数据库中的抢购活动（种子数据或后台新增）不会自动写入 Redis，
 * 这里在应用启动后统一同步，使用 setIfAbsent 避免覆盖抢购中的实时库存。
 */
@Slf4j
@Component
public class SeckillStockWarmUp implements ApplicationRunner {

    @Resource
    private ISeckillCollectibleService seckillCollectibleService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<SeckillCollectible> seckills = seckillCollectibleService.list();
        int count = 0;
        for (SeckillCollectible seckill : seckills) {
            if (seckill.getStock() == null || seckill.getStock() < 0) {
                continue;
            }
            Boolean ok = stringRedisTemplate.opsForValue()
                    .setIfAbsent(SECKILL_STOCK_KEY + seckill.getCollectibleId(), seckill.getStock().toString());
            if (Boolean.TRUE.equals(ok)) {
                count++;
            }
        }
        log.info("秒杀库存预热完成，共同步 {} 个抢购活动", count);
    }
}
