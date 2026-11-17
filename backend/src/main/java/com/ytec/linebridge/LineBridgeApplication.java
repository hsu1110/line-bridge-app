package com.ytec.linebridge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * LINE 模板訊息平台主程式入口
 */
@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan
@MapperScan("com.ytec.linebridge.mapper")
public class LineBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LineBridgeApplication.class, args);
    }
}
