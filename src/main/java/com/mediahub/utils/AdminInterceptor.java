package com.mediahub.utils;

import com.mediahub.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 管理员拦截器：/admin/** 路径要求当前登录用户为管理员（role=1），否则返回 403
 * <p>
 * 注册顺序在登录拦截器之后：匿名请求先被登录拦截器以 401 拦截，这里兜底校验角色。
 */
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.兜底判断登录（正常情况下匿名请求已被登录拦截器拦截）
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            response.setStatus(401);
            return false;
        }
        // 2.校验管理员角色
        Integer role = user.getRole();
        if (role == null || role != 1) {
            response.setStatus(403);
            return false;
        }
        return true;
    }
}
