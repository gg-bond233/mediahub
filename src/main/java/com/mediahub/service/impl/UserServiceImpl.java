package com.mediahub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mediahub.dto.LoginFormDTO;
import com.mediahub.dto.ProfileDTO;
import com.mediahub.result.Result;
import com.mediahub.dto.UserDTO;
import com.mediahub.entity.User;
import com.mediahub.entity.UserInfo;
import com.mediahub.mapper.UserMapper;
import com.mediahub.service.IUserInfoService;
import com.mediahub.service.IUserService;
import com.mediahub.utils.RegexUtils;
import com.mediahub.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.mediahub.utils.RedisConstants.*;
import static com.mediahub.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * 用户服务实现
 * 登录会话基于 Redis Hash 存储（token -> 用户信息），由拦截器负责刷新有效期。
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 开发模式下直接把验证码返回给前端，方便本地演示（未接入真实短信通道）
     */
    @Value("${app.dev-return-code:true}")
    private boolean devReturnCode;

    @Override
    public Result sendCode(String phone) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 2.生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 3.保存验证码到 redis，设置有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 4.发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        return devReturnCode ? Result.ok(code) : Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 2.从redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }

        // 3.一致，根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();

        // 4.判断用户是否存在
        if (user == null) {
            // 不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }

        // 5.保存用户信息到 redis
        // 5.1.随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 5.2.将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 5.3.存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 5.4.设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 6.返回token
        return Result.ok(token);
    }

    @Override
    public Result logout(String token) {
        if (StrUtil.isNotBlank(token)) {
            stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        }
        return Result.ok();
    }

    @Override
    public Result sign() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.防重复签到：SETBIT 原子写入并返回旧值，旧值为 1 说明今天已签
        //（写入与检查合并为一条命令，Redis 单线程排队执行，天然消除并发重复加分窗口）
        Boolean old = stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        if (Boolean.TRUE.equals(old)) {
            return Result.fail("今天已经签到过了");
        }
        // 6.首次签到 +5 积分
        userInfoService.addCredits(userId, IUserInfoService.CREDITS_SIGN);
        return Result.ok(IUserInfoService.CREDITS_SIGN);
    }

    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.循环遍历，统计从今天开始向前的连续签到天数
        int count = 0;
        while (true) {
            // 6.1.让这个数字与1做与运算，得到数字的最后一个bit位
            if ((num & 1) == 0) {
                // 为0，说明未签到，结束
                break;
            } else {
                // 不为0，说明已签到，计数器+1
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    @Override
    public Result uvCount() {
        // HyperLogLog 统计当日独立访客数
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long uv = stringRedisTemplate.opsForHyperLogLog().size(UV_KEY + today);
        return Result.ok(uv == null ? 0 : uv);
    }

    @Override
    public Result updateProfile(ProfileDTO profile) {
        Long userId = UserHolder.getUser().getId();
        // 1.昵称存在 tb_user（2~24 字）
        String nickName = StrUtil.trimToNull(profile.getNickName());
        if (nickName != null) {
            if (nickName.length() < 2 || nickName.length() > 24) {
                return Result.fail("昵称长度需在 2~24 字之间！");
            }
            User user = new User();
            user.setId(userId);
            user.setNickName(nickName);
            updateById(user);
        }
        // 2.其余字段存在 tb_user_info（无记录则补建）
        UserInfo info = userInfoService.query().eq("user_id", userId).one();
        boolean exists = info != null;
        if (!exists) {
            info = new UserInfo();
            info.setUserId(userId);
            info.setFans(0);
            info.setFollowee(0);
            info.setCredits(0);
            info.setLevel(1);
        }
        if (StrUtil.isNotBlank(profile.getCity())) {
            info.setCity(StrUtil.maxLength(profile.getCity(), 32));
        }
        if (profile.getIntroduce() != null) {
            info.setIntroduce(StrUtil.maxLength(profile.getIntroduce(), 200));
        }
        if (profile.getGender() != null && profile.getGender() >= 0 && profile.getGender() <= 2) {
            info.setGender(profile.getGender());
        }
        if (profile.getBirthday() != null) {
            info.setBirthday(profile.getBirthday());
        }
        if (exists) {
            userInfoService.updateById(info);
        } else {
            userInfoService.save(info);
        }
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        // 同步创建用户详情，保证个人主页统计栏有默认数据
        UserInfo info = new UserInfo();
        info.setUserId(user.getId());
        info.setCity("未知");
        info.setFans(0);
        info.setFollowee(0);
        info.setCredits(0);
        info.setLevel(1);
        userInfoService.save(info);
        return user;
    }
}
