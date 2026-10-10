package com.mediahub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mediahub.result.Result;
import com.mediahub.entity.Mark;

/**
 * 标记与短评服务
 */
public interface IMarkService extends IService<Mark> {

    /**
     * 打标记 / 更新标记（「看过」时可附评分与短评）
     */
    Result saveMark(Mark mark);

    /**
     * 取消标记
     */
    Result deleteMark(Long mediaId);

    /**
     * 我对某个条目的标记（详情页回显）
     */
    Result queryMyMark(Long mediaId);

    /**
     * 我的标记列表（个人主页）
     */
    Result queryMyMarks(Integer current);
}
