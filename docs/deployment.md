# 部署

面向「把 Inkwell 放到一台服务器上给人访问」。默认形态是**单机 Docker Compose**（仓库里没有 K8s 清单，也不打算有）。

## 1. 服务器准备

```bash
# Docker + Compose v2
curl -fsSL https://get.docker.com | sh
docker compose version

# Debian 系还需要 apparmor，否则容器起不来
apt-get install -y apparmor apparmor-utils && systemctl restart docker
```

- 内存：建议 ≥ 4 GB（9 个容器实测 ≈ 1.75 GB，构建时会更高）。
- 磁盘：≥ 5 GB 给镜像与数据卷。
- 不需要装 JDK/Maven：用 `APP_DOCKERFILE=deploy/Dockerfile.app` 让容器内编译（首次慢一些）。

```bash
git clone https://github.com/lmliheng/inkwell.git /opt/inkwell
cd /opt/inkwell && cp .env.example .env && vi .env
APP_DOCKERFILE=deploy/Dockerfile.app docker compose up -d --build
```

## 2. 上线前先改这几处

| 项 | 为什么 | 怎么做 |
| :-- | :-- | :-- |
| 管理员口令 | 种子里的 `admin` / `123456` 是公开值 | 登录后台 → 个人设置改密码（库里存 SHA-256，与原版一致） |
| `DB_PASSWORD`、`JWT_SECRET` | 默认留空，且必须足够随机 | `openssl rand -hex 32` 生成 `JWT_SECRET`；首次启动前设好 `DB_PASSWORD` |
| `ADMIN_ALLOW_HOSTS` | 默认只放行本机与 docker 私网段 | 把自己的出口 IP 写进去；不写就是「谁都打不开后台」 |
| `ADMIN_URL` | 博客页脚「进入后台」链接 | 改成后台的真实地址，然后重建 `blog-web` |
| `FRONTEND_URLS` | 跨域白名单 | 加上真实站点 origin（例如 `https://inkwell.example.com`），改完重建网关 |
| 对外端口 | 后台默认占 80 | 用反代/域名时通常把后台降到 127.0.0.1 或改端口（见下） |

## 3. 域名、HTTPS 与反向代理

三个入口都在本机监听，最常见的做法是用 nginx/Caddy 在前面接域名和证书：

| 入口 | 本机地址 | 建议对外 |
| :-- | :-- | :-- |
| 后台 | `127.0.0.1:${WEB_PORT}`（默认 80） | `https://admin.example.com/`（**建议加访问控制**） |
| 博客 | `127.0.0.1:${BLOG_PORT}`（默认 8080） | `https://inkwell.example.com/` |
| 网关 API | `127.0.0.1:${APP_PORT}`（默认 7000） | `https://inkwell.example.com/api/` |

nginx 示例（证书用 certbot 申请好）：

```nginx
# 博客 + 网关
server {
    listen 443 ssl;
    server_name inkwell.example.com;
    ssl_certificate     /etc/letsencrypt/live/inkwell.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/inkwell.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;        # blog-web
        proxy_set_header Host $http_host;         # 注意：要带端口/域名，别用 $host
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:7000/;        # 尾斜杠 = 去掉 /api 前缀
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}

# 后台（建议只给自己的 IP 访问）
server {
    listen 443 ssl;
    server_name admin.example.com;
    ssl_certificate     /etc/letsencrypt/live/admin.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/admin.example.com/privkey.pem;

    allow 203.0.113.7;    # 你的固定出口 IP
    deny all;

    location / {
        proxy_pass http://127.0.0.1:80;           # admin-web
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

两个必须注意的点：

1. **`Host` 用 `$http_host`，不要用 `$host`**。网关用 Spring 默认的 CORS 处理器，按 scheme+host+port 判断是否跨域；
   把端口抹掉会让同源请求被当成跨域，而该 origin 不在 `FRONTEND_URLS` 里 → **403**（前端表现为「登录失败」）。
2. 加了反代后，**站点 origin 必须进 `FRONTEND_URLS`**，改完 `docker compose up -d gateway`。否则同样是 403。

### 白名单在反代下的局限

后台的 `ADMIN_ALLOW_HOSTS` 是按 nginx 看到的**来源 IP** 放行的。如果请求先过本机反代，来源就永远是 `127.0.0.1`
（在默认白名单里），白名单等于失效。想要真正的限制，就在反代层限制（上面的 `allow/deny`），或者让后台干脆不对外：
把 `WEB_PORT` 的映射改成只绑本机（`127.0.0.1:${WEB_PORT:-80}:80`），只通过内网/跳板访问。

## 4. 数据

| 目的 | 做法 |
| :-- | :-- |
| 备份（不依赖 `mysqldump`） | 登录后台管理员 → 系统工具里下载，或直接 `GET /backup/download`（system 服务），得到 zip（每张表的建表 + 数据 SQL） |
| 备份（命令行） | `docker exec inkwell-mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" fastweb_test' > backup.sql` |
| 恢复自己的数据 | 见下面「导入已有 dump」 |
| 清空重来 | `docker compose down -v` 会连数据卷一起删，下次起会重新建表 + 灌种子 |

