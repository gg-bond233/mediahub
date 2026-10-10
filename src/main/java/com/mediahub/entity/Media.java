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
 * 书影音条目（图书 / 电影 / 剧集 / 音乐）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_media")
public class Media implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 条目名称，例如《三体》
     */
    private String name;

    /**
     * 分类id：1图书 2电影 3剧集 4音乐
     */
    private Long typeId;

    /**
     * 封面图，多张以','隔开
     */
    private String images;

    /**
     * 国家 / 地区，例如中国大陆
     */
    private String area;

    /**
     * 作者 / 导演 / 主创，例如刘慈欣
     */
    private String author;

    /**
     * 发行年份，例如 2008
     */
    private Integer year;

    /**
     * 标记（想看/在看/看过）人数
     */
    private Integer markCount;

    /**
     * 长评数量
     */
    private Integer comments;

    /**
     * 评分，1~5分，乘10保存，避免小数，例如92代表9.2分
     */
    private Integer score;

    /**
     * 时长 / 篇幅，例如 142分钟、560页、12首
     */
    private String duration;

    /**
     * 一句话简介
     */
    private String intro;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 热度分（热榜用，非数据库字段）
     */
    @TableField(exist = false)
    private Integer hotScore;
}
