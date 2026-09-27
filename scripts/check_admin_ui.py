"""用真实浏览器验证后台前端：登录页能渲染、登录能打通 /api 到网关、登录后能进主页。

需要 playwright（本机在 shared_env/webcheck 里）：
    /root/.penguin/data/acc/agents/default_agent/shared_env/webcheck/bin/python \
        scripts/check_admin_ui.py http://127.0.0.1:31058 admin 123456
退出码 0 表示登录接口、profile 接口都是 200 且拿得到 token；截图落在 $SHOT（默认 admin-ui.png）。
"""
import asyncio
import json
import os
import re
import sys

from playwright.async_api import async_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:31058"
USER = sys.argv[2] if len(sys.argv) > 2 else "admin"
PWD = sys.argv[3] if len(sys.argv) > 3 else "123456"
SHOT = os.environ.get("SHOT", "admin-ui.png")


async def main() -> int:
    console, errors, api = [], [], []
    result = {"base": BASE, "ok": False}

    async with async_playwright() as p:
        browser = await p.chromium.launch()
        page = await browser.new_page(viewport={"width": 1440, "height": 900})
        page.on("console", lambda m: console.append(f"{m.type}: {m.text}"))
        page.on("pageerror", lambda e: errors.append(str(e)))

        async def on_response(resp):
            if "/api/" in resp.url:
                api.append({"url": resp.url.split(BASE)[-1], "status": resp.status})

        page.on("response", on_response)

        await page.goto(f"{BASE}/auth", wait_until="networkidle")
        result["login_title"] = await page.title()
        result["login_heading"] = (await page.locator("h2").first.inner_text()).strip()

        inputs = page.locator(".n-input input")
        await inputs.nth(0).fill(USER)
        await inputs.nth(1).fill(PWD)
        await page.get_by_role("button", name=re.compile("^登录$")).first.click()

        # 登录成功后路由跳到 '/'，主页会拉 /api/sys/profile
        try:
            await page.wait_for_url(re.compile(r"^" + re.escape(BASE) + r"/($|[^a])"), timeout=15000)
        except Exception as exc:  # noqa: BLE001
            result["nav_error"] = repr(exc)[:200]
        await page.wait_for_timeout(4000)

        result["url_after_login"] = page.url
        result["menu_items"] = [
            t.strip()
            for t in await page.locator(".n-menu-item-content-header").all_inner_texts()
            if t.strip()
        ][:12]
        result["stored_token"] = await page.evaluate(
            "() => (JSON.parse(localStorage.getItem('auth') || '{}').token || '').slice(0, 20)"
        )
        result["api_calls"] = api
        result["console"] = console[-15:]
        result["page_errors"] = errors[-10:]

        out = SHOT
        await page.screenshot(path=out, full_page=False)
        result["screenshot"] = out
        await browser.close()

    ok_statuses = {c["status"] for c in api}
    result["ok"] = (
        not errors
        and any(c["url"].endswith("/api/sys/login") and c["status"] == 200 for c in api)
        and any(c["url"].endswith("/api/sys/profile") and c["status"] == 200 for c in api)
        and bool(result["stored_token"])
        and 500 not in ok_statuses
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["ok"] else 1


sys.exit(asyncio.run(main()))
