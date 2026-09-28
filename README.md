# Inkwell · 个人内容平台

把原来 Express + TypeScript 写的 JScreator 后端，重做成 **Spring Cloud 微服务**：
一个网关 + 四个业务域服务 + Nacos 服务发现 + MySQL，外带两个 Vue 3 前端（后台管理 / 博客），
`docker compose up -d --build` 一条命令把**后端和前端一起**起起来。

- 前端源码在 `apps/`：`apps/admin`（后台，Vue 3 + Naive UI/Element Plus）、`apps/blog`（博客，Vue 3 + Tailwind 4）
- 后端在 `jscreator-*` 模块：`gateway` / `auth` / `content` / `social` / `system`
- 接口行为以原 Node 后端为规格：**逐接口对照 613/613 一致**（详见「验收与对照」）

> 名字说明：仓库从 `jscreator-java` 改名而来。「Inkwell」是这一版对外的名字；
> 模块名、包名、Nacos 注册名仍保留 `jscreator-*` 前缀 —— 它们是已通过 613 项对照的运行时身份
> （服务名同时写在网关的 `lb://jscreator-auth` 路由里），改名牵动的是验证过的链路，收益不划算。

## 拓扑

```
                        ┌───────────────────────────────────────────────┐
浏览器 ──► admin-web :80 │ nginx 托管 apps/admin 产物，/api → gateway     │  （来源 IP 白名单）
       ──► blog-web :8080│ nginx 托管 apps/blog  产物，/api → gateway     │  （对外）
       ──► gateway :7000 │ 直连网关（与原 Node 后端同端口，前端零改动）   │
                        └──────────────┬────────────────────────────────┘
                                       │
        gateway :8088 ──────┬──────────┼───────────┬──────────────┐
                            │          │           │              │
                        auth:8090  content:8091  social:8092  system:8093
                     认证/用户/角色   文章/评论/博客  关注/点赞/私信  监控/备份
                      OAuth/API Key   上传/广告/公告   通知
                            └──────────┴───────────┴──────────────┘
                                       │
                                  MySQL 8 :3306（宿主 127.0.0.1:3308，22 张表，由 dump 初始化）

        五个服务 ──注册/发现──► Nacos :8848（宿主 127.0.0.1:8848，仅控制台用）
```

## 快速开始

前置：Docker + compose v2（`docker compose version` 有输出即可）。Debian 上还需 `apparmor`，
否则容器起不来（报 `AppArmor enabled on system but the docker-default profile could not be loaded`）：
`apt-get install -y apparmor apparmor-utils && systemctl restart docker`。

```bash
cp .env.example .env      # 填 DB_PASSWORD 与 JWT_SECRET（JWT_SECRET 要与原 Node 版一致，否则老 token 全失效）
deploy/deploy.sh          # 编译 → 构建镜像（后端 + 两个前端）→ 起容器
python3 scripts/smoke_test.py    # 冒烟：注册/登录/profile 共 20 项断言
```

等价的逐步命令：

```bash
mvn -B -DskipTests package                                    # 后端 jar
docker build -t inkwell-app:local -f deploy/Dockerfile.runtime .   # 后端运行镜像（吃上面的 jar）
docker compose build                                          # 前端镜像：容器里 npm ci + vite build
docker compose up -d
```

开箱后可访问：

| 入口 | 地址 | 说明 |
| :-- | :-- | :-- |
| 后台管理 | http://127.0.0.1/ | 端口取 `.env` 的 `WEB_PORT`；`admin` / `123456`；**有来源 IP 白名单**（见 `apps/admin/nginx.conf`） |
| 博客 | http://127.0.0.1:8080/ | 端口取 `BLOG_PORT`；对外站点，无白名单 |
| 网关 API | http://127.0.0.1:7000/ | 与原 Node 后端同端口，前端与第三方都打这里 |
| Nacos 控制台 | http://127.0.0.1:8848/nacos | 只绑本机；服务列表在「服务管理 → 服务列表」 |
| MySQL | 127.0.0.1:3308 | 只绑本机，避开宿主 3306 |

日常运维：

