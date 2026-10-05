package com.jscreator.auth.controller;

import com.jscreator.auth.service.UserManageService;
import com.jscreator.common.api.Resp;
import com.jscreator.common.web.CurrentUser;
import com.jscreator.common.web.RequireAdmin;
import com.jscreator.common.web.RequireLogin;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /userInfo、/resetPassword、/user-manage/*，逐字段对齐 Express 版
 * modules/user/user.routes.ts + user.controller.ts：
 *
 * <ul>
 *   <li>{@code PUT /userInfo}、{@code GET /user-manage/detail/:id}：登录即可（detail 不校验归属，照抄原版）</li>
 *   <li>{@code /user-manage/list|add|update|reset-password|delete|delete-batch}：仅管理员</li>
 *   <li>400 校验文案、404「用户不存在」、403 文案、成功信封（无 data 就不加 data 键）全部照抄</li>
 * </ul>
 *
 * <p>数据库/运行时异常不在这里吞：GlobalExceptionHandler 输出 HTTP 500 + 服务器内部错误，与原版一致。
 */
@RestController
public class UserManageController {

    /** PUT /userInfo 允许写的字段（原版 updateSelf 的 pickFields 列表）。 */
    private static final String[] SELF_FIELDS = {
            "username", "email", "bio", "vip", "checkinDay", "name", "area", "avatar",
            "socials", "featured_articles"};

    /** PUT /user-manage/update 允许写的字段：管理员多一个 role_id，少 socials / featured_articles。 */
    private static final String[] ADMIN_FIELDS = {
            "username", "email", "role_id", "bio", "vip", "checkinDay", "name", "area", "avatar"};

    /** JS parseInt 的前缀匹配：跳过前导空白、可选符号、连续数字，后面多余的字符忽略。 */
    private static final Pattern JS_INT_PREFIX = Pattern.compile("^\\s*([+-]?\\d+)");

    private final UserManageService userManageService;

    public UserManageController(UserManageService userManageService) {
        this.userManageService = userManageService;
    }

    // ================= 个人 =================

    /** PUT /userInfo：本人或管理员，写入白名单字段；无字段可写也返回 200（原版行为）。 */
    @RequireLogin
    @PutMapping("/userInfo")
    public ResponseEntity<Map<String, Object>> updateSelf(@RequestBody(required = false) Map<String, Object> body,
                                                          HttpServletRequest request) {
        Long actor = CurrentUser.id(request);
        if (actor == null) {
            return fail(401, "未登录或登录过期");
        }
        Map<String, Object> in = body == null ? Map.of() : body;
        Object targetId = in.get("id");
        if (falsy(targetId)) {
            return fail(400, "用户id不能为空");
        }
        if (!userManageService.canEditTarget(targetId, actor)) {
            return fail(403, "权限不足，仅可修改自己的主页设置");
        }
        userManageService.updateSelfProfile(targetId, pickFields(in, SELF_FIELDS));
        return ResponseEntity.ok(Resp.ok("更新用户信息成功"));
    }

    /** POST /userInfo/unbind-github：把本人 github_id 置 NULL（重复解绑也是 200）。 */
    @RequireLogin
    @PostMapping("/userInfo/unbind-github")
    public ResponseEntity<Map<String, Object>> unbindGithub(HttpServletRequest request) {
        Long actor = CurrentUser.id(request);
        if (actor == null) {
            return fail(401, "未登录或登录过期");
        }
        userManageService.unbindGithub(actor);
        return ResponseEntity.ok(Resp.ok("已解除 GitHub 绑定"));
    }

    /** POST /resetPassword：改自己的密码（SHA-256 hex，与 Node 版一致）。 */
    @RequireLogin
    @PostMapping("/resetPassword")
    public ResponseEntity<Map<String, Object>> resetMyPassword(@RequestBody(required = false) Map<String, Object> body,
                                                               HttpServletRequest request) {
        Long actor = CurrentUser.id(request);
        if (actor == null) {
            return fail(401, "未登录或登录过期");
        }
        Map<String, Object> in = body == null ? Map.of() : body;
        String password = jsToString(in.get("password"));
        if (password.isEmpty()) {
            return fail(400, "密码不能为空");
        }
        userManageService.resetMyPassword(actor, password);
        return ResponseEntity.ok(Resp.ok("重置用户密码成功"));
    }

    // ================= 用户管理（管理员） =================

    /** GET /user-manage/list：真分页 + 关键词（username/email/name 模糊），page/pageSize 缺省 1/10。 */
    @RequireAdmin
    @GetMapping("/user-manage/list")
    public ResponseEntity<Map<String, Object>> listUsers(
            @RequestParam(value = "page", required = false) String page,
            @RequestParam(value = "pageSize", required = false) String pageSize,
            @RequestParam(value = "page_size", required = false) String pageSizeAlias,
            @RequestParam(value = "keyword", required = false) String keyword) {

        Long parsedPage = jsParseInt(page == null ? "" : page);
        long pageNum = (parsedPage == null || parsedPage == 0L) ? 1L : parsedPage;
        Long parsedSize = jsParseInt(pageSize != null ? pageSize : (pageSizeAlias != null ? pageSizeAlias : ""));
        long sizeNum = (parsedSize == null || parsedSize == 0L) ? 10L : parsedSize;
        String kw = (keyword == null ? "" : keyword).trim();

        return ResponseEntity.ok(Resp.ok("获取用户列表成功", userManageService.list(pageNum, sizeNum, kw)));
    }

