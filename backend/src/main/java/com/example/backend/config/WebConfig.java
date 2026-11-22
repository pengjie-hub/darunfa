package com.example.backend.config;

import com.example.backend.security.AuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册鉴权拦截器、跨域、密码编码器。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/ping", "/api/chat", "/api/ai/**");
    }

    /**
     * Vue 后台的入口跳转。
     *
     * <p>后台已经整体迁到 {@code frontend/}（Vite 构建，产物落 {@code static/app/}，
     * 路由用 <b>hash</b> 模式）。Spring 的静态资源处理器不会给目录做 index 解析，
     * 于是直接访问 {@code /app/} 会掉进 {@link com.example.backend.common.GlobalExceptionHandler}
     * 的 {@code NoResourceFoundException} 分支，返回一段
     * {@code {"code":404,"message":"接口不存在: app"}} 的 JSON —— 用户看到的是纯接口报错，
     * 完全看不出这里应该是一个页面。
     *
     * <p>这里只补两个「裸目录」入口，真正的页面仍然是 {@code /app/index.html}
     * （hash 路由的 {@code #/xxx} 部分由前端自己处理，服务端不需要管）。
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/app", "/app/index.html");
        registry.addRedirectViewController("/app/", "/app/index.html");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Android 原生 App 不受 CORS 限制；此处为将来可能的 Web 后台预留
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