```bash
docker compose ps                                     # 状态
docker compose logs -f content                        # 某个服务的日志
docker compose up -d --build admin-web                # 只重建后台前端
docker compose stop / start                           # 停 / 起（数据卷保留，省内存）
docker compose down                                   # 删容器（数据卷仍在；加 -v 才会连库一起删）
```

首次 `up` 会拉 `nacos/nacos-server:v2.3.2`（约 820M）与 `node:24-bookworm-slim`（前端构建用），
网络慢可先 `docker pull` 预热。

### 内存

宿主机 3.8G 且**没有 swap**，所以每个容器都设了 `mem_limit`，实测（5 个 JVM + MySQL + Nacos + 两个 nginx）：

| 容器 | 占用 / 限额 | | 容器 | 占用 / 限额 |
| :-- | --: | :- | :-- | --: |
| nacos | 525M / 900M | | gateway | 210M / 320M |
| auth | 216M / 480M | | content | 205M / 480M |
| social | 200M / 420M | | system | 195M / 380M |
| mysql | 172M / 512M | | admin + blog | 9M + 6M / 2×64M |
| **合计** | **≈ 1.75G** | | | |

Nacos 堆锁在 256M（`JVM_XMS/JVM_XMX`）。容器限额总和略小于物理内存，
所以**别在服务全开时跑 `vite build`**：前端构建峰值 1G 以上，先 `docker compose stop` 再构建更稳。

## 前端（apps/）

两个前端都**在容器里构建**：多阶段 Dockerfile 先 `npm ci` + `vite build`，再把产物拷进 `nginx:alpine`。
仓库里不再有「先跑脚本产出 dist、再 bind mount 进容器」的环节。

| 应用 | 源码 | 镜像 | 对外 | 说明 |
| :-- | :-- | :-- | :-- | :-- |
| 后台 | `apps/admin` | `inkwell-admin:local` | `WEB_PORT`（默认 80） | 服务端渲染无关；`/api` 反代网关；来源 IP 白名单 |
| 博客 | `apps/blog` | `inkwell-blog:local` | `BLOG_PORT`（默认 8080） | 对外站点；`/api` 反代网关 |

**`apps/admin` 是把原来 `admin/` 与 `Admin/` 两半合并成的一份**。老仓库在 Windows 上编辑，
大小写不敏感，同一个目录被以两种拼写提交进 git；在 Linux 上 clone 会拆成两半且互补：
`admin/` 是脚手架（`index.html`、`vite.config.ts`、`tsconfig*`、`main.ts`、`router/`、`store/`、`i18n/`、`composables/`、`asset/main.css`），
`Admin/` 是业务（`package.json`、`.env.*`、`views/`、`components/`、`font/`）。两半无同名文件，合并后就是一个能直接 `npm run dev` 的完整应用。

要点：

- **接口基址**：`.env.production` 里 `VITE_API_BASE=/api`，由容器里的 nginx 去掉 `/api` 前缀转发到 `gateway:8088`；
  前端里不再出现 `127.0.0.1:7000`。开发态（`.env.development`）仍直连 `http://127.0.0.1:7000`。
- **那个没有 `VITE_` 前缀的变量**：历史源码里三处用的是 `import.meta.env.API_BASE`，Vite 不会替换它，
  所以 `apps/admin/vite.config.ts` 用 `define` 把它顶掉，值同样取自 `.env[.mode]`。
- **`nginx.conf` 里的 `Host` 必须是 `$http_host`（带端口）**：网关用 Spring 默认的 CORS 处理器，
  它按 scheme+host+port 判断是否为跨域请求。浏览器发往 `http://host:8080/api/...` 的 POST 带 `Origin: http://host:8080`，
  若把 `Host` 改写成不带端口的 `$host`，网关会把它当跨域请求、而该 origin 不在 `FRONTEND_URLS` 白名单里 → **403**（表现为「登录失败」）。
  已验证：同源 POST 200，伪造外部 Origin 403。
- **`resolver 127.0.0.11` + 变量式 `proxy_pass`**：nginx 默认只在启动时解析一次上游域名，
  网关容器重建换 IP 后会持续 502；用变量 + Docker 内嵌 DNS 后自动跟上。
