package com.luoyujia.yjoj.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication(scanBasePackages = "com.luoyujia.yjoj")
@MapperScan("com.luoyujia.yjoj.user.mapper")
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableDiscoveryClient
public class YjojBackendUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(YjojBackendUserServiceApplication.class, args);
    }
}
