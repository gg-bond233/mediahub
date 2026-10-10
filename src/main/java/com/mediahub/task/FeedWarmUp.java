package com.mediahub.task;

import com.mediahub.entity.Comment;
import com.mediahub.entity.Follow;
import com.mediahub.service.ICommentService;
import com.mediahub.service.IFollowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.sql.Timestamp;
import java.util.List;

import static com.mediahub.utils.RedisConstants.FEED_KEY;

/**
 * 启动时为种子数据预热 Feed 收件箱：
 * 推模式下，收件箱（ZSET）只有在「有人发评论」时才会写入，
 * 种子导入的评论不会自动推送，这里按关注关系补齐，保证关注页有初始内容。
 * ZADD 幂等，重复执行不会产生重复数据。
 */
@Slf4j
@Component
public class FeedWarmUp implements ApplicationRunner {

    @Resource
    private IFollowService followService;

    @Resource
    private ICommentService commentService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // 1.查询所有关注关系
        List<Follow> follows = followService.list();
        int count = 0;
        for (Follow follow : follows) {
            Long userId = follow.getUserId();
            Long followUserId = follow.getFollowUserId();
            // 2.查询被关注者的所有一级评论（楼内回复不进 Feed）
            List<Comment> comments = commentService.query()
                    .eq("user_id", followUserId).eq("parent_id", 0).list();
            for (Comment comment : comments) {
                // 3.推送到粉丝的收件箱（score为发布时间戳）
                String key = FEED_KEY + userId;
                stringRedisTemplate.opsForZSet()
                        .add(key, comment.getId().toString(), Timestamp.valueOf(comment.getCreateTime()).getTime());
                count++;
            }
        }
        log.info("Feed 收件箱预热完成，共同步 {} 条推送记录", count);
    }
}
