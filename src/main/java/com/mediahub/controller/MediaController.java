package com.mediahub.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mediahub.result.Result;
import com.mediahub.entity.Media;
import com.mediahub.service.IMediaService;
import com.mediahub.utils.SystemConstants;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 书影音条目前端控制器（查询公开；新增/更新等管理操作见 AdminMediaController，/admin/**）
 */
@RestController
@RequestMapping("/media")
public class MediaController {

    // 条目录服务
    @Resource
    public IMediaService mediaService;

    /**
     * 根据id查询条目信息
     * @param id 条目id
     * @return 条目详情数据
     */
    @GetMapping("/{id}")
    public Result queryMediaById(@PathVariable("id") Long id) {
        return mediaService.queryById(id);
    }

    /**
     * 根据分类分页查询条目信息
     * @param typeId 分类id
     * @param current 页码
     * @return 条目列表
     */
    @GetMapping("/of/type")
    public Result queryMediaByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        return mediaService.queryMediaByType(typeId, current);
    }

    /**
     * 根据条目名称关键字分页查询条目信息
     * @param name 名称关键字
     * @param current 页码
     * @return 条目列表
     */
    @GetMapping("/of/name")
    public Result queryMediaByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        Page<Media> page = mediaService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }
}
