package com.example.seatreservation.config;

import com.example.seatreservation.security.UserAuthenticationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final UserAuthenticationInterceptor userAuthenticationInterceptor;

    public WebConfig(UserAuthenticationInterceptor userAuthenticationInterceptor) {
        this.userAuthenticationInterceptor = userAuthenticationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userAuthenticationInterceptor);
    }
}
