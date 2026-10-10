package com.mediahub.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 编辑个人资料参数（昵称存 tb_user，其余存 tb_user_info）
 */
@Data
public class ProfileDTO {

    /**
     * 昵称（tb_user.nick_name）
     */
    private String nickName;

    /**
     * 城市（tb_user_info.city）
     */
    private String city;

    /**
     * 个人简介（tb_user_info.introduce）
     */
    private String introduce;

    /**
     * 性别：0 未知 1 女 2 男
     */
    private Integer gender;

    /**
     * 生日（tb_user_info.birthday）
     */
    private LocalDate birthday;
}
