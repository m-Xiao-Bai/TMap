package com.mu.transitmap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.mu.transitmap.mapper")
@EnableScheduling
public class

TransitMapUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransitMapUserApplication.class, args);
    }
}
