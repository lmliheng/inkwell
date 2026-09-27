# JScreator 后端 → Java 微服务移植方案

> 目标：把 `/root/JScreator/Backend`（Express + TypeScript）按业务域拆成 Spring Boot 微服务，用 Docker Compose 整体部署，**前端三个应用（Admin / Blog / IMG）零改动**。

---

## 1. 现状盘点（实测）

| 项目 | 数值 |
| :-- | :-- |
| 源码 | 100 个文件 / **8523 行** TypeScript |
| 接口 | **111 个**唯一 REST 端点（含 `GET /backup/download` 这类不在 `*.routes.ts` 里注册的），16 个模块 |
| 数据表 | **22 张**（MySQL 8，dump 592K） |
| 运行时依赖 | MySQL、阿里云 OSS（图片）、DeepSeek（AI 摘要）、SMTP（邮件验证码）、GitHub OAuth |
| 没用到的 | **无 Redis、无消息队列、无定时任务** |
| 入口 | `src/app.ts` 挂载 17 个 router；`src/legacy.ts` 只是兼容桥，`registerLegacyRoutes` 是空实现 |
| 前端 | 三个前端都吃 `http://127.0.0.1:7000`（`Admin/.env.*`、`Blog/src/api/http.js`），IMG 走相对路径 `/api` |

模块与接口分布：`auth 2 + email 2 + github 3 + totp 5 + user 11 + rbac 8 + oauth 7 + openapi 8 + article 12 + comment 5 + blog 5 + content 15 + social 15 + dm 4 + notification 6 + systemmon 3 = 111`。

---

## 2. 兼容性红线（不改就可能把前端打挂）

| # | 约定 | 原版实现 | Java 侧必须做到 |
| :-- | :-- | :-- | :-- |
| 1 | **路径** | 无全局前缀，直接 `/sys/login`、`/article/list`、`/upload/image` | 网关按原路径透传，一字不改 |
| 2 | **响应信封** | `{code:200,success:true,message,data?}`；失败为 HTTP 状态码 + `{code,success:false,message}` | 统一 `ApiResponse`，成功/失败两套写法照抄 |
| 3 | **JWT** | HS256，payload `{id, role_id}`，有效期 7d，`Authorization: Bearer <token>`，密钥 `JWT_SECRET`（缺省 `test`） | 用同一算法与 payload 结构签发/校验，**老 token 继续有效**，登录不中断 |
| 4 | **密码** | `SHA256(password)` 十六进制（crypto-js），无盐 | 用 SHA-256 hex 校验，**库里存量用户能直接登录**，不重置密码 |

数据库表结构**原样复用**，不做 ORM 改写、不迁移数据。

---

## 3. 目标架构

```
                         ┌──────────────────────────────┐
   浏览器/前端  ────►    │  gateway  :8088  (WebFlux)    │
   (Admin/Blog/IMG)      │  CORS · 路由 · JWT 粗粒度校验  │
                         └───────┬──────────────────────┘
                 ┌───────────────┼───────────────┬───────────────┐
                 ▼               ▼               ▼               ▼
        ┌───────────────┐ ┌────────────┐ ┌──────────────┐ ┌──────────────┐
        │ auth  :8090   │ │content:8091│ │ social :8092 │ │ system :8093 │
        │ 46 个接口      │ │ 37 个接口   │ │ 25 个接口     │ │ 3 个接口      │
        │ 认证/用户/RBAC │ │ 文章/评论/  │ │ 关注/私信/    │ │ 监控/备份/    │
        │ OAuth/API Key │ │ 博客/运营   │ │ 通知          │ │ 广告/公告     │
        └───────┬───────┘ └─────┬──────┘ └──────┬───────┘ └──────┬───────┘
                └───────────────┴───────────────┴───────────────┘
                                        ▼
                              MySQL 8  :3308（22 张表）
```

| 服务 | 端口 | 模块 | 接口数 | 主要表 |
| :-- | :-- | :-- | --: | :-- |
| `jscreator-gateway` | 8088 | — | — | — |
| `jscreator-auth` | 8090 | auth, email, github, totp, user, rbac, oauth, openapi | 46 | user, role, permission, roleandpermission_middle, oauth_client/code/consent, api_key |
| `jscreator-content` | 8091 | article, comment, blog, content | 37 | article, article_category, articleandcategory_middle, comment, ad, announcement |
| `jscreator-social` | 8092 | social, dm, notification | 25 | follow, message, notification, notification_read, user_notification, article_like, article_favorite |
| `jscreator-system` | 8093 | systemmon, backup | 4 | vip_code + 运行时统计（内存态）；`/backup/download` 依赖 `mysqldump` |

