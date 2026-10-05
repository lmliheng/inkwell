#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""M0 冒烟测试：注册 → 登录 → /sys/profile，走网关（默认 http://127.0.0.1:7000）。

因为库里存量用户的口令是哈希后的、拿不到明文，这里用一个临时用户走完整链路。
校验点（对齐 Node 版的响应形状与状态码怪癖）：
  1. POST /sys/register   → HTTP 200, code=200, 有 token 与 user_info{id,username,email}
  2. POST /sys/login      → HTTP 200, code=200, token 是顶层字段, user_info 含 role_id/login_time
  3. GET  /sys/profile    → HTTP 200, code=200, user_info.user_detail / user_permission
  4. 密码错误时的登录       → HTTP 401, code=401, message 文案
  5. 非法 token 的 profile  → HTTP 200, code=500（原版怪癖，必须保持一致）
  6. 用 admin 删掉本次注册的临时账号（否则演示库里会越积越多 java_m0_* 用户）
"""

import json
import sys
import time
import urllib.error
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:7000"
NAME = "java_m0_%d" % int(time.time())
PASS = "M0-smoke-pw"

ok_count = 0
fail_count = 0


def call(method, path, payload=None, token=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(payload).encode() if payload is not None else None
    try:
        with urllib.request.urlopen(req, data=data, timeout=20) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        try:
            return e.code, json.loads(body)
        except json.JSONDecodeError:
            return e.code, body


def check(label, cond, detail=""):
    global ok_count, fail_count
    if cond:
        ok_count += 1
        print("  ✅ %s" % label)
    else:
        fail_count += 1
        print("  ❌ %s  %s" % (label, detail))


def wait_health(timeout=180):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(BASE + "/actuator/health", timeout=5) as r:
                if r.status == 200:
                    return True
        except Exception:
            time.sleep(3)
    return False


print("等待网关就绪 %s ..." % BASE)
if not wait_health():
    print("❌ 网关未就绪")
    sys.exit(1)

print("\n1) 注册 %s" % NAME)
status, body = call("POST", "/sys/register", {"username": NAME, "email": NAME + "@example.com", "password": PASS})
check("HTTP 200", status == 200, "实际 %s" % status)
check("code=200 且 success=true", isinstance(body, dict) and body.get("code") == 200 and body.get("success") is True, body)
check("返回顶层 token", isinstance(body, dict) and bool(body.get("token")))
check(
    "user_info 含 id/username/email",
    isinstance(body, dict) and set(map(str, (body.get("user_info") or {}).keys())) >= {"id", "username", "email"},
    body.get("user_info") if isinstance(body, dict) else body,
)
NEW_ID = (body.get("user_info") or {}).get("id") if isinstance(body, dict) else None

print("\n2) 用户名登录")
status, body = call("POST", "/sys/login", {"username": NAME, "password": PASS})
check("HTTP 200", status == 200, "实际 %s" % status)
check("code=200 且 success=true", isinstance(body, dict) and body.get("code") == 200, body)
token = body.get("token") if isinstance(body, dict) else None
check("顶层 token", bool(token))
info = (body.get("user_info") or {}) if isinstance(body, dict) else {}
check("user_info 含 role_id", "role_id" in info)
check("user_info 含 login_time", "login_time" in info)
check("user_info 含 vipLevel（原版字段怪癖）", "vipLevel" in info)

print("\n3) 带 token 取 /sys/profile")
status, body = call("GET", "/sys/profile", token=token)
check("HTTP 200", status == 200, "实际 %s" % status)
check("code=200 且 success=true", isinstance(body, dict) and body.get("code") == 200, body)
ui = (body.get("user_info") or {}) if isinstance(body, dict) else {}
detail = ui.get("user_detail") or {}
check("user_detail 有 id/username", "id" in detail and "username" in detail, detail)
check("user_detail 带 article_count/comment_count", "article_count" in detail and "comment_count" in detail, detail)
check("user_permission 非空（role 2 已配权限）", bool(ui.get("user_permission")), ui.get("user_permission"))

print("\n4) 密码错误登录（应 401）")
status, body = call("POST", "/sys/login", {"username": NAME, "password": "wrong"})
check("HTTP 401", status == 401, "实际 %s" % status)
check("code=401 且 message 为『用户名或密码错误』",
      isinstance(body, dict) and body.get("code") == 401 and body.get("message") == "用户名或密码错误", body)

print("\n5) 非法 token 取 profile（原版怪癖：HTTP 200 + code 500）")
status, body = call("GET", "/sys/profile", token="not-a-token")
check("HTTP 200", status == 200, "实际 %s" % status)
check("code=500 且 message 为『获取用户信息失败』",
      isinstance(body, dict) and body.get("code") == 500 and body.get("message") == "获取用户信息失败", body)

print("\n6) 清理：用 admin 删掉刚注册的临时账号（否则它会一直留在用户列表里）")
status, body = call("POST", "/sys/login", {"username": "admin", "password": "123456"})
admin_token = body.get("token") if isinstance(body, dict) else None
if admin_token and NEW_ID:
    status, body = call("DELETE", "/user-manage/delete", {"id": NEW_ID}, token=admin_token)
    check("已删除临时账号 %s" % NAME, status == 200, body)
else:
    print("  ⚠️ 没拿到 admin token，临时账号 %s 留在库里；可用 scripts/ref_env.sh clean 清掉" % NAME)

print("\n结果：%d 项通过，%d 项失败" % (ok_count, fail_count))
sys.exit(1 if fail_count else 0)
