package com.mediahub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mediahub.result.Result;
import com.mediahub.result.ScrollResult;
import com.mediahub.dto.UserDTO;
import com.mediahub.entity.Comment;
import com.mediahub.entity.Follow;
import com.mediahub.entity.Media;
import com.mediahub.entity.User;
import com.mediahub.mapper.CommentMapper;
import com.mediahub.service.ICommentService;
import com.mediahub.service.IFollowService;
import com.mediahub.service.IMediaService;
import com.mediahub.service.IRankService;
import com.mediahub.service.IUserInfoService;
import com.mediahub.service.IUserService;
import com.mediahub.utils.SystemConstants;
import com.mediahub.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.mediahub.utils.RedisConstants.COMMENT_LIKED_KEY;
import static com.mediahub.utils.RedisConstants.FEED_KEY;
import static com.mediahub.utils.RedisConstants.RANK_COMMENT_KEY;

/**
 * 统一评论服务（长评短评融合，楼中楼）
 * <p>
 * 一级评论：推送到粉丝收件箱（Feed 推模式）+ 条目评论数 +1；
 * 楼内回复：楼层回复数 +1，不推送。
 */
@Slf4j
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements ICommentService {

    /**
     * 评论内容上限
     */
    private static final int MAX_CONTENT_LENGTH = 5000;
    /**
     * 标题上限
     */
    private static final int MAX_TITLE_LENGTH = 128;

    @Resource
    private IUserService userService;

    @Resource
    private IMediaService mediaService;

    @Resource
    private IFollowService followService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private IRankService rankService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public Result saveComment(Comment comment) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        // 2.参数校验
        Long mediaId = comment.getMediaId();
        if (mediaId == null || mediaService.getById(mediaId) == null) {
            return Result.fail("条目不存在！");
        }
        if (StrUtil.isBlank(comment.getContent())) {
            return Result.fail("评论内容不能为空！");
        }
        if (comment.getContent().length() > MAX_CONTENT_LENGTH) {
            return Result.fail("评论最长 " + MAX_CONTENT_LENGTH + " 字！");
        }
        if (StrUtil.isNotBlank(comment.getTitle()) && comment.getTitle().length() > MAX_TITLE_LENGTH) {
            return Result.fail("标题最长 " + MAX_TITLE_LENGTH + " 字！");
        }
        // 标题传空白视为不填
        if (StrUtil.isBlank(comment.getTitle())) {
            comment.setTitle(null);
        }
        if (comment.getScore() != null && (comment.getScore() < 1 || comment.getScore() > 5)) {
            return Result.fail("评分只能是 1-5 星！");
        }
        comment.setUserId(userId);
        // 3.楼内回复：归一到楼层根id，保证回复永远挂在一级评论下
        Long parentId = comment.getParentId() == null ? 0L : comment.getParentId();
        boolean isReply = parentId != 0;
        if (isReply) {
            //查询数据库
            Comment parent = getById(parentId);
            if (parent == null || !parent.getMediaId().equals(mediaId)) {
                return Result.fail("回复的楼层不存在！");
            }
            if (parent.getParentId() != null && parent.getParentId() != 0) {
                parentId = parent.getParentId();
            }
            // 回复楼内回复时记录被回复人
            if (comment.getReplyUserId() == null) {
                comment.setReplyUserId(parent.getUserId());
            }
        }
        comment.setParentId(parentId);
        comment.setLiked(0);
        comment.setComments(0);
        // 4.保存
        boolean isSuccess = save(comment);
        if (!isSuccess) {
            return Result.fail("发布评论失败!");
        }
        if (isReply) {
            // 5.1 楼层回复数 +1，楼层热度 +3，回复者 +2 积分
            // 这里的parentId是指楼层的根评论
            update().setSql("comments = comments + 1").eq("id", parentId).update();
            rankService.increaseCommentScore(parentId, 3);
            userInfoService.addCredits(userId, IUserInfoService.CREDITS_REPLY);
        } else {
            // 5.2 条目评论数 +1，条目热度 +20，评论者 +10 积分
            mediaService.update()
                    .setSql("comments = comments + 1")
                    .eq("id", mediaId)
                    .update();
            rankService.increaseMediaScore(mediaId, 20);
            userInfoService.addCredits(userId, IUserInfoService.CREDITS_COMMENT);
            // 6.推模式：把一级评论推送给作者所有粉丝的收件箱（ZSET，score为时间戳）
            List<Follow> follows = followService.query().eq("follow_user_id", userId).list();
            for (Follow follow : follows) {
                String key = FEED_KEY + follow.getUserId();
                stringRedisTemplate.opsForZSet().add(key, comment.getId().toString(), System.currentTimeMillis());
            }
        }
        // 7.返回id
        return Result.ok(comment.getId());
    }

    @Override
    @Transactional
    public Result deleteComment(Long id) {
        // 1.校验：评论存在，且只能删除自己的
        Long userId = UserHolder.getUser().getId();
        Comment comment = getById(id);
        if (comment == null) {
            return Result.fail("评论不存在或已被删除！");
        }
        if (!comment.getUserId().equals(userId)) {
            return Result.fail("只能删除自己的评论！");
        }
        Long mediaId = comment.getMediaId();
        if (comment.getParentId() != null && comment.getParentId() != 0) {
            // 2.楼内回复：楼层回复数 -1、楼层热度 -3、回复者积分 -2（对称回滚）
            update().setSql("comments = comments - 1").eq("id", comment.getParentId()).update();
            rankService.increaseCommentScore(comment.getParentId(), -3);
            userInfoService.addCredits(userId, -IUserInfoService.CREDITS_REPLY);
        } else {
            // 3.一级评论：连带删除楼内全部回复
            remove(new QueryWrapper<Comment>().eq("parent_id", id));
            // 条目评论数 -1、条目热度 -20（与发布时 +20 对称）
            mediaService.update().setSql("comments = comments - 1").eq("id", mediaId).update();
            rankService.increaseMediaScore(mediaId, -20);
            // 从热评榜移除；作者积分回滚：发评论 -10、被赞累计 -liked（每赞 +1）
            stringRedisTemplate.opsForZSet().remove(RANK_COMMENT_KEY, id.toString());
            int liked = comment.getLiked() == null ? 0 : comment.getLiked();
            userInfoService.addCredits(userId, -(IUserInfoService.CREDITS_COMMENT + liked));
            // 清理点赞记录，并从所有粉丝收件箱（feed 推模式）移除
            stringRedisTemplate.delete(COMMENT_LIKED_KEY + id);
            //这是构建 SCAN 的过滤条件
            try (Cursor<String> cursor = stringRedisTemplate.scan(
                    ScanOptions.scanOptions().match(FEED_KEY + "*").build())) {
                //Cursor 是游标对象，代表当前遍历到了哪个位置。
                while (cursor.hasNext()) {
                    stringRedisTemplate.opsForZSet().remove(cursor.next(), id.toString());
                }
            }
        }
        // 4.删除评论本体
        removeById(id);
        return Result.ok();
    }

    @Override
    public Result queryCommentsOfMedia(Long mediaId, Integer current) {
        // 一级评论分页，时间倒序
        Page<Comment> page = query()
                .eq("media_id", mediaId)
                .eq("parent_id", 0)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        List<Comment> floors = page.getRecords();
        // 楼内回复 + 用户信息组装
        fillRepliesAndUsers(floors);
        // 评分统计（均分保留 1 位小数）
        Map<String, Object> data = new HashMap<>();
        data.put("list", floors);
        Map<String, Object> stat = getMap(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Comment>()
                //IFNULL:如果“表达式”算出来是 NULL，就返回“默认值”；如果不是 NULL，就返回“表达式”原本的值。
                .select("COUNT(*) AS cnt", "IFNULL(AVG(score), 0) AS avg_score")
                .eq("media_id", mediaId)
                //只统计有评分的评论。因为评论和评分在同一个表里，有些用户只评论不评分，score 为空，这些不能被算进平均分，否则会拉低均分。
                .isNotNull("score"));
        data.put("scoreCount", stat == null ? 0 : stat.get("cnt"));
        Object avg = stat == null ? 0 : stat.get("avg_score");
        //String.format("%.1f", ...)：格式化为保留 1 位小数的字符串，例如 4.5、3.0。
        data.put("avgScore", avg == null ? 0 : String.format("%.1f", Double.parseDouble(avg.toString())));
        return Result.ok(data);
    }

    //查询单条评论详情
    @Override
    public Result queryCommentById(Long id) {
        Comment comment = getById(id);
        if (comment == null) {
            return Result.fail("评论不存在！");
        }
        queryCommentUser(comment);
        isCommentLiked(comment);
        // 详情页加载楼内回复
        //Collections.singletonList(...) 是 Java 提供的一个快捷方法，用来创建一个只包含一个元素的、不可变的 List。
        List<Comment> floors = new ArrayList<>(Collections.singletonList(comment));
        fillRepliesAndUsers(floors);
        return Result.ok(comment);
    }

    @Override
    public Result queryHotComments(Integer current) {
        // 发现页热评：只取一级评论，按点赞数排序
        Page<Comment> page = query()
                .eq("parent_id", 0)
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Comment> records = page.getRecords();
        records.forEach(comment -> {
            queryCommentUser(comment);
            isCommentLiked(comment);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeComment(Long id) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.判断当前登录用户是否已经点赞
        String key = COMMENT_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 3.未点赞 → 点赞：数据库 +1，Redis ZSet 记录（score为时间戳，便于取最早点赞的人），评论热度 +2，作者 +1 积分
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
                rankService.increaseCommentScore(id, 2);
                Comment comment = getById(id);
                if (comment != null && !comment.getUserId().equals(userId)) {
                    userInfoService.addCredits(comment.getUserId(), IUserInfoService.CREDITS_LIKED);
                }
            }
        } else {
            // 4.已点赞 → 取消：数据库 -1，移除记录，评论热度 -2，作者 -1 积分
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
                rankService.increaseCommentScore(id, -2);
                Comment comment = getById(id);
                if (comment != null && !comment.getUserId().equals(userId)) {
                    userInfoService.addCredits(comment.getUserId(), -IUserInfoService.CREDITS_LIKED);
                }
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryCommentLikes(Long id) {
        String key = COMMENT_LIKED_KEY + id;
        // 1.查询top5的点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2.解析出其中的用户id
        //map(Long::valueOf)：把 Redis 返回的字符串 ID，转换成 Long 类型，方便后续 MyBatis-Plus 的 in 查询
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // 3.根据用户id查询用户 WHERE id IN ( 5 , 1 ) ORDER BY FIELD(id, 5, 1)
        List<UserDTO> userDTOS = userService.query()
                //ORDER BY FIELD它返回 id 在列表中的位置索引
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result queryCommentsOfFollow(Long max, Integer offset) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.查询收件箱 ZREVRANGEBYSCORE key Max Min LIMIT offset count
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 3.非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        // 4.解析数据：评论id、minTime（时间戳）、offset（处理同分值数据）
        List<Long> ids = new ArrayList<>(typedTuples.size());
        //记录本轮查询中，最小的那个时间戳，作为下次查询的 max
        long minTime = 0;
        //记录最小时间戳的数据出现了几次，用于下次查询的偏移量。
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            //tuple.getValue()：拿到评论 ID（String），转成 Long 塞进 ids。
            ids.add(Long.valueOf(tuple.getValue()));
            //tuple.getScore().longValue()：拿到时间戳。
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        // 5.根据id查询评论
        String idStr = StrUtil.join(",", ids);
        List<Comment> comments = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Comment comment : comments) {
            queryCommentUser(comment);
            isCommentLiked(comment);
        }
        // 6.封装并返回
        ScrollResult r = new ScrollResult();
        r.setList(comments);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
    }

    /**
     * 批量填充楼内回复 + 评论人昵称头像 + 被回复人昵称
     */
    private void fillRepliesAndUsers(List<Comment> floors) {
        if (floors.isEmpty()) {
            return;
        }
        // 1.批量查所有楼层内的回复，时间正序
        List<Long> floorIds = floors.stream().map(Comment::getId).collect(Collectors.toList());
        //Key 是一级评论 ID，Value 是那个楼层下的所有回复列表。
        Map<Long, List<Comment>> replyMap = query()
                .in("parent_id", floorIds)
                .orderByAsc("create_time")
                .list()
                .stream()
                .collect(Collectors.groupingBy(Comment::getParentId));
        // 2.收集所有涉及的用户id（评论人 + 被回复人）
        Set<Long> userIds = floors.stream().map(Comment::getUserId).collect(Collectors.toSet());
        //.values()这是 Map 的方法，返回的是一个 Collection<List<Comment>>。也就是说，它拿到的是所有的“回复列表”。
        //flatMap 的作用是“扁平化”。它会把每个 List<Comment> 里的元素单独拿出来，全部合并成一个大的 Stream<Comment>。
        replyMap.values().stream().flatMap(List::stream).forEach(c -> {
            //把回复的作者 ID 塞进刚才的 userIds 集合里（如果是同一个人，Set 会自动去重）。
            userIds.add(c.getUserId());
            if (c.getReplyUserId() != null) {
                userIds.add(c.getReplyUserId());
            }
        });
        //批量查询用户并转成 Map
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userService.listByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
        // 3.填充一级评论
        floors.forEach(floor -> {
            //调用方法，填充用户信息
            fillOne(floor, userMap);
            //拿着当前楼层的 ID 去 replyMap 里找对应的回复列表。
            //如果找到了，就返回那个列表；如果没找到（说明这个楼层没人回复），就返回一个空集合
            floor.setReplies(replyMap.getOrDefault(floor.getId(), Collections.emptyList()));
        });
        // 4.填充楼内回复
        replyMap.values().stream().flatMap(List::stream)
                .forEach(c -> fillOne(c, userMap));
    }

    /**
     * 填充单条评论的用户信息
     */
    private void fillOne(Comment comment, Map<Long, User> userMap) {
        User user = userMap.get(comment.getUserId());
        if (user != null) {
            comment.setName(user.getNickName());
            comment.setIcon(user.getIcon());
        }
        if (comment.getReplyUserId() != null) {
            User replyUser = userMap.get(comment.getReplyUserId());
            comment.setReplyUserName(replyUser == null ? "蘑菇友" : replyUser.getNickName());
        }
    }

    private void queryCommentUser(Comment comment) {
        User user = userService.getById(comment.getUserId());
        if (user != null) {
            comment.setName(user.getNickName());
            comment.setIcon(user.getIcon());
        }
    }

    private void isCommentLiked(Comment comment) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        String key = COMMENT_LIKED_KEY + comment.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, user.getId().toString());
        comment.setIsLike(score != null);
    }
}
