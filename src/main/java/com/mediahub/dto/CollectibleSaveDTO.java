package com.mediahub.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 新增典藏 DTO（管理端）：
 * 公共字段用注解校验；秒杀专属字段（库存/起止时间）为跨字段规则，由 Controller 兜底校验。
 * type / status 由服务端决定，不暴露给客户端。
 */
@Data
public class CollectibleSaveDTO {

    /**
     * 典藏商品标题
     */
    @NotBlank(message = "典藏标题不能为空")
    @Size(max = 64, message = "典藏标题不能超过64字")
    private String title;

    /**
     * 副标题
     */
    @Size(max = 128, message = "副标题不能超过128字")
    private String subTitle;

    /**
     * 关联的书影音条目id
     */
    @NotNull(message = "关联条目id不能为空")
    private Long mediaId;

    /**
     * 购买规则
     */
    @Size(max = 512, message = "购买规则不能超过512字")
    private String rules;

    /**
     * 抢购价（分）
     */
    @NotNull(message = "抢购价不能为空")
    @Min(value = 1, message = "抢购价必须大于0")
    private Long payValue;

    /**
     * 原价（分）
     */
    @NotNull(message = "原价不能为空")
    @Min(value = 1, message = "原价必须大于0")
    private Long actualValue;

    /**
     * 秒杀库存（仅秒杀典藏需要，跨字段校验由服务端兜底）
     */
    @Min(value = 1, message = "秒杀库存必须大于0")
    private Integer stock;

    /**
     * 抢购开始时间（仅秒杀典藏需要）
     */
    private LocalDateTime beginTime;

    /**
     * 抢购结束时间（仅秒杀典藏需要）
     */
    private LocalDateTime endTime;
}
