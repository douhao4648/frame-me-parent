package com.frame.me.tester;

import com.alicp.jetcache.anno.config.EnableMethodCache;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableMethodCache(basePackages = "com.frame.me.tester")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
