package com.luoyujia.yjoj.judge;

import com.luoyujia.yjoj.client.service.QuestionFeignClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.luoyujia.yjoj")
@EnableDiscoveryClient
@EnableFeignClients(clients = QuestionFeignClient.class)
public class YjojBackendJudgeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(YjojBackendJudgeServiceApplication.class, args);
    }
}
