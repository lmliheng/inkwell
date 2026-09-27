package com.jscreator.common.web;

import com.jscreator.common.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** JWT 工具装配。密钥取 {@code JWT_SECRET}，缺省值与 Node 版一致（test）。 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtUtil jwtUtil(@Value("${jscreator.jwt.secret:${JWT_SECRET:}}") String secret) {
        return new JwtUtil(secret);
    }
}
