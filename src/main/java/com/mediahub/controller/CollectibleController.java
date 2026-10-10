package com.mediahub.controller;


import com.mediahub.result.Result;
import com.mediahub.service.ICollectibleService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * 限量典藏前端控制器（查询公开；新增典藏/秒杀活动等管理操作见 AdminCollectibleController，/admin/**）
 */
@RestController
@RequestMapping("/collectible")
public class CollectibleController {

    // 典藏服务
    @Resource
    private ICollectibleService collectibleService;

    /**
     * 查询店铺的优惠券列表
     * @param mediaId 店铺id
     * @return 优惠券列表
     */
    @GetMapping("/list/{mediaId}")
    public Result queryCollectibleOfMedia(@PathVariable("mediaId") Long mediaId) {
       return collectibleService.queryCollectibleOfMedia(mediaId);
    }

    /**
     * 查询全部限量秒杀典藏（抢购大厅）
     * @return 秒杀典藏列表（含所属条目名称、库存、起止时间）
     */
    @GetMapping("/seckill/list")
    public Result queryAllSeckillCollectibles() {
        return collectibleService.queryAllSeckillCollectibles();
    }
}
