# JScreator 后端 · Java 微服务版

把 `JScreator/Backend`（Express + TypeScript，111 个接口 / 22 张表）移植成 Spring Boot 微服务，用 Docker Compose 整体部署。
设计方案与里程碑见 [PLAN.md](PLAN.md)。

## 拓扑

```
前端(Admin/Blog/IMG) ──► gateway :8088（宿主 7000，与原 Node 后端同端口）
                            ├── auth     :8090   认证/用户/角色/OAuth/API Key
                            ├── content  :8091   文章/评论/博客/上传/广告/公告
                            ├── social   :8092   关注/点赞/收藏/私信/通知
                            └── system   :8093   监控/备份
                                      └── MySQL 8（127.0.0.1:3308，22 张表，由现有 dump 初始化）

浏览器 ──► web :80（nginx 托管 Admin 构建产物，/api/* 反代到 gateway）
```

## 后台前端（Admin）

`web/` 目录负责把 `JScreator/Admin` 的前端构建成静态站点，由 nginx 容器托管，并按来源 IP 限制访问。

```bash
bash web/build.sh                     # 合并源码 → npm ci → vite build → 产物落在 web/dist
docker compose up -d web              # 起 nginx（宿主端口取 .env 的 WEB_PORT，默认 80）
WEB_PORT=31058 docker compose up -d web   # 临时换端口，临时值优先于 .env
```

- **为什么要“合并”**：仓库在 Windows 上编辑，git 里同时存在 `admin/` 与 `Admin/` 两条路径，Windows 不区分大小写所以是同一个目录；Linux 上 clone 后拆成两半且互补 ——
  `admin/` 是脚手架（`index.html`、`vite.config.ts`、`tsconfig*`、`main.ts`、`router/`、`store/`、`i18n/`、`composables/`、`asset/main.css`），
  `Admin/` 是业务（`package.json`、`.env.*`、`views/`、`components/`、`font/`）。`web/build.sh` 把两半合到 `web/src-app/` 再构建。
- **API 基址**：前端统一用同源相对路径 `/api`（`web/build.sh` 写进 `.env.production`，并用 vite `define` 注入源码里那个没有 `VITE_` 前缀的 `import.meta.env.API_BASE`），
  由 nginx `location /api/` 去掉前缀转发到 `gateway:8088`。前端里不再出现 `127.0.0.1:7000`。
- **访问控制**：`web/nginx.conf` 的 `allow/deny` 白名单（默认放行两个已知来源 IP + 本机 + compose 网桥），其余来源返回 403。
  改完 `docker compose restart web` 生效；手机 IP 变了要同步改这里。
- **端口**：宿主端口由 `.env` 的 `WEB_PORT` 决定（默认 80）。若 80 被别的站点占用，用 `WEB_PORT=31058` 之类（31058 已在 ufw 放行名单里）启动，或先停掉占用的服务。
- **跨域白名单**：浏览器的请求仍然带 `Origin`，所以站点的 origin 必须列进 `.env` 的 `FRONTEND_URLS`（否则网关按 CORS 拒掉，返回 403，浏览器里表现为「登录失败」）。
  换域名/端口/IP 后，改了 `FRONTEND_URLS` 要 `docker compose up -d gateway` 让网关重建（环境变量只在创建容器时生效）。
- 路由是 vue-router 的 history 模式，nginx 用 `try_files ... /index.html` 回落。

验证：

```bash
python3 scripts/smoke_test.py                      # 后端：注册/登录/profile 20 项断言（跑完自动删掉临时账号）
python3 scripts/check_admin_ui.py http://127.0.0.1:${WEB_PORT} admin 123456
                                                   # 前端：真实浏览器登录，/api/sys/login 与 /api/sys/profile 都要 200，并截图
python3 scripts/admin_page_probe.py http://127.0.0.1 admin 123456
                                                   # 前端：逐页翻 M1 页面，页面自己发的 /api 请求必须全 2xx
                                                   # （依赖 playwright：shared_env/webcheck/bin/python3 里已装好）
```

实测（2026-09-28，`http://127.0.0.1:31058` 与切到 80 后的 `http://127.0.0.1`）：`admin` 登录成功，主页渲染出超级管理员、42 篇文章、2 条评论、104 天签到、12 项权限（数据来自 Java 后端）。

`admin_page_probe.py` 会区分「已移植」与「未移植」：M1 页面（用户配置/用户管理/角色管理/权限管理/主页设置/API Keys/OAuth 应用）的请求全部 2xx；
只有 `notification/unread-count`（M3）、`system-monitor/*`（M4）、`article/*` 与 `blog/articles/admin`（M2）还是 404，属预期，脚本会单独列出来。

