package com.jscreator.auth.config;

import com.jscreator.auth.mapper.UserMapper;
import com.jscreator.common.web.RoleLookup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 管理端鉴权需要的角色查询：auth 服务直接查 user 表（user 表归本服务独占）。 */
@Configuration
public class AuthBeans {

    @Bean
    public RoleLookup roleLookup(UserMapper userMapper) {
        return userId -> userId == null ? null : userMapper.selectRoleId(userId);
    }
}
