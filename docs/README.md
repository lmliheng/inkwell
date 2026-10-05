# Inkwell 文档

这里放「怎么做某事」的说明；[根目录 README](../README.md) 只留介绍和五分钟快速开始。

## 按目的找

| 我想… | 看这篇 |
| :-- | :-- |
| 把它跑起来 | [getting-started.md](getting-started.md) |
| 弄明白它由哪些服务组成 | [architecture.md](architecture.md) |
| 改端口、填密钥、配白名单 | [configuration.md](configuration.md) |
| 部署到服务器、开域名和 HTTPS | [deployment.md](deployment.md) |
| 改代码、加接口、前端本地调试 | [development.md](development.md) |
| 跑验收 / 和原 Node 版对照 | [testing.md](testing.md) |
| 确认接口行为没跑偏 | [compatibility.md](compatibility.md) |
| 排错 | [faq.md](faq.md) |

## 全部文档

| 文档 | 一句话 |
| :-- | :-- |
| [getting-started.md](getting-started.md) | 三种起法（一键脚本 / 只用 Docker / 逐步命令）、首次启动流程、日常运维命令、彻底重来 |
| [architecture.md](architecture.md) | 服务与端口、请求链路、Nacos 服务发现、数据边界、技术栈、目录结构 |
| [configuration.md](configuration.md) | `.env` 逐项说明、端口与内存限额、后台来源白名单、跨域、外部服务密钥 |
| [deployment.md](deployment.md) | 上服务器：反向代理与域名、安全加固清单、备份与恢复、升级、日志 |
| [development.md](development.md) | 后端编译与单服务调试、前端 `npm run dev`、加一个接口要走哪几步 |
| [testing.md](testing.md) | 冒烟、页面探测、逐接口对照 harness 的完整跑法与加用例方法 |
| [compatibility.md](compatibility.md) | 兼容红线表、照抄的原版怪癖、刻意保留的差异 |
| [faq.md](faq.md) | 已知问题与常见问答（403、注册失败、1038、端口冲突等） |
| [PLAN.md](../PLAN.md) | 移植方案、里程碑与踩坑记录（开发过程文档，不是使用文档） |

## 几个名词

- **Inkwell**：本仓库对外的名字。**JScreator**：上游的 Express + TypeScript 单体版，本项目的接口规格来源。
- **原版 / Node 版**：指 JScreator 的 Node 后端；「对照」即拿它和本版本逐接口比对。
- **对照 harness**：`scripts/ref_env.sh` + `scripts/ref_diff.py` + `scripts/ref_cases/*`，起原版、发同一组请求、逐字段比对。
- **dump**：带真实业务数据的完整数据库导出。仓库里**没有** dump，只有脱敏种子 `deploy/mysql/init/01-schema.sql`；
  跑对照需要自己准备一份完整 dump（见 [testing.md](testing.md)）。
- **种子**：首次启动时建 22 张表、灌入角色/权限和一个默认管理员的最小数据集。
