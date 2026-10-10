package com.mediahub.dto;

import lombok.Data;

/**
 * 用户信息 DTO（对外展示的用户基础信息，脱敏精简后返回）
 */
@Data
public class UserDTO {
    /**
     * 用户id
     */
    private Long id;
    /**
     * 昵称
     */
    private String nickName;
    /**
     * 头像
     */
    private String icon;
    /**
     * 角色：0普通用户 1管理员（管理员可访问 /admin/** 管理接口）
     */
    private Integer role;
}
