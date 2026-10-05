package com.jscreator.social.controller;

import com.jscreator.common.exception.BizException;
import com.jscreator.common.web.CurrentUser;
import com.jscreator.common.web.RequireLogin;
import com.jscreator.social.service.DmService;
import com.jscreator.social.service.SResult;
import com.jscreator.social.util.Js;
import com.jscreator.social.util.NodeResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * /dm/*，逐字段对齐 Express 版 modules/dm/dm.controller.ts。
 * 会话列表 / 消息 / 未读数的响应都只有 data（没有 message 键）。
 */
@RestController
public class DmController {

    private static final Logger log = LoggerFactory.getLogger(DmController.class);

    private final DmService service;

    public DmController(DmService service) {
        this.service = service;
    }

    @RequireLogin
    @GetMapping("/dm/conversations")
    public ResponseEntity<Map<String, Object>> conversations(HttpServletRequest request) {
        Long actor = CurrentUser.id(request);
        return wrap("获取失败", () -> service.conversations(actor));
    }

    @RequireLogin
    @GetMapping("/dm/messages/{otherId}")
    public ResponseEntity<Map<String, Object>> messages(
            @PathVariable("otherId") String otherId,
            @RequestParam(name = "page", required = false) List<String> page,
            @RequestParam(name = "pageSize", required = false) List<String> pageSize, HttpServletRequest request) {
        Long actor = CurrentUser.id(request);
        return wrap("获取失败",
                () -> service.messages(actor, otherId, SocialController.queryParam(page), SocialController.queryParam(pageSize)));
    }

    @RequireLogin
    @GetMapping("/dm/unread-count")
    public ResponseEntity<Map<String, Object>> unreadCount(HttpServletRequest request) {
        Long actor = CurrentUser.id(request);
        return wrap("获取失败", () -> service.unreadCount(actor));
    }

    @RequireLogin
    @PostMapping("/dm/read")
    public ResponseEntity<Map<String, Object>> read(@RequestBody(required = false) Object body,
                                                    HttpServletRequest request) {
        Long actor = CurrentUser.id(request);
        Map<String, Object> in = Js.asObjectMap(body);
        Object otherId = in.get("other_id");
        if (!Js.truthy(otherId)) {
            throw BizException.badRequest("参数缺失");
        }
        return wrap("操作失败", () -> service.markRead(actor, in));
    }

    private ResponseEntity<Map<String, Object>> wrap(String errorMessage, Supplier<SResult> call) {
        try {
            return NodeResponse.render(call.get());
        } catch (Exception e) {
            log.error("dm 操作错误：{}", errorMessage, e);
            return NodeResponse.fail(500, errorMessage);
        }
    }
}
