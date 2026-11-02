package com.gongpu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GongpuApplication {

    public static final String VERSION = "3.5";

    private static final Logger log = LoggerFactory.getLogger(GongpuApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(GongpuApplication.class, args);
        Environment env = ctx.getEnvironment();
        // 启动横幅:版本与激活 profile 一眼可辨(日志排障/巡检用)
        log.info("============================================================");
        log.info("  普攻永久增加生命上限 · gongpu-server v{}", VERSION);
        log.info("  profile: {}  port: {}", String.join(",", env.getActiveProfiles().length == 0
                ? new String[]{"default"} : env.getActiveProfiles()), env.getProperty("server.port"));
        log.info("============================================================");
    }
}
