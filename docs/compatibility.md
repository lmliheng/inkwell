# 兼容性

本项目的硬要求是：**同一个前端、同一个客户端，对着这套微服务和对着原 Node 版，行为一致**。
所以「原版怎么怪，这里就得怎么怪」。这份文档是移植与改代码时的红线清单。

## 红线（破了就可能把前端打挂）

| 约定 | 具体 |
| :-- | :-- |
| 路径 | **无全局前缀**：`/sys/login`、`/article/list` 原样；分派靠网关按路径匹配 |
| 响应信封 | 成功 `{code:200, success:true, message, data?}`；失败 HTTP 状态码 + `{code, success:false, message}`。**登录响应的 `token`、`user_info` 是顶层字段**，不放在 `data` 里 |
| 状态码怪癖 | 注册校验失败 = HTTP **200** + `code:400`；`/sys/profile` token 无效 = HTTP **200** + `code:500`；用户名登录异常 = HTTP **200** + `code:500`；邮箱登录异常 = HTTP **500** 且响应体**没有 `code` 字段** |
| JWT | HS256，载荷 `{id, role_id}`，有效期 7 天。`JWT_SECRET` 必须与原版一致，否则存量 token 全部失效 |
| 密码 | SHA-256 十六进制（原 `CryptoJS.SHA256`），**不加盐**；库里存量用户可直接登录 |
| 列名 | MyBatis 关掉下划线转驼峰（`map-underscore-to-camel-case: false`），`role_name`、`checkinDay` 这类**原始列名直接进 JSON** |
| NULL 列 | 四个服务开了 `call-setters-on-nulls: true`（原版 mysql2 会把 NULL 列输出成 `键: null`，MyBatis 默认丢键） |
| 字段怪癖 | `vipLevel` 里装的其实是 `name`；`/oauth/authorize` 已登录时恒 500 是**原版自身的 bug**，这里照抄 |

## 管理接口的 403 文案

`@RequireAdmin` 有两种文案，别混：
- 普通管理接口：「权限不足，仅管理员可操作」
- `/system-monitor`：「权限不足，仅管理员可查看系统监控」

为此注解带了 `message` 属性，按原版字面量传。

## 与路径归属有关的两个坑

- `/api/v1/**`（开放 API，走 API Key 鉴权）在原版里属于 **openapi 模块**，所以归 **auth** 服务 ——
  它不是 content 的 `/article/**`，别并错。
- 根路径 `GET /` 是 system 服务的公开健康检查，路由**放在最后**，只匹配「就是 `/`」这一个请求。

## 刻意保留的差异（都不影响前端）

| 位置 | 原版 | 这里 | 为什么 |
| :-- | :-- | :-- | :-- |
| 登录返回的 `login_time` | `new Date().toLocaleString()`（随环境 locale 变） | 固定 `yyyy/M/d HH:mm:ss` | 消除不可复现的差异，前端按字符串展示 |
| 时间字段序列化 | MySQL DATETIME → UTC 的 `...Z` | `LocalDateTime` → `2026-09-28T03:45:12` | 浏览器按本地时间解析后显示一致 |
| CORS 拒绝 | 不回 CORS 头 | Spring 直接 403 | 浏览器侧行为一致 |
| `GET /system-monitor` | Node 的 `os.*` | `arch` 是 JVM 的 `amd64`（Node 是 `x64`）、`process.nodeVersion` 键名照抄但值是 JVM 版本、`hostname` 在容器里是容器主机名 | 键集合必须一致，取值来源没法完全对齐 |
| `data.memory.*` | `os.totalmem()`（宿主视图） | 读 `/proc/meminfo`，**刻意不用 `OperatingSystemMXBean`** | JDK 会按 cgroup 限制读，在容器里会拿到容器配额（例如 380M），监控页显示的就不是服务器内存了 |
| `data.cpu.usage` | 两次采样求差 | 同样两次 `/proc/stat` 求差，首次 `null` | 行为对齐 |
| `GET /system-monitor/api-stats` | 单体登记了全部 111 个接口 | 只统计 **system 服务自身**的请求 | 口径随拆分而变，无法等价 |
| `GET /backup/download` | npm `mysqldump` 包自己拼 SQL；先发响应头，中途出错只能断流 | JDBC 读 `SHOW CREATE TABLE` + `SELECT *` 生成可重复导入的 SQL；先生成完整产物，失败返回 500 信封 | 运行镜像里没有 mysql-client；INSERT 用紧凑单行写法 |
| `POST /article/ai-summary/regenerate/:id` | DeepSeek | 同左，`DEEPSEEK_API_KEY` 为空时走同样的失败分支 | — |

## 前端从旧仓库切过来时的注意点

后端契约一致，所以前端代码不用改；但有四处历史坑值得知道：

1. **`import.meta.env.API_BASE`（没有 `VITE_` 前缀）**：老源码三处这么写，Vite 不会替换它，
   靠 `apps/admin/vite.config.ts` 的 `define` 顶掉，值从 `.env[.mode]` 取。
2. **nginx 反代必须用 `$http_host`**，不能用 `$host`（会抹掉端口，让同源 POST 被网关判成跨域 → 403，表象是「登录失败」）。
3. **上游用 `resolver 127.0.0.11` + 变量式 `proxy_pass`**：否则网关容器重建换 IP 后前端一直 502。
4. **站点 origin 要在 `FRONTEND_URLS` 里**，换域名/端口后同步并重建网关。

## 验收这些约定

上面的每一条都在 [testing.md](testing.md) 的对照用例里有覆盖；改代码前先读红线表，改完跑一次对照与冒烟。
