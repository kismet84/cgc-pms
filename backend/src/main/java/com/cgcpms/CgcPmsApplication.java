package com.cgcpms;

import com.cgcpms.auth.config.JwtProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Construction General Contracting Project Management System.
 * Application entry point.
 */
@SpringBootApplication
@MapperScan("com.cgcpms.**.mapper")
@EnableConfigurationProperties(JwtProperties.class)
@EnableScheduling
@EnableAspectJAutoProxy(exposeProxy = true)
public class CgcPmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CgcPmsApplication.class, args);
    }
}
