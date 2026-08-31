package com.nongpi.fulfillment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 农批履约中台 — 主启动类。
 * <p>
 * 通过 {@code @ComponentScan("com.nongpi.fulfillment")} 扫描所有子模块包，
 * {@code @MapperScan("com.nongpi.fulfillment")} 扫描所有 MyBatis Mapper 接口，
 * {@code @EnableScheduling} 开启定时任务。
 * </p>
 */
// Spring AI 三个 exclude 已移除(依赖删除后无需排除)。仅保留 UserDetailsServiceAutoConfiguration 排除
@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
})
@ComponentScan("com.nongpi.fulfillment")
@EnableScheduling
@MapperScan("com.nongpi.fulfillment")
public class NongpiFulfillmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(NongpiFulfillmentApplication.class, args);
    }
}