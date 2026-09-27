"""user 模块（/userInfo、/resetPassword、/user-manage/*）的对照用例。

覆盖：正常路径 + 各 400/401/403/404/500 分支 + 写后读回。
写用例都自带还原或本身就是幂等的（第二次跑同样全绿）：
  * 写入靶子用 dump 里的种子账号 testuser_0001（id 1778237621054），写完全部还原；
  * 密码轮换用 editor（id 2）：改成新密码 → 用新密码登录 → 旧密码 401 → 还原；
  * 新增用户用带 STAMP 的名字，重复跑不会撞名（行会留在库里，报告里说明）。
"""

from _spec import STAMP, case

FIXTURE_ID = 1778237621054  # testuser_0001（种子用户，写用例的靶子，写完还原）
EDITOR_ID = 2               # editor / role_id=3：只有本模块用它做密码轮换，中途中断也不影响别人的登录
ADMIN_ID = 1

TMP_USER = f"m1b-tmp-{STAMP}"
TMP_MAIL = f"m1b-tmp-{STAMP}@example.com"
DUP_MAIL = f"m1b-dup-{STAMP}@example.com"
FK_USER = f"m1b-fk-{STAMP}"
NEW_PASS = f"m1b-pass-{STAMP}"

CASES = [
    # ---------- GET /user-manage/list ----------
    case("list 无 token → 401", "GET", "/user-manage/list"),
    case("list 非管理员 → 403", "GET", "/user-manage/list", auth="user"),
    case("list 关键词无匹配 → 空列表", "GET",
         f"/user-manage/list?keyword=zzz-nope-{STAMP}", auth="admin"),
    case("list 关键词命中种子用户（含 NULL 列）", "GET",
         "/user-manage/list?keyword=testuser_0001@example.com&page=1&pageSize=5", auth="admin"),
    case("list 非法分页参数回落默认", "GET",
         "/user-manage/list?keyword=testuser_0001@example.com&page=abc&pageSize=abc", auth="admin"),
    case("list page=0 回落第 1 页", "GET",
         "/user-manage/list?keyword=testuser_0001@example.com&page=0", auth="admin"),

    # ---------- GET /user-manage/detail/:id ----------
    case("detail 无 token → 401", "GET", "/user-manage/detail/3"),
    case("detail 不存在 → 404", "GET", "/user-manage/detail/999999999", auth="user"),
    case("detail 非数字 id → 404", "GET", "/user-manage/detail/abc", auth="user"),
    case("detail 种子用户", "GET", f"/user-manage/detail/{FIXTURE_ID}", auth="user"),

    # ---------- PUT /userInfo ----------
    case("updateSelf 无 token → 401", "PUT", "/userInfo", {"id": 3}),
    case("updateSelf 缺 id → 400", "PUT", "/userInfo", {}, auth="user"),
    case("updateSelf id=0 → 400", "PUT", "/userInfo", {"id": 0}, auth="user"),
    case("updateSelf 无白名单字段 → 200（原版不看返回值）", "PUT", "/userInfo",
         {"id": 3, "unknown_field": "x"}, auth="user"),
    case("updateSelf 改他人 → 403", "PUT", "/userInfo", {"id": ADMIN_ID, "bio": "x"}, auth="user"),
    case("updateSelf 邮箱撞唯一键 → 500", "PUT", "/userInfo",
         {"id": FIXTURE_ID, "email": "admin@test.com"}, auth="admin"),
    case("updateSelf 不存在的 id → 200", "PUT", "/userInfo",
         {"id": 999999999, "name": "m1b-ghost"}, auth="admin"),
    case("updateSelf 字符串 id 写 name", "PUT", "/userInfo",
         {"id": str(FIXTURE_ID), "name": f"m1b-self-{STAMP}"}, auth="admin"),
    case("detail 读回字符串 id 写入的 name", "GET", f"/user-manage/detail/{FIXTURE_ID}", auth="user"),
    case("updateSelf 写 socials（非数组落成 []）与 featured_articles", "PUT", "/userInfo",
         {"id": FIXTURE_ID, "socials": {"a": 1}, "featured_articles": [{"id": 1, "title": f"m1b-{STAMP}"}]},
         auth="admin"),
    case("detail 读回 socials/featured_articles", "GET", f"/user-manage/detail/{FIXTURE_ID}", auth="user"),
    case("updateSelf 还原 name/socials/featured_articles", "PUT", "/userInfo",
         {"id": FIXTURE_ID, "name": None, "socials": [], "featured_articles": []}, auth="admin"),
    case("detail 读回还原后的行", "GET", f"/user-manage/detail/{FIXTURE_ID}", auth="user"),

    # ---------- POST /userInfo/unbind-github ----------
    case("unbindGithub 无 token → 401", "POST", "/userInfo/unbind-github"),
    case("unbindGithub 本人", "POST", "/userInfo/unbind-github", auth="user"),
    case("unbindGithub 管理员", "POST", "/userInfo/unbind-github", auth="admin"),

    # ---------- POST /resetPassword ----------
    case("resetPassword 无 token → 401", "POST", "/resetPassword", {"password": "123456"}),
    case("resetPassword 缺密码 → 400", "POST", "/resetPassword", {}, auth="user"),
    case("resetPassword 空密码 → 400", "POST", "/resetPassword", {"password": ""}, auth="user"),
    case("resetPassword 本人重置为原密码（幂等）", "POST", "/resetPassword", {"password": "123456"}, auth="user"),

    # ---------- POST /user-manage/add ----------
    case("add 无 token → 401", "POST", "/user-manage/add",
         {"username": TMP_USER, "email": TMP_MAIL, "password": "123456"}),
    case("add 非管理员 → 403", "POST", "/user-manage/add",
         {"username": TMP_USER, "email": TMP_MAIL, "password": "123456"}, auth="user"),
    case("add 缺参 → 400", "POST", "/user-manage/add", {}, auth="admin"),
    case("add 邮箱已存在 → 400", "POST", "/user-manage/add",
         {"username": FK_USER + "e", "email": "admin@test.com", "password": "123456"}, auth="admin"),
    case("add 用户名已存在 → 400", "POST", "/user-manage/add",
         {"username": "admin", "email": DUP_MAIL, "password": "123456"}, auth="admin"),
    case("add role_id 不存在 → 500（外键）", "POST", "/user-manage/add",
         {"username": FK_USER, "email": FK_USER + "@example.com", "password": "123456", "role_id": 999999},
         auth="admin"),
    case("add 成功", "POST", "/user-manage/add",
         {"username": TMP_USER, "email": TMP_MAIL, "password": "123456"}, auth="admin",
         ignore=("body.data.id",)),
    case("add 重复用户名 → 400", "POST", "/user-manage/add",
         {"username": TMP_USER, "email": DUP_MAIL, "password": "123456"}, auth="admin"),
    case("add 重复邮箱 → 400", "POST", "/user-manage/add",
         {"username": FK_USER + "y", "email": TMP_MAIL, "password": "123456"}, auth="admin"),

    # ---------- 写后读回：新增用户能登录（校验 SHA-256 哈希互通） ----------
    case("login 新用户（密码哈希互通）", "POST", "/sys/login",
         {"username": TMP_USER, "password": "123456"}, ignore=("body.user_info.id",)),
    case("login 新用户 错密码 → 401", "POST", "/sys/login",
         {"username": TMP_USER, "password": "m1b-wrong"}),

    # ---------- 分页读到刚新增的用户 ----------
    case("list 关键词命中新用户", "GET",
         f"/user-manage/list?keyword={TMP_USER}", auth="admin", ignore=("body.data.list[0].id",)),
    case("list 关键词 + pageSize=1", "GET",
         f"/user-manage/list?keyword={TMP_USER}&page=1&pageSize=1", auth="admin",
         ignore=("body.data.list[0].id",)),
    case("list 关键词 + page_size 别名", "GET",
         f"/user-manage/list?keyword={TMP_USER}&page=1&page_size=3", auth="admin",
         ignore=("body.data.list[0].id",)),
    case("list 关键词 page=2 → 空页", "GET",
         f"/user-manage/list?keyword={TMP_USER}&page=2&pageSize=1", auth="admin"),

    # ---------- PUT /user-manage/update ----------
    case("update 无 token → 401", "PUT", "/user-manage/update", {"id": FIXTURE_ID, "name": "x"}),
    case("update 非管理员 → 403", "PUT", "/user-manage/update", {"id": FIXTURE_ID, "name": "x"}, auth="user"),
    case("update 缺 id → 400", "PUT", "/user-manage/update", {"name": "x"}, auth="admin"),
    case("update 无字段 → 400", "PUT", "/user-manage/update", {"id": FIXTURE_ID}, auth="admin"),
    case("update 未知字段 → 400", "PUT", "/user-manage/update", {"id": FIXTURE_ID, "whatever": 1}, auth="admin"),
    case("update socials 不在管理员白名单 → 400", "PUT", "/user-manage/update",
         {"id": FIXTURE_ID, "socials": [{"a": 1}]}, auth="admin"),
    case("update 写 name+checkinDay", "PUT", "/user-manage/update",
         {"id": FIXTURE_ID, "name": f"m1b-admin-{STAMP}", "checkinDay": 7}, auth="admin"),
    case("detail 读回 update 写入的 name/checkinDay", "GET", f"/user-manage/detail/{FIXTURE_ID}", auth="user"),
    case("update 还原 name/checkinDay", "PUT", "/user-manage/update",
         {"id": FIXTURE_ID, "name": None, "checkinDay": 0}, auth="admin"),
    case("detail 读回还原后的行", "GET", f"/user-manage/detail/{FIXTURE_ID}", auth="user"),
    case("update 用户名撞唯一键 → 500", "PUT", "/user-manage/update",
         {"id": FIXTURE_ID, "username": "admin"}, auth="admin"),

    # ---------- PUT /user-manage/reset-password ----------
    case("adminReset 无 token → 401", "PUT", "/user-manage/reset-password",
         {"id": EDITOR_ID, "password": NEW_PASS}),
    case("adminReset 非管理员 → 403", "PUT", "/user-manage/reset-password",
         {"id": EDITOR_ID, "password": NEW_PASS}, auth="user"),
    case("adminReset 缺密码 → 400", "PUT", "/user-manage/reset-password", {"id": EDITOR_ID}, auth="admin"),
    case("adminReset 缺 id → 400", "PUT", "/user-manage/reset-password", {"password": NEW_PASS}, auth="admin"),
    case("adminReset 改 editor 密码", "PUT", "/user-manage/reset-password",
         {"id": EDITOR_ID, "password": NEW_PASS}, auth="admin"),
    case("login editor 新密码（写后读回）", "POST", "/sys/login",
         {"username": "editor", "password": NEW_PASS}, ignore=("body.user_info.id",)),
    case("login editor 旧密码 → 401", "POST", "/sys/login",
         {"username": "editor", "password": "123456"}),
    case("adminReset 还原 editor 密码", "PUT", "/user-manage/reset-password",
         {"id": EDITOR_ID, "password": "123456"}, auth="admin"),
    case("login editor 还原后", "POST", "/sys/login",
         {"username": "editor", "password": "123456"}, ignore=("body.user_info.id",)),

    # ---------- DELETE /user-manage/delete（id 走请求体） ----------
    case("delete 无 token → 401", "DELETE", "/user-manage/delete", {"id": 999999999}),
    case("delete 非管理员 → 403", "DELETE", "/user-manage/delete", {"id": 999999999}, auth="user"),
    case("delete 缺 id → 400", "DELETE", "/user-manage/delete", {}, auth="admin"),
    case("delete 不存在的 id → 200", "DELETE", "/user-manage/delete", {"id": 999999999}, auth="admin"),

    # ---------- POST /user-manage/delete-batch ----------
    case("deleteBatch 无 token → 401", "POST", "/user-manage/delete-batch", {"ids": [999999999]}),
    case("deleteBatch 非管理员 → 403", "POST", "/user-manage/delete-batch", {"ids": [999999999]}, auth="user"),
    case("deleteBatch 缺 ids → 400", "POST", "/user-manage/delete-batch", {}, auth="admin"),
    case("deleteBatch ids 空数组 → 400", "POST", "/user-manage/delete-batch", {"ids": []}, auth="admin"),
    case("deleteBatch ids 非数组 → 400", "POST", "/user-manage/delete-batch", {"ids": "1"}, auth="admin"),
    case("deleteBatch 只删自己 → 400", "POST", "/user-manage/delete-batch", {"ids": [ADMIN_ID]}, auth="admin"),
    case("deleteBatch 含自己+不存在（剔除自己）", "POST", "/user-manage/delete-batch",
         {"ids": [ADMIN_ID, 999999999]}, auth="admin"),
    case("deleteBatch 都不存在 → 已删除 0 个用户", "POST", "/user-manage/delete-batch",
         {"ids": [999999998, 999999999]}, auth="admin"),
]
