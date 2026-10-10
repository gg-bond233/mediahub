package com.mediahub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mediahub.dto.LoginFormDTO;
import com.mediahub.dto.ProfileDTO;
import com.mediahub.result.Result;
import com.mediahub.entity.User;

/**
 * 用户服务：短信验证码登录（Redis 会话）、签到（BitMap）、站点 UV（HyperLogLog）
 */
public interface IUserService extends IService<User> {

    /**
     * 发送短信验证码并缓存，用于登录校验
     */
    Result sendCode(String phone);

    /**
     * 登录：校验验证码，成功后创建 Redis 会话并返回 token
     */
    Result login(LoginFormDTO loginForm);

    /**
     * 登出：删除会话 token，返回操作结果
     */
    Result logout(String token);

    /**
     * 每日签到，使用 BitMap 记录，返回本次签到结果
     */
    Result sign();

    /**
     * 查询当前用户累计签到天数
     */
    Result signCount();

    /**
     * 统计站点独立访客数（UV），基于 HyperLogLog
     */
    Result uvCount();

    /**
     * 编辑个人资料（昵称、城市、简介、性别、生日）
     */
    Result updateProfile(ProfileDTO profile);
}
