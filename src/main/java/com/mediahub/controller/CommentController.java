package com.mediahub.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mediahub.result.Result;
import com.mediahub.dto.UserDTO;
import com.mediahub.entity.Comment;
import com.mediahub.service.ICommentService;
import com.mediahub.utils.SystemConstants;
import com.mediahub.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 统一评论控制器（长评短评融合，楼中楼）
 */
@RestController
@RequestMapping("/comment")
public class CommentController {

    // 评论服务
    @Resource
    private ICommentService commentService;

    /**
     * 发表评论 / 楼内回复（标题可选，星级可选）
     */
    @PostMapping
    public Result saveComment(@RequestBody Comment comment) {
        return commentService.saveComment(comment);
    }

    /**
     * 删除自己的评论（一级评论连带楼内回复）
     */
    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable("id") Long id) {
        return commentService.deleteComment(id);
    }

    /**
     * 点赞 / 取消点赞
     */
    @PutMapping("/like/{id}")
    public Result likeComment(@PathVariable("id") Long id) {
        return commentService.likeComment(id);
    }

    /**
     * 我的评论列表
     */
    @GetMapping("/of/me")
    public Result queryMyComments(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        UserDTO user = UserHolder.getUser();
        Page<Comment> page = commentService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Comment> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 发现页热评流
     */
    @GetMapping("/hot")
    public Result queryHotComments(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return commentService.queryHotComments(current);
    }

    /**
     * 评论详情（含楼内回复）
     */
    @GetMapping("/{id}")
    public Result queryCommentById(@PathVariable("id") Long id) {
        return commentService.queryCommentById(id);
    }

    /**
     * 最早点赞的 5 位用户
     */
    @GetMapping("/likes/{id}")
    public Result queryCommentLikes(@PathVariable("id") Long id) {
        return commentService.queryCommentLikes(id);
    }

    /**
     * 某用户的评论列表（个人主页）
     */
    @GetMapping("/of/user")
    public Result queryCommentsByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        Page<Comment> page = commentService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Comment> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 某条目的一级评论列表 + 评分统计（游客可看）
     */
    @GetMapping("/of/media/{mediaId}")
    public Result queryCommentsOfMedia(
            @PathVariable("mediaId") Long mediaId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return commentService.queryCommentsOfMedia(mediaId, current);
    }

    /**
     * 关注流（滚动分页）
     */
    @GetMapping("/of/follow")
    public Result queryCommentsOfFollow(
            @RequestParam("lastId") Long max, @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return commentService.queryCommentsOfFollow(max, offset);
    }
}
