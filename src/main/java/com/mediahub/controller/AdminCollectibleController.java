package com.mediahub.controller;

import cn.hutool.core.bean.BeanUtil;
import com.mediahub.dto.CollectibleSaveDTO;
import com.mediahub.result.Result;
import com.mediahub.entity.Collectible;
import com.mediahub.service.ICollectibleService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 典藏管理控制器（/admin/**：需登录且角色为管理员，由 AdminInterceptor 校验）
 */
@RestController
@RequestMapping("/admin/collectible")
public class AdminCollectibleController {

    // 典藏服务
    @Resource
    private ICollectibleService collectibleService;

    /**
     * 新增普通典藏
     * @param collectibleSaveDTO 典藏数据
     * @return 典藏id
     */
    @PostMapping
    public Result addCollectible(@Valid @RequestBody CollectibleSaveDTO collectibleSaveDTO) {
        // 防重：同名典藏不允许重复录入
        Long count = collectibleService.query().eq("title", collectibleSaveDTO.getTitle()).count();
        if (count > 0) {
            return Result.fail("同名典藏已存在");
        }
        Collectible collectible = BeanUtil.copyProperties(collectibleSaveDTO, Collectible.class);
        // 类型/上下架状态由服务端决定，不信任客户端
        collectible.setType(0);
        collectible.setStatus(1);
        collectibleService.save(collectible);
        return Result.ok(collectible.getId());
    }

    /**
     * 新增秒杀典藏（含库存与抢购起止时间）
     * @param collectibleSaveDTO 典藏数据（含秒杀字段）
     * @return 典藏id
     */
    @PostMapping("/seckill")
    public Result addSeckillCollectible(@Valid @RequestBody CollectibleSaveDTO collectibleSaveDTO) {
        // 秒杀专属跨字段校验（注解不便表达，服务端兜底）
        if (collectibleSaveDTO.getStock() == null) {
            return Result.fail("秒杀库存不能为空");
        }
        if (collectibleSaveDTO.getBeginTime() == null || collectibleSaveDTO.getEndTime() == null) {
            return Result.fail("秒杀起止时间不能为空");
        }
        if (!collectibleSaveDTO.getBeginTime().isBefore(collectibleSaveDTO.getEndTime())) {
            return Result.fail("抢购开始时间需早于结束时间");
        }
        Long count = collectibleService.query().eq("title", collectibleSaveDTO.getTitle()).count();
        if (count > 0) {
            return Result.fail("同名典藏已存在");
        }
        Collectible collectible = BeanUtil.copyProperties(collectibleSaveDTO, Collectible.class);
        collectible.setType(1);
        collectible.setStatus(1);
        // 复用原逻辑：保存典藏 + 保存秒杀表 + 初始化 Redis 库存
        collectibleService.addSeckillCollectible(collectible);
        return Result.ok(collectible.getId());
    }
}