- **后台的访问白名单**：`apps/admin/nginx.conf` 的 `allow/deny`（默认放行两个已知来源 IP、本机、`172.16.0.0/12` 私网段），
  其余一律 403。换网络/换手机号段时改这里再 `docker compose up -d --build admin-web`。
- **换域名/端口后**：站点的 origin 要进 `.env` 的 `FRONTEND_URLS`（否则网关按 CORS 拒掉），改完 `docker compose up -d gateway` 让网关重建（环境变量只在创建容器时生效）。

本地开发（不用容器）：

```bash
cd apps/admin && npm ci && npm run dev     # 5173，接口直连本机网关 7000
cd apps/blog  && npm ci && npm run dev     # 5173，同上
```

`apps/admin` 的 `npm run build` 会先跑 `vue-tsc` 类型检查，而这份历史代码的类型并不干净；
容器里构建走的是 `npx vite build`（跳过类型检查）。

## 服务发现（Nacos）

五个服务启动时把自己的 `spring.application.name` 注册到 Nacos，网关的路由目标写 `lb://jscreator-auth` 这类**服务名**而不是固定 IP，
新增实例（扩容、换端口、换机器）只要注册上来就能被网关发现，不用改配置、不用重启网关。

- 地址由 `NACOS_ADDR` 注入：容器里是 `nacos:8848`（compose 网络），本机开发默认 `127.0.0.1:8848`。
- Nacos 单机 standalone 模式，用内嵌存储（不依赖 MySQL）；控制台端口只绑 `127.0.0.1`，不对外，因此关掉了鉴权（`NACOS_AUTH_ENABLE=false`）。
- 可用性上不设硬依赖：网关找不到实例时按 `lb` 的默认行为返回 503，其余服务不受影响。
- 看注册了哪些实例：
  `curl -s '127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=jscreator-auth'`

## 数据库

- 首次启动（数据目录为空）时，`deploy/mysql/init/*.sql` 会自动执行：`01-schema.sql` 是完整 dump（22 张表 + 现有数据），`02-analyze.sql` 紧接着 `ANALYZE` 全库。
- MySQL 有一个非默认参数（见 `docker-compose.yml` 注释）：`--sort-buffer-size=2M`。
  `/blog/feed`、`/blog/hot` 这类「`GROUP BY` 含 `content` 这种 TEXT 列 + `ORDER BY`」的查询，
  统计信息不准时优化器会选到耗内存的计划，默认 256K 直接报 `1038 Out of sort memory`、接口变成 500。
- **往一个已经存在的库导入 dump 之后不会自动 `ANALYZE`**，这时手动跑一次：
  ```bash
  docker exec inkwell-mysql mysql -uroot -p"$DB_PASSWORD" <库名> -e "ANALYZE TABLE <表名>;"
  ```
- 备份：`GET /backup/download`（system 服务）导出 zip，内含每张表的建表 + 数据 SQL 与 README；
  实现不依赖 `mysqldump` 二进制，所以运行镜像里没装 mysql-client。

## 验收与对照

### 逐接口对照原版（移植的判据）

判据不是「能跑」，而是「和原 Node 版一模一样」：原版起在另一个端口、连一个从 dump 单独建的库，
两边跑同一组请求、逐字段比对。

```bash
bash   scripts/ref_env.sh db  fastweb_ref            # 从 dump 建对照库（口令统一 123456）
bash   scripts/ref_env.sh start fastweb_ref 7001     # 编译并起原版（日志 /tmp/jscreator-ref-7001.log）
python3 scripts/ref_diff.py --ref http://127.0.0.1:7001 --java http://127.0.0.1:7000
python3 scripts/ref_diff.py --list                   # 有哪些模块 / 用例
python3 scripts/ref_diff.py -v --cases rbac,user     # 只跑指定模块，打印响应体
bash   scripts/ref_env.sh clean fastweb_test         # 跑完清掉写进库里的残留
bash   scripts/ref_env.sh stop 7001
```

