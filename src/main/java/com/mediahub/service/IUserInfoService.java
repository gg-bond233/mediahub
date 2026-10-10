package com.mediahub.service;

import com.mediahub.entity.UserInfo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户详情服务类
 */
public interface IUserInfoService extends IService<UserInfo> {

    /**
     * 积分事件分值：签到
     */
    int CREDITS_SIGN = 5;
    /**
     * 积分事件分值：发表一级评论
     */
    int CREDITS_COMMENT = 10;
    /**
     * 积分事件分值：楼内回复
     */
    int CREDITS_REPLY = 2;
    /**
     * 积分事件分值：评论被点赞
     */
    int CREDITS_LIKED = 1;
    /**
     * 积分事件分值：被关注
     */
    int CREDITS_FOLLOWED = 3;

    /**
     * 累加积分并按阈值同步等级（Lv1~Lv8）：
     * 0 / 100 / 300 / 600 / 1000 / 1600 / 2500 / 4000
     *
     * @param userId 用户id
     * @param delta  积分增量，可为负（取消赞、取关），积分下限为 0
     */
    void addCredits(Long userId, int delta);
}
