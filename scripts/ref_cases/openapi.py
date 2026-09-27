"""/api-keys* 与 /api/v1/* 的对照用例（openapi 模块 8 个接口）。

API Key 由 harness 自己建（auth="apikey:read" / "apikey:write" 时会用管理员身份建一把并自动清理），
所以这里只需要覆盖：key 管理端点的正常/400/401/403 分支、开放接口的鉴权与 scope 分支、
以及「发布文章 → 按关键词读回」。

导入时会把两侧库里上一轮对照留下的 api_key 测试数据清掉（refdiff / m1c-openapi-* / 未命名），
这样 /api-keys 列表的 id 与顺序才是确定的，连续跑两次结果一致。库与口令取自
jcreator-java/.env + 本机 mysql 客户端（不打印任何密钥）；连不上库时只打一行警告。
"""

import os
import re
import subprocess

from _spec import STAMP, case

# 造数据的目标库：默认本地联调的对照库 + 本机实例库；
# 换环境（例如对着线上/docker 那套跑）时用环境变量覆盖：
#   REF_SEED_DBS=fastweb_deployref,fastweb_test python3 scripts/ref_diff.py ...
SEED_DBS = tuple(d.strip() for d in os.environ.get(
    "REF_SEED_DBS", "fastweb_m1ref,fastweb_m1c").split(",") if d.strip())


def _env(name, default=None):
    path = "/root/jscreator-java/.env"
    try:
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                key, value = line.split("=", 1)
                if key.strip() == name:
                    # .env 里有的行尾带注释（MYSQL_PORT=3308    # 说明），去掉「空白 + #」之后的部分
                    value = re.sub(r"\s+#.*$", "", value).strip().strip('"').strip("'")
                    return value
    except OSError:
        pass
    return default


def list_case(name, method, path, auth="admin"):
    """列表类用例：harness 新版支持 key=(路径, 主键) 按主键配对比较，
    这样共享对照库里被别人写进去的多余行不会误报；旧版 harness 就直接普通比较。"""
    if "key" in getattr(case, "__code__").co_varnames:
        return case(name, method, path, auth=auth, key=("body.data.list", "id"))
    return case(name, method, path, auth=auth)


def _cleanup():
    """清掉对照用例自己造的 api_key（harness 每次运行都会建 name=refdiff 的 key）。"""
    password = _env("DB_PASSWORD")
    port = _env("MYSQL_PORT", "3308")
    if not password:
        print("⚠ openapi 用例：读不到 DB_PASSWORD，跳过 api_key 清理，列表类用例可能失败")
        return
    # 顺带把自增计数拨到同一个值：两个库的历史不同（ref 里可能被之前的运行推进过），
    # 不拨平的话 /api-keys 列表里的 id 会比不出来。
    sql = ("DELETE FROM api_key WHERE name IN ('refdiff', '未命名') OR name LIKE 'm1c-openapi-%';"
           "ALTER TABLE api_key AUTO_INCREMENT = 1000;"
           "DELETE FROM article WHERE title LIKE 'm1c-openapi-%';")
    for db in SEED_DBS:
        try:
            subprocess.run(
                ["mysql", "-h127.0.0.1", f"-P{port}", "-uroot", f"-p{password}",
                 "--default-character-set=utf8mb4", db, "-e", sql],
                check=True, capture_output=True, timeout=20,
            )
        except Exception as exc:  # noqa: BLE001
            print(f"⚠ openapi 用例：清理 {db} 的 api_key 失败（{exc.__class__.__name__}）")


if os.environ.get("REFDIFF_NO_SEED") != "1":
    _cleanup()

