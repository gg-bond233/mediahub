package com.mediahub.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mediahub.result.Result;
import com.mediahub.dto.UserDTO;
import com.mediahub.entity.Comment;
import com.mediahub.entity.Media;
import com.mediahub.entity.User;
import com.mediahub.mapper.CommentMapper;
import com.mediahub.service.IMediaService;
import com.mediahub.service.IRankService;
import com.mediahub.service.IUserService;
import com.mediahub.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.mediahub.utils.RedisConstants.*;

/**
 * 热榜排行榜服务实现
 * <p>
 * 条目热度分 = 评分 x 3 + 评论数 x 20 + 标记数 / 10（评分满分 100 制）；
 * 评论热度分 = 点赞数 x 2 + 回复数 x 3。
 * <p>
 * 榜单采用「惰性重建 + 事件增量」策略：
 * 1. ZSet 不存在时，从数据库全量计算写入并设置 TTL；
 * 2. 互动事件（发评论 / 点赞 / 标记）通过 ZINCRBY 实时累计；
 * 3. TTL 到期后自动过期，下次查询用最新库数据重建，保证与库最终一致。
 */
@Slf4j
@Service
public class RankServiceImpl implements IRankService {

    /**
     * 条目榜默认取前几名
     */
    private static final int DEFAULT_MEDIA_TOP = 20;
    /**
     * 热评榜默认取前几名
     */
    private static final int DEFAULT_COMMENT_TOP = 20;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IMediaService mediaService;

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private IUserService userService;

    @Override
    public Result queryMediaRank(Long typeId, Integer top) {
        if (top == null || top <= 0) {
            top = DEFAULT_MEDIA_TOP;
        }
        // 1.榜单不存在则重建
        rebuildMediaRankIfNeeded();
        // 2.按热度倒序取全量（条目量有限），score 即热度分  0, -1：拉取全量数据（从第 0 个到最后一个）。
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(RANK_MEDIA_KEY, 0, -1);
        if (tuples == null || tuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 3.解析 id -> 热度分
        Map<Long, Double> scoreMap = new HashMap<>(tuples.size());
        List<Long> ids = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            Long id = Long.valueOf(t.getValue());
            ids.add(id);
            scoreMap.put(id, t.getScore());
        }
        // 4.批量查条目并保持热度排序
        //listByIds(ids)：一次 IN 查询，把所有媒体全部查出来。避免了 N+1 问题。
        //注意这里是 b 和 a，意思是降序（分数高的排前面）。直接用 Redis 里的热度分来对比，效率极高。
        List<Media> mediaList = mediaService.listByIds(ids).stream()
                .sorted((a, b) -> Double.compare(scoreMap.get(b.getId()), scoreMap.get(a.getId())))
                .collect(Collectors.toList());
        // 5.类型过滤 + 截取 top + 填充热度分
        List<Media> result = new ArrayList<>();
        for (Media media : mediaList) {
            if (typeId != null && !typeId.equals(media.getTypeId())) {
                continue;
            }
            media.setHotScore(scoreMap.get(media.getId()).intValue());
            result.add(media);
            if (result.size() >= top) {
                break;
            }
        }
        return Result.ok(result);
    }

