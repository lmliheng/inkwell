package com.jscreator.system.config;

import com.jscreator.common.web.RoleLookup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 管理端鉴权需要的角色查询。user 表由 auth 服务独占写入，这里只读 role_id；
 * 后续跨域调用变多时再换成 Feign 调 auth。
 */
@Configuration
public class SystemBeans {

    @Bean
    public RoleLookup roleLookup(JdbcTemplate jdbcTemplate) {
        return userId -> {
            if (userId == null) {
                return null;
            }
            try {
                return jdbcTemplate.queryForObject("SELECT role_id FROM user WHERE id = ?", Long.class, userId);
            } catch (EmptyResultDataAccessException e) {
                return null;
            }
        };
    }
}
