package com.mediahub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 限量典藏（挂在书影音条目下的限量抢购商品，例如签名书、黑胶、海报套装）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_collectible")
public class Collectible implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联的书影音条目id
     */
    private Long mediaId;

    /**
     * 所属条目名称（联查填充，用于抢购大厅展示）
     */
    @TableField(exist = false)
    private String mediaName;

    /**
     * 典藏商品标题
     */
    private String title;

    /**
     * 副标题
     */
    private String subTitle;

    /**
     * 购买规则
     */
    private String rules;

    /**
     * 抢购价（分）
     */
    private Long payValue;

    /**
     * 原价（分）
     */
    private Long actualValue;

    /**
     * 类型：0普通商品 1限量秒杀
     */
    private Integer type;

    /**
     * 状态：0下架 1上架
     */
    private Integer status;

    /**
     * 秒杀库存（来自 tb_seckill_collectible，联查填充）
     */
    @TableField(exist = false)
    private Integer stock;

    /**
     * 抢购开始时间（来自 tb_seckill_collectible，联查填充）
     */
    @TableField(exist = false)
    private LocalDateTime beginTime;

    /**
     * 抢购结束时间（来自 tb_seckill_collectible，联查填充）
     */
    @TableField(exist = false)
    private LocalDateTime endTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
