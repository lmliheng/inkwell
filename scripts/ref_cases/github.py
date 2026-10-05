"""github 模块（/auth/github、/auth/github/bind、/auth/github/callback）的对照用例。

三个接口都是 302 重定向流，harness 用 urllib 会**自动跟随** Location，所以直接比对看不到
Location 本身。这里用一个「回显小服务」把这些重定向变成可逐字节比对的响应：

- 用例把 state（回调模式下就是「回跳目标」）设成 http://127.0.0.1:<回显端口>/cb，
  ref 与 Java 都会 302 到那里，回显服务把收到的 path+query 原样写成 JSON 返回，
  于是「重定向到哪、带什么参数」就变成了响应体里可比的值；
- /auth/github 与 /auth/github/bind 的目标固定在 github.com（改不了），
  只能靠跟随后的结果对照（两个进程打同一个 URL，结果一致即通过），
  Location 的精确值另用 curl -i 人工核对（见结论）。

没有 GITHUB_CLIENT_ID/SECRET：原版把空串拼进 client_id，回调时 axios 换 token 会被
上层网络/网关以 404 打回（非 2xx → axios 抛错 → error=github_login_failed）。
Java 侧照抄「非 2xx 抛错」的判定，所以这一条也能对照。
"""

import json
import threading
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from _spec import case


class _EchoHandler(BaseHTTPRequestHandler):
    """把收到的请求行原样回显成 JSON，用来核对 302 的 Location。"""

    def do_GET(self):  # noqa: N802 - BaseHTTPRequestHandler 的命名
        body = json.dumps({"echo": self.path}, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):  # 静音
        pass


def _start_echo_server():
    server = ThreadingHTTPServer(("127.0.0.1", 0), _EchoHandler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    port = server.server_address[1]
    # 起好了再返回，避免第一条用例打空
    urllib.request.urlopen(f"http://127.0.0.1:{port}/ready", timeout=5).read()
    return port


ECHO = f"http://127.0.0.1:{_start_echo_server()}/cb"
ECHO_Q = ECHO + "?from=bind"  # 已带 ? 的情形，用来核对 sep 是 '&'
BIND_STATE = json.dumps({"mode": "bind", "token": "", "redirect": ECHO}, ensure_ascii=False)

Q = lambda value: urllib.parse.quote(value, safe="")  # noqa: E731

CASES = [
    # ---------- 授权跳转（Location 固定指向 github.com，只能比对跟随后的结果） ----------
    case("GET /auth/github → 302 授权页", "GET", "/auth/github"),
    case("GET /auth/github?redirect=… → 302", "GET", "/auth/github?redirect=" + Q(ECHO)),
    case("GET /auth/github/bind → 302", "GET", "/auth/github/bind"),
    case("GET /auth/github/bind?redirect&token → 302", "GET",
         "/auth/github/bind?redirect=" + Q(ECHO) + "&token=" + Q("0.abc.def")),

    # ---------- 回调：无 code 的分支（回显核对最终 Location） ----------
    case("callback 无 code 无 state → 302 到默认前端", "GET", "/auth/github/callback"),
    case("callback 无 code + state=回显地址（回显核对）", "GET",
         "/auth/github/callback?state=" + Q(ECHO)),
    case("callback 无 code + state 已带 ? （sep 应为 &）", "GET",
         "/auth/github/callback?state=" + Q(ECHO_Q)),
    case("callback 无 code + code 为空串（同上分支）", "GET",
         "/auth/github/callback?code=&state=" + Q(ECHO)),
    case("callback 绑定模式 state（JSON）→ 走 redirect", "GET",
         "/auth/github/callback?state=" + Q(BIND_STATE)),

    # ---------- 回调：带 code（换 token 失败 → github_login_failed） ----------
    case("callback 带 code → 换 token 失败", "GET",
         "/auth/github/callback?code=m1d-dummy&state=" + Q(ECHO)),
]
