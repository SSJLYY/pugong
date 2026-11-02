package com.gongpu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisSocketConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * 部署适配（本机环境专用）：本 VM 上 JVM 进程的直连 TCP 被平台拦截，
 * Redis 改走 Unix 域套接字。定义此 Bean 后 Spring Boot 的 Redis
 * 自动配置会退让，StringRedisTemplate 自动改用本工厂。
 */
@Configuration
public class DeploySocketConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(
                new RedisSocketConfiguration("/home/hatch/workspace/pugong-deploy/redis.sock"));
    }
}
