package com.mediahub.service;

import com.mediahub.result.Result;
import com.mediahub.entity.Collectible;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 典藏服务
 */
public interface ICollectibleService extends IService<Collectible> {

    /**
     * 查询某条目关联的典藏列表
     */
    Result queryCollectibleOfMedia(Long mediaId);

    /**
     * 新增一条秒杀典藏配置
     */
    void addSeckillCollectible(Collectible collectible);

    /**
     * 查询所有秒杀典藏列表
     */
    Result queryAllSeckillCollectibles();
}