- 用例按模块放在 `scripts/ref_cases/<模块>.py`，格式见 `scripts/ref_cases/_spec.py`。
- 比对规则：状态码必须相同；响应体的**键集合**递归比对（含键序）；`token`/时间戳/`client_secret` 这类易变值跳过；
  列表响应用 `key=(路径, 主键字段)` 配对，容忍对照库里多出来的行。
- **残留**：`ref_diff` 与 `smoke_test` 会真的写库，跑完用 `ref_env.sh clean <db>` 清一次，两个库都要清。

最近一次全量结果（真实网关 + 5 个容器，两侧库都从 dump 全新重建）：

```
REF_SEED_DBS=fastweb_m1ref,fastweb_deployref python3 scripts/ref_diff.py \
    --ref http://127.0.0.1:7001 --java http://127.0.0.1:7000
# 结果：613/613 用例一致   （M1 214 + M2 251 + M3 139 + M4 9）
```

### 整栈冒烟与页面探测

```bash
python3 scripts/smoke_test.py                                   # 后端 20 项断言（跑完自动删临时账号）
python3 scripts/admin_page_probe.py                             # 后台九页：真实浏览器登录，页面发的 /api 必须全 2xx
python3 scripts/blog_page_probe.py                              # 博客首页：接口全 2xx、渲染出文章卡片、无 JS 报错
```

两个页面探测依赖 playwright，装在 `shared_env/webcheck/bin/python3`（不在项目依赖里）。

## 移植时必须守住的兼容性

| 约定 | 说明 |
| :-- | :-- |
| 路径 | 无全局前缀，`/sys/login`、`/article/list` 原样；网关按路径分派 |
| 响应信封 | 成功 `{code:200,success:true,message,data?}`；失败 HTTP 状态码 + `{code,success:false,message}`。**登录响应的 `token`/`user_info` 是顶层字段**，不放在 `data` 里 |
| 状态码怪癖 | 注册校验失败是 HTTP 200 + `code:400`；`/sys/profile` token 无效是 HTTP 200 + `code:500`；用户名登录异常是 HTTP 200 + `code:500`。照抄原版，前端按此实现 |
| JWT | HS256、载荷 `{id, role_id}`、7 天。`JWT_SECRET` 必须与原版一致，否则旧 token 全部失效 |
| 密码 | SHA-256 十六进制（原 `CryptoJS.SHA256`），不加盐。库里存量用户可直接登录 |
| 列名 | MyBatis 关掉了下划线转驼峰（`map-underscore-to-camel-case: false`），`role_name`、`checkinDay` 等原始列名直接进 JSON |

已知的、刻意的差异（都不影响前端）：

- `login_time` 由 `new Date().toLocaleString()` 改成固定的 `yyyy/M/d HH:mm:ss`（原输出随运行环境 locale 变）。
- CORS：原版对不在白名单的 Origin 是「不回 CORS 头」，Spring 会直接 403；浏览器行为一致。
- 时间字段：MySQL `DATETIME` 映射为 `LocalDateTime`，JSON 为 `2026-09-28T03:45:12`（Node 版是 UTC 的 `...Z`），浏览器按本地时间解析后显示一致。
- M2 `POST /article/ai-summary/regenerate/:id` 走 DeepSeek，`DEEPSEEK_API_KEY` 为空时与原版同样走失败分支。
- M4 `GET /system-monitor`：`data.system.arch`（Node `x64` / JVM `amd64`）、`hostname`（容器部署时是容器主机名）、
  `process.nodeVersion`（键名照抄，值是 JVM 版本）、`data.cpu.usage`（同样两次 `/proc/stat` 采样求差，首次为 `null`）。
  `data.memory.*` 读 `/proc/meminfo`：刻意不用 `OperatingSystemMXBean` —— JDK 会按 cgroup 限制读，
  服务在容器里拿到的是**容器配额**（例如 380M），监控页显示的就不是服务器内存了。
- M4 `GET /system-monitor/api-stats`：原版是单体、登记了全部 111 个接口；微服务版统计的是 **system 服务自身**的请求（口径随拆分而变）。
- M4 `GET /backup/download`：原版用 npm 的 `mysqldump` 包自己拼 SQL；这里改用 JDBC 读 `SHOW CREATE TABLE` + `SELECT *` 生成可重复导入的 SQL。
  两处刻意的不同：INSERT 用紧凑单行写法；原版先发响应头、中途出错只能断流，这里先生成完整产物、失败返回 500 信封。

