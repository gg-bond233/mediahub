package com.mediahub.vo;

import lombok.Data;

/**
 * 用户详情对外展示 VO（白名单字段）
 * <p>
 * 性别可由用户选择保密，生日为选填字段（不填即不展示）；
 * 库表时间戳不对外暴露。生日用 String 存 yyyy-MM-dd，
 * 避免 LocalDate 被序列化为时间戳数组导致前端日期控件无法回显。
 */
@Data
public class UserInfoVO {

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 城市名称
     */
    private String city;

    /**
     * 个人介绍
     */
    private String introduce;

    /**
     * 粉丝数量
     */
    private Integer fans;

    /**
     * 关注数量
     */
    private Integer followee;

    /**
     * 积分
     */
    private Integer credits;

    /**
     * 等级
     */
    private Integer level;

    /**
     * 性别，0：保密，1：女，2：男
     */
    private Integer gender;

    /**
     * 生日（yyyy-MM-dd），选填，未设置为 null
     */
    private String birthday;
}
