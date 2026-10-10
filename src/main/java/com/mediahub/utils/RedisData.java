package com.mediahub.utils;

import lombok.Data;

import java.time.LocalDateTime;

/** 逻辑过期缓存的包装对象：data 为实际数据，expireTime 为逻辑过期时间 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