**nginx 的一个坑（已修）**：`web/nginx.conf` 里原来写的是 `proxy_pass http://gateway:8088`，nginx 只在启动时解析一次域名；
`docker compose up -d gateway` 重建网关后容器 IP 变了，nginx 还发往旧 IP，前端表现为 **502 / 全部接口异常**。
现在改成 `resolver 127.0.0.11 valid=10s` + 变量式 `proxy_pass $auth_upstream`，网关重建后 nginx 自己会跟上（不必重启 web，重启也无害）。

## 和原版逐接口对照（M1 起的验收方式）

移植的判据不是「能跑」，而是「和原版一模一样」。为此有一套对照环境：原版 Node 后端起在另一个端口、连一个从 dump 单独建的库，两边跑同一组请求、逐字段比对。

```bash
bash   scripts/ref_env.sh db  fastweb_ref          # 从 dump 建一个对照库（口令统一成 123456）
bash   scripts/ref_env.sh start fastweb_ref 7001   # 编译并起原版（日志 /tmp/jscreator-ref-7001.log）
bash   scripts/dev_service.sh start auth fastweb_ref 8099   # 可选：不进 Docker 直接起某个 Java 服务
python3 scripts/ref_diff.py --ref http://127.0.0.1:7001 --java http://127.0.0.1:7000
python3 scripts/ref_diff.py --list                 # 看有哪些模块 / 用例
python3 scripts/ref_diff.py -v --cases rbac,user   # 只跑指定模块，打印响应体
bash   scripts/ref_env.sh clean fastweb_test       # 跑完清掉写进库里的残留（见下）
bash   scripts/ref_env.sh stop 7001
```

- 用例按模块放在 `scripts/ref_cases/<模块>.py`，格式见 `scripts/ref_cases/_spec.py`；新增模块加一个文件即可。
- 比对规则：状态码必须相同；响应体的**键集合**递归比对；`token`/时间戳/`client_secret` 这类易变值跳过；列表响应用 `key=(路径, 主键字段)` 配对，
  容忍对照库里多出来的行。用例里的字段顺序也一并比对 —— 这正是抓出 `call-setters-on-nulls` 那类差异的原因。
- **残留**：`ref_diff` 与 `smoke_test` 会真的写库（新增角色/用户/API Key/OAuth 客户端/文章），用例自带还原的只有 role 3 的权限。
  跑完用 `ref_env.sh clean <db>` 清一次；两个库都要清，否则下次对照会因为「一边有、一边没有」而误报。

M1 的实测结果（2026-09-28）：把线上库 `fastweb_test` 完整复制成 `fastweb_deployref`，原版跑 7002 连它，
Java 侧走**真实网关 + 真实容器**（`http://127.0.0.1:7000`），

```bash
REF_SEED_DBS=fastweb_deployref,fastweb_test python3 scripts/ref_diff.py \
    --ref http://127.0.0.1:7002 --java http://127.0.0.1:7000
# 结果：214/214 用例一致
```

## 快速开始

### 依赖的前置条件

- JDK 17 + Maven（本机编译用）：`apt-get install -y openjdk-17-jdk-headless maven`
- Docker：`apt-get install -y docker.io`，compose v2 插件（`docker compose version` 能出结果即可）
- **Debian 上必须装 apparmor**：否则容器起不来，报
  `AppArmor enabled on system but the docker-default profile could not be loaded ... apparmor_parser ... not found`。
  装法：`apt-get install -y apparmor apparmor-utils && systemctl restart docker`

### 部署

```bash
cp .env.example .env      # 填 DB_PASSWORD 与 JWT_SECRET（JWT_SECRET 要与 Node 版一致）
deploy/deploy.sh          # 编译 → 构建镜像 → 起容器
python3 scripts/smoke_test.py   # 冒烟：注册/登录/profile 共 19 项断言
```

手动等价于：

```bash
mvn -B -DskipTests package
docker build -t jscreator-app:local -f deploy/Dockerfile.runtime .
docker compose up -d
```

> 五个服务共用同一个镜像（`jscreator-app:local`），构建一次、靠启动参数选 jar。
> `.dockerignore` 已排除 `.env` 等文件，密钥不会进镜像。
> 要从零在容器里编译（CI/换机器）就用 `deploy/Dockerfile.app`：`docker build -t jscreator-app:local -f deploy/Dockerfile.app .`
> （本机 2 核，容器内编译约十几分钟，所以日常用上面的运行镜像流程。）

日常运维：

```bash
docker compose ps            # 看状态
docker compose logs -f auth  # 看某个服务日志
docker compose stop          # 停掉全部（省内存，数据卷保留）
docker compose start         # 再起来
docker compose down          # 删容器（数据卷仍在；加 -v 才会连库一起删）
```

实测内存占用（5 个 JVM + MySQL，宿主机 3.8G）：

| 容器 | 占用 |
| :-- | --: |
| gateway | 171M |
| auth | 160M |
| content | 150M |
| social | 146M |
| system | 141M |
| mysql | 198M |
| **合计** | **≈ 966M** |

