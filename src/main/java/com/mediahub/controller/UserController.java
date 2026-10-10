package com.mediahub.controller;


import cn.hutool.core.bean.BeanUtil;
import com.mediahub.dto.LoginFormDTO;
import com.mediahub.dto.ProfileDTO;
import com.mediahub.result.Result;
import com.mediahub.dto.UserDTO;
import com.mediahub.vo.UserInfoVO;
import com.mediahub.entity.User;
import com.mediahub.entity.UserInfo;
import com.mediahub.service.IUserInfoService;
import com.mediahub.service.IUserService;
import com.mediahub.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    // 用户服务
    @Resource
    private IUserService userService;

    // 用户详情服务
    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone) {
        return userService.sendCode(phone);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm) {
        return userService.login(loginForm);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "authorization", required = false) String token) {
        return userService.logout(token);
    }

    /** 获取当前登录用户的信息 */
    @GetMapping("/me")
    public Result me() {
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    /** 查询用户详情（关注/粉丝数等统计，无详情时返回默认值；生日选填，性别可保密） */
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 尚无详情（老数据），返回默认统计，避免前端空指针
            info = new UserInfo();
            info.setUserId(userId);
            info.setFans(0);
            info.setFollowee(0);
            info.setCredits(0);
            info.setLevel(0);
        }
        // 白名单拷贝到 VO：库表时间戳不对外暴露；生日转 yyyy-MM-dd 字符串，避免时间戳数组无法回显
        UserInfoVO vo = BeanUtil.copyProperties(info, UserInfoVO.class);
        vo.setBirthday(info.getBirthday() == null ? null : info.getBirthday().toString());
        return Result.ok(vo);
    }

    /**
     * 编辑个人资料（昵称、城市、简介、性别、生日），需登录
     */
    @PutMapping("/profile")
    public Result updateProfile(@RequestBody ProfileDTO profile) {
        return userService.updateProfile(profile);
    }

    /** 根据 id 查询用户基础信息（仅匹配纯数字 id，避免 "code" 等已知子路径被误当作 id 解析） */
    @GetMapping("/{id:[0-9]+}")
    public Result queryUserById(@PathVariable("id") Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return Result.fail("用户不存在或已注销");
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    /** 完成当日签到 */
    @PostMapping("/sign")
    public Result sign() {
        return userService.sign();
    }

    /** 统计当前用户当月签到次数 */
    @GetMapping("/sign/count")
    public Result signCount() {
        return userService.signCount();
    }

    /**
     * 查询当日站点 UV（HyperLogLog）
     */
    @GetMapping("/uv")
    public Result uv() {
        return userService.uvCount();
    }
}
