package com.ytec.linebridge.config;

import com.ytec.linebridge.security.AdminAuthInterceptor;
import com.ytec.linebridge.security.ApiClientInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 攔截器與 CORS 設定
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final ApiClientInterceptor apiClientInterceptor;

    public WebMvcConfig(AdminAuthInterceptor adminAuthInterceptor, ApiClientInterceptor apiClientInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.apiClientInterceptor = apiClientInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login");

        registry.addInterceptor(apiClientInterceptor)
                .addPathPatterns("/api/send/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
