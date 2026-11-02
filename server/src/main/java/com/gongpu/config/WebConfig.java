package com.gongpu.config;

import com.gongpu.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final com.gongpu.admin.AdminInterceptor adminInterceptor;
    private final com.gongpu.web.TimingInterceptor timingInterceptor;

    @org.springframework.beans.factory.annotation.Value("${gongpu.cors.origins:http://127.0.0.1:*,http://localhost:*}")
    private String corsOrigins;

    public WebConfig(AuthInterceptor authInterceptor,
                     com.gongpu.admin.AdminInterceptor adminInterceptor,
                     com.gongpu.web.TimingInterceptor timingInterceptor) {
        this.authInterceptor = authInterceptor;
        this.adminInterceptor = adminInterceptor;
        this.timingInterceptor = timingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/health",
                        "/api/config/meta",
                        "/api/leaderboard/**",
                        "/api/social/graveyard",
                        "/api/social/flavor",
                        "/api/social/bulletin",
                        "/api/admin/**");
        // 管理端：/api/admin/** 全部走 AdminGuard（login 在拦截器内放行由 login 端点自身处理，
        // 这里改为拦截 admin 全部路径，但 /api/admin/login 需要放行）
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/login");
        // 慢端点观测:>gongpu.slow-ms(默认 500ms)的请求打 WARN
        registry.addInterceptor(timingInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 生产为同源部署（nginx /gongpu/api/ 反代），但浏览器 fetch 即使同源 POST 也会带 Origin 头，
        // 必须把部署源本身加进白名单，否则 Spring 返回 403 Invalid CORS request
        // 额外来源用 gongpu.cors.extra-origins 配置（逗号分隔通配模式），不再硬编码内网 IP
        registry.addMapping("/api/**")
                .allowedOriginPatterns(corsOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
