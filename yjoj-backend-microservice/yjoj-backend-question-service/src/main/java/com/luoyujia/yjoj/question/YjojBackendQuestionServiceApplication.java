package com.luoyujia.yjoj.question;

import com.luoyujia.yjoj.client.service.UserFeignClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication(scanBasePackages = "com.luoyujia.yjoj")
@MapperScan("com.luoyujia.yjoj.question.mapper")
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableDiscoveryClient
@EnableFeignClients(clients = UserFeignClient.class)
public class YjojBackendQuestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(YjojBackendQuestionServiceApplication.class, args);
    }
}