## 已知问题与缺口

- **`POST /upload/image` 的成功分支未实现**（M2）。它要阿里云 OSS 的 AccessKey（原版走 ali-oss，bucket `fast-node-server`、杭州 region、V4 签名），
  没有密钥时两侧都停在 HTTP 500 +「上传失败，请检查 OSS 配置」。对照用例覆盖的是失败分支与 401/400 校验分支；
  Java 侧 `jscreator-content` 的 `UploadService.uploadToOss` 检测到密钥存在时会明确抛「OSS 上传实现待补」，**不会假装上传成功**。
  补齐只需按阿里 OSS 的 PUT 实现（bucket/region/对象路径已按原版写死），把 `OSS_ACCESS_KEY_ID/SECRET` 填进 `.env` 即可。
- **Admin「角色管理」页一直是 No Data**：`apps/admin/src/views/privateViews/RoleManage.vue:27` 读的是 `res.list`，
  而后端（原版与 Java 版返回一致）把列表放在 `res.data.list`，同目录的 `UserManage.vue` 读的就是 `res.data.list`。
  即这一页**对着原 Node 后端也一样是空的**，是前端自身的问题；改法是把那行对齐成 `res.data.list`。
  验证接口本身：登录拿 token 后 `GET /api/role/list` 能正常返回 3 个角色。
- **IMG 前台不迁**：原 JScreator 里的图床前端没有搬过来，需要的话另说。
- **手机上列多的表格仍偏挤**（用户名这类长值会换行，次要列要横向滚动）。两种改法：
  按页面在窄屏隐藏次要列（改动小），或列表页单独做卡片式布局（接近两套前端）。见 `PLAN.md` 的 M4 待定项。

## 不用容器的开发

```bash
mvn -DskipTests package
java -jar jscreator-auth/target/jscreator-auth-1.0.0.jar      # 各服务单独起
```

`scripts/dev_service.sh start <mod> <db> <port>` 会连隔离库、脱离当前会话起一个实例，方便与 Node 原版逐接口对照。
**本机实例要关掉 Nacos 注册**：compose 里的 Nacos 只把控制台端口 8848 映射到宿主，gRPC 端口（9848/9849）没映射，
本机服务注册不上会直接启动失败：

```bash
SPRING_CLOUD_NACOS_DISCOVERY_ENABLED=false \
  JAVA_TOOL_OPTIONS="-Xmx220m -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC" \
  scripts/dev_service.sh start system fastweb_m4dev 8105
```

（容器里不存在这个问题：五个服务与 nacos 在同一个 compose 网络里，走容器内的 9848。）

## 进度

| 阶段 | 状态 |
| :-- | :-- |
| M0 骨架（网关 + auth 登录/注册/profile + 四服务拓扑 + compose） | ✅ 已完成 |
| M1 认证授权域 46 接口（email/github/totp/user/rbac/oauth/api-key） | ✅ 已完成（对照原版 **214/214**） |
| Nacos 服务注册与发现（网关路由改 `lb://<服务名>`） | ✅ 已完成（无回归） |
| M2 内容域 37 接口（article/blog/comment/ad/announcement/upload） | ✅ 已完成（对照原版 **251/251**） |
| M3 互动域 25 接口（social/dm/notification） | ✅ 已完成（对照原版 **139/139**） |
| M4 系统域 4 接口（监控 / 接口统计 / 备份 / 根健康检查） | ✅ 已完成（对照原版 **9/9**） |
| 前端迁入 `apps/` 并随容器部署（后台合并两半 + 博客） | ✅ 已完成 |
| 全栈容器验收（613/613 对照 + 冒烟 + 两个前端页面探测） | ✅ 已完成 |
| M5 agent 模块 / 对外 openapi / 拆库 | ⬜ 可选（`agent` 在原版未挂路由，可不做） |

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
网关把 `/api/v1/**` 全部给 **auth**（它其实是 openapi 模块）；`jscreator-auth/pom.xml` 补 `spring-boot-starter-mail`。
