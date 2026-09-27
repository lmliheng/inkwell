"""totp 模块（/totp/*）的对照用例。

TOTP 成功分支要自己算动态码：用例模块在 import 时按原版 otplib 的同一套算法
（HMAC-SHA1 / Base32 / 30 秒步长 / 6 位 / window=0）算出当前步长的验证码，
所以 ref（Node）与 Java 两侧拿到的是同一个码。

两张密钥：
- admin 的 totp_secret 是 dump 里就有的（两个库一致），用它做「登录成功」用例，与写库无关；
- user1 起始未绑定，用固定密钥走 confirm → login → disable 的完整来回（自带善后，可重复执行）。

因为 window=0，用例必须跑在当前 30 秒步长内：import 时若剩余时间不足 12 秒，先睡到下一步长再算码。
"""

import base64
import hashlib
import hmac
import struct
import time

from _spec import case


STEP_SECONDS = 30
DIGITS = 6

# 库里 admin 已有的密钥（deploy/mysql/init/01-schema.sql，两个库一致）
ADMIN_SECRET = "REPLACE_WITH_YOUR_TOTP_SECRET"
# user1 用例自己带的固定密钥（合法 Base32，16 字符 → 10 字节）
USER_SECRET = "JBSWY3DPEHPK3PXP"


def totp(secret, at=None, step=STEP_SECONDS, digits=DIGITS):
    """按 RFC 6238 算动态码（等价于 otplib authenticator.generate(secret)）。"""
    key = base64.b32decode(secret + "=" * ((8 - len(secret) % 8) % 8))
    counter = int((time.time() if at is None else at) // step)
    digest = hmac.new(key, struct.pack(">Q", counter), hashlib.sha1).digest()
    offset = digest[-1] & 0x0F
    binary = struct.unpack(">I", digest[offset:offset + 4])[0] & 0x7FFFFFFF
    return str(binary % (10 ** digits)).zfill(digits)


def _align_to_step():
    """留足余量：剩余不足 12 秒就先跨到下一个步长，避免两条请求落到不同步长。"""
    left = STEP_SECONDS - (time.time() % STEP_SECONDS)
    if left < 12:
        time.sleep(left + 0.5)


_align_to_step()
USER_CODE = totp(USER_SECRET)
ADMIN_CODE = totp(ADMIN_SECRET)
WRONG_CODE = "123456" if USER_CODE != "123456" else "654321"
WRONG_CODE_ADMIN = "123456" if ADMIN_CODE != "123456" else "654321"

CASES = [
    # ---------- 鉴权：四个绑定/状态接口都要登录 ----------
    case("setup 未登录 → 401", "POST", "/totp/setup"),
    case("confirm 未登录 → 401", "POST", "/totp/confirm", {"secret": USER_SECRET, "code": USER_CODE}),
    case("disable 未登录 → 401", "POST", "/totp/disable", {"code": USER_CODE}),
    case("status 未登录 → 401", "GET", "/totp/status"),
    case("status 带坏 token → 401", "GET", "/totp/status", auth="none"),

    # ---------- /totp/login 的校验分支（公开） ----------
    case("login 缺参 → 400", "POST", "/totp/login", {}, auth="none"),
    case("login 只有账号 → 400", "POST", "/totp/login", {"account": "admin"}, auth="none"),
    case("login 账号不存在 → 401", "POST", "/totp/login",
         {"account": "m1d-nobody@example.com", "code": "000000"}, auth="none"),
    case("login 未绑定账号 → 400", "POST", "/totp/login",
         {"account": "editor", "code": ADMIN_CODE}, auth="none"),

    # ---------- setup（登录后） ----------
    case("setup 成功（secret/uri 随机，忽略取值）", "POST", "/totp/setup", auth="user",
         ignore=("body.data.secret", "body.data.uri")),

    # ---------- confirm / status 来回 ----------
    case("status user1 初始未绑定", "GET", "/totp/status", auth="user"),
    case("confirm 参数缺失 → 400", "POST", "/totp/confirm", {}, auth="user"),
    case("confirm 只有 secret → 400", "POST", "/totp/confirm", {"secret": USER_SECRET}, auth="user"),
    case("confirm 错误验证码 → 400", "POST", "/totp/confirm",
         {"secret": USER_SECRET, "code": WRONG_CODE}, auth="user"),
    case("confirm 正确验证码 → 绑定成功", "POST", "/totp/confirm",
         {"secret": USER_SECRET, "code": USER_CODE}, auth="user"),
    case("status 绑定后读回 bound=true", "GET", "/totp/status", auth="user"),
    case("confirm 非法 secret（原版静默解码成垃圾）→ 400", "POST", "/totp/confirm",
         {"secret": "!!!!", "code": USER_CODE}, auth="user"),

    # ---------- /totp/login 成功分支（用刚绑定的 user1 与库里已有的 admin） ----------
    case("login user1 动态码错误 → 401", "POST", "/totp/login",
         {"account": "user1", "code": WRONG_CODE}, auth="none"),
    case("login user1 成功", "POST", "/totp/login",
         {"account": "user1", "code": USER_CODE}, auth="none"),
    case("login admin（库内已有密钥）成功", "POST", "/totp/login",
         {"account": "admin", "code": ADMIN_CODE}, auth="none"),
    case("login 用邮箱也能登录成功", "POST", "/totp/login",
         {"account": "user1@test.com", "code": USER_CODE}, auth="none"),

    # ---------- disable：先错后对，最后回到未绑定 ----------
    case("disable 动态码错误 → 400", "POST", "/totp/disable", {"code": WRONG_CODE}, auth="user"),
    case("disable 正确 → 解绑成功", "POST", "/totp/disable", {"code": USER_CODE}, auth="user"),
    case("status 解绑后读回 bound=false", "GET", "/totp/status", auth="user"),
    case("disable 未绑定 → 400", "POST", "/totp/disable", {"code": USER_CODE}, auth="user"),
    case("login 解绑后再登录 → 400", "POST", "/totp/login",
         {"account": "user1", "code": USER_CODE}, auth="none"),
]
