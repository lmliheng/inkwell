"""真实浏览器打开博客前端，检查首页渲染与它发出的 /api 请求。

M0–M4 接口都已移植，所以这里要求**所有** /api 请求都是 2xx，
并确认首页真的渲染出了文章卡片（不是只有壳子）。

用法：
    python scripts/blog_page_probe.py [base]
    base 默认 http://127.0.0.1:8080（宿主上博客前端的端口，见 .env 的 BLOG_PORT）

退出码 0 = 所有接口 2xx、页面渲染出内容、无 JS 报错。
"""
import asyncio
import sys

from playwright.async_api import async_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080"

# 首页会并发请求这三个公开接口（见 apps/blog/src/views/HomeView.vue）
EXPECT_CALLS = ("/api/blog/feed", "/api/blog/hot", "/api/blog/users")


async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch()
        page = await browser.new_page(viewport={"width": 1440, "height": 900})
        calls, errors = [], []
        page.on("response",
                lambda r: calls.append((r.url.split(f"{BASE}/api")[-1], r.status)) if "/api/" in r.url else None)
        page.on("pageerror", lambda e: errors.append(str(e)))

        await page.goto(BASE + "/", wait_until="networkidle")
        await page.wait_for_timeout(3000)

        # 接口层
        bad = sorted({f"{u} → {s}" for u, s in calls if not (200 <= s < 300)})
        for u, s in sorted(set(calls)):
            print(f"{'✅' if 200 <= s < 300 else '❌'} {s}  {u}")
        hit = [u for u, _ in calls if any(u.startswith(e.split('/api')[-1]) for e in EXPECT_CALLS)]
        print(f"\n首页接口调用：{len(calls)} 个，其中 feed/hot/users 命中 {len(set(hit))}/3")

        # 渲染层
        cards = await page.locator("article").count()
        title = await page.title()
        print(f"页面标题：{title}")
        print(f"文章卡片：{cards} 个")

        await browser.close()

        if errors:
            print("\nJS 报错：", errors[-5:])
        else:
            print("JS 报错：无")

        ok = not bad and cards > 0 and not errors
        if not ok:
            print("\n❌ 博客首页有问题")
            if bad:
                print("   未预期的接口失败：", bad)
            if cards == 0:
                print("   页面没有渲染出任何文章卡片")
            return 1
        print("\n✅ 博客首页正常（接口全 2xx、渲染出文章卡片、无 JS 报错）")
        return 0


sys.exit(asyncio.run(main()))
