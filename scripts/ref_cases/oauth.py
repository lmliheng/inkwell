"""/oauth/** 的对照用例（管理端 5 条 + authorize / token）。

为什么需要 fixture：
    原版的 oauth_client.client_id 是 `'oa_' + randomBytes(16).hex`，每次 POST /oauth/admin/clients 都不一样，
    而用例是静态数据 —— 没法「写一个客户端、后面再用它」。所以这里在导入时往**两侧的库**里
    upsert 一组固定 id 的 oauth_client / oauth_code（两侧同库同值），用来覆盖 authorize 与 token
    的正常分支、白名单分支、PKCE 分支、client_credentials 分支；导入时同时清掉上一轮用例
    自己造出来的行，保证「连续跑两次结果一致」。

    库与口令取自 jcreator-java/.env + 本机 mysql 客户端（不打印任何密钥）：
      - 原版对照库：fastweb_m1ref（ref_diff 的默认 ref 侧）
      - 本机 Java 实例库：fastweb_m1c
    连不上库时只打一行警告，用例会照常跑（只是需要 fixture 的那几条会失败）。

fixture（两侧同值）：
    oauth_client 900001 client_id=oa_m1fixtureauth0000000000000000 status=1  redirect=http://127.0.0.1:7001/role/list
    oauth_client 900002 client_id=oa_m1fixturedis00000000000000000 status=1  同上（给「禁用后无效」用）
    oauth_code  aaaaaaaaaaaaaaaaaaaaaaaa  未用、2030 到期、无 PKCE
    oauth_code  bbbbbbbbbbbbbbbbbbbbbbbb  未用、2030 到期、challenge=S256(m1c-pkce-verifier)
    oauth_code  cccccccccccccccccccccccc  未用、2020 已过期
"""

import base64
import hashlib
import os
import re
import subprocess

from _spec import STAMP, case

FIXTURE_CLIENT_ID = "oa_m1fixtureauth0000000000000000"
FIXTURE_CLIENT_ID_DISABLED = "oa_m1fixturedis00000000000000000"
FIXTURE_SECRET = "0123456789abcdef" * 3          # 48 位，与 Node randomBytes(24).hex 同长度
WRONG_SECRET_SAME_LEN = "0123456789abcdeX" * 3   # 48 位，长度一致 → 401 客户端密钥错误
WRONG_SECRET_SHORT = "short"                     # 长度不一致 → Node timingSafeEqual 抛错 → 500
REDIRECT_URI = "http://127.0.0.1:7001/role/list"  # 必须与 fixture 的 redirect_uris 完全一致

CODE_PLAIN = "aaaaaaaaaaaaaaaaaaaaaaaa"
CODE_PKCE = "bbbbbbbbbbbbbbbbbbbbbbbb"        # PKCE 成功用（失败用例会先把码消费掉，得用两枚）
CODE_PKCE_BAD = "dddddddddddddddddddddddd"    # PKCE 校验失败用
CODE_EXPIRED = "cccccccccccccccccccccccc"
PKCE_VERIFIER = "m1c-pkce-verifier"
PKCE_CHALLENGE = base64.urlsafe_b64encode(hashlib.sha256(PKCE_VERIFIER.encode()).digest()).rstrip(b"=").decode()

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


def _seed_sql():
    clients = (
        # 先删 fixture 行本身 + 用例自己造出来的行（可重复执行），再重新插
        "DELETE FROM oauth_client WHERE id NOT IN (900001, 900002);"
        "DELETE FROM oauth_client WHERE id IN (900001, 900002);"
        f"INSERT INTO oauth_client (id, client_id, client_secret, name, description, redirect_uris, scopes,"
        f" grant_types, logo, status, created_at) VALUES"
        f" (900001, '{FIXTURE_CLIENT_ID}', '{FIXTURE_SECRET}', 'm1c-oauth-fixture', 'refdiff fixture',"
        f" '{REDIRECT_URI}', 'read', 'authorization_code,client_credentials', '', 1, '2020-01-01 00:00:00'),"
        f" (900002, '{FIXTURE_CLIENT_ID_DISABLED}', '{FIXTURE_SECRET}', 'm1c-oauth-status', 'refdiff fixture',"
        f" '{REDIRECT_URI}', 'read', 'authorization_code,client_credentials', '', 1, '2020-01-02 00:00:00');"
    )
    codes = (
        "DELETE FROM oauth_code;"
        "INSERT INTO oauth_code (code, client_id, user_id, scope, code_challenge, redirect_uri, expires_at, used)"
        " VALUES"
        f" ('{CODE_PLAIN}', '{FIXTURE_CLIENT_ID}', 1, 'read', NULL, '{REDIRECT_URI}', '2030-01-01 00:00:00', 0),"
        f" ('{CODE_PKCE}', '{FIXTURE_CLIENT_ID}', 1, 'read', '{PKCE_CHALLENGE}', '{REDIRECT_URI}',"
        " '2030-01-01 00:00:00', 0),"
        f" ('{CODE_PKCE_BAD}', '{FIXTURE_CLIENT_ID}', 1, 'read', '{PKCE_CHALLENGE}', '{REDIRECT_URI}',"
        " '2030-01-01 00:00:00', 0),"
        f" ('{CODE_EXPIRED}', '{FIXTURE_CLIENT_ID}', 1, 'read', NULL, '{REDIRECT_URI}', '2020-01-01 00:00:00', 0);"
    )
    return clients + codes


