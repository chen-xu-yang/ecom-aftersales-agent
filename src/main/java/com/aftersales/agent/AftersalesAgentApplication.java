package com.aftersales.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {
        // 默认走内存向量存储，不依赖数据库；切换到 PgVector 时删除该排除项
        DataSourceAutoConfiguration.class
})
public class AftersalesAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AftersalesAgentApplication.class, args);
    }

}
