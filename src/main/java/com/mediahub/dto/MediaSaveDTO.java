package com.mediahub.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增条目 DTO（管理端）：
 * 只声明允许客户端填写的字段，markCount / comments 等服务端维护的冗余计数不在其中，
 * 客户端越权传入的字段会在反序列化阶段被直接丢弃（防 mass assignment）
 */
@Data
public class MediaSaveDTO {

    /**
     * 条目名称
     */
    @NotBlank(message = "条目名称不能为空")
    @Size(max = 128, message = "条目名称不能超过128字")
    private String name;

    /**
     * 分类id：1图书 2电影 3剧集 4音乐
     */
    @NotNull(message = "分类id不能为空")
    @Min(value = 1, message = "分类id为1~4")
    @Max(value = 4, message = "分类id为1~4")
    private Long typeId;

    /**
     * 封面图路径，多张以','隔开
     */
    @Size(max = 1024, message = "封面图字段过长")
    private String images;

    /**
     * 国家 / 地区
     */
    @Size(max = 64, message = "国家/地区不能超过64字")
    private String area;

    /**
     * 作者 / 导演 / 主创
     */
    @Size(max = 64, message = "作者不能超过64字")
    private String author;

    /**
     * 发行年份
     */
    @Min(value = 1900, message = "发行年份不合法")
    @Max(value = 2100, message = "发行年份不合法")
    private Integer year;

    /**
     * 评分（满分100制，如92代表9.2分）；热榜公式依赖该字段，必填
     */
    @NotNull(message = "初始评分不能为空")
    @Min(value = 0, message = "评分范围为0~100")
    @Max(value = 100, message = "评分范围为0~100")
    private Integer score;

    /**
     * 时长 / 篇幅
     */
    @Size(max = 32, message = "时长/篇幅字段过长")
    private String duration;

    /**
     * 一句话简介
     */
    @Size(max = 512, message = "简介不能超过512字")
    private String intro;
}
