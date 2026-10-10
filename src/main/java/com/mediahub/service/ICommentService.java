package com.mediahub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mediahub.result.Result;
import com.mediahub.entity.Comment;

/**
 * 统一评论服务（长评短评融合，楼中楼）
 */
public interface ICommentService extends IService<Comment> {

    /**
     * 发表评论 / 楼内回复（parentId=0 为一级评论）
     */
    Result saveComment(Comment comment);

    /**
     * 删除自己的评论（一级评论连带楼内回复，计数/热度/积分对称回滚）
     */
    Result deleteComment(Long id);

    /**
     * 某条目的一级评论列表（时间倒序）+ 评分统计（游客可看）
     */
    Result queryCommentsOfMedia(Long mediaId, Integer current);

    /**
     * 评论详情（游客可看）
     */
    Result queryCommentById(Long id);

    /**
     * 发现页热评流（按点赞数）
     */
    Result queryHotComments(Integer current);

    /**
     * 点赞 / 取消点赞
     */
    Result likeComment(Long id);

    /**
     * 最早点赞的 5 位用户
     */
    Result queryCommentLikes(Long id);

    /**
     * 关注流（推模式收件箱，滚动分页）
     */
    Result queryCommentsOfFollow(Long max, Integer offset);
}
