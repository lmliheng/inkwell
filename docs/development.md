# 开发

## 环境

| 需要 | 版本 | 用途 |
| :-- | :-- | :-- |
| JDK | 17 | 编译与跑后端 |
| Maven | 3.8+ | `mvn -DskipTests package` |
| Node | 20 / 22 / 24 | 前端本地开发（容器里用的是 `node:24-bookworm-slim`） |
| Python 3 | 3.8+ | 仓库里的验收脚本（只用标准库） |
| Playwright | 可选 | 两个页面探测脚本需要（不在项目依赖里） |

## 后端

```bash
mvn -B -DskipTests package                  # 全部模块（约 1–2 分钟）
mvn -B -DskipTests -pl jscreator-auth -am package   # 只编译 auth 及其依赖
java -jar jscreator-auth/target/jscreator-auth-1.0.0.jar    # 单跑一个服务
```

模块依赖关系：`jscreator-common`（信封、异常、工具）和 `jscreator-common-web`（Web 拦截器、`@RequireAdmin`）
被四个业务服务依赖；`gateway` 只依赖框架。

### 本机跑单个服务要和 Nacos 断开

compose 里的 Nacos 只把控制台端口 8848 映射到宿主，gRPC 端口（9848/9849）没有映射，
**本机服务注册不上会直接启动失败**。所以本机跑单服务要显式关掉注册：

```bash
SPRING_CLOUD_NACOS_DISCOVERY_ENABLED=false \
  JAVA_TOOL_OPTIONS="-Xmx220m -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC" \
  scripts/dev_service.sh start system fastweb_m4dev 8105
```

`scripts/dev_service.sh start <模块> <库名> <端口>` 会：连指定库、脱离当前会话后台起一个实例、日志写到
`/tmp/inkwell-dev-<模块>-<端口>.log`。**不要用 `flock` 包住 `start`**：那会在子进程里留下锁 fd，后面任何
`flock` 都会永久阻塞。

单独调试时还要注意环境变量：`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`/`JWT_SECRET`/`FRONTEND_URLS`
都是从环境读的，容器里由 compose 注入；本机跑就自己 export（或写 `application-local.yml`）。

## 前端

```bash
cd apps/admin && npm ci && npm run dev     # 默认 5173，接口直连 http://127.0.0.1:7000
cd apps/blog  && npm ci && npm run dev     # 同上
```

- 开发态接口地址在 `.env.development`（`VITE_API_BASE` 或 `import.meta.env.API_BASE`），生产态是 `/api`，
  由容器里的 nginx 反代到网关。
- **`apps/admin` 的 `npm run build` 会先跑 `vue-tsc` 类型检查**，而这份历史代码的类型并不干净 ——
  容器里构建走的是 `npx vite build`（跳过类型检查）。本地想验证类型就 `npm run build`，想快速出产物用 `npm run build-only`。
- 前端在容器里构建：`apps/*/Dockerfile` 是多阶段的（`node` → `vite build` → `nginx:alpine`）。

**`apps/admin` 的来历**：老仓库在 Windows 上编辑，大小写不敏感，同一个目录被以 `admin/` 和 `Admin/` 两种拼写提交进 git；
在 Linux 上 clone 会拆成互补的两半，这里已合并成一份能直接 `npm run dev` 的应用。

## 改代码时守住的约定

这些是「前端不改一行也能跑」的前提，动手前先看 [compatibility.md](compatibility.md) 的红线表：

- **响应信封**：成功 `{code:200,success:true,message,data?}`；登录的 `token`/`user_info` 在**顶层**。
- **列名原样**：MyBatis 关了 `map-underscore-to-camel-case`，数据库列名（`role_name`、`checkinDay`…）直接进 JSON；
  另外四个服务开了 `call-setters-on-nulls: true`（原版 mysql2 会把 NULL 列输出成 `键: null`，MyBatis 默认丢键）。
- **状态码怪癖照抄**：注册校验失败是 HTTP 200 + `code:400`；`/sys/profile` token 无效是 HTTP 200 + `code:500`。
- **管理员接口**用 `@RequireAdmin`；注意它的 403 文案与 systemmon 的 403 文案不同，加注解时按原版字面量传 `message`。

## 加一个接口要走哪几步

1. 在对应的 `jscreator-*/src/main/java/.../controller` 下加方法（路径**不带前缀**，与原版一致）；
2. service / mapper 按现有写法补（SQL 尽量照抄原版语义，包括它「把参数原样塞进 SQL」的怪癖）；
3. 在 `scripts/ref_cases/<模块>.py` 里补一条 `case(...)` 用例（格式见 `scripts/ref_cases/_spec.py`）；
4. 起两侧（原版 + 本版）跑对照，确认键集合/键序/值一致：

```bash
python3 scripts/ref_diff.py -v --cases rbac,user      # 只跑指定模块并打印响应体
```

5. 跑一遍整栈冒烟，避免回归：`python3 scripts/smoke_test.py`。

对照 harness 的完整跑法（含「必须要一份完整 dump」这件事）见 [testing.md](testing.md)。

## 提交约定

- 每个功能一个 commit，信息写清「做了什么 + 为什么」；不要在一个 commit 里混无关改动。
- 验收结论写进 commit message 或对应的 release 说明（例如「对照 613/613」「冒烟 20/20」）。
- 改前端品牌/文案这类可观测行为时，顺手跑一次 `scripts/admin_page_probe.py` / `scripts/blog_page_probe.py`。
