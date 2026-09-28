# 架构

## 一张图

```
                          宿主机（浏览器 / 第三方）
                                    │
        ┌───────────────────────────┼──────────────────────────────┐
        │                           │                              │
   admin-web :80               blog-web :8080                 gateway :7000
   nginx 托管 apps/admin       nginx 托管 apps/blog           直连网关
   /api → gateway（去前缀）    /api → gateway（去前缀）       （与原 Node 后端同端口）
   来源 IP 白名单              对外，无白名单
        │                           │                              │
        └───────────┬───────────────┘                              │
                    ▼                                              ▼
             gateway :8088  ← 路由（lb://<服务名>）———————————————┘
                    │
     ┌──────────────┼───────────────┬────────────────┐
     ▼              ▼               ▼                ▼
 auth :8090   content :8091   social :8092    system :8093
 认证/用户/角色  文章/评论/博客   关注/点赞/私信  监控/接口统计
 OAuth/API Key  上传/广告/公告   收藏/通知        备份/根健康检查
     └──────────────┴───────────────┴────────────────┘
                    │                    │
                    ▼                    ▼
        MySQL 8 :3306（宿主 127.0.0.1:3308，22 张表）
        Nacos :8848（宿主 127.0.0.1:8848，注册中心；gRPC 9848/9849 只在 compose 网络内）
```

五个服务共用**同一个镜像** `inkwell-app:local`，靠启动参数选跑哪个 jar（见 `deploy/entrypoint.sh`）：
2 核机器上构建 5 次代价太高，构建一次、复用五次。

## 服务清单

| 服务 | 模块 | 容器内端口 | 宿主端口 | 职责 | 接口数 |
| :-- | :-- | --: | --: | :-- | --: |
| gateway | `jscreator-gateway` | 8088 | `APP_PORT`=7000 | 路由分派、CORS、根健康检查转发 | — |
| auth | `jscreator-auth` | 8090 | — | 登录/注册/profile、邮箱验证码、GitHub OAuth、TOTP、用户管理、角色权限、OAuth 应用、API Key | 46 |
| content | `jscreator-content` | 8091 | — | 文章、评论、博客、图片上传、广告、公告 | 37 |
| social | `jscreator-social` | 8092 | — | 关注、点赞、收藏、私信、通知 | 25 |
| system | `jscreator-system` | 8093 | — | 系统监控、接口统计、数据库备份 | 4 |

另有两个前端（都不是 Spring 应用）：

| 应用 | 源码 | 镜像 | 宿主端口 | 说明 |
| :-- | :-- | :-- | :-- | :-- |
| 后台管理 | `apps/admin` | `inkwell-admin:local` | `WEB_PORT`=80 | Vue 3 + Element Plus + Naive UI；`/api` 反代；来源白名单 |
| 博客 | `apps/blog` | `inkwell-blog:local` | `BLOG_PORT`=8080 | Vue 3 + Vite + Tailwind CSS 4；`/api` 反代 |

## 一次请求怎么走

以「登录成功后加载后台首页的文章列表」为例：

1. 浏览器 `POST http://<host>/api/sys/login` —— 打在 admin-web 的 nginx 上；
2. nginx 去掉 `/api` 前缀，转发到 `gateway:8088` 的 `/sys/login`（用 `$http_host` 保留端口，否则会被网关当成跨域）；
3. 网关按 `Path=/sys/login` 匹配到 `auth-sys` 路由，目标 `lb://jscreator-auth`；
4. 网关向 Nacos 查 `jscreator-auth` 的实例列表，负载均衡挑一个（这里是 auth:8090）；
5. auth 用 MyBatis 查 MySQL `fastweb_test`，按原版信封返回（登录时 `token`、`user_info` 是**顶层字段**）；
6. 响应原路返回；后台再用同一个 token 请求 `/api/article/list` → 网关路由到 content。

服务之间的通信只有「网关 → 服务」这一条（服务之间没有互相调用）。

## 路由表

网关按**路径**分派，规则都在 `jscreator-gateway/src/main/resources/application.yml`：

| 路径 | 去向 |
| :-- | :-- |
| `/sys/login`、`/sys/register`、`/sys/profile`、`/resetPassword` | auth |
| `/email/**`、`/auth/github/**`、`/totp/**` | auth |
| `/oauth/**` | auth |
| `/user-manage/**`、`/userInfo`、`/userInfo/**`、`/api-keys`、`/api-keys/**` | auth |
| `/role/**`、`/permission/**` | auth |
| `/api/v1/**`（开放 API，走 API Key 鉴权） | auth（原版里属 openapi 模块） |
| `/article/**`、`/comment/**`、`/blog/**`、`/upload/image` | content |
| `/ad/**`、`/announcement/**` | content |
| `/social/**`、`/dm/**`、`/notification/**` | social |
| `/system-monitor`、`/system-monitor/**`、`/backup/**` | system |
| `/`（公开健康检查，放最后只匹配根路径） | system |

