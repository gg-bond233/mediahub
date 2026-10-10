package com.mediahub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mediahub.result.Result;
import com.mediahub.entity.Mark;
import com.mediahub.entity.Media;
import com.mediahub.mapper.MarkMapper;
import com.mediahub.service.IMarkService;
import com.mediahub.service.IMediaService;
import com.mediahub.service.IRankService;
import com.mediahub.utils.SystemConstants;
import com.mediahub.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.mediahub.utils.RedisConstants.CACHE_MEDIA_KEY;

/**
 * 条目标记服务（纯三态：想看/在看/看过）
 * <p>
 * 标记会改变条目的 mark_count，为避免读到旧计数，
 * 采用与 {@link MediaServiceImpl} 一致的 Cache Aside 策略：先更新库，再删缓存。
 */
@Slf4j
@Service
public class MarkServiceImpl extends ServiceImpl<MarkMapper, Mark> implements IMarkService {

    @Resource
    private IMediaService mediaService;

    @Resource
    private IRankService rankService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public Result saveMark(Mark mark) {
        Long userId = UserHolder.getUser().getId();
        Long mediaId = mark.getMediaId();
        if (mediaId == null || mediaService.getById(mediaId) == null) {
            return Result.fail("条目不存在！");
        }
        Integer status = mark.getStatus();
        if (status == null || status < 1 || status > 3) {
            return Result.fail("标记状态不合法！");
        }
        mark.setUserId(userId);
        // 一人一条目一条记录：已有标记则更新，没有则新增
        Mark old = query().eq("user_id", userId).eq("media_id", mediaId).one();
        if (old == null) {
            save(mark);
            // 条目标记数 +1（原子更新），条目热度 +0.1（与重建公式 mark_count / 10 对齐）
            mediaService.update(new UpdateWrapper<Media>()
                    .setSql("mark_count = mark_count + 1").eq("id", mediaId));
            rankService.increaseMediaScore(mediaId, 0.1);
        } else {
            mark.setId(old.getId());
            updateById(mark);
        }
        // Cache Aside：标记数变了，删除条目缓存
        stringRedisTemplate.delete(CACHE_MEDIA_KEY + mediaId);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result deleteMark(Long mediaId) {
        Long userId = UserHolder.getUser().getId();
        boolean removed = remove(new QueryWrapper<Mark>()
                .eq("user_id", userId).eq("media_id", mediaId));
        if (removed) {
            mediaService.update(new UpdateWrapper<Media>()
                    .setSql("mark_count = mark_count - 1").eq("id", mediaId));
            // 取消标记：条目热度 -0.1，与保存时的 +0.1 对称回滚
            rankService.increaseMediaScore(mediaId, -0.1);
            stringRedisTemplate.delete(CACHE_MEDIA_KEY + mediaId);
        }
        return Result.ok();
    }

    @Override
    public Result queryMyMark(Long mediaId) {
        Long userId = UserHolder.getUser().getId();
        Mark mark = query().eq("user_id", userId).eq("media_id", mediaId).one();
        return Result.ok(mark);
    }

    @Override
    public Result queryMyMarks(Integer current) {
        Long userId = UserHolder.getUser().getId();
        Page<Mark> page = query()
                .eq("user_id", userId)
                .orderByDesc("update_time")
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        List<Mark> records = page.getRecords();
        if (records.isEmpty()) {
            return Result.ok(records);
        }
        // 组装条目信息（批量查询，避免 N+1）
        Set<Long> mediaIds = records.stream().map(Mark::getMediaId).collect(Collectors.toSet());
        Map<Long, Media> mediaMap = mediaService.listByIds(mediaIds).stream()
                .collect(Collectors.toMap(Media::getId, m -> m));
        List<Map<String, Object>> list = records.stream().map(mark -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("mediaId", mark.getMediaId());
            Media media = mediaMap.get(mark.getMediaId());
            item.put("mediaName", media == null ? "已下架条目" : media.getName());
            item.put("mediaImage", media == null ? "" : media.getImages());
            item.put("typeId", media == null ? null : media.getTypeId());
            item.put("status", mark.getStatus());
            item.put("createTime", mark.getCreateTime());
            item.put("updateTime", mark.getUpdateTime());
            return item;
        }).collect(Collectors.toList());
        return Result.ok(list);
    }
}
