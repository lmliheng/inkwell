package com.jscreator.system.monitor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Set;

/**
 * 启动后把本服务的全部路由登记进 {@link ApiMonitor}（未调用时 count=0），
 * 对齐 Express 版 app.ts 末尾的 {@code registerRoutes(app)}。
 *
 * <p>两处刻意的映射：
 * <ul>
 *   <li>Spring 的路径变量 {@code /article/detail/{id}} 转成 Express 写法 {@code /article/detail/:id}，
 *       前端系统监控页展示的路径与原版同形；</li>
 *   <li>框架自带的 {@code /error}、{@code /actuator/**} 不登记（原版没有这些端点）。</li>
 * </ul>
 */
@Component
public class ApiRouteRegistrar implements ApplicationRunner {

    /** Spring MVC 自带、原版不存在的端点，登记了只会让列表变味。 */
    private static final List<String> IGNORED_PREFIXES = List.of("/error", "/actuator");

    private static final String[] ALL_METHODS = {"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"};

    private final RequestMappingHandlerMapping handlerMapping;
    private final ApiMonitor monitor;

    /**
     * 必须按名字取 —— actuator 也注册了一个 RequestMappingHandlerMapping
     * （controllerEndpointHandlerMapping），不指定就会「找到两个同类型 bean」启动失败。
     */
    public ApiRouteRegistrar(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
                             ApiMonitor monitor) {
        this.handlerMapping = handlerMapping;
        this.monitor = monitor;
    }

    @Override
    public void run(ApplicationArguments args) {
        handlerMapping.getHandlerMethods().forEach(this::register);
    }

    private void register(RequestMappingInfo info, Object handler) {
        Set<String> patterns;
        if (info.getPathPatternsCondition() != null) {
            patterns = info.getPathPatternsCondition().getPatternValues();
        } else if (info.getPatternsCondition() != null) {
            patterns = info.getPatternsCondition().getPatterns();
        } else {
            return;
        }

        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        String[] verbs = methods.isEmpty()
                ? ALL_METHODS
                : methods.stream().map(Enum::name).toArray(String[]::new);

        for (String pattern : patterns) {
            if (IGNORED_PREFIXES.stream().anyMatch(pattern::startsWith)) {
                continue;
            }
            String path = toExpressPath(pattern);
            for (String verb : verbs) {
                monitor.register(verb, path);
            }
        }
    }

    /** {@code /a/{id}/b/{x}} → {@code /a/:id/b/:x} */
    static String toExpressPath(String pattern) {
        return pattern.replaceAll("\\{([^}:]+)(?::[^}]*)?}", ":$1");
    }
}