CASES = [
    # ---------- /api-keys（登录用户） ----------
    list_case("api-keys 列表（管理员，初始为空）", "GET", "/api-keys"),
    case("api-keys 创建 read", "POST", "/api-keys",
         {"name": f"m1c-openapi-{STAMP}", "scopes": "read"}, auth="admin",
         ignore=("body.data.prefix",)),
    list_case("api-keys 列表 读回新建的 key", "GET", "/api-keys"),
    case("api-keys 创建 write（管理员可）", "POST", "/api-keys",
         {"name": f"m1c-openapi-w-{STAMP}", "scopes": "write"}, auth="admin",
         ignore=("body.data.prefix",)),
    case("api-keys 创建 write（普通用户 → 403）", "POST", "/api-keys",
         {"name": f"m1c-openapi-u-{STAMP}", "scopes": "write"}, auth="user"),
    case("api-keys 创建 无名 → 未命名", "POST", "/api-keys", {}, auth="admin",
         ignore=("body.data.prefix",)),
    list_case("api-keys 列表（user1 为空）", "GET", "/api-keys", auth="user"),
    case("api-keys 改状态（不存在的 id）", "PUT", "/api-keys/999999/status", {"status": 0}, auth="admin"),
    case("api-keys 删除（不存在的 id）", "DELETE", "/api-keys/999999", auth="admin"),
    case("api-keys 列表 无 token → 401", "GET", "/api-keys"),
    case("api-keys 创建 无 token → 401", "POST", "/api-keys", {"name": "x"}),

    # ---------- /api/v1/* 鉴权 ----------
    case("v1 文章列表 无 key → 401", "GET", "/api/v1/articles"),
    case("v1 文章列表 拿 JWT 当 key → 401", "GET", "/api/v1/articles", auth="user"),

    # ---------- /api/v1/articles（read） ----------
    case("v1 文章列表 默认分页", "GET", "/api/v1/articles", auth="apikey:read"),
    case("v1 文章列表 指定分页", "GET", "/api/v1/articles?page=2&pageSize=3", auth="apikey:read"),
    case("v1 文章列表 关键词", "GET", "/api/v1/articles?keyword=LangChain", auth="apikey:read"),
    case("v1 文章列表 分类", "GET", "/api/v1/articles?category_id=1", auth="apikey:read"),
    case("v1 文章列表 分页参数非法", "GET", "/api/v1/articles?page=abc&pageSize=0", auth="apikey:read"),
    case("v1 文章详情 已发布（无 AI 摘要）", "GET", "/api/v1/articles/7", auth="apikey:read"),
    case("v1 文章详情 已发布（带 AI 摘要 JSON）", "GET", "/api/v1/articles/15", auth="apikey:read"),
    case("v1 文章详情 未发布 → 404", "GET", "/api/v1/articles/85", auth="apikey:read"),
    case("v1 文章详情 不存在 → 404", "GET", "/api/v1/articles/999999", auth="apikey:read"),
    case("v1 文章详情 id=0 → 400", "GET", "/api/v1/articles/0", auth="apikey:read"),
    case("v1 文章详情 id 非数字 → 400", "GET", "/api/v1/articles/abc", auth="apikey:read"),

    # ---------- /api/v1/users/:username（read） ----------
    case("v1 用户公开信息（socials 非空）", "GET", "/api/v1/users/admin", auth="apikey:read"),
    case("v1 用户公开信息（socials 为空）", "GET", "/api/v1/users/user1", auth="apikey:read"),
    case("v1 用户公开信息 不存在 → 404", "GET", "/api/v1/users/m1c-no-such-user", auth="apikey:read"),

    # ---------- /api/v1/articles（write） ----------
    case("v1 发布文章 read key → 403（无 write 权限）", "POST", "/api/v1/articles",
         {"title": "x", "content": "y"}, auth="apikey:read"),
    case("v1 发布文章 缺标题内容 → 400", "POST", "/api/v1/articles", {}, auth="apikey:write"),
    case("v1 发布文章 只给标题 → 400", "POST", "/api/v1/articles", {"title": "x"}, auth="apikey:write"),
    case("v1 发布文章 无 key → 401", "POST", "/api/v1/articles", {"title": "x", "content": "y"}),
    case("v1 发布文章", "POST", "/api/v1/articles",
         {"title": f"m1c-openapi-{STAMP}", "content": f"refdiff 对照内容 {STAMP}"}, auth="apikey:write",
         ignore=("body.data.article_id",)),
    case("v1 发布后按关键词读回", "GET", f"/api/v1/articles?keyword=m1c-openapi-{STAMP}", auth="apikey:read",
         ignore=("body.data.list[0].article_id",)),
]
