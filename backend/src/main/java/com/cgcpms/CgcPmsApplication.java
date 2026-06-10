package com.cgcpms;

import com.cgcpms.auth.config.JwtProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Construction General Contracting Project Management System.
 * Application entry point.
 */
@SpringBootApplication
@MapperScan("com.cgcpms.**.mapper")
@EnableConfigurationProperties(JwtProperties.class)
public class CgcPmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CgcPmsApplication.class, args);
    }
}
