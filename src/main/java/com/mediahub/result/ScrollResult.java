package com.mediahub.result;

import lombok.Data;

import java.util.List;

/**
 * 滚动分页（基于时间游标）响应结果，常用于关注动态/评论分页
 */
@Data
public class ScrollResult {
    /**
     * 本次返回的数据列表
     */
    private List<?> list;
    /**
     * 下次查询的最小时间游标
     */
    private Long minTime;
    /**
     * 偏移量（用于同一时间戳下的记录跳过）
     */
    private Integer offset;
}
