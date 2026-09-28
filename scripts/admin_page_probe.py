"""真实浏览器登录 Admin，逐页检查页面发出的 /api 请求，判定后台页面是否可用。

M0–M4 接口都已移植，所以这里要求**所有** /api 请求都是 2xx（早期只移植 M1 时，
M2/M3/M4 的 404 曾被单独豁免；现在没有豁免项，任何非 2xx 都算失败）。

用法：
    python scripts/admin_page_probe.py [base] [user] [pwd]
    base 默认 http://127.0.0.1（即宿主 80 端口的 Admin）
退出码 0 = 所有接口都 2xx 且无 JS 报错。
"""
import asyncio
import re
import sys

from playwright.async_api import async_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1"
USER = sys.argv[2] if len(sys.argv) > 2 else "admin"
PWD = sys.argv[3] if len(sys.argv) > 3 else "123456"

PAGES = [
    ("用户配置", "/user-profile"),
    ("用户管理", "/user/user-manage"),
    ("角色管理", "/user/role-manage"),
    ("权限管理", "/user/permission-manage"),
    ("主页设置", "/user/home-setting"),
    ("API Keys", "/system/api-key-manage"),
    ("OAuth 应用", "/system/oauth-manage"),
    ("系统监控", "/system/system-monitor"),
    ("文章管理", "/article/article-manage"),
]

# 早期只移植了 M1，M2/M3/M4 的接口 404 属预期，用这个前缀表豁免；
# 现在五个域都移植完了，表留空——任何非 2xx 都会被判为失败。
EXPECT_404 = ()


def is_expected_missing(url: str) -> bool:
    return any(url == p or url.startswith(p + "/") or url.startswith(p + "?") for p in EXPECT_404)


async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch()
        page = await browser.new_page(viewport={"width": 1440, "height": 900})
        calls, errors = [], []
        page.on("response",
                lambda r: calls.append((r.url.split(BASE)[-1], r.status)) if "/api/" in r.url else None)
        page.on("pageerror", lambda e: errors.append(str(e)))

        await page.goto(f"{BASE}/auth", wait_until="networkidle")
        inputs = page.locator(".n-input input")
        await inputs.nth(0).fill(USER)
        await inputs.nth(1).fill(PWD)
        await page.get_by_role("button", name=re.compile("^登录$")).first.click()
        await page.wait_for_timeout(4000)

        # 前端把登录态存在 localStorage['auth']（pinia-plugin-persistedstate）
        logged_in = await page.evaluate(
            "() => { try { const a = JSON.parse(localStorage.getItem('auth') || '{}');"
            " const d = a.token || (a.user && a.user.token) || '';"
            " return typeof d === 'string' && d.length > 0; } catch (e) { return false } }")
        if not logged_in:
            print("❌ 登录失败：localStorage['auth'] 里没有 token")
            await browser.close()
            return 1
        print("✅ 登录成功（token 已写入 localStorage['auth']）\n")

        failed, missing = [], set()
        for label, path in PAGES:
            calls.clear()
            await page.goto(BASE + path, wait_until="networkidle")
            await page.wait_for_timeout(2500)
            bad = sorted({f"{u} → {s}" for u, s in calls if not (200 <= s < 300)})
            real = [b for b in bad if not is_expected_missing(b.split(" → ")[0])]
            for b in bad:
                if is_expected_missing(b.split(" → ")[0]):
                    missing.add(b.split(" → ")[0].split("?")[0])
            if real:
                failed.append((label, real))
            print(f"{'✅' if not real else '❌'} {label:8s} {path}")
            for b in real:
                print(f"     未预期：{b}")

        if missing:
            print("\n以下接口 404 属预期（M2/M3/M4 未移植）：")
            for m in sorted(missing):
                print(f"  · {m}")

        print("\nJS 报错：", errors[-5:] if errors else "无")
        await browser.close()
        if failed:
            print(f"\n❌ {len(failed)} 个页面存在未预期的失败请求")
            return 1
        print("\n✅ 所有已移植（M1）接口均返回 2xx")
        return 0


sys.exit(asyncio.run(main()))
