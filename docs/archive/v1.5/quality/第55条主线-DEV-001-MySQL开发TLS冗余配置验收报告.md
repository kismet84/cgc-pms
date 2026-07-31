# 第55条主线 DEV-001：MySQL 开发 TLS 冗余配置验收报告

验收日期：2026-07-27

验收结论：`通过 / VerifiedResolved`

## 1. 目标与边界

- 目标：验证 `deploy/mysql/conf.d/ssl.cnf` 是否被 MySQL 8 使用，以及删除前后 TLS 正向、明文负向行为是否等价。
- 范围：固定 MySQL 8.0.46 镜像摘要、一次性 `tmpfs` 数据目录、三套 Compose 静态配置。
- 非目标：不连接生产，不复用或修改项目数据库卷，不重启现有 MySQL，不修改旧 migration，不把本地证据替代目标环境发布证据。

## 2. 运行证据

固定镜像：

```text
mysql@sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b
mysqld 8.0.46
```

现有 dev 容器及一次性“带配置”组均确认：

```text
World-writable config file '/etc/mysql/conf.d/ssl.cnf' is ignored.
```

一次性对照均使用 `--require-secure-transport=ON`，数据目录为临时 `tmpfs`，未挂项目网络和数据卷：

| 对照组 | 配置状态 | `@@require_secure_transport` | `@@have_ssl` | TLS REQUIRED | TLS cipher | TLS DISABLED |
| --- | --- | ---: | --- | --- | --- | --- |
| 带 `ssl.cnf` | `777`，被忽略 | 1 | YES | 成功 | `TLS_AES_256_GCM_SHA384` | 失败，安全传输门拒绝 |
| 无 `ssl.cnf` | 不存在 | 1 | YES | 成功 | `TLS_AES_256_GCM_SHA384` | 失败，安全传输门拒绝 |

两组行为等价，证明 MySQL 8 自动生成并使用 TLS 证书；该文件未生效且不提供额外安全能力。

首轮固定等待未命中最终服务 Ready，分类为 `environment_prerequisite`；改为等待官方 entrypoint 的 `MySQL init process done` 及最终 `port: 3306` 后一次复验通过。该失败不构成业务或安全缺陷。

## 3. 处置

- 删除 `deploy/mysql/conf.d/ssl.cnf`。
- 删除仅说明该废弃挂载的 `deploy/mysql/conf.d/README.md`。
- 删除 `docker-compose.yml`、`docker-compose.dev.yml`、`docker-compose.prod.yml` 中三处 `./mysql/conf.d:/etc/mysql/conf.d` 挂载。
- 保留生产 Compose 的 `--require-secure-transport=ON`；不改变 JDBC TLS 约束。

## 4. 验证

- 三套 `docker compose ... config --quiet` 通过。
- Compose 中 `mysql/conf.d`、`ssl.cnf` 引用为 0。
- 两个一次性容器均已删除，`cgc-pms.dev001=ephemeral` 残留为 0。
- 未修改项目数据库卷、旧 migration 或现有运行数据。

## 5. 风险与回滚

- 本证据只裁决冗余配置，不解除三项目标环境 `RELEASE_GATE`。
- 若后续镜像升级改变自动 TLS 行为，升级验收必须重新执行 REQUIRED/DISABLED 正负样本。
- 回滚只需恢复两个配置目录文件与三处 Compose 挂载；无需数据回滚。

## 6. 零悬空

- 新增后续项：0
- 关闭后续项：1（`DEV-001`）
- 后续项净变化：-1
- 无载体悬空项：0
