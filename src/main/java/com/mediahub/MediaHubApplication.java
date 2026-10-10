package com.mediahub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.mediahub.mapper")
@SpringBootApplication
/** mediaHub 应用启动类 */
public class MediaHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaHubApplication.class, args);
    }

}
