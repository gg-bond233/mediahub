package com.mediahub.service;

import com.mediahub.result.Result;

/**
 * 热榜排行榜服务
 * <p>
 * 榜单数据存于 Redis ZSet（member=业务id，score=热度分），
 * 首次查询或缓存过期时从数据库全量重建（惰性加载 + TTL），
 * 评论 / 点赞 / 标记等互动事件通过 {@code increase*Score} 做增量更新。
 */
public interface IRankService {

    /**
     * 条目热度榜（书影音综合热度）
     *
     * @param typeId 分类id，null 表示全部类型
     * @param top   取前几名
     */
    Result queryMediaRank(Long typeId, Integer top);

    /**
     * 热评榜（点赞 + 回复加权）
     *
     * @param top 取前几名
     */
    Result queryCommentRank(Integer top);

    /**
     * 条目热度增量（榜存在时才更新，榜过期后由下次查询全量重建）
     *
     * @param mediaId 条目id
     * @param delta  增量，可为负
     */
    void increaseMediaScore(Long mediaId, double delta);

    /**
     * 评论热度增量
     *
     * @param commentId 评论id
     * @param delta     增量，可为负
     */
    void increaseCommentScore(Long commentId, double delta);
}
