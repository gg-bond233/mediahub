package com.mediahub.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.mediahub.dto.MediaSaveDTO;
import com.mediahub.dto.MediaUpdateDTO;
import com.mediahub.result.Result;
import com.mediahub.entity.Media;
import com.mediahub.service.IMediaService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 条目管理控制器（/admin/**：需登录且角色为管理员，由 AdminInterceptor 校验）
 * <p>
 * 写操作只接收 DTO 而非实体：markCount / comments 等服务端维护的字段不允许客户端传入。
 */
@RestController
@RequestMapping("/admin/media")
public class AdminMediaController {

    // 条目服务
    @Resource
    public IMediaService mediaService;

    /**
     * 新增条目信息
     * @param mediaSaveDTO 条目数据（仅允许客户端填写的字段）
     * @return 条目id
     */
    @PostMapping
    public Result saveMedia(@Valid @RequestBody MediaSaveDTO mediaSaveDTO) {
        // 防重：同名（填写了作者时连同作者）条目不允许重复录入
        Long count = mediaService.query()
                .eq("name", mediaSaveDTO.getName())
                .eq(StrUtil.isNotBlank(mediaSaveDTO.getAuthor()), "author", mediaSaveDTO.getAuthor())
                .count();
        if (count > 0) {
            return Result.fail("同名条目已存在，请勿重复录入");
        }
        // DTO -> 实体：markCount/comments 等字段不在 DTO 中，插入时走数据库默认值 0
        Media media = BeanUtil.copyProperties(mediaSaveDTO, Media.class);
        mediaService.save(media);
        return Result.ok(media.getId());
    }

    /**
     * 更新条目信息
     * @param dto 条目数据（含条目id）
     * @return 操作结果
     */
    @PutMapping
    public Result updateMedia(@Valid @RequestBody MediaUpdateDTO dto) {
        // DTO 全字段拷贝为实体，冗余计数字段保持 null 不参与更新（MyBatis-Plus 默认跳过 null）
        Media media = BeanUtil.copyProperties(dto, Media.class);
        // service 内部校验条目存在性，更新后清理缓存
        return mediaService.update(media);
    }
}
