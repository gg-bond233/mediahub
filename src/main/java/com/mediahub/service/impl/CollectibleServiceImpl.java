package com.mediahub.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mediahub.result.Result;
import com.mediahub.entity.SeckillCollectible;
import com.mediahub.entity.Collectible;
import com.mediahub.mapper.CollectibleMapper;
import com.mediahub.service.ISeckillCollectibleService;
import com.mediahub.service.ICollectibleService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;

import static com.mediahub.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class CollectibleServiceImpl extends ServiceImpl<CollectibleMapper, Collectible> implements ICollectibleService {

    @Resource
    private ISeckillCollectibleService seckillCollectibleService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryCollectibleOfMedia(Long mediaId) {
        // 查询优惠券信息
        List<Collectible> collectibles = getBaseMapper().queryCollectibleOfMedia(mediaId);
        // 返回结果
        return Result.ok(collectibles);
    }

    @Override
    public Result queryAllSeckillCollectibles() {
        List<Collectible> collectibles = getBaseMapper().queryAllSeckillCollectibles();
        return Result.ok(collectibles);
    }

    @Override
    @Transactional
    public void addSeckillCollectible(Collectible collectible) {
        // 保存优惠券
        save(collectible);
        // 保存秒杀信息
        SeckillCollectible seckillCollectible = new SeckillCollectible();
        seckillCollectible.setCollectibleId(collectible.getId());
        seckillCollectible.setStock(collectible.getStock());
        seckillCollectible.setBeginTime(collectible.getBeginTime());
        seckillCollectible.setEndTime(collectible.getEndTime());
        seckillCollectibleService.save(seckillCollectible);
        // 保存秒杀库存到Redis中
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + collectible.getId(), collectible.getStock().toString());
    }
}
