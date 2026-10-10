package com.mediahub.config;

import cn.hutool.core.util.StrUtil;
import com.mediahub.utils.AdminInterceptor;
import com.mediahub.utils.LoginInterceptor;
import com.mediahub.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;

@Configuration
/** MVC 配置：注册登录/刷新 token 拦截器，并映射评论图片静态资源 */
public class MvcConfig implements WebMvcConfigurer {

    // Redis 模板，用于 token 刷新拦截器
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 评论图片上传目录
     */
    @Value("${app.upload-dir}")
    private String uploadDir;

    /** 注册拦截器：token刷新 -> 登录校验 -> 管理员校验，逐层放行 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录拦截器：写操作与管理接口不再放行，仅放行公开查询路径
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        // 条目查询公开；新增/更新已迁移至 /admin/**，受登录+角色双重拦截
                        "/media/*",
                        "/media/of/**",
                        // 典藏/秒杀大厅查询公开；新增已迁移至 /admin/**
                        "/collectible/list/**",
                        "/collectible/seckill/list",
                        "/media-type/**",
                        "/rank/**",
                        // 评论配图静态资源：img 标签请求不携带 authorization 头，必须放行
                        "/images/**",
                        "/comment/hot",
                        "/comment/{id}",
                        "/comment/likes/**",
                        "/comment/of/media/**",
                        "/comment/of/user",
                        // 配图上传需要登录，不再放行 /upload/**
                        "/user/code",
                        "/user/login",
                        "/user/info/**",
                        "/user/{id:[0-9]+}",
                        // Spring Boot 404/异常时内部转发到 /error：放行，避免未登录误报 401
                        "/error"
                ).order(1);
        // 管理员拦截器：/admin/** 需要管理员角色（在登录拦截器之后执行）
        registry.addInterceptor(new AdminInterceptor())
                .addPathPatterns("/admin/**")
                .order(2);
        // token刷新的拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }

    /** 将上传目录映射为 /images/** 静态资源供前端访问 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将上传目录映射为静态资源，供前端直接访问（规范化为绝对路径，与写入侧保持一致）
        String absoluteDir = new java.io.File(uploadDir).getAbsolutePath();
        String location = absoluteDir.endsWith("/") ? absoluteDir : absoluteDir + "/";
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + location);
    }
}
