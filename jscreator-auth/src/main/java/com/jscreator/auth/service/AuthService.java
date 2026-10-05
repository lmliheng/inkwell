package com.jscreator.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jscreator.auth.entity.User;
import com.jscreator.auth.mapper.UserMapper;
import com.jscreator.common.security.JwtUtil;
import com.jscreator.common.security.PasswordUtil;
import org.springframework.stereotype.Service;

/**
 * 登录/注册业务规则，逐条对齐 Express 版 modules/auth/auth.service：
 * 校验失败返回 null（由 controller 翻成 401），注册查重返回带 code 的失败对象。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    public AuthService(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    public record LoginOk(String token, User user) {
    }

    public record RegisterOk(String token, Long id, String username, String email) {
    }

    public record RegisterFail(int code, String message) {
    }

    public LoginOk loginByUsername(String username, String password) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !PasswordUtil.compare(password, user.getPassword())) {
            return null;
        }
        return new LoginOk(jwtUtil.sign(user.getId(), user.getRoleId()), user);
    }

    public LoginOk loginByEmail(String email, String password) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null || !PasswordUtil.compare(password, user.getPassword())) {
            return null;
        }
        return new LoginOk(jwtUtil.sign(user.getId(), user.getRoleId()), user);
    }

    /** 注册：查重 → 哈希 → 落库 → 发 token。id 沿用原版的毫秒时间戳生成方式。 */
    public Object register(String username, String email, String password) {
        if (existsByEmail(email)) {
            return new RegisterFail(400, "邮箱已存在");
        }
        if (existsByUsername(username)) {
            return new RegisterFail(400, "用户名已存在");
        }
        long id = System.currentTimeMillis();
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(PasswordUtil.toHash(password));
        userMapper.insert(user);
        return new RegisterOk(jwtUtil.signWithoutRole(id), id, username, email);
    }

    public boolean existsByEmail(String email) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0;
    }

    public boolean existsByUsername(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0;
    }
}
