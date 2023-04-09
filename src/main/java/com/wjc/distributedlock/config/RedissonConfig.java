package com.wjc.distributedlock.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        // 默认连接地址 127.0.0.1:6379
        // 初始化一个配置对象
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:6379");
//                .setDatabase(0) // 指定redis数据库编号
//                .setUsername("").setPassword()  // redis用户名和密码
//                .setConnectionMinimumIdleSize(10) // 连接池最小空闲连接数
//                .setConnectionPoolSize(50) //连接池最大线程数
//                .setIdleConnectionTimeout(60000) // 线程超时时间
//                .setConnectTimeout() // 客户端程序获取redis链接的超时时间
//                .setTimeout() // 响应超时时间
        return Redisson.create(config);
    }
}
