package com.nongpi.fulfillment.lot.infrastructure.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端配置
 *
 * <p>使用单节点模式连接 Redis。锁默认等待时间 5 秒，持有时间 10 秒，
 * 在 {@link com.nongpi.fulfillment.lot.infrastructure.OutboundService} 中使用。</p>
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * 锁等待时间（毫秒）
     */
    @Value("${redisson.lock.wait-time:5000}")
    private long lockWaitTime;

    /**
     * 锁持有时间（毫秒）
     */
    @Value("${redisson.lock.lease-time:10000}")
    private long lockLeaseTime;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + redisHost + ":" + redisPort;
        config.useSingleServer()
                .setAddress(address)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                .setConnectionPoolSize(10)
                .setConnectionMinimumIdleSize(5)
                .setDnsMonitoringInterval(5000);
        return Redisson.create(config);
    }

    public long getLockWaitTime() {
        return lockWaitTime;
    }

    public long getLockLeaseTime() {
        return lockLeaseTime;
    }
}