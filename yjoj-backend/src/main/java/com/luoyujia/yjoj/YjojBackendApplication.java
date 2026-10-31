package com.luoyujia.yjoj;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * YJOJ 单体后端启动入口。
 */
@SpringBootApplication
@MapperScan("com.luoyujia.yjoj.mapper")
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class YjojBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(YjojBackendApplication.class, args);
    }
}