    /** GET /user-manage/detail/:id：登录即可看（原版不校验归属），查不到 404。 */
    @RequireLogin
    @GetMapping("/user-manage/detail/{id}")
    public ResponseEntity<Map<String, Object>> detailUser(@PathVariable("id") String id) {
        Map<String, Object> user = userManageService.detail(id);
        if (user == null) {
            return fail(404, "用户不存在");
        }
        return ResponseEntity.ok(Resp.ok("获取用户详情成功", user));
    }

    /** POST /user-manage/add：用户名/邮箱查重 → 400；成功返回自增 id。 */
    @RequireAdmin
    @PostMapping("/user-manage/add")
    public ResponseEntity<Map<String, Object>> addUser(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> in = body == null ? Map.of() : body;
        String username = falsy(in.get("username")) ? "" : jsToString(in.get("username"));
        String email = falsy(in.get("email")) ? "" : jsToString(in.get("email"));
        String password = falsy(in.get("password")) ? "" : jsToString(in.get("password"));
        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            return fail(400, "用户名、邮箱、密码不能为空");
        }
        Object id = userManageService.add(username, email, password, in.get("role_id"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        return ResponseEntity.ok(Resp.ok("新增用户成功", data));
    }

    /** PUT /user-manage/update：管理员更新（白名单含 role_id，不含 socials/featured_articles）。 */
    @RequireAdmin
    @PutMapping("/user-manage/update")
    public ResponseEntity<Map<String, Object>> updateByAdmin(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> in = body == null ? Map.of() : body;
        Object id = in.get("id");
        if (falsy(id)) {
            return fail(400, "用户id不能为空");
        }
        if (!userManageService.updateByAdmin(id, pickFields(in, ADMIN_FIELDS))) {
            return fail(400, "没有需要更新的字段");
        }
        return ResponseEntity.ok(Resp.ok("更新用户成功"));
    }

    /** PUT /user-manage/reset-password：管理员重置任意用户密码。 */
    @RequireAdmin
    @PutMapping("/user-manage/reset-password")
    public ResponseEntity<Map<String, Object>> resetByAdmin(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> in = body == null ? Map.of() : body;
        String id = jsToString(in.get("id"));
        String password = jsToString(in.get("password"));
        if (id.isEmpty() || password.isEmpty()) {
            return fail(400, "用户id和新密码不能为空");
        }
        userManageService.adminResetPassword(id, password);
        return ResponseEntity.ok(Resp.ok("重置密码成功"));
    }

    /** DELETE /user-manage/delete：id 走请求体（原版就是 req.body.id）。 */
    @RequireAdmin
    @DeleteMapping("/user-manage/delete")
    public ResponseEntity<Map<String, Object>> deleteUser(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> in = body == null ? Map.of() : body;
        Object id = in.get("id");
        if (falsy(id)) {
            return fail(400, "用户id不能为空");
        }
        userManageService.remove(id);
        return ResponseEntity.ok(Resp.ok("删除用户成功"));
    }

    /** POST /user-manage/delete-batch：先剔除操作者本人（全被剔除则 400），文案带实际删除行数。 */
    @RequireAdmin
    @PostMapping("/user-manage/delete-batch")
    public ResponseEntity<Map<String, Object>> deleteBatch(@RequestBody(required = false) Map<String, Object> body,
                                                           HttpServletRequest request) {
        Long me = CurrentUser.id(request);
        if (me == null) {
            return fail(401, "未登录或登录过期");
        }
        Map<String, Object> in = body == null ? Map.of() : body;
        Object ids = in.get("ids");
        if (!(ids instanceof List<?> list) || list.isEmpty()) {
            return fail(400, "请选择要删除的用户");
        }
        List<Object> filtered = new ArrayList<>(list.size());
        for (Object one : list) {
            Double asNumber = UserManageService.jsNumber(one);
            if (asNumber != null && asNumber.doubleValue() == me.doubleValue()) {
                continue;
            }
            filtered.add(one);
        }
        if (filtered.isEmpty()) {
            return fail(400, "不能删除自己");
        }
        int affected = userManageService.removeBatch(filtered);
        return ResponseEntity.ok(Resp.ok("已删除 " + affected + " 个用户"));
    }

    // ================= 小工具：对齐 JS 的取值语义 =================

    private static ResponseEntity<Map<String, Object>> fail(int code, String message) {
        return ResponseEntity.status(code).body(Resp.fail(code, message));
    }

    /** 只挑出请求体里出现过（含显式 null）的白名单字段，对应原版 pickFields 的 {@code !== undefined}。 */
    private static Map<String, Object> pickFields(Map<String, Object> body, String[] keys) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : keys) {
            if (body.containsKey(key)) {
                out.put(key, body.get(key));
            }
        }
        return out;
    }

    /** JS 的 falsy：undefined / null / false / 0 / '' / NaN 都为假。 */
    private static boolean falsy(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Boolean bool) {
            return !bool;
        }
        if (value instanceof Number number) {
            double d = number.doubleValue();
            return d == 0 || Double.isNaN(d);
        }
        return String.valueOf(value).isEmpty();
    }

    /** JS 的 String(v)：null 得到 ''，整数不带小数点（配合 ?? 使用的语义）。 */
    private static String jsToString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean bool) {
            return String.valueOf(bool);
        }
        if (value instanceof Double d) {
            if (!d.isInfinite() && !d.isNaN() && d == Math.floor(d)) {
                return String.valueOf(d.longValue());
            }
            return String.valueOf(d);
        }
        return String.valueOf(value);
    }

    /** JS 的 parseInt(v, 10)：解析不出来返回 null（对应 NaN，调用方按 falsy 处理）。 */
    private static Long jsParseInt(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = JS_INT_PREFIX.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
