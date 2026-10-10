package com.mediahub.config;

import cn.hutool.core.util.StrUtil;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置，复用 spring.data.redis 的连接参数（支持环境变量覆盖）
 */
@Configuration
public class RedissonConfig {

    // Redis 主机地址（default 为 localhost）
    @Value("${spring.data.redis.host:localhost}")
    private String host;

    // Redis 端口（default 为 6379）
    @Value("${spring.data.redis.port:6379}")
    private int port;

    // Redis 密码（可为空）
    @Value("${spring.data.redis.password:}")
    private String password;

    /** 创建 Redisson 客户端（单机模式，供分布式锁等使用） */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(0);
        if (StrUtil.isNotBlank(password)) {
            config.useSingleServer().setPassword(password);
        }
        return Redisson.create(config);
    }
}
