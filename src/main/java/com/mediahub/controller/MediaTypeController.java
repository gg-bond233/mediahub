package com.mediahub.controller;

import com.mediahub.result.Result;
import com.mediahub.service.IMediaTypeService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 书影音分类控制器
 */
@RestController
@RequestMapping("/media-type")
public class MediaTypeController {

    // 分类服务
    @Resource
    private IMediaTypeService typeService;

    /** 查询全部书影音分类（按 sort 升序） */
    @GetMapping("/list")
    public Result queryTypeList() {
        return Result.ok(typeService.query().orderByAsc("sort").list());
    }
}
