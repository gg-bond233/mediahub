package com.mediahub.controller;

import com.mediahub.result.Result;
import com.mediahub.service.IRankService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 热榜排行榜前端控制器（游客可看）
 */
@RestController
@RequestMapping("/rank")
public class RankController {

    // 热榜服务
    @Resource
    private IRankService rankService;

    /**
     * 条目热度榜
     *
     * @param typeId 分类id，不传为全部类型
     * @param top   取前几名，默认 20
     */
    @GetMapping("/media")
    public Result queryMediaRank(
            @RequestParam(value = "typeId", required = false) Long typeId,
            @RequestParam(value = "top", required = false) Integer top) {
        return rankService.queryMediaRank(typeId, top);
    }

    /**
     * 热评榜
     *
     * @param top 取前几名，默认 20
     */
    @GetMapping("/comment")
    public Result queryCommentRank(@RequestParam(value = "top", required = false) Integer top) {
        return rankService.queryCommentRank(top);
    }
}
