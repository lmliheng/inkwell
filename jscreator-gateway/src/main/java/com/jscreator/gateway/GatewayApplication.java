package com.jscreator.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * JScreator 网关：对外只暴露本服务（宿主 7000），按原路径把请求分发到四个业务服务。
 *
 * <p>鉴权不在这里做，保持与原 Express 版一致——每个接口的登录/管理员校验由下游服务承担，
 * 这样 111 个接口的权限语义只有一处定义。
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
