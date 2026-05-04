package com.ticketing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan(basePackages = "com.ticketing.dao")
@EnableScheduling
@EnableAsync
public class TicketingSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketingSystemApplication.class, args);
    }
}