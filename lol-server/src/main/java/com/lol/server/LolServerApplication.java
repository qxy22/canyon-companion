package com.lol.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "com.lol")
public class LolServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LolServerApplication.class, args);
    }
}
