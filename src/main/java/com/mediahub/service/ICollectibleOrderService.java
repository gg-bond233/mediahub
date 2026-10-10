package com.mediahub.service;

import com.mediahub.result.Result;
import com.mediahub.entity.CollectibleOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 典藏订单服务
 */
public interface ICollectibleOrderService extends IService<CollectibleOrder> {

    /**
     * 秒杀典藏：抢购指定典藏并生成订单
     */
    Result seckillCollectible(Long collectibleId);

    /**
     * 查询当前用户的所有订单
     */
    Result queryMyOrders();
}
