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
import java.util.List;

/**
 * 统一评论（长评短评融合 + 楼中楼）
 * <p>
 * title 可选（短评无需）；score 可选（1-5 星，计入条目均分）；
 * parentId=0 为一级评论，非 0 为楼内回复。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_comment")
public class Comment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /**
     * 书影音条目id
     */
    private Long mediaId;
    /**
     * 评论人id
     */
    private Long userId;
    /**
     * 标题（可选，短评无需）
     */
    private String title;
    /**
     * 配图路径，多个用逗号分隔（可选）
     */
    private String images;
    /**
     * 评论内容，长短皆可
     */
    private String content;
    /**
     * 评分 1-5 星（可选，计入条目均分）
     */
    private Integer score;
    /**
     * 所属楼层id，0 为一级评论
     */
    private Long parentId;
    /**
     * 被回复的用户id（楼内回复@用）
     */
    private Long replyUserId;
    /**
     * 点赞数（仅一级评论）
     */
    private Integer liked;
    /**
     * 楼内回复数
     */
    private Integer comments;
    /**
     * 热度分（热榜用，非表字段）
     */
    @TableField(exist = false)
    private Integer hotScore;
    /**
     * 评论人昵称（展示用，非表字段）
     */
    @TableField(exist = false)
    private String name;
    /**
     * 评论人头像（展示用，非表字段）
     */
    @TableField(exist = false)
    private String icon;
    /**
     * 被回复人昵称（展示用，非表字段）
     */
    @TableField(exist = false)
    private String replyUserName;
    /**
     * 楼内回复列表（展示用，非表字段）
     */
    @TableField(exist = false)
    private List<Comment> replies;
    /**
     * 当前用户是否已点赞（展示用，非表字段）
     */
    @TableField(exist = false)
    private Boolean isLike;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