> `/backup/download` 用 `mysqldump` 打包 zip，Java 侧两种做法：镜像里装 `mysql-client` 直接调命令行（最省事、行为一致），或用 JDBC 自己拼 SQL（无外部依赖但易漏字符集/触发器）。M4 实做时定，倾向后者跟镜像解耦、前者保证 dump 语义一致 —— 届时按你的偏好选。

**对外只暴露网关**（映射到宿主 **7000**，与原 Node 后端一致，前端不用改配置）。

### 数据边界（务实取舍）

一棵 MySQL 实例、**一个 schema**（直接导入现有 dump，零数据迁移风险）。每个服务只**写**自己名下的表；跨域读取（如博客主页要用户信息、点赞要文章标题）优先走服务内联表查询（同实例，别无谓地拆成网络调用），实时性要求高的少数地方用 OpenFeign 调 `auth`。这是刻意的技术债，换来的是移植风险最小；后续要拆 schema 时，因为每个服务已经有独立 DataSource，改动可控。

两个细节：
- **邮件验证码**在原版是进程内存态（`emailAuth.service` 里存 Map），Java 侧同样放 `auth` 服务内存（单实例，行为一致）；将来横向扩容时再落库。
- **TOTP 密钥**存在 `user` 表（`auth` 服务独占），`content`/`social` 只读不写。

---

## 4. 技术栈

| 层 | 选型 | 理由 |
| :-- | :-- | :-- |
| 语言/运行时 | **Java 17**、Spring Boot **3.2.5**、Spring Cloud **2023.0.1** | 和你已有的 `LianjinTai` 保持同一套版本与规范，将来共用经验/模块 |
| 网关 | Spring Cloud Gateway（WebFlux） | 替换 Express 中间件：CORS + 路由 + JWT 前置校验 |
| 服务间调用 | OpenFeign + LoadBalancer | 走静态地址（`SPRING_CLOUD_DISCOVERY` 关掉），**不引入 Nacos**——省 600MB 内存 |
| 持久层 | MyBatis-Plus 3.5.x | SQL 可见、易与原版 DAO 的 SQL 对齐 |
| 鉴权 | java-jwt（HS256）+ Spring 拦截器 | 对齐原版 `verifyToken` / `requireRole` |
| 接口文档 | springdoc-openapi | 自动产出 111 个接口的对照表 |
| 构建 | Maven 多模块 | 单命令全量构建 |
| 部署 | Docker Compose（MySQL 8 + 5 个 JVM） | 见下 |

---

## 5. 部署拓扑与内存预算（关键约束）

这台服务器 **2 核 / 3.8G / 无 swap**，你已经为了省内存停掉了宝塔和 MySQL。所以要精打细算：

| 组件 | 镜像 | `-Xmx` | 预算 | **实测** |
| :-- | :-- | --: | --: | --: |
| nacos | `nacos/nacos-server:v2.3.2` | 256m | ~600M | 546M |
| gateway | `eclipse-temurin:17-jre` | 160m | ~180M | 229M |
| auth | 同上 | 256m | ~320M | 224M |
| content | 同上 | 256m | ~320M | 234M |
| social | 同上 | 224m | ~280M | 209M |
| system | 同上 | 192m | ~240M | 186M |
| mysql | `mysql:8.0` | `innodb_buffer_pool_size=128M` | ~350M | 199M |
| web | `nginx:alpine` | — | ~20M | 4M |
| docker 引擎 | — | — | ~100M | — |
| **合计** | | | **≈ 2.4G** | **≈ 1.83G + 引擎** |

实测比预算省（`-XX:+UseSerialGC` + 小堆 + 连接池限 5）：全栈起来后 `available` 仍有约 1G。

留出约 1G 给系统和你现有的 penguin-server，**能跑但没富余**；因此 compose 里每个服务都写死 `mem_limit`，避免某个服务把机器吃干净。

> 关于 Nacos：M0/M1 时为了这份预算表刻意没引入注册中心，网关路由指向写死的服务地址。
> 后来按需求接入 Nacos 做服务发现（网关路由改成 `lb://<服务名>`），代价是约 550M 常驻内存；
> 换来的是扩容/换端口/换机器时不用改配置、不用重启网关。依旧是单机 standalone + 内嵌存储，
> 不额外依赖 MySQL。

---

## 6. 里程碑（每轮都是可运行、可验证的）

