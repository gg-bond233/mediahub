package com.mediahub.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mediahub.result.Result;
import com.mediahub.entity.Media;
import com.mediahub.mapper.MediaMapper;
import com.mediahub.service.IMediaService;
import com.mediahub.utils.CacheClient;
import com.mediahub.utils.SystemConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.mediahub.utils.RedisConstants.*;

/**
 * 书影音条目服务，包含基于 Redis 的多级缓存方案：
 * 查询走「缓存穿透」防护（空值缓存），更新采用 Cache Aside（先更新库，再删缓存）。
 */
@Service
public class MediaServiceImpl extends ServiceImpl<MediaMapper, Media> implements IMediaService {

    @Resource
    private StringRedisTemplate stringRedisTemplate; // Redis 操作模板

    @Resource
    private CacheClient cacheClient; // 统一缓存客户端（穿透/击穿防护）

    @Override
    // 按 id 查询条目详情（带缓存穿透防护），不存在返回失败
    public Result queryById(Long id) {
        // 解决缓存穿透
        Media media = cacheClient
                .queryWithPassThrough(CACHE_MEDIA_KEY, id, Media.class, this::getById, CACHE_MEDIA_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
        // Media media = cacheClient
        //         .queryWithMutex(CACHE_MEDIA_KEY, id, Media.class, this::getById, CACHE_MEDIA_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿
        // Media media = cacheClient
        //         .queryWithLogicalExpire(CACHE_MEDIA_KEY, id, Media.class, this::getById, 20L, TimeUnit.SECONDS);

        if (media == null) {
            return Result.fail("条目不存在！");
        }
        return Result.ok(media);
    }

    @Override
    @Transactional
    // 更新条目：先更新数据库再删除缓存，返回操作结果
    public Result update(Media media) {
        Long id = media.getId();
        if (id == null) {
            return Result.fail("条目id不能为空");
        }
        // 1.更新数据库（条目不存在则拒绝）
        if (!updateById(media)) {
            return Result.fail("条目不存在！");
        }
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_MEDIA_KEY + id);
        return Result.ok();
    }

    @Override
    // 按分类分页查询条目列表，评分高者优先
    public Result queryMediaByType(Integer typeId, Integer current) {
        // 按分类分页查询，评分高者优先
        Page<Media> page = query()
                .eq("type_id", typeId)
                .orderByDesc("score", "mark_count")
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }
}
