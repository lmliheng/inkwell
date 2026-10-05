
<img width="837" height="272" alt="{B422DFAE-9C24-445A-8C48-4A80C1A54D07}" src="https://github.com/user-attachments/assets/0788e3d1-4db2-433e-9774-ac8bd61c341e" />


[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)](docs/architecture.md)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.1-6db33f?logo=spring&logoColor=white)](docs/architecture.md)
[![Vue](https://img.shields.io/badge/Vue-3-42b883?logo=vuedotjs&logoColor=white)](docs/architecture.md)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479a1?logo=mysql&logoColor=white)](docs/architecture.md)
[![Docker Compose](https://img.shields.io/badge/Docker%20Compose-一条命令起全套-2496ed?logo=docker&logoColor=white)](docs/getting-started.md)
[![逐接口对照原版](https://img.shields.io/badge/逐接口对照-613%2F613-brightgreen)](docs/testing.md)
[![release](https://img.shields.io/badge/release-v1.0.2-blue)](https://github.com/lmliheng/inkwell/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

个人内容平台：**一个网关 + 四个业务域微服务 + Nacos 服务发现 + MySQL**，外带两个 Vue 3 前端（后台管理、博客）。，一条 `docker compose up -d --build` 把前后端一起起来。

> **只想知道怎么跑起来** → [快速开始](#快速开始)（三条命令）/ [docs/getting-started.md](docs/getting-started.md)

## 特性

- **按业务域拆成微服务**：`gateway` 路由 + `auth`（认证授权，46 接口）/ `content`（内容，37）/ `social`（互动，25）/ `system`（系统，4）
- **服务注册与发现**：Nacos + 网关按 `lb://<服务名>` 路由，加实例不用改配置、不用重启网关
- **前后端一起容器化**：前端源码在 `apps/`，容器内 `vite build` + nginx 托管并反代 `/api`，仓库里没有构建产物
- **契约与原版逐字段一致**：键集合、键序、状态码怪癖都照抄，前端零改动即可切过来（见 [兼容性](docs/compatibility.md)）
- **可自证的验收**：冒烟 20 项 + **逐接口对照 harness**（613 项）+ 两个真实浏览器页面探测（见 [验收](docs/testing.md)）
- **部署友好**：后台来源白名单、站点地址、外部密钥全部外置成环境变量；首次启动自动建表 + 灌最小种子数据

## 架构

```
浏览器 ──► admin-web :80   nginx 托管 apps/admin，/api → gateway   （来源 IP 白名单）
       ──► blog-web  :8080 nginx 托管 apps/blog ，/api → gateway   （对外）
       ──► gateway   :7000 直连网关（与原 Node 后端同端口，前端零改动）
                    │
        gateway :8088 ──┬──────────┬──────────┬──────────┐
                        │          │          │          │
                    auth:8090  content:8091 social:8092 system:8093
                 认证/用户/角色  文章/博客/评论 关注/点赞/私信 监控/接口统计/备份
                        └──────────┴──────────┴──────────┘
                    │                                 │
              MySQL 8 :3306（宿主 127.0.0.1:3308）   Nacos :8848（宿主 127.0.0.1:8848）
```

完整架构、请求链路与目录结构见 [docs/architecture.md](docs/architecture.md)。

## 快速开始

前置：**Docker + Compose v2**（`docker compose version` 有输出即可），以及 **JDK 17 + Maven**（默认在本机编译后端）。
没有 JDK/Maven 也行，让容器内编译即可（首次会下 Maven 依赖，慢一些）：

```bash
git clone https://github.com/lmliheng/inkwell.git && cd inkwell
cp .env.example .env          # 只必填两项：DB_PASSWORD、JWT_SECRET
deploy/deploy.sh              # 编译 → 构建镜像 → 起容器

# 没装 JDK/Maven 时，把最后一条换成：
# APP_DOCKERFILE=deploy/Dockerfile.app docker compose up -d --build
```

首次会拉 `mysql` / `nacos-server` / `node` 等镜像并构建两个前端，**等 3–10 分钟**，然后：

| 入口 | 地址 | 说明 |
| :-- | :-- | :-- |
| 后台管理 | http://localhost/ | 默认管理员 **`admin` / `123456`**，**登录后第一件事就是改掉** |
| 博客 | http://localhost:8080/ | 对外站点，无来源限制 |
| 网关 API | http://localhost:7000/ | 前端与第三方都打这里 |
| Nacos 控制台 | http://127.0.0.1:8848/nacos | 只绑本机，看「服务管理 → 服务列表」 |

确认装好了：

```bash
docker compose ps                 # 9 个容器都 Up
python3 scripts/smoke_test.py     # 冒烟 20 项，应输出「20 项通过，0 项失败」
```

两个默认值先改一下再对外：`ADMIN_ALLOW_HOSTS`（后台来源白名单，默认只有本机与 docker 私网段能打开后台）
和站点地址 `ADMIN_URL` / `FRONTEND_URLS`。逐项说明见 [docs/configuration.md](docs/configuration.md)。

遇到起不来、端口被占、内存不够、登录 403 这类问题：[docs/faq.md](docs/faq.md)。

## 文档

| 文档 | 内容 |
| :-- | :-- |
| [docs/getting-started.md](docs/getting-started.md) | 快速开始（详细版）：三种起法、首次启动都发生了什么、日常运维命令、彻底重来 |
| [docs/architecture.md](docs/architecture.md) | 架构：服务与端口、请求链路、服务发现、数据边界、技术栈、目录结构 |
| [docs/configuration.md](docs/configuration.md) | 配置：`.env` 逐项、端口、内存限额、后台白名单、跨域 |
| [docs/deployment.md](docs/deployment.md) | 部署到服务器：域名与 HTTPS、安全加固清单、备份与恢复、升级 |
| [docs/development.md](docs/development.md) | 本地开发：后端编译与单服务调试、前端 dev、加一个接口要走哪几步 |
| [docs/testing.md](docs/testing.md) | 验收：冒烟、页面探测、逐接口对照 harness 的完整跑法 |
| [docs/compatibility.md](docs/compatibility.md) | 与原 Node 版的兼容红线、照抄的怪癖、刻意保留的差异 |
| [docs/faq.md](docs/faq.md) | 已知问题与常见问答 |
| [PLAN.md](PLAN.md) | 移植方案与里程碑记录（开发过程文档） |

## 验收

三层，都能一条命令重跑（细节见 [docs/testing.md](docs/testing.md)）：

```bash
python3 scripts/smoke_test.py            # 整栈冒烟 20/20
python3 scripts/admin_page_probe.py      # 后台九个页面：真实浏览器，页面发出的请求必须全 2xx
python3 scripts/blog_page_probe.py       # 博客首页：接口全 2xx、渲染出文章卡片、无 JS 报错
```

```bash
bash   scripts/ref_env.sh db  fastweb_ref          # 从完整 dump 建对照库
bash   scripts/ref_env.sh start fastweb_ref 7001   # 起原版 Node 后端
python3 scripts/ref_diff.py --ref http://127.0.0.1:7001 --java http://127.0.0.1:7000
# 最近一次：613/613 一致（M1 214 + M2 251 + M3 139 + M4 9）
```

## 许可

[MIT](LICENSE) © 2026 lmliheng —— 可自由使用、修改、分发（保留版权与许可声明即可），软件按「原样」提供、不含任何担保。
