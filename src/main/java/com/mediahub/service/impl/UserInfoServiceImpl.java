package com.mediahub.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mediahub.entity.UserInfo;
import com.mediahub.mapper.UserInfoMapper;
import com.mediahub.service.IUserInfoService;
import org.springframework.stereotype.Service;

/**
 * 用户详情服务实现类
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

    /**
     * 等级阈值 SQL（credits 为同一 UPDATE 中累加后的新值，MySQL SET 从左到右生效）：
     * 0 / 100 / 300 / 600 / 1000 / 1600 / 2500 / 4000 对应 Lv1~Lv8
     */
    private static final String LEVEL_SQL =
            "level = CASE WHEN credits >= 4000 THEN 8" +
                    " WHEN credits >= 2500 THEN 7" +
                    " WHEN credits >= 1600 THEN 6" +
                    " WHEN credits >= 1000 THEN 5" +
                    " WHEN credits >= 600 THEN 4" +
                    " WHEN credits >= 300 THEN 3" +
                    " WHEN credits >= 100 THEN 2" +
                    " ELSE 1 END";

    @Override
    // 累加积分（下限0）并同步重算用户等级
    public void addCredits(Long userId, int delta) {
        if (userId == null || delta == 0) {
            return;
        }
        // 单条 UPDATE 原子完成：积分累加（下限0）+ 按新积分重算等级
        update(new UpdateWrapper<UserInfo>()
                .setSql("credits = GREATEST(0, credits + " + delta + ")")
                .setSql(LEVEL_SQL)
                .eq("user_id", userId));
    }
}
