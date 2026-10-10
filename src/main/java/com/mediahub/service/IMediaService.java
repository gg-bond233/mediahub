package com.mediahub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mediahub.result.Result;
import com.mediahub.entity.Media;

/**
 * 书影音条目服务
 */
public interface IMediaService extends IService<Media> {

    /**
     * 根据条目id查询详情（含缓存穿透防护），不存在返回失败
     */
    Result queryById(Long id);

    /**
     * 更新条目：先更新数据库，再删除缓存，返回操作结果
     */
    Result update(Media media);

    /**
     * 按分类分页查询条目列表，评分高者优先
     */
    Result queryMediaByType(Integer typeId, Integer current);
}
