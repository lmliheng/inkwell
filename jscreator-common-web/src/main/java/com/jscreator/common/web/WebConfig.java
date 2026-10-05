package com.jscreator.common.web;

import com.jscreator.common.security.JwtUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册鉴权拦截器：拦全部路径，但只有带 @RequireLogin / @RequireAdmin 的处理方法会被拒绝。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtUtil jwtUtil;
    private final RoleLookup roleLookup;

    public WebConfig(JwtUtil jwtUtil, RoleLookup roleLookup) {
        this.jwtUtil = jwtUtil;
        this.roleLookup = roleLookup;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(jwtUtil, roleLookup)).addPathPatterns("/**");
    }
}