## 服务注册与发现（Nacos）

- 每个服务的 `spring.application.name` 是注册名：`jscreator-auth` 等；网关路由写 `lb://jscreator-auth`，**不写 IP**。
- 地址由 `NACOS_ADDR` 注入：容器里是 `nacos:8848`（compose 网络内），本机开发默认 `127.0.0.1:8848`。
- Nacos 用 **standalone + 内嵌存储**（不依赖 MySQL），控制台只绑 `127.0.0.1`，因此关掉鉴权（`NACOS_AUTH_ENABLE=false`）。
- 不设硬依赖：实例都找不到时网关按 `lb` 默认行为返回 503，其余服务不受影响。
- 看有哪些实例：
  ```bash
  curl -s '127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=jscreator-auth'
  ```
- **本机跑单个服务时必须关掉注册**（compose 只映射了 8848 控制台端口，gRPC 9848/9849 没映射到宿主，注册不上会直接启动失败）：
  `SPRING_CLOUD_NACOS_DISCOVERY_ENABLED=false`，见 [development.md](development.md)。

## 数据边界

**一个库（`fastweb_test`）、22 张表、5 个服务共用**。没有按服务拆库，这是刻意的：

- 原版就是单库，接口里存在跨域查询（例如博客列表要 join 用户表）。拆库会引入分布式事务与跨服务调用，
  而本项目的硬要求是「与原版逐字段一致」——换来的复杂度没有任何验收价值。
- 五个服务各自持有 `HikariCP` 连接池，MySQL 侧把 `max-connections` 提到 80（默认 151 对 4G 机器偏大）。

表结构与种子数据在 `deploy/mysql/init/01-schema.sql`（脱敏版，只有表结构 + RBAC 种子 + 一个管理员）。

## 技术栈

| 层 | 选型 | 版本 |
| :-- | :-- | :-- |
| 语言/运行时 | Java | 17 |
| 框架 | Spring Boot | 3.2.5 |
| 微服务 | Spring Cloud | 2023.0.1 |
| 注册中心 | Spring Cloud Alibaba + Nacos | 2023.0.1.2 / nacos-server v2.3.2 |
| 数据访问 | MyBatis-Plus | 见根 `pom.xml` |
| 鉴权 | java-jwt（HS256） | 见根 `pom.xml` |
| 数据库 | MySQL | 8.0 |
| 前端 | Vue 3 + Vite；后台 Element Plus/Naive UI，博客 Tailwind CSS 4 | 见 `apps/*/package.json` |
| 静态托管 | nginx（alpine） | 见 `apps/*/Dockerfile` |
| 编排 | Docker Compose v2 | 项目名 `inkwell` |

## 目录结构

```
inkwell/
├── README.md                     项目介绍 + 五分钟快速开始
├── PLAN.md                       移植方案与里程碑记录
├── docs/                         本目录：使用与开发文档
├── docker-compose.yml            九个容器的编排（含内存限额、健康检查、卷名钉死）
├── .env.example                  配置模板（复制成 .env）
├── pom.xml                       Maven 父 POM
├── jscreator-common/             公共库（响应信封、异常处理、工具）
├── jscreator-common-web/         Web 公共库（拦截器、鉴权注解 @RequireAdmin 等）
├── jscreator-gateway/            网关：路由 + CORS
├── jscreator-auth/               auth 服务（46 接口）
├── jscreator-content/            content 服务（37 接口）
├── jscreator-social/             social 服务（25 接口）
├── jscreator-system/             system 服务（4 接口）
├── apps/
│   ├── admin/                    后台前端（容器内 vite build → nginx）
│   └── blog/                     博客前端（同上）
├── deploy/
│   ├── deploy.sh                 一键：本机编译 → 建镜像 → 起容器
│   ├── Dockerfile.runtime        运行镜像（吃本机已编译好的 jar，默认）
│   ├── Dockerfile.app            多阶段：容器内编译（没装 JDK/Maven 时用）
│   ├── entrypoint.sh             按启动参数选 jar
│   └── mysql/init/*.sql          首次启动执行的 SQL（建表 + 种子，然后 ANALYZE）
└── scripts/
    ├── smoke_test.py             整栈冒烟 20 项
    ├── admin_page_probe.py       后台九页真实浏览器探测
    ├── blog_page_probe.py        博客首页探测
    ├── ref_env.sh                对照用：建库 / 起原版 Node / 清理
    ├── ref_diff.py               逐接口对照运行器
    ├── ref_cases/*.py            对照用例（按模块）
    ├── dev_service.sh            本机起单个服务（隔离库 + 指定端口）
    └── reset-passwords-dev.sh    开发用：把测试账号口令重置成已知值
```