**导入已有 dump**（例如从原 Node 版迁移）：

```bash
# 导入（口令从容器环境里取，宿主 shell 不需要 source .env）
docker exec -i inkwell-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" fastweb_test' < backup.sql
# 导完必须 ANALYZE 一次，见下
```

> **导入后必须 `ANALYZE`**：首次启动时 compose 会执行 `deploy/mysql/init/02-analyze.sql`，但你手动导入一份新数据后
> **不会**自动分析统计信息，此时 `/blog/feed`、`/blog/hot` 这类查询可能选到错计划、报 `1038 Out of sort memory`（接口 500）。
> 整库分析一次（运行镜像是精简的 `mysql:8.0`，**没有 `mysqlcheck`**，所以用一段 SQL 生成并执行 `ANALYZE`）：
>
> ```bash
> docker exec -i inkwell-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" fastweb_test' <<'SQL'
> SELECT GROUP_CONCAT(CONCAT('`', table_name, '`')) INTO @t FROM information_schema.tables WHERE table_schema = DATABASE();
> SET @s = CONCAT('ANALYZE TABLE ', @t);
> PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
> SQL
> ```
>
> 只分析几张表也可以：
>
> ```bash
> docker exec inkwell-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" fastweb_test -e "ANALYZE TABLE article, comment, blog;"'
> ```
>
> 上面这些命令都会打印一行 `Using a password on the command line interface can be insecure`，是 mysql 客户端的例行提示，不影响结果。

口径提醒：**接口行为以原 Node 版为规格**，其中不少用例依赖库里有真实数据（例如评论按绝对 id 引用）。
只想看界面、不需要迁移数据的话，种子数据就能把流程点通。

## 5. 升级与回滚

```bash
cd /opt/inkwell
git pull
deploy/deploy.sh                 # 或：mvn -DskipTests package && docker compose up -d --build
```

- 数据卷不会被动；`deploy/mysql/init/*.sql` **只在数据目录为空时执行**，所以改表结构要自己写迁移 SQL。
- 回滚：`git checkout <旧 tag>` 后重新 `deploy.sh`；数据不会自动回滚，涉及表结构变更时要自己处理。
- 版本记录见 [Releases](https://github.com/lmliheng/inkwell/releases)。

## 6. 看日志与健康

```bash
docker compose ps                                  # 容器状态
docker compose logs -f --tail=200 gateway          # 网关日志
docker compose logs -f auth content social system  # 后端全部
curl -s http://127.0.0.1:7000/actuator/health      # 各服务都暴露 health/info
curl -s '127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=jscreator-auth'   # Nacos 里注册了什么
```

- 后台内置系统监控页：CPU/内存/接口统计/数据库备份（对应 `/system-monitor/**`）。
- 容器 `restart: unless-stopped`，机器重启后会自动起来（但 `docker compose stop` 过的容器不会）。

## 7. 稳定性注意

- **内存**：每个容器都有 `mem_limit`（见 [configuration.md](configuration.md)）。宿主机没有 swap 时，
  5 个 JVM + MySQL + Nacos 全开约 1.75G，剩余给系统和前端构建。
- **别在服务全开时构建前端**：`vite build` 峰值 1G 以上，2 核机器上并行构建两个前端会拖很久（实测 27 分钟），
  低峰期部署或先 `docker compose stop`。
- **Nacos 只绑本机**，控制台不对外；生产上如果做多实例，注意 9848/9849 gRPC 端口要通（本 compose 只在容器网络内暴露）。

## 8. 上线清单

- [ ] `DB_PASSWORD` / `JWT_SECRET` 已改成随机值
- [ ] 管理员口令已改掉默认的 `123456`
- [ ] `ADMIN_ALLOW_HOSTS` 或反代层已限制后台来源
- [ ] `ADMIN_URL`、`FRONTEND_URLS` 已按真实域名配好，且网关/博客已重建
- [ ] HTTPS 证书自动续期（certbot timer）已就绪
- [ ] 备份已跑通一次并落到别处（`GET /backup/download` 或 `mysqldump`）
- [ ] `docker compose ps` 九个容器都 Up，冒烟 `scripts/smoke_test.py` 20/20
