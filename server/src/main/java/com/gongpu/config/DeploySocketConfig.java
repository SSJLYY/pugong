package com.gongpu.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisSocketConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * 部署适配（Muse VM 等套接字环境专用）：该 VM 上 JVM 进程的直连 TCP 被
 * 平台拦截，Redis 改走 Unix 域套接字。
 *
 * 默认不启用（136 等常规环境走 application.yml 的 127.0.0.1:6379 TCP）。
 * 仅当启动参数显式指定 -Dgongpu.redis.socket=&lt;路径&gt;（或环境变量
 * GONGPU_REDIS_SOCKET）时，本配置才生效，StringRedisTemplate 改用本工厂。
 */
@Configuration
@ConditionalOnProperty("gongpu.redis.socket")
public class DeploySocketConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${gongpu.redis.socket}") String socketPath) {
        return new LettuceConnectionFactory(new RedisSocketConfiguration(socketPath));
    }
}
