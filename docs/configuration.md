# 配置

所有配置都从项目根目录的 `.env` 读（`cp .env.example .env`）。compose 只读这个文件，**密钥不进镜像**。

## `.env` 逐项

### 运行必备

| 变量 | 默认 | 说明 |
| :-- | :-- | :-- |
| `DB_NAME` | `fastweb_test` | 数据库名 |
| `DB_USER` | `root` | 数据库用户 |
| `DB_PASSWORD` | 无（必填） | MySQL root 口令；首次建库就用它。compose 里是 `:?` 必填，不填直接报错退出 |
| `JWT_SECRET` | 无（必填） | token 签名密钥。**必须与原 Node 版一致**，否则存量 token 全部失效 |

### 端口

| 变量 | 默认 | 说明 |
| :-- | :-- | :-- |
| `APP_PORT` | 7000 | 网关对外端口（与原 Node 后端同端口） |
| `WEB_PORT` | 80 | 后台管理前端 |
| `BLOG_PORT` | 8080 | 博客前端 |
| `MYSQL_PORT` | 3308 | MySQL；只绑 `127.0.0.1`，避开宿主 3306 |
| `NACOS_PORT` | 8848 | Nacos 控制台；只绑 `127.0.0.1` |

### 站点地址与访问控制

| 变量 | 默认 | 说明 |
| :-- | :-- | :-- |
| `ADMIN_ALLOW_HOSTS` | `127.0.0.1;::1;172.16.0.0/12` | **后台来源白名单**，分号或逗号分隔的 IP/CIDR，名单外一律 403 |
| `ADMIN_URL` | `http://127.0.0.1/` | 博客首页「进入后台」链接指向的地址（前端构建期注入，改了要重建 `blog-web`） |
| `FRONTEND_URLS` | 本机 5173/8085 若干 | 网关的 **CORS 白名单**（逗号分隔）。换域名/端口后不同步 → 浏览器请求被拒（表现为「登录失败」） |

`ADMIN_ALLOW_HOSTS` 有两个坑：

- 值里带 `;` / `,`，**在 `.env` 里要加引号**：这个文件会被 `deploy/deploy.sh` `source` 进 shell，不加引号会变成
  `::1: command not found`。
- 它是**部署者的出口 IP**，属于本地配置，所以仓库里只有默认值；你的 IP 写在 `.env`（不进仓库）。
  改完 `docker compose up -d admin-web` 重建容器即生效（启动脚本会重新渲染 nginx 的 allow 列表）。

从别的机器访问后台的完整做法：

```bash
# .env
ADMIN_ALLOW_HOSTS="127.0.0.1;::1;172.16.0.0/12;203.0.113.7;198.51.100.0/24"
```

```bash
docker compose up -d admin-web      # 重建后台容器，白名单生效
```

### 外部服务（留空 = 走原版的失败分支）

留空不影响启动，也不影响其余接口；填上对应功能才会真正生效。

| 变量 | 影响的功能 | 填上之后 |
| :-- | :-- | :-- |
| `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | `POST /upload/image` | 阿里云 OSS 上传（bucket `fast-node-server`、杭州 region）。**注意**：Java 侧成功分支尚未实现，见 [faq.md](faq.md) |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_SECURE` / `SMTP_USER` / `SMTP_PASS` / `FROM_EMAIL` | `/email/send-code`、`/email/login` | 真发邮件验证码 |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` / `GITHUB_CALLBACK_URL` | `/auth/github*` | GitHub OAuth 登录/绑定 |
| `DEEPSEEK_API_KEY` / `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL` | `POST /article/ai-summary/regenerate/:id` | 调用 DeepSeek 生成文章摘要 |

### 构建期开关（不是 `.env` 项，是命令行环境变量）

| 变量 | 默认 | 说明 |
| :-- | :-- | :-- |
| `APP_DOCKERFILE` | `deploy/Dockerfile.runtime` | 换成 `deploy/Dockerfile.app` 让后端在容器里编译（没装 JDK/Maven 时用） |

```bash
APP_DOCKERFILE=deploy/Dockerfile.app docker compose up -d --build
```

## 容器与资源

宿主机 3.8G 且**没有 swap**，所以每个容器都有 `mem_limit`（在 `docker-compose.yml` 里）。实测占用：

| 容器 | 占用 / 限额 | 容器 | 占用 / 限额 |
| :-- | --: | :-- | --: |
| nacos | 525M / 900M | gateway | 210M / 320M |
| auth | 216M / 480M | content | 205M / 480M |
| social | 200M / 420M | system | 195M / 380M |
| mysql | 172M / 512M | admin + blog | 9M + 6M / 2×64M |
| **合计** | **≈ 1.75G** | | |

- Nacos 的堆锁在 256M（`JVM_XMS` / `JVM_XMX`），各 JVM 服务另有 `JAVA_TOOL_OPTIONS` 限制堆。
- **别在服务全开时跑 `vite build`**：前端构建峰值 1G 以上，先 `docker compose stop` 再构建更稳（这也是
  `deploy/deploy.sh` 建议在低负载时执行的原因）。
- 想压更低：调小各服务的 `mem_limit` 与 `JAVA_TOOL_OPTIONS` 里的 `-Xmx`；`system` 服务不吃内存，可压到 `-Xmx128m`。

## 容器、镜像与数据卷

| 名称 | 值 |
| :-- | :-- |
| compose 项目名 | `inkwell` |
| 容器名 | `inkwell-nacos` / `inkwell-mysql` / `inkwell-gateway` / `inkwell-auth` / `inkwell-content` / `inkwell-social` / `inkwell-system` / `inkwell-admin` / `inkwell-blog` |
| 镜像 | `inkwell-app:local`（五个后端服务共用）、`inkwell-admin:local`、`inkwell-blog:local` |
| 数据卷 | `jscreator_mysql-data`、`jscreator_nacos-data` |

数据卷名**故意钉死成 `jscreator_*`**（`docker-compose.yml` 里的 `name:`）：compose 默认给卷加项目名前缀，
项目从 `jscreator-java` 改名为 `inkwell` 后，不钉住就会新建两个空卷、库被重新初始化。看到「卷名与项目名不匹配」的
warning 属正常。

## MySQL 的非默认参数

`docker-compose.yml` 里给了数据库几个参数，其中一个是必须的：

- `--sort-buffer-size=2M`：`/blog/feed`、`/blog/hot` 这类「`GROUP BY` 含 `content` 这种 TEXT 列 + `ORDER BY`」的查询，
  统计信息不准时优化器会选到耗内存的计划，默认 256K 直接报 `1038 Out of sort memory`、接口变 500。实测 512K 即够。
- `--innodb-buffer-pool-size=128M`、`--max-connections=80`、`--performance-schema=OFF`：都在 4G 机器的预算内。

## 改配置后谁需要重建

compose 的 `environment` 只在**创建容器**时注入，改完要重建对应容器：

| 改了什么 | 执行 |
| :-- | :-- |
| `ADMIN_ALLOW_HOSTS` | `docker compose up -d admin-web` |
| `FRONTEND_URLS` | `docker compose up -d gateway` |
| `ADMIN_URL`（博客里的后台链接） | `docker compose up -d --build blog-web`（构建期注入，必须重建镜像） |
| 外部服务密钥（OSS/SMTP/GitHub/DeepSeek） | `docker compose up -d auth content` |
| 端口 | `docker compose up -d`（相关容器会重建） |
| `DB_*` | `docker compose up -d`；库内已有数据时改口令要同时改 MySQL 用户 |
