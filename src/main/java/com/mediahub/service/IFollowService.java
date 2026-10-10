package com.mediahub.service;

import com.mediahub.result.Result;
import com.mediahub.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 关注关系服务
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注 / 取消关注指定用户
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 查询当前用户是否已关注指定用户
     */
    Result isFollow(Long followUserId);

    /**
     * 查询当前用户与指定用户的共同关注列表
     */
    Result followCommons(Long id);
}
