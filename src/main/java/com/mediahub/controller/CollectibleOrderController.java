package com.mediahub.controller;


import com.mediahub.result.Result;
import com.mediahub.service.ICollectibleOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 */
@RestController
@RequestMapping("/collectible-order")
public class CollectibleOrderController {

    // 典藏订单服务
    @Resource
    private ICollectibleOrderService collectibleOrderService;

    /** 秒杀下单典藏，返回下单结果 */
    @PostMapping("/seckill/{id}")
    public Result seckillCollectible(@PathVariable("id") Long collectibleId) {
        return collectibleOrderService.seckillCollectible(collectibleId);
    }

    /**
     * 查询当前登录用户的全部典藏订单（个人主页「我的典藏」）
     */
    @GetMapping("/my")
    public Result queryMyOrders() {
        return collectibleOrderService.queryMyOrders();
    }
}