| 阶段 | 交付 | 验证方式 |
| :-- | :-- | :-- |
| **M0 骨架** | 父 POM + common（响应/异常/JWT/拦截器）+ gateway + auth 骨架 + Dockerfile + compose + `.env.example` | ✅ 已完成：`docker compose ps` 六个容器全 Up，`scripts/smoke_test.py` **20 项断言全过**（注册/登录/profile/401/怪癖 500，跑完自动删掉临时账号），网关按路径分派到四个服务（未实现路径返回下游信封的 404） |
| **M1 认证授权域** | auth/email/github/totp、user、rbac、oauth、api-key 全量 46 接口 | ✅ 已完成：把线上库复制一份、原版 Node 起在 7002，两边各跑 214 个用例（`scripts/ref_diff.py`），**状态码 + 响应体键集合/键序逐字段一致 214/214**；`scripts/smoke_test.py` 20/20；浏览器逐页验证 M1 页面请求全 2xx |
| **M2 内容域** | article、blog、comment、ad、announcement、upload 共 37 接口 | ✅ 已完成：对照原版 **251/251** 用例一致（`scripts/ref_cases/content.py`，连续三次从干净库重跑）。**未覆盖**：`/upload/image` 的成功分支要 OSS 密钥，本机没有，两侧都停在 500 失败分支 |
| **M3 互动域** | social、dm、notification 25 接口 | ✅ 已完成：对照原版 **139/139** 用例一致 |
| **M4 系统域** | systemmon（监控 / 接口统计）、backup、根健康检查 4 接口 | ✅ 已完成：对照原版 **9/9** 用例一致；备份产物解压后可完整恢复成同构同量的库（逐表行数一致） |
| **Nacos 服务发现** | 五个服务注册、网关路由改 `lb://<服务名>` | ✅ 已完成：五服务注册成功，全量对照 **613/613**，冒烟 20/20 |
| **M5 前端联调 + 收尾（可选）** | 三个前端指向网关跑通主要流程；agent 模块（DeepSeek 摘要）、对外 `/api/v1/*`、按 schema 拆库 | Admin 九页浏览器实测全 2xx、无 JS 报错；Blog/IMG 未跑。**待定**：Admin 手机端只做了「布局壳 + 全局兜底」一版响应式，列多的表格仍偏挤 |

## 7. 目录结构

```
/root/jscreator-java/
├── PLAN.md                  ← 本文件
├── pom.xml                  ← 父 POM（统一版本、插件、Docker 镜像构建）
├── docker-compose.yml       ← 一键起全套（MySQL + 5 服务）
├── .env.example             ← DB/JWT/OSS/SMTP/GitHub 密钥清单
├── jscreator-common/        ← ApiResponse、异常、JWT 工具、鉴权拦截器、分页、MyBatis 配置
├── jscreator-gateway/       ← 8088
├── jscreator-auth/          ← 8090
├── jscreator-content/       ← 8091
├── jscreator-social/        ← 8092
├── jscreator-system/        ← 8093
├── scripts/                 ← smoke_test.py（冒烟）、admin_page_probe.py（浏览器逐页探针）、
│                              ref_env.sh / dev_service.sh / ref_diff.py + ref_cases/（与原版逐接口对照）
└── deploy/
    ├── Dockerfile.service   ← 多阶段构建（maven 编译 → jre 运行），按 Maven profile 选模块
    └── mysql/init/          ← 挂载现有 dump，首次启动导入
```

## 8. 风险与待确认

1. **部署目标机**：本机内存虽勉强够，但与你「省内存」的既定方针冲突；也可只产出代码 + compose，部署到别的机器。
2. **外部依赖可用性**：**M1 的邮件/GitHub 逻辑已按原版实现并跑通对照**（未配密钥时走原版同样的失败分支），
   只差真密钥：`SMTP_*`/`FROM_EMAIL` 一填就能真发验证码，`GITHUB_CLIENT_ID/SECRET/CALLBACK_URL` 一填就能真走 GitHub 登录
   （值走 `.env` → 容器环境变量，`docker compose up -d auth` 生效）。
   **图片上传是唯一没实现完的分支**：`/upload/image` 的成功路径要阿里云 OSS 的 AccessKey，本机没有密钥，
   两侧都停在 500 失败分支，所以这条 200 路径既没实现也没观测过（`UploadService.uploadToOss` 会明确报
   「OSS 上传实现待补」，不会假装成功）。要补齐，按阿里 OSS 的 PUT 实现即可，bucket/region/对象路径已按原版写死。
3. **agent 模块**：`src/modules/agent` 走 DeepSeek，属 AI 能力，建议放 M5（不阻塞主链路）。
4. **旧 token 兼容**：必须做到，否则前端用户全部掉线。
