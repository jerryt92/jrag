package io.github.jerryt92.jrag.config;

import io.github.jerryt92.jrag.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final CommonProperties commonProperties;

    public InterceptorConfig(LoginInterceptor loginInterceptor, CommonProperties commonProperties) {
        this.loginInterceptor = loginInterceptor;
        this.commonProperties = commonProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!commonProperties.publicMode) {
            registry.addInterceptor(loginInterceptor)
                    .excludePathPatterns("/v*/auth/**")
                    .addPathPatterns("/v*/rest/**", "/v*/api/**");
        }
    }
}