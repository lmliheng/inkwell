package com.jscreator.auth.controller;

import com.jscreator.auth.service.RbacService;
import com.jscreator.common.api.Resp;
import com.jscreator.common.web.RequireAdmin;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /role/*、/permission/*，逐字段对齐 Express 版 modules/rbac/rbac.controller：
 * 成功文案、400 校验文案、返回结构（role/permission 列表是 data.list，角色权限是 data.permission_ids）
 * 全部照抄；数据库报错交给 GlobalExceptionHandler → HTTP 500 + 服务器内部错误。
 *
 * <p>鉴权：role/list 与 permission/list 在原版里是公开的（等价保留），其余是 [verifyToken, adminOnly]。
 */
@RestController
public class RbacController {

    private final RbacService rbacService;

    public RbacController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    // ================= role =================

    @GetMapping("/role/list")
    public ResponseEntity<Map<String, Object>> roleList() {
        return ok("获取所有角色成功", dataList(rbacService.roleGetAll()));
    }

    @RequireAdmin
    @GetMapping("/role/permission/{id}")
    public ResponseEntity<Map<String, Object>> rolePermission(@PathVariable("id") String id) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("permission_ids", rbacService.rolePermissionIds(id));
        return ok("获取角色权限成功", data);
    }

    @RequireAdmin
    @PostMapping("/role/setPermission")
    public ResponseEntity<Map<String, Object>> roleSetPermission(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> in = body == null ? Map.of() : body;
        Object roleId = in.get("role_id");
        Object permissionIdList = in.get("permission_id_list");
        if (falsy(roleId) || !(permissionIdList instanceof List<?> list)) {
            return fail(400, "role_id 与 permission_id_list(数组) 不能为空");
        }
        rbacService.setRolePermission(roleId, list);
        return ResponseEntity.ok(Resp.ok("分配角色权限成功"));
    }

    @RequireAdmin
    @PostMapping("/role/add")
    public ResponseEntity<Map<String, Object>> roleAdd(@RequestBody(required = false) Map<String, Object> body) {
        String roleName = jsToString(raw(body, "role_name"));
        if (roleName.isEmpty()) {
            return fail(400, "角色名不能为空");
        }
        rbacService.roleAdd(roleName);
        return ResponseEntity.ok(Resp.ok("增加角色成功"));
    }

    @RequireAdmin
    @PutMapping("/role/update")
    public ResponseEntity<Map<String, Object>> roleUpdate(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> in = body == null ? Map.of() : body;
        if (falsy(in.get("role_id")) || falsy(in.get("role_name"))) {
            return fail(400, "角色id与角色名不能为空");
        }
        rbacService.roleUpdateName(in.get("role_id"), jsToString(in.get("role_name")));
        return ResponseEntity.ok(Resp.ok("修改角色名成功"));
    }

    @RequireAdmin
    @DeleteMapping("/role/delete")
    public ResponseEntity<Map<String, Object>> roleDelete(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> in = body == null ? Map.of() : body;
        if (falsy(in.get("role_id"))) {
            return fail(400, "角色id不能为空");
        }
        rbacService.roleDelete(in.get("role_id"));
        return ResponseEntity.ok(Resp.ok("删除角色成功"));
    }

    // ================= permission =================

    @GetMapping("/permission/list")
    public ResponseEntity<Map<String, Object>> permissionList() {
        return ok("获取所有权限成功", dataList(rbacService.permissionGetAll()));
    }

    @RequireAdmin
    @PutMapping("/permission/update")
    public ResponseEntity<Map<String, Object>> permissionUpdate(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> in = body == null ? Map.of() : body;
        if (falsy(in.get("permission_id")) || falsy(in.get("permission_name"))) {
            return fail(400, "权限id与权限名不能为空");
        }
        rbacService.permissionUpdate(in.get("permission_id"), jsToString(in.get("permission_name")),
                in.get("permission_description"));
        return ResponseEntity.ok(Resp.ok("修改权限成功"));
    }

    // ================= 小工具：对齐 JS 的取值语义 =================

    /** { list: [...] } */
    private static Map<String, Object> dataList(Object list) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        return data;
    }

    private static ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        return ResponseEntity.ok(Resp.ok(message, data));
    }

    private static ResponseEntity<Map<String, Object>> fail(int code, String message) {
        return ResponseEntity.status(code).body(Resp.fail(code, message));
    }

    private static Object raw(Map<String, Object> body, String key) {
        return body == null ? null : body.get(key);
    }

    /** JS 的 falsy：undefined / null / false / 0 / '' / NaN 都为假。 */
    static boolean falsy(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof Boolean b) {
            return !b;
        }
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return d == 0 || Double.isNaN(d);
        }
        return String.valueOf(v).isEmpty();
    }

    /** JS 的 String(v)：整数不带小数点，null/undefined → ''（配合 ?? 使用的语义）。 */
    static String jsToString(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof Boolean b) {
            return String.valueOf(b);
        }
        if (v instanceof Double d) {
            if (!d.isInfinite() && !d.isNaN() && d == Math.floor(d)) {
                return String.valueOf(d.longValue());
            }
            return String.valueOf(d);
        }
        return String.valueOf(v);
    }
}
