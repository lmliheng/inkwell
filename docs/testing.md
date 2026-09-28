# 验收

本项目的判据不是「能跑」，而是「**和原 Node 版逐字段一致**」。为此有布置了三层：

| 层 | 脚本 | 需要什么 | 看什么 |
| :-- | :-- | :-- | :-- |
| 冒烟 | `scripts/smoke_test.py` | 一套跑起来的栈 | 注册/登录/profile 共 20 项断言 |
| 页面探测 | `scripts/admin_page_probe.py`、`scripts/blog_page_probe.py` | 栈 + Playwright | 真实浏览器里页面发出的 `/api` 请求是否全 2xx、有无 JS 报错 |
| 逐接口对照 | `scripts/ref_env.sh` + `scripts/ref_diff.py` | 一套栈 + **一份完整 dump** + 原版 Node 代码 | 613 项用例，状态码 + 响应体键集合/键序/值 |

## 1. 冒烟

```bash
python3 scripts/smoke_test.py
# 结果：20 项通过，0 项失败
```

只依赖 Python 标准库。它会真写库（注册一个临时账号），跑完**自带清理**（用 admin 删掉临时账号）。

## 2. 页面探测

两个脚本用真实浏览器（Playwright）走一遍界面，要求「页面发出的每个 `/api` 请求都 2xx、控制台无 JS 报错」：

```bash
pip install playwright && playwright install chromium   # 一次即可

python3 scripts/admin_page_probe.py     # 后台九个页面（登录 admin/123456 后逐页访问）
python3 scripts/blog_page_probe.py      # 博客首页：接口全 2xx + 渲染出文章卡片
```

它们**不在项目依赖里**（只在验收时用）；用哪个解释器跑，那个解释器里要有 playwright。
注意后台探测需要白名单放行运行脚本的机器（脚本从本机访问，默认白名单已包含 `127.0.0.1`）。

## 3. 逐接口对照（核心验收）

### 前提：你需要一份完整 dump

对照用例按**绝对 id** 引用资源（例如 `PUT /article/update/86`），所以两侧的库必须**与线上同构同量**。
仓库里的 `deploy/mysql/init/01-schema.sql` 是**脱敏种子**（只有表结构 + RBAC + 一个管理员），
跑不了对照 —— 请自备完整 dump，放到本机 `/root/jscreator-full-dump.sql`（**不要进仓库**，`chmod 600`）。
`scripts/ref_env.sh` 会优先用它，找不到才退回种子：

```bash
# 从你现有的 Node 版数据库导出（容器里那个库）
docker exec inkwell-mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" fastweb_test' \
  > /root/jscreator-full-dump.sql && chmod 600 /root/jscreator-full-dump.sql
```

还需要原版 Node 后端的代码（对照的「规格」一侧）：默认路径 `/root/JScreator`，可用 `REPO=` 覆盖。

### 跑法

```bash
bash   scripts/ref_env.sh db  fastweb_ref                    # 从完整 dump 建对照库（库内口令统一 123456）
bash   scripts/ref_env.sh start fastweb_ref 7001             # 编译并起原版 Node（日志 /tmp/jscreator-ref-7001.log）
python3 scripts/ref_diff.py --ref http://127.0.0.1:7001 --java http://127.0.0.1:7000

python3 scripts/ref_diff.py --list                           # 有哪些模块/用例
python3 scripts/ref_diff.py -v --cases rbac,user             # 只跑指定模块并打印响应体
```

- 原版**必须起在 7001**：`ref_cases/oauth.py` 的 fixture 与重定向 URI 硬编码了 7001。
- 两侧的库都要**从 dump 全新重建**：不干净会让自增起点不同，M2 的用例按绝对 id 引用新资源，
  于是满屏 `java=404` 的**假失败**。
- 最近一次全量结果（真实网关 + 5 个容器，两侧库都从完整 dump 重建）：

  ```
  REF_SEED_DBS=fastweb_m1ref,fastweb_deployref python3 scripts/ref_diff.py \
      --ref http://127.0.0.1:7001 --java http://127.0.0.1:7000
  # 结果：613/613 用例一致   （M1 214 + M2 251 + M3 139 + M4 9）
  ```

### 清理

`ref_diff` 与 `smoke_test` 都会**真写库**（建文章、加关注、绑定 TOTP…），跑完两个库都要清：

```bash
bash scripts/ref_env.sh clean fastweb_test     # 清掉写进库里的残留
bash scripts/ref_env.sh clean fastweb_ref
bash scripts/ref_env.sh stop 7001              # 停掉原版
```

### 一条用例要用真实密钥时

`ref_cases/totp.py` 里「admin 用动态码登录成功」依赖库里 admin 的 TOTP 密钥 —— 那是真实用户的秘密，
不写进公开代码，跑之前用环境变量传进去，不给就只跳过这一条：

```bash
export REF_ADMIN_TOTP_SECRET=$(docker exec -i inkwell-mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT totp_secret FROM fastweb_test.user WHERE id=1"' 2>/dev/null)
```

### 比对规则

- 状态码必须相同。
- 响应体按**键集合**递归比对（含键序）；`token`、时间戳、`client_secret` 这类易变值跳过。
- 列表响应用 `key=(路径, 主键字段)` 配对，容忍对照库里多出来的行。

## 加用例

用例按模块放在 `scripts/ref_cases/<模块>.py`，格式见 `scripts/ref_cases/_spec.py`：

```python
case("登录：错误口令 → 401", "POST", "/sys/login", {"username": "admin", "password": "x"})
```

写完直接 `python3 scripts/ref_diff.py -v --cases <模块>` 跑。

## 为什么没有 CI

对照那层依赖「一份完整 dump + 原版 Node 代码」和一台能跑满 9 个容器的机器，不适合放进公开 CI。
想接 CI 的话，**冒烟那层最容易移植**（只需要 Docker）。

## 残留数据提醒

对照与冒烟都会写库。跑完记得 `ref_env.sh clean`；
如果不小心污染了主库（`fastweb_test`），最省事的恢复是 `docker compose down -v` 后重新起（会重新灌种子，业务数据丢失）。
