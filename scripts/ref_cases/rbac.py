"""rbac 模块（/role/*、/permission/*）的对照用例。"""

from _spec import STAMP, case

# role 3（编辑）在 dump 里的原始权限集合：下面的写用例会把它改成 [1,2]，
# 跑完必须还原，否则对照库里的角色权限被永久改小（两边都改也照样「一致」，但库脏了）。
ROLE3_PERMISSIONS = [2, 5, 6, 7, 8, 9, 10, 11, 12]

CASES = [
    case("role_list 公开", "GET", "/role/list", key=("body.data.list", "role_name")),
    case("permission_list 公开", "GET", "/permission/list", key=("body.data.list", "permission_name")),
    case("role_permission admin", "GET", "/role/permission/1", auth="admin"),
    case("role_permission 无 token → 401", "GET", "/role/permission/1"),
    case("role_permission 非管理员 → 403", "GET", "/role/permission/1", auth="user"),
    case("role_add 缺名 → 400", "POST", "/role/add", {}, auth="admin"),
    case("role_add 新增", "POST", "/role/add", {"role_name": f"m1-role-{STAMP}"}, auth="admin"),
    case("role_add 重名 → 500", "POST", "/role/add", {"role_name": f"m1-role-{STAMP}"}, auth="admin"),
    case("role_update 缺参 → 400", "PUT", "/role/update", {"role_id": 1}, auth="admin"),
    case("role_setPermission 缺参 → 400", "POST", "/role/setPermission", {"role_id": 3}, auth="admin"),
    case("role_setPermission 写读回", "POST", "/role/setPermission",
         {"role_id": 3, "permission_id_list": [1, 2]}, auth="admin"),
    case("role_permission 读回（3 的角色权限应为 1,2）", "GET", "/role/permission/3", auth="admin"),
    # ---------- 善后：还原 role 3 的权限 ----------
    case("role_setPermission 善后：还原 role 3 权限", "POST", "/role/setPermission",
         {"role_id": 3, "permission_id_list": ROLE3_PERMISSIONS}, auth="admin"),
    case("role_permission 读回（已还原为 dump 原值）", "GET", "/role/permission/3", auth="admin"),
    case("role_delete 缺参 → 400", "DELETE", "/role/delete", {}, auth="admin"),
    case("permission_update 缺参 → 400", "PUT", "/permission/update", {"permission_id": 1}, auth="admin"),
]
