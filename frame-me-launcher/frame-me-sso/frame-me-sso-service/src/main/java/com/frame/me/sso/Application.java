package com.frame.me.sso;

import com.alicp.jetcache.anno.config.EnableMethodCache;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SSO 单点登录服务启动类.
 *
 * @author frame-me
 */
@EnableMethodCache(basePackages = "com.frame.me.sso")
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
