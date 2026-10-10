package com.mediahub.utils;

import com.mediahub.dto.UserDTO;

/** 基于 ThreadLocal 保存当前请求的用户信息，供同线程内任意位置获取 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
