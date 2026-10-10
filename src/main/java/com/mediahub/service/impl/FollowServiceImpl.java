package com.mediahub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mediahub.result.Result;
import com.mediahub.dto.UserDTO;
import com.mediahub.entity.Follow;
import com.mediahub.entity.Comment;
import com.mediahub.entity.UserInfo;
import com.mediahub.mapper.FollowMapper;
import com.mediahub.mapper.CommentMapper;
import com.mediahub.service.IFollowService;
import com.mediahub.service.IUserInfoService;
import com.mediahub.service.IUserService;
import com.mediahub.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.mediahub.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Resource
    private IUserInfoService userInfoService;
    /** 用 Mapper 直接查询，避免与 CommentServiceImpl（注入 IFollowService 做推送）形成循环依赖 */
    @Resource
    private CommentMapper commentMapper;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        // 1.判断到底是关注还是取关
        if (isFollow) {
            // 2.关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                // 把关注用户的id，放入redis的set集合 sadd userId followerUserId
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
                // 同步维护双方的用户详情统计：我的关注数 +1，TA 的粉丝数 +1，TA +3 积分
                userInfoService.update(new UpdateWrapper<UserInfo>()
                        .setSql("followee = followee + 1").eq("user_id", userId));
                userInfoService.update(new UpdateWrapper<UserInfo>()
                        .setSql("fans = fans + 1").eq("user_id", followUserId));
                userInfoService.addCredits(followUserId, IUserInfoService.CREDITS_FOLLOWED);
                // 回填 TA 的历史一级评论到我的收件箱（推模式下发布才会推送，
                // 若不回填，关注后只能看到 TA 之后新发的评论）
                List<Comment> comments = commentMapper.selectList(
                        new QueryWrapper<Comment>().eq("user_id", followUserId).eq("parent_id", 0));
                String feedKey = FEED_KEY + userId;
                for (Comment comment : comments) {
                    stringRedisTemplate.opsForZSet().add(feedKey, comment.getId().toString(),
                            Timestamp.valueOf(comment.getCreateTime()).getTime());
                }
            }
        } else {
            // 3.取关，删除 delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));
            if (isSuccess) {
                // 把关注用户的id从Redis集合中移除
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
                // 取关：我的关注数 -1，TA 的粉丝数 -1，TA -3 积分
                userInfoService.update(new UpdateWrapper<UserInfo>()
                        .setSql("followee = followee - 1").eq("user_id", userId));
                userInfoService.update(new UpdateWrapper<UserInfo>()
                        .setSql("fans = fans - 1").eq("user_id", followUserId));
                userInfoService.addCredits(followUserId, -IUserInfoService.CREDITS_FOLLOWED);
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.查询是否关注 select count(*) from tb_follow where user_id = ? and follow_user_id = ?
        long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        // 3.判断
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        // 2.求交集
        String key2 = "follows:" + id;
        //intersect：两个集合的交集，即双方都关注的用户 id。
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()) {
            // 无交集
            return Result.ok(Collections.emptyList());
        }
        // 3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 4.查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