    @Override
    public Result queryCommentRank(Integer top) {
        if (top == null || top <= 0) {
            top = DEFAULT_COMMENT_TOP;
        }
        // 1.榜单不存在则重建
        rebuildCommentRankIfNeeded();
        // 2.按热度倒序取 top N
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(RANK_COMMENT_KEY, 0, top - 1);
        if (tuples == null || tuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 3.解析 id -> 热度分
        Map<Long, Double> scoreMap = new HashMap<>(tuples.size());
        List<Long> ids = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            Long id = Long.valueOf(t.getValue());
            ids.add(id);
            scoreMap.put(id, t.getScore());
        }
        // 4.批量查评论（保持热度排序），只保留一级评论
        List<Comment> comments = commentMapper.selectList(
                        new QueryWrapper<Comment>().in("id", ids).eq("parent_id", 0))
                .stream()
                .sorted((a, b) -> Double.compare(scoreMap.get(b.getId()), scoreMap.get(a.getId())))
                .collect(Collectors.toList());
        // 5.填充热度分 / 作者 / 当前用户点赞状态
        Set<Long> userIds = new HashSet<>();
        comments.forEach(c -> userIds.add(c.getUserId()));
        Map<Long, User> userMap = userIds.isEmpty() ? new HashMap<>() :
                userService.listByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
        UserDTO me = UserHolder.getUser();
        for (Comment comment : comments) {
            comment.setHotScore(scoreMap.get(comment.getId()).intValue());
            User user = userMap.get(comment.getUserId());
            if (user != null) {
                comment.setName(user.getNickName());
                comment.setIcon(user.getIcon());
            }
            if (me != null) {
                Double liked = stringRedisTemplate.opsForZSet()
                        .score(COMMENT_LIKED_KEY + comment.getId(), me.getId().toString());
                //这是一个布尔表达式，它的计算结果只有两种：true 或 false
                comment.setIsLike(liked != null);
            }
        }
        return Result.ok(comments);
    }

    @Override
    public void increaseMediaScore(Long mediaId, double delta) {
        // 榜不存在说明已过期，等下次查询重建，不额外写入
        Boolean exists = stringRedisTemplate.hasKey(RANK_MEDIA_KEY);
        if (Boolean.TRUE.equals(exists)) {
            stringRedisTemplate.opsForZSet().incrementScore(RANK_MEDIA_KEY, mediaId.toString(), delta);
        }
    }

    @Override
    public void increaseCommentScore(Long commentId, double delta) {
        Boolean exists = stringRedisTemplate.hasKey(RANK_COMMENT_KEY);
        if (Boolean.TRUE.equals(exists)) {
            stringRedisTemplate.opsForZSet().incrementScore(RANK_COMMENT_KEY, commentId.toString(), delta);
        }
    }

    /**
     * 条目榜不存在时从数据库全量重建：hot = score x 3 + comments x 20 + mark_count / 10
     */
    private void rebuildMediaRankIfNeeded() {
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(RANK_MEDIA_KEY))) {
            return;
        }
        List<Media> all = mediaService.list();
        if (all.isEmpty()) {
            return;
        }
        String key = RANK_MEDIA_KEY;
        for (Media media : all) {
            double hot = mediaScore(media);
            stringRedisTemplate.opsForZSet().add(key, media.getId().toString(), hot);
        }
        stringRedisTemplate.expire(key, RANK_TTL, java.util.concurrent.TimeUnit.MINUTES);
        log.debug("条目热榜已重建，共 {} 条", all.size());
    }

    /**
     * 热评榜不存在时从数据库全量重建：hot = liked x 2 + comments x 3（仅一级评论）
     */
    private void rebuildCommentRankIfNeeded() {
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(RANK_COMMENT_KEY))) {
            return;
        }
        List<Comment> all = commentMapper.selectList(
                new QueryWrapper<Comment>().eq("parent_id", 0));
        if (all.isEmpty()) {
            return;
        }
        String key = RANK_COMMENT_KEY;
        for (Comment comment : all) {
            double hot = commentScore(comment);
            stringRedisTemplate.opsForZSet().add(key, comment.getId().toString(), hot);
        }
        stringRedisTemplate.expire(key, RANK_TTL, java.util.concurrent.TimeUnit.MINUTES);
        log.debug("热评榜已重建，共 {} 条", all.size());
    }

    /**
     * 条目热度分：评分 x 3 + 评论数 x 20 + 标记数 / 10
     */
    private double mediaScore(Media media) {
        int score = media.getScore() == null ? 0 : media.getScore();
        int comments = media.getComments() == null ? 0 : media.getComments();
        int markCount = media.getMarkCount() == null ? 0 : media.getMarkCount();
        return score * 3.0 + comments * 20.0 + markCount / 10.0;
    }

    /**
     * 评论热度分：点赞数 x 2 + 回复数 x 3
     */
    private double commentScore(Comment comment) {
        int liked = comment.getLiked() == null ? 0 : comment.getLiked();
        int comments = comment.getComments() == null ? 0 : comment.getComments();
        return liked * 2.0 + comments * 3.0;
    }
}
