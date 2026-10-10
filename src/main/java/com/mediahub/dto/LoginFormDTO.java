package com.mediahub.dto;

import lombok.Data;

/**
 * 登录/注册表单参数 DTO
 */
@Data
public class LoginFormDTO {
    /**
     * 手机号
     */
    private String phone;
    /**
     * 手机验证码（验证码登录时必填）
     */
    private String code;
    /**
     * 密码（密码登录/注册时必填）
     */
    private String password;
}
