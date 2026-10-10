package com.mediahub.controller;

import com.mediahub.result.Result;
import com.mediahub.entity.Mark;
import com.mediahub.service.IMarkService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 条目标记控制器（纯三态：想看/在看/看过）
 */
@RestController
@RequestMapping("/mark")
public class MarkController {

    // 条目标记服务
    @Resource
    private IMarkService markService;

    /**
     * 打标记 / 更新标记
     */
    @PostMapping
    public Result saveMark(@RequestBody Mark mark) {
        return markService.saveMark(mark);
    }

    /**
     * 取消标记
     */
    @DeleteMapping("/{mediaId}")
    public Result deleteMark(@PathVariable("mediaId") Long mediaId) {
        return markService.deleteMark(mediaId);
    }

    /**
     * 我对某个条目的标记（详情页回显）
     */
    @GetMapping("/my/{mediaId}")
    public Result queryMyMark(@PathVariable("mediaId") Long mediaId) {
        return markService.queryMyMark(mediaId);
    }

    /**
     * 我的标记列表（个人主页）
     * current 当前页码
     */
    @GetMapping("/of/me")
    public Result queryMyMarks(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return markService.queryMyMarks(current);
    }
}