def list_case(name, method, path, auth="admin"):
    """列表类用例：harness 新版支持 key=(路径, 主键) 按主键配对比较，
    这样共享对照库里被别人写进去的多余行不会误报；旧版 harness 就直接普通比较。"""
    if "key" in getattr(case, "__code__").co_varnames:
        return case(name, method, path, auth=auth, key=("body.data.list", "id"))
    return case(name, method, path, auth=auth)


def _seed():
    """把 fixture 写进 ref / java 两侧的库（可重复执行）。"""
    password = _env("DB_PASSWORD")
    port = _env("MYSQL_PORT", "3308")
    if not password:
        print("⚠ oauth 用例：读不到 DB_PASSWORD，跳过 fixture，授权码相关用例可能失败")
        return
    sql = _seed_sql()
    for db in SEED_DBS:
        try:
            subprocess.run(
                ["mysql", "-h127.0.0.1", f"-P{port}", "-uroot", f"-p{password}", db, "-e", sql],
                check=True, capture_output=True, timeout=20,
            )
        except Exception as exc:  # noqa: BLE001
            print(f"⚠ oauth 用例：往 {db} 写 fixture 失败（{exc.__class__.__name__}），相关用例可能失败")


if os.environ.get("REFDIFF_NO_SEED") != "1":
    _seed()

