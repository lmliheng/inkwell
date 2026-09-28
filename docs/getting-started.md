# 快速开始

目标：从零到「后台能登录、博客能打开」。**只想跑起来的话，三条命令就够**；本文把每一步摊开讲，并附排错。

## 0. 你需要什么

| 项 | 要求 | 说明 |
| :-- | :-- | :-- |
| Docker | 20.10+ | `docker --version` |
| Docker Compose | v2（`docker compose version`） | 是 `docker compose` 插件，不是老的 `docker-compose` 脚本 |
| 磁盘 | 约 5 GB | 镜像 + 数据卷（MySQL 数据、Nacos 数据） |
| 内存 | 建议 ≥ 4 GB | 9 个容器实测合计约 1.75 GB，构建时会更高 |
| JDK + Maven | 17 + 3.8+，**可选** | 默认在本机编译后端；没装就用容器内编译（见 [方式 B](#b-只有-docker没装-jdkmaven)） |

Debian 系还需要 `apparmor`，否则容器起不来（报 `AppArmor enabled on system but the docker-default profile could not be loaded`）：

```bash
apt-get install -y apparmor apparmor-utils && systemctl restart docker
```

## 1. 拿代码

```bash
git clone https://github.com/lmliheng/inkwell.git
cd inkwell
```

## 2. 配置 `.env`

```bash
cp .env.example .env
```

只**必填两项**，其余留空也能起来：

| 变量 | 必填 | 说明 |
| :-- | :-- | :-- |
| `DB_PASSWORD` | ✅ | MySQL root 口令，首次建库就用它 |
| `JWT_SECRET` | ✅ | 签发 token 的密钥；**必须与原 Node 版一致**，否则已登录用户会掉线 |
| `WEB_PORT` / `BLOG_PORT` / `APP_PORT` | | 默认 80 / 8080 / 7000；80 被占用时改这里 |
| `ADMIN_ALLOW_HOSTS` | | 后台来源白名单，默认只放行本机与 docker 私网段；**不带引号会报 `::1: command not found`**，值里带分隔符就加引号 |
| `ADMIN_URL` | | 博客首页「进入后台」链接的目标，换域名时改 |
| `FRONTEND_URLS` | | 跨域白名单；换域名/端口后要同步，否则前端请求被网关按 CORS 拒成 403 |

逐项说明见 [configuration.md](configuration.md)。

## 3. 起服务

### A. 一键脚本（有 JDK 17 + Maven，推荐）

```bash
deploy/deploy.sh
```

它按三步做：本机 `mvn -DskipTests package` → 构建后端运行镜像 → `docker compose up -d --build`（含两个前端）。
本机编译好后，镜像构建只剩拷贝，几秒钟完成；**首次**主要等前端 `npm ci` + `vite build` 和镜像拉取。

### B. 只有 Docker（没装 JDK/Maven）

后端改为在容器里编译（多阶段构建 `deploy/Dockerfile.app`，首次要下载 Maven 依赖，慢一些）：

```bash
cp .env.example .env
APP_DOCKERFILE=deploy/Dockerfile.app docker compose up -d --build
```

### C. 逐步命令（想弄清每步在做什么）

```bash
cp .env.example .env
mvn -B -DskipTests package                                          # 1. 后端 jar（Java 17 + Maven）
docker build -t inkwell-app:local -f deploy/Dockerfile.runtime .    # 2. 后端运行镜像（吃上面的 jar）
docker compose build                                                # 3. 两个前端镜像（容器内 npm ci + vite build）
docker compose up -d                                                # 4. 起全部容器
```

**首次大概 3–10 分钟**（看网速和 CPU）：要拉 `mysql:8.0`、`nacos/nacos-server:v2.3.2`、`node:24-bookworm-slim`
等镜像，并构建两个前端。可以先 `docker pull` 预热。

## 4. 确认起来了

```bash
docker compose ps                  # 9 个容器：nacos / mysql / 5×应用 / admin-web / blog-web
python3 scripts/smoke_test.py      # 冒烟 20 项，成功时输出「20 项通过，0 项失败」
```

可以直接 curl 一下网关（登录响应的 `token` 是**顶层字段**，这是原版契约）：

```bash
curl -s http://127.0.0.1:7000/sys/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}'
```

浏览器打开：

| 入口 | 地址 | 说明 |
| :-- | :-- | :-- |
| 后台管理 | http://localhost/ | 默认 `admin` / `123456`，**登录后第一件事就是改掉**；有来源白名单 |
| 博客 | http://localhost:8080/ | 对外站点，无来源限制 |
| 网关 API | http://localhost:7000/ | 前端与第三方都打这里 |
| Nacos 控制台 | http://127.0.0.1:8848/nacos | 只绑本机；「服务管理 → 服务列表」里应有 5 个服务 |

> 后台打不开、只显示 403？这是**来源白名单**在起作用：默认只放行本机与 `172.16.0.0/12`。
> 从别的机器访问时，把自己的出口 IP 加进 `.env` 的 `ADMIN_ALLOW_HOSTS`，再 `docker compose up -d admin-web`。

## 首次启动都发生了什么

1. **拉镜像**：mysql、nacos、（前端构建用的）node。
2. **MySQL 初始化**：数据目录为空时，容器按文件名顺序执行 `deploy/mysql/init/*.sql` ——
   `01-schema.sql` 建 22 张表并灌最小种子（角色/权限 + 管理员 `admin`/`123456`），`02-analyze.sql` 紧接着 `ANALYZE` 全库。
3. **Nacos 起来**（standalone，内嵌存储，控制台只绑 `127.0.0.1`）。
4. **五个服务注册上来**（`gateway`/`auth`/`content`/`social`/`system`），网关按服务名 `lb://jscreator-auth` 路由。
5. **两个前端**在容器里构建好后由 nginx 托管，`/api` 反代到网关。
6. 之后每次启动都跳过 2（数据卷里有库了）。

种子数据里**没有业务数据**：文章、评论、私信都是空的。要恢复自己的数据，见 [deployment.md](deployment.md#4-数据)。

## 日常命令

| 目的 | 命令 |
| :-- | :-- |
| 看状态 | `docker compose ps` |
| 看某服务日志 | `docker compose logs -f content` |
| 只重建后台前端 | `docker compose up -d --build admin-web` |
| 改完后端代码重新部署 | `deploy/deploy.sh`（或 `mvn -DskipTests package && docker compose up -d --build gateway auth content social system`） |
| 停服务（保留数据） | `docker compose stop` |
| 再起 | `docker compose start` |
| 删容器（数据卷还在） | `docker compose down` |
| 连数据一起删（**不可逆**） | `docker compose down -v` |
| 拉最新代码后升级 | `git pull && deploy/deploy.sh` |

## 端口被占用

`.env` 里改三个对外端口即可：`WEB_PORT`（后台）、`BLOG_PORT`（博客）、`APP_PORT`（网关）。
MySQL 与 Nacos 只绑 `127.0.0.1`，端口分别是 `MYSQL_PORT`（默认 3308，避开宿主 3306）和 `NACOS_PORT`（8848）。

改完执行 `docker compose up -d`（端口变化会让相关容器重建）。**同时要**把新地址写进 `FRONTEND_URLS`
（例如 `http://192.168.1.10:8080`），否则前端请求会被网关按 CORS 拒掉。

## 彻底重来

```bash
docker compose down          # 停并删容器，数据卷保留
docker compose down -v       # 连数据卷一起删：下次 up 会重新建库 + 灌种子
docker volume ls | grep jscreator   # 数据卷名是 jscreator_mysql-data / jscreator_nacos-data（历史原因，见 configuration.md）
```

只想重置数据库、保留 Nacos 数据：

```bash
docker compose down
docker volume rm jscreator_mysql-data
docker compose up -d
```

## 起不来时先按这个顺序看

| 现象 | 多半是 | 怎么办 |
| :-- | :-- | :-- |
| `AppArmor … could not be loaded` | Debian 缺 apparmor | 见 [第 0 节](#0-你需要什么) |
| `Error response from daemon: … no space left` | 磁盘满了 | `docker system prune -a`，或换机器 |
| 容器反复重启、`docker compose ps` 里没有 Up | 内存不够（宿主机 3.8G 且无 swap 时最容易） | 先 `docker compose stop` 再构建；或调低各服务的 `mem_limit` |
| `Bind for 0.0.0.0:80 failed: port is already allocated` | 80 被别的服务占了 | 改 `.env` 的 `WEB_PORT` |
| 后台 403 | 来源白名单 | 把出口 IP 加进 `ADMIN_ALLOW_HOSTS` |
| 前端能开、接口 502 | 网关还没起好或服务没注册 | `docker compose logs -f gateway nacos`，等 Nacos 里出现 5 个服务 |
| 登录「失败」但接口 200 | 跨域被拒（前端站点不在 `FRONTEND_URLS`） | 把站点 origin 加进 `FRONTEND_URLS` 后重建网关 |
| `1038 Out of sort memory` | 库导入了 dump 但没 ANALYZE | 见 [deployment.md](deployment.md#4-数据) |

更多见 [faq.md](faq.md)。

## 下一步

- 要放到公网：看 [deployment.md](deployment.md)（反代、域名、HTTPS、安全加固清单）。
- 要改代码：看 [development.md](development.md)。
- 想确认接口没跑偏：看 [testing.md](testing.md)。
