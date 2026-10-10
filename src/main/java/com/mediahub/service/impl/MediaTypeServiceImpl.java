package com.mediahub.service.impl;

import com.mediahub.entity.MediaType;
import com.mediahub.mapper.MediaTypeMapper;
import com.mediahub.service.IMediaTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 书影音分类服务实现类
 */
@Service
public class MediaTypeServiceImpl extends ServiceImpl<MediaTypeMapper, MediaType> implements IMediaTypeService {

}