CASES = [
    # ---------- 管理端：列表 / 更新（写后读回） ----------
    list_case("oauth clients 列表（fixture）", "GET", "/oauth/admin/clients"),
    case("oauth client 更新（改名）", "PUT", "/oauth/admin/clients/900001",
         {"name": f"m1c-oauth-fixture-{STAMP}", "description": "renamed", "redirect_uris": REDIRECT_URI,
          "scopes": "read", "grant_types": "authorization_code,client_credentials"}, auth="admin"),
    list_case("oauth client 列表 读回改名结果", "GET", "/oauth/admin/clients"),

    # ---------- 管理端：创建（含 400 / 500 分支） ----------
    case("oauth client 创建", "POST", "/oauth/admin/clients",
         {"name": f"m1c-oauth-new-{STAMP}", "description": "d", "redirect_uris": [REDIRECT_URI],
          "scopes": "read", "grant_types": "authorization_code,client_credentials", "logo": "l"},
         auth="admin", ignore=("body.data.id", "body.data.client_id")),
    case("oauth client 创建 缺名称 → 400", "POST", "/oauth/admin/clients", {"description": "x"}, auth="admin"),
    case("oauth client 创建 授权码模式缺回调 → 400", "POST", "/oauth/admin/clients",
         {"name": f"m1c-oauth-bad-{STAMP}"}, auth="admin"),
    case("oauth client 创建 名称超长 → 500", "POST", "/oauth/admin/clients",
         {"name": "m1c" + "x" * 200, "redirect_uris": [REDIRECT_URI]}, auth="admin"),
    case("oauth client 创建 纯 client_credentials 无回调", "POST", "/oauth/admin/clients",
         {"name": f"m1c-oauth-cc-{STAMP}", "grant_types": "client_credentials"}, auth="admin",
         ignore=("body.data.id", "body.data.client_id")),
    case("oauth client 更新 缺参数 → 400", "PUT", "/oauth/admin/clients/900001", {"description": "x"}, auth="admin"),
    case("oauth client 更新 id 非法 → 400", "PUT", "/oauth/admin/clients/abc", {"name": "x"}, auth="admin"),

    # ---------- 管理端：鉴权 401 / 403 ----------
    case("oauth clients 无 token → 401", "GET", "/oauth/admin/clients"),
    case("oauth clients 非管理员 → 403", "GET", "/oauth/admin/clients", auth="user"),
    case("oauth client 创建 非管理员 → 403", "POST", "/oauth/admin/clients", {"name": "x"}, auth="user"),
    case("oauth client 创建 无 token → 401", "POST", "/oauth/admin/clients", {"name": "x"}),
    case("oauth client 更新 无 token → 401", "PUT", "/oauth/admin/clients/900001", {"name": "x"}),
    case("oauth client 状态 非管理员 → 403", "PUT", "/oauth/admin/clients/900001/status", {"status": 0}, auth="user"),
    case("oauth client 删除 无 token → 401", "DELETE", "/oauth/admin/clients/900002"),

    # ---------- authorize ----------
    case("authorize 缺参数 → 400 文本", "GET", "/oauth/authorize"),
    case("authorize 客户端不存在 → 400 文本", "GET",
         f"/oauth/authorize?client_id=nope&redirect_uri={REDIRECT_URI}&response_type=code"),
    case("authorize redirect_uri 不在白名单 → 400 文本", "GET",
         f"/oauth/authorize?client_id={FIXTURE_CLIENT_ID}&redirect_uri=http://127.0.0.1:7001/other&response_type=code"),
    case("authorize 未登录 → 302 登录页", "GET",
         f"/oauth/authorize?client_id={FIXTURE_CLIENT_ID}&redirect_uri={REDIRECT_URI}&response_type=code",
         ignore=("body",)),
    case("authorize response_type 非 code → 400 文本", "GET",
         f"/oauth/authorize?client_id={FIXTURE_CLIENT_ID}&redirect_uri={REDIRECT_URI}&response_type=token"),
    # 原版 createCode 少绑一个参数（expires_at）→ MySQL 语法错误 → controller 兜底 500 文本，
    # 所以「已登录且校验全过」这条路两边都必须是 500「服务器内部错误」，与原文案逐字一致。
    case("authorize 已登录（原版 createCode 报错 → 500 文本）", "GET",
         f"/oauth/authorize?client_id={FIXTURE_CLIENT_ID}&redirect_uri={REDIRECT_URI}"
         f"&response_type=code&scope=read&state=m1c-{STAMP}", auth="admin"),
    case("authorize 已登录带 code_challenge → 500 文本", "GET",
         f"/oauth/authorize?client_id={FIXTURE_CLIENT_ID}&redirect_uri={REDIRECT_URI}"
         f"&response_type=code&code_challenge={PKCE_CHALLENGE}", auth="user"),

    # ---------- token：grant_type 分支 ----------
    case("token 不支持的 grant_type → 400", "POST", "/oauth/token", {"grant_type": "password"}),
    case("token 缺 grant_type → 400", "POST", "/oauth/token", {}),
    case("token authorization_code 缺 code → 400", "POST", "/oauth/token", {"grant_type": "authorization_code"}),
    case("token authorization_code 码无效 → 400", "POST", "/oauth/token",
         {"grant_type": "authorization_code", "code": "no-such-code"}),
    case("token authorization_code 码已过期 → 400", "POST", "/oauth/token",
         {"grant_type": "authorization_code", "code": CODE_EXPIRED}),
    case("token PKCE 校验失败 → 400", "POST", "/oauth/token",
         {"grant_type": "authorization_code", "code": CODE_PKCE_BAD, "code_verifier": "wrong-verifier"}),
    case("token PKCE 成功", "POST", "/oauth/token",
         {"grant_type": "authorization_code", "code": CODE_PKCE, "code_verifier": PKCE_VERIFIER},
         ignore=("body.access_token",)),
    case("token authorization_code 成功", "POST", "/oauth/token",
         {"grant_type": "authorization_code", "code": CODE_PLAIN}, ignore=("body.access_token",)),
    case("token authorization_code 重复用码 → 400", "POST", "/oauth/token",
         {"grant_type": "authorization_code", "code": CODE_PLAIN}),
    case("token client_credentials 缺凭证 → 400", "POST", "/oauth/token", {"grant_type": "client_credentials"}),
    case("token client_credentials 客户端不存在 → 401", "POST", "/oauth/token",
         {"grant_type": "client_credentials", "client_id": "nope", "client_secret": FIXTURE_SECRET}),
    case("token client_credentials 密钥等长但错误 → 401", "POST", "/oauth/token",
         {"grant_type": "client_credentials", "client_id": FIXTURE_CLIENT_ID, "client_secret": WRONG_SECRET_SAME_LEN}),
    case("token client_credentials 密钥长度不符 → 500", "POST", "/oauth/token",
         {"grant_type": "client_credentials", "client_id": FIXTURE_CLIENT_ID, "client_secret": WRONG_SECRET_SHORT}),
    case("token client_credentials 成功", "POST", "/oauth/token",
         {"grant_type": "client_credentials", "client_id": FIXTURE_CLIENT_ID, "client_secret": FIXTURE_SECRET},
         ignore=("body.access_token",)),

    # ---------- 写后读回 + 删除（禁用客户端 → authorize 无效；删掉 fixture 由导入时重建） ----------
    case("oauth client 状态置 0", "PUT", "/oauth/admin/clients/900002/status", {"status": 0}, auth="admin"),
    case("authorize 已禁用客户端 → 400 文本", "GET",
         f"/oauth/authorize?client_id={FIXTURE_CLIENT_ID_DISABLED}&redirect_uri={REDIRECT_URI}&response_type=code"),
    case("oauth client 删除", "DELETE", "/oauth/admin/clients/900002", auth="admin"),
]