本机开发（不用 Docker）需要 JDK 17 + Maven：

```bash
mvn -DskipTests package
java -jar jscreator-auth/target/jscreator-auth-1.0.0.jar     # 各服务单独起
```

## 移植时必须守住的兼容性

| 约定 | 说明 |
| :-- | :-- |
| 路径 | 无全局前缀，`/sys/login`、`/article/list` 原样；网关按路径分派 |
| 响应信封 | 成功 `{code:200,success:true,message,data?}`；失败 HTTP 状态码 + `{code,success:false,message}`。**登录响应的 `token`/`user_info` 是顶层字段**，不放在 `data` 里 |
| 状态码怪癖 | 注册校验失败是 HTTP 200 + `code:400`；`/sys/profile` token 无效是 HTTP 200 + `code:500`；用户名登录异常是 HTTP 200 + `code:500`。这些都照抄原版，前端按此实现 |
| JWT | HS256、载荷 `{id, role_id}`、7 天。`JWT_SECRET` 必须与原版一致，否则旧 token 全部失效 |
| 密码 | SHA-256 十六进制（原 `CryptoJS.SHA256`），不加盐。库里存量用户可直接登录 |
| 列名 | MyBatis 关掉了下划线转驼峰（`map-underscore-to-camel-case: false`），`role_name`、`checkinDay` 等原始列名直接进 JSON |

已知的、刻意的差异（都不影响前端）：

- `login_time` 由 `new Date().toLocaleString()` 改成固定的 `yyyy/M/d HH:mm:ss`（原输出随运行环境 locale 变）。
- CORS：原版对不在白名单的 Origin 是「不回 CORS 头」，Spring 会直接 403；浏览器行为一致。
- 时间字段：MySQL `DATETIME` 映射为 `LocalDateTime`，JSON 为 `2026-09-28T03:45:12`（Node 版为 UTC 的 `...Z`），浏览器按本地时间解析后显示一致。

已知问题（**不属于本次移植范围**，与后端无关）：

- Admin 的「角色管理」页一直是 **No Data**：`Admin/src/views/privateViews/RoleManage.vue:27` 读的是 `res.list`，
  而后端（原版和 Java 版返回的都一样）把列表放在 `res.data.list`，同目录的 `UserManage.vue` 读的就是 `res.data.list`。
  也就是说这一页**对着原版 Node 后端也一样是空的**，是前端自身的问题；改法是把那行对齐成 `res.data.list`。
  验证时点开这一页看到空表不必怀疑接口：`curl -X POST /api/sys/login` 拿 token 后 `GET /api/role/list` 能正常返回 3 个角色。

## 进度

| 阶段 | 状态 |
| :-- | :-- |
| M0 骨架（网关 + auth 登录/注册/profile + 四服务拓扑 + compose） | ✅ 已完成 |
| M1 认证授权域 46 接口（email/github/totp/user/rbac/oauth/api-key） | ✅ 已完成（对照原版 **214/214** 用例一致） |
| M2 内容域 37 接口 | ⬜ 待做 |
| M3 互动域 25 接口 | ⬜ 待做 |
| M4 系统域 + 前端联调 | ⬜ 待做 |
| M5 agent 模块 / 对外 openapi / 拆库 | ⬜ 可选 |

### M1 交付了什么

| 模块 | 接口 | 说明 |
| :-- | :-- | :-- |
| auth | `/sys/login`、`/sys/register`、`/sys/profile`、`/resetPassword` | M0 已做 |
| email | `/email/send-code`、`/email/login` | 验证码存服务内存（与原版一致）；SMTP 未配置时走原版的失败分支 |
| github | `/auth/github`、`/auth/github/bind`、`/auth/github/callback` | 302 跳授权页/回调；`GITHUB_*` 未配时与原版同样失败 |
| totp | `/totp/status`、`/totp/setup`、`/totp/enable`、`/totp/disable`、`/totp/verify` | TOTP 自己实现（`window=0`，与原版一致） |
| user | `/userInfo`、`/user-manage/*`（list/detail/update/reset-password/delete/delete-batch） | 列表的键集合与键序与原版逐字段一致 |
| rbac | `/role/*`、`/permission/*` | 参数按原版「原样塞进 SQL」处理 |
| oauth | `/oauth/admin/clients/*`、`/oauth/authorize`、`/oauth/token` | 授权码 + PKCE + client_credentials 全支持 |
| openapi | `/api-keys/*`、`/api/v1/*` | API Key 鉴权 + scope 校验 |

配置上为了对齐原版补的三处：四个服务的 `application.yml` 加 `call-setters-on-nulls: true`（原版 mysql2 会把 NULL 列输出成 `键: null`，MyBatis 默认丢键）；
网关把 `/api/v1/**` 全部给 **auth**（原来误配到 content，而它其实是 openapi 模块）；`jscreator-auth/pom.xml` 补 `spring-boot-starter-mail`。
