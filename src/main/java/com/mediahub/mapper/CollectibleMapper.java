package com.mediahub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mediahub.entity.Collectible;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 典藏（mediaHub 中用户收藏/典藏条目）Mapper 接口
 */
public interface CollectibleMapper extends BaseMapper<Collectible> {

    /** 查询某部媒体下的所有典藏 */
    List<Collectible> queryCollectibleOfMedia(@Param("mediaId") Long mediaId);

    /** 查询全部秒杀典藏 */
    List<Collectible> queryAllSeckillCollectibles();
}
