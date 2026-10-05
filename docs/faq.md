# 常见问题

## 已知问题（还没修）

| 问题 | 现状 | 说明 |
| :-- | :-- | :-- |
| `POST /upload/image` 成功分支未实现 | 配了 OSS 也会走失败分支 | 原版走 ali-oss（bucket `fast-node-server`、杭州 region、V4 签名）。对照用例覆盖的是失败分支与 401/400 校验分支；Java 侧检测到密钥存在会明确抛「OSS 上传实现待补」，**不会假装上传成功**。补齐只需按 OSS 的 PUT 实现。 |
| 后台「角色管理」页显示 No Data | 前端自身的问题 | `apps/admin/src/views/privateViews/RoleManage.vue` 读的是 `res.list`，后端给的是 `res.data.list`（同目录的 `UserManage.vue` 读对了）。**对着原 Node 后端也同样为空**；接口本身正常（`GET /api/role/list` 返回 3 个角色）。 |
| 手机上列多的表格偏挤 | 只做了布局兜底 | 断点 768px：侧边栏进抽屉、栅格单列、弹窗限宽；用户名这类长值仍会换行。改法两种：窄屏隐藏次要列（改动小），或列表页做卡片式布局。 |
| 图床前端（IMG）未迁入 | 不做 | 原 JScreator 里的图床前端没有搬过来。 |

## 常见问答

### 后台打开是 403

来源白名单在起作用。默认只放行 `127.0.0.1`、`::1`、`172.16.0.0/12`；从别的机器访问，要把自己的出口 IP 加进 `.env`：

```bash
ADMIN_ALLOW_HOSTS="127.0.0.1;::1;172.16.0.0/12;203.0.113.7"   # 注意加引号
docker compose up -d admin-web
```

### 页面能打开，但一登录就「失败」，接口其实是 200/403

跨域被网关拒了。把站点 origin（含端口/协议）加进 `.env` 的 `FRONTEND_URLS`，然后 `docker compose up -d gateway`。
另外确认反代用的是 `Host $http_host`（带端口），不是 `$host` —— 抹掉端口会让同源请求被判成跨域。

### Nacos 里看不到服务 / 网关返回 503

1. `docker compose ps` 看容器是否都 Up；
2. `docker compose logs -f auth` 看服务是否连上 `nacos:8848`；
3. 服务起得比 Nacos 慢是正常的，等十几秒再刷 Nacos 控制台；
4. **本机**（不在 compose 网络里）跑的服务必须关掉注册：`SPRING_CLOUD_NACOS_DISCOVERY_ENABLED=false`，
   因为 compose 只映射了 8848，gRPC 9848/9849 没有映射到宿主。

### 接口报 `1038 Out of sort memory`（或 500）

`/blog/feed`、`/blog/hot` 这类查询需要准确的统计信息。首次启动时 `deploy/mysql/init/02-analyze.sql` 会 `ANALYZE` 全库，
但**你后来手动导入 dump 后不会自动跑**。手动补一次：

```bash
docker exec inkwell-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" fastweb_test -e "ANALYZE TABLE article, comment, blog;"'
```

（也可以按 [deployment.md](deployment.md#4-数据) 里的整库分析。`docker-compose.yml` 已经把 `--sort-buffer-size` 提到 2M 作为兜底。）

### 端口被占用（`Bind for 0.0.0.0:80 failed`）

改 `.env` 里的 `WEB_PORT` / `BLOG_PORT` / `APP_PORT`，然后 `docker compose up -d`。
别忘了一并更新 `FRONTEND_URLS` 和 `ADMIN_URL`。

### 忘了管理员口令

口令是**无盐 SHA-256**，直接改库即可：

```bash
HASH=$(printf '%s' '你的新口令' | sha256sum | cut -d' ' -f1)
printf "UPDATE user SET password='%s' WHERE id=1;\n" "$HASH" | \
  docker exec -i inkwell-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" fastweb_test'
```

只是本地联调、不介意所有账号同口令的话，`scripts/reset-passwords-dev.sh [口令]` 会把全库口令统一成同一个
（⚠️ 别在对外环境跑）。

### 仓库里为什么没有数据库 dump？怎么用我自己的数据？

公开的 `deploy/mysql/init/01-schema.sql` 是**脱敏种子**（22 张表 + RBAC + 一个管理员），不含业务数据。
用你自己的备份导入即可，**导完记得 `ANALYZE`**：见 [deployment.md](deployment.md#4-数据)。

### 为什么要一份完整 dump 才能跑对照？

对照用例按**绝对 id** 引用资源（如 `PUT /article/update/86`）。种子库里没有这些行，跑出来就是满屏 `java=404` 的假失败。
细节见 [testing.md](testing.md)。

### 前端的 dist 在哪？为什么仓库里没有构建产物？

前端**在容器里构建**（多阶段 Dockerfile：`node` → `vite build` → `nginx:alpine`），仓库里只放源码。
想本地出产物：`cd apps/admin && npm ci && npm run build-only`（`npm run build` 会先跑 `vue-tsc`，
而这份历史代码的类型并不干净，容器里构建走的是跳过类型检查的 `npx vite build`）。

### 改了前端代码怎么生效

```bash
docker compose up -d --build admin-web     # 或 blog-web，或两个一起（不带参数）
```

**别在服务全开时构建**：这台机器只有 2 核，前端构建峰值 1G 以上，实测并行构建两个镜像要 27 分钟；
先 `docker compose stop` 再构建，或一次只建一个。

### 数据卷为什么叫 `jscreator_*`？

项目从 `jscreator-java` 改名为 `inkwell`，而 compose 默认会给卷加项目名前缀，不钉住就会新建两个空卷、库被重新初始化。
所以 `docker-compose.yml` 里用 `name:` 把卷名钉死成 `jscreator_mysql-data` / `jscreator_nacos-data`。
看到「卷与项目名不匹配」的 warning 属正常，**不要**去改卷名，除非你打算迁移数据。

### 模块名/包名/注册名为什么还叫 `jscreator`

它们是**已通过 613 项对照的运行时身份**（服务名同时写在网关的 `lb://jscreator-auth` 路由里），改名要重跑全套对照，
收益不划算。「Inkwell」只是对外的名字。

### 只部署后端 / 只部署前端可以吗

可以，compose 是分开的服务：`docker compose up -d nacos mysql gateway auth content social system` 只起后端；
前端单独部署时注意两点：nginx 要把 `/api` 反代到网关，且站点 origin 要在 `FRONTEND_URLS` 里。

### 支持多实例 / 集群吗

现状是**单机部署**：`docker compose` 里每个服务一个实例，MySQL、Nacos 都是单实例。
服务本身无状态（JWT 在客户端、验证码在服务内存里 —— 后者是原版行为，多实例下会不一致），
水平扩要先把「邮箱验证码存内存」这点改掉；Nacos 与 MySQL 也得换成集群形态。

### 时区、日志

- 容器统一 `TZ=Asia/Shanghai`。
- 日志：`docker compose logs -f <服务>`；对照用的原版 Node 日志在 `/tmp/jscreator-ref-<端口>.log`。
- 服务本身暴露 `/actuator/health`（例如 `curl 127.0.0.1:7000/actuator/health`）。

### 有 CI 吗

没有。对照那层依赖完整 dump 与原版代码，不适合公开 CI；要接的话先接冒烟那层（只需要 Docker），见 [testing.md](testing.md)。
