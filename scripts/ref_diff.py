#!/usr/bin/env python3
"""逐接口对照：把同一组请求分别打到「原版 Node 后端」与「Java 版」，比对状态码与响应体。

用法：
    python3 scripts/ref_diff.py                                       # ref=127.0.0.1:7001, java=127.0.0.1:7000
    python3 scripts/ref_diff.py --java http://127.0.0.1:8090          # 直接打本机的 auth 服务
    python3 scripts/ref_diff.py --cases rbac,user                     # 只跑指定模块
    python3 scripts/ref_diff.py --list
    python3 scripts/ref_diff.py -v                                    # 打印每个用例的响应体

用例放在 scripts/ref_cases/<模块>.py，文件里导出 CASES；新增模块就加一个文件，互不干扰。
字段说明见 scripts/ref_cases/_spec.py。

对照环境（原版 + 独立库）用 `scripts/ref_env.sh start` 起，口令统一成 123456；
Java 侧用 `scripts/dev_service.sh start auth <db> <port>` 起本地实例。

比对规则：
- 状态码必须一致；响应体的键集合必须一致（递归）；
- 值比对跳过「易变键」（token / 时间戳 / secret / hash 等），用例还能用 ignore 跳过指定路径；
- 写用例请自带善后（新增后删除），并把列表类用例放在写用例之前，别污染后续用例。
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import os
import sys
import urllib.error
import urllib.request

DEFAULT_REF = "http://127.0.0.1:7001"
DEFAULT_JAVA = "http://127.0.0.1:7000"

ADMIN = ("admin", "123456")
USER = ("user1", "123456")

CASES_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ref_cases")

# 值比对时跳过的键名（键本身仍要比对存在性）
VOLATILE_KEYS = {
    "token", "login_time", "created_at", "updated_at", "last_used_at", "expires_at",
    "client_secret", "plain", "key_hash", "password", "key_prefix",
}


def available_modules() -> list[str]:
    if not os.path.isdir(CASES_DIR):
        return []
    return sorted(
        f[:-3] for f in os.listdir(CASES_DIR)
        if f.endswith(".py") and not f.startswith("_")
    )


def _ensure_spec_helper() -> None:
    """把 scripts/ref_cases/_spec.py 注册成 `_spec`，满足用例文件里的 `from _spec import case`。

    不用往 sys.path 里塞目录：那会让同目录的 `email.py` 顶掉标准库的 email 包
    （`import urllib.request` 内部要 `import email.parser`，然后就会报
    "'email' is not a package"）。
    """
    if "_spec" in sys.modules:
        return
    path = os.path.join(CASES_DIR, "_spec.py")
    if not os.path.isfile(path):
        raise SystemExit(f"缺少 {path}")
    spec = importlib.util.spec_from_file_location("_spec", path)
    mod = importlib.util.module_from_spec(spec)
    sys.modules["_spec"] = mod
    spec.loader.exec_module(mod)


def load_cases(module: str) -> list[dict]:
    """按文件路径加载用例文件。

    不能用 import_module：模块名会撞标准库（`email` 就是典型——`import urllib.request`
    已经把 email 包塞进 sys.modules，再 import_module("email") 拿到的是标准库，用例永远是空的。
    """
    path = os.path.join(CASES_DIR, module + ".py")
    if not os.path.isfile(path):
        raise SystemExit(f"用例文件不存在：{path}")
    _ensure_spec_helper()
    name = f"ref_cases_{module}"
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    sys.modules[name] = mod
    spec.loader.exec_module(mod)
    return list(getattr(mod, "CASES", []))


def http(base: str, method: str, path: str, body=None, token=None, timeout=20):
    url = base.rstrip("/") + path
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", "replace")
            status = resp.status
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        status = e.code
    except Exception as e:  # noqa: BLE001
        return 0, {"_error": repr(e)}
    try:
        return status, json.loads(raw)
    except json.JSONDecodeError:
        return status, raw


def login(base: str, username: str, password: str) -> str:
    status, body = http(base, "POST", "/sys/login", {"username": username, "password": password})
    if status != 200 or not isinstance(body, dict):
        raise SystemExit(f"登录失败 {base} {username}: {status} {body}")
    return body.get("token")


def create_api_key(base: str, token: str, scopes: str) -> tuple[str, int]:
    """建一把 API Key，返回 (明文, id)，供 apikey:* 用例使用。"""
    status, body = http(base, "POST", "/api-keys", {"name": "refdiff", "scopes": scopes}, token)
    if status != 200 or not isinstance(body, dict):
        raise SystemExit(f"建 API Key 失败 {base}: {status} {body}")
    return body["data"]["plain"], body["data"].get("id")


def delete_api_key(base: str, token: str, key_id) -> None:
    if key_id is not None:
        http(base, "DELETE", f"/api-keys/{key_id}", None, token)


def diff(exp, got, path="", ignore=(), out=None, key=None):
    """key=(列表的 JSON 路径, 主键字段)：该列表按主键配对比较，
    这样共享对照库里被别人测试写进去的多余行不会误报。"""
    out = [] if out is None else out
    if path in ignore or any(path.endswith("." + i) or path == i for i in ignore):
        return out
    last = path.split(".")[-1].split("[")[0]
    if last in VOLATILE_KEYS:
        return out
    if isinstance(exp, dict) and isinstance(got, dict):
        for k in exp:
            if k not in got:
                out.append(f"{path}.{k}: 参考有、Java 没有")
            else:
                diff(exp[k], got[k], f"{path}.{k}", ignore, out, key)
        for k in got:
            if k not in exp:
                out.append(f"{path}.{k}: Java 多出该键")
    elif isinstance(exp, list) and isinstance(got, list):
        if key and path == key[0]:
            field = key[1]
            gmap = {json.dumps(g.get(field), sort_keys=True): g for g in got if isinstance(g, dict)}
            for e in exp:
                if not isinstance(e, dict):
                    continue
                k = json.dumps(e.get(field), sort_keys=True)
                if k not in gmap:
                    out.append(f"{path}: Java 侧缺少 {field}={k}")
                else:
                    diff(e, gmap[k], f"{path}[{field}={k}]", ignore, out)
        else:
            if len(exp) != len(got):
                out.append(f"{path}: 数组长度 参考={len(exp)} Java={len(got)}")
            for i, (a, b) in enumerate(zip(exp, got)):
                diff(a, b, f"{path}[{i}]", ignore, out, key)
    elif isinstance(exp, bool) or isinstance(got, bool):
        if exp != got:
            out.append(f"{path}: 参考={exp!r} Java={got!r}")
    elif isinstance(exp, (int, float)) and isinstance(got, (int, float)):
        if float(exp) != float(got):
            out.append(f"{path}: 参考={exp!r} Java={got!r}")
    elif exp != got:
        out.append(f"{path}: 参考={exp!r} Java={got!r}")
    return out


def run(java_base: str, ref_base: str, modules: list[str], verbose: bool, only: str | None) -> int:
    tokens = {}
    for base, tag in ((ref_base, "ref"), (java_base, "java")):
        tokens[tag] = {"user": login(base, *USER), "admin": login(base, *ADMIN)}

    # API Key 按需创建（Java 侧还没实现 /api-keys 时，apikey 用例自动跳过而不是整体报错）
    api_keys: dict[str, dict[str, tuple]] = {"ref": {}, "java": {}}
    bases = {"ref": ref_base, "java": java_base}

    def api_key(tag: str, scope: str):
        if scope not in api_keys[tag]:
            try:
                api_keys[tag][scope] = create_api_key(bases[tag], tokens[tag]["admin"], scope)
            except SystemExit:
                api_keys[tag][scope] = None
        return api_keys[tag][scope]

    total = fails = 0
    skipped = 0
    try:
        for module in modules:
            cases = load_cases(module)
            if only:
                cases = [c for c in cases if only.lower() in c["name"].lower()]
            print(f"\n===== {module}（{len(cases)} 个用例）=====")
            if not cases:
                skipped += 1
                continue
            for c in cases:
                auth = c["auth"]
                if auth.startswith("apikey"):
                    scope = auth.split(":")[-1] if ":" in auth else "read"
                    if api_key("ref", scope) is None or api_key("java", scope) is None:
                        print(f"  ⏭ 跳过（该侧不能建 API Key）{c['name']}")
                        skipped += 1
                        continue
                total += 1
                result = {}
                for base, tag in ((ref_base, "ref"), (java_base, "java")):
                    token = None
                    if auth in ("user", "admin"):
                        token = tokens[tag][auth]
                    elif auth.startswith("apikey"):
                        scope = auth.split(":")[-1] if ":" in auth else "read"
                        token = api_key(tag, scope)[0]
                    status, body = http(base, c["method"], c["path"], c.get("body"), token)
                    result[tag] = (status, body)
                rs, rb = result["ref"]
                js, jb = result["java"]
                problems = []
                if rs != js:
                    problems.append(f"状态码 参考={rs} Java={js}")
                problems += diff(rb, jb, "body", tuple(c.get("ignore", ())), key=c.get("key"))
                ok = not problems
                fails += 0 if ok else 1
                print(f"  {'✅' if ok else '❌'} {c['name']}  [{c['method']} {c['path']}] ref={rs} java={js}")
                for p in problems[:8]:
                    print(f"       - {p}")
                if verbose or not ok:
                    print(f"       参考：{json.dumps(rb, ensure_ascii=False)[:300]}")
                    print(f"       Java：{json.dumps(jb, ensure_ascii=False)[:300]}")
    finally:
        for tag, base in bases.items():
            for key in api_keys[tag].values():
                if key:
                    delete_api_key(base, tokens[tag]["admin"], key[1])

    print(f"\n结果：{total - fails}/{total} 用例一致" + (f"（{skipped} 个模块暂无用例）" if skipped else ""))
    return 0 if fails == 0 else 1


def main() -> int:
    modules = available_modules()
    ap = argparse.ArgumentParser(description="原版 Node ↔ Java 版逐接口对照")
    ap.add_argument("--ref", default=DEFAULT_REF, help="原版 Node 地址")
    ap.add_argument("--java", default=DEFAULT_JAVA, help="Java 侧地址（服务直连或网关）")
    ap.add_argument("--cases", default=",".join(modules), help="要跑的模块，逗号分隔")
    ap.add_argument("--only", default=None, help="只跑用例名里包含该子串的用例")
    ap.add_argument("--list", action="store_true", help="列出用例")
    ap.add_argument("-v", "--verbose", action="store_true", help="打印每个用例的响应体")
    args = ap.parse_args()

    if args.list:
        for module in modules:
            cases = load_cases(module)
            print(f"{module}: {len(cases)}")
            for c in cases:
                print(f"  - {c['name']}: {c['method']} {c['path']}")
        return 0

    wanted = [m.strip() for m in args.cases.split(",") if m.strip()]
    return run(args.java, args.ref, wanted, args.verbose, args.only)


if __name__ == "__main__":
    sys.exit(main())
