# CGC-PMS 云服务器部署总结报告

> 部署日期：2026-06-30
> 项目：CGC-PMS（建筑工程总包项目全过程管理系统）
> 服务器：阿里云 ECS

---

## 1. 部署背景

### 1.1 项目简介

CGC-PMS 是面向建筑工程总包项目的全过程管理系统，覆盖项目、合同、成本、采购库存、分包、付款、发票、审批、预警和经营驾驶舱等核心业务模块。技术栈为 Spring Boot 3.3 + Vue 3 + MySQL 8 + Redis 7 + MinIO，采用 Docker Compose 全容器化部署。

### 1.2 部署目标

将 CGC-PMS 从本地开发环境部署到阿里云 ECS 生产服务器，实现公网可访问的全功能运行环境，包括后端 API、前端 SPA、数据库、缓存和对象存储服务。

### 1.3 服务器规格

| 项目 | 规格 |
|------|------|
| 云服务商 | 阿里云 ECS |
| 操作系统 | Ubuntu 22.04 LTS |
| CPU | 4 vCPU |
| 内存 | 7.1 GiB |
| 系统盘 | 40 GB |
| 公网 IP | 121.41.73.124 |
| 域名（前端） | cgc.bzywc.cn |
| 域名（宝塔面板） | bt.bzywc.cn |

---

## 2. 服务器环境

### 2.1 初始环境

服务器为阿里云 ECS 全新实例，预装 Ubuntu 22.04 LTS 操作系统。部署前确认以下基础环境就绪：

- SSH 远程连接正常，使用密钥对认证
- Docker 已安装（版本 29.6.1）
- Docker Compose 已安装（版本 v5.2.0）
- 宝塔面板（BT Panel）已安装并运行，用于 Nginx 反向代理和 SSL 管理

### 2.2 安全组配置

阿里云安全组开放以下端口：

| 端口 | 用途 | 源 |
|------|------|----|
| 22 | SSH | 受限 IP |
| 80 | HTTP | 0.0.0.0/0 |
| 443 | HTTPS | 0.0.0.0/0 |
| 8916 | 宝塔面板 | 受限 IP |

---

## 3. 部署架构

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                        阿里云 ECS (121.41.73.124)                     │
│                                                                     │
│  ┌──────────┐   HTTPS:443    ┌──────────────────────────────────┐   │
│  │  用户浏览器 │ ──────────────▶ │      宝塔面板 Nginx             │   │
│  └──────────┘                 │  (反向代理 + SSL 终结)           │   │
│                               │                                  │   │
│  cgc.bzywc.cn ────────────────▶ │  server_name cgc.bzywc.cn      │   │
│  bt.bzywc.cn ────────────────▶ │  server_name bt.bzywc.cn:8916  │   │
│                               └──────┬───────────────────────────┘   │
│                                       │                              │
│                         cgc.bzywc.cn  │  反代到 127.0.0.1:8081      │
│                                       ▼                              │
│                               ┌──────────────────────────────────┐   │
│                               │      Frontend Nginx (Docker)      │   │
│                               │  容器内端口 :80 / :443             │   │
│                               │  宿主机映射 127.0.0.1:8081:80     │   │
│                               │                                  │   │
│                               │  / → SPA 静态文件                 │   │
│                               │  /api/* → 反向代理到 backend      │   │
│                               └──────────┬───────────────────────┘   │
│                                          │                           │
│                              Docker 内部网络 (cgc-pms-net)           │
│                                          │                           │
│                               ┌──────────▼───────────────────────┐   │
│                               │  Backend (Spring Boot)           │   │
│                               │  :8080 (容器内)                   │   │
│                               │  JVM: -Xms512m -Xmx1g            │   │
│                               └──┬──────┬──────┬─────────────────┘   │
│                                  │      │      │                     │
│                    ┌─────────────┘      │      └─────────────┐       │
│                    ▼                    ▼                    ▼       │
│          ┌─────────────────┐  ┌──────────────┐  ┌─────────────────┐  │
│          │  MySQL 8.0      │  │  Redis 7      │  │  MinIO          │  │
│          │  :3306 (容器内)  │  │  :6379 (容器内)│  │  :9000 (容器内)  │  │
│          │  512M 内存限制   │  │  256M 内存限制 │  │  512M 内存限制   │  │
│          └─────────────────┘  └──────────────┘  └─────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 关键设计决策

- **所有基础设施端口不对外暴露**：MySQL、Redis、MinIO 仅监听 Docker 内部网络（`cgc-pms-net`），宿主机也无法直连，仅 backend 通过 Docker DNS 名称访问
- **前端 Nginx 宿主机端口映射为 127.0.0.1:8081**：仅本机反代可访问，不暴露到公网
- **SSL 终结在宝塔面板 Nginx**：由宝塔统一管理 HTTPS 证书和反向代理，前端容器内 Nginx 也配置了自签证书作为降级方案
- **预检容器（preflight）**：启动前强制校验必须的环境变量和 SSL 证书文件，避免因配置遗漏导致启动后不可用

### 3.3 容器角色说明

| 容器名 | 镜像 | 角色 | 内存限制 |
|--------|------|------|----------|
| cgc-pms-preflight | busybox:1.36 | 启动前预检（退出型容器） | - |
| cgc-pms-mysql | mysql:8.0 | 关系型数据库 | 512M |
| cgc-pms-redis | redis:7-alpine | 缓存 + Token 黑名单 | 256M |
| cgc-pms-minio | minio/minio | 对象存储 | 512M |
| cgc-pms-backend | cgc-pms-backend:deploy | Spring Boot API 服务 | 1G |
| cgc-pms-frontend | cgc-pms-frontend:deploy | Nginx SPA + 反代 | 128M |

---

## 4. 完整实施步骤

### 4.1 准备阶段

**步骤 1：SSH 连接并配置密钥对**

```bash
ssh root@121.41.73.124
# 配置 SSH 密钥对，禁用密码登录
```

确认基础环境：

```bash
docker --version          # Docker 29.6.1
docker compose version    # Docker Compose v5.2.0
```

**步骤 2：克隆代码仓库**

```bash
cd /opt
git clone https://github.com/kismet84/cgc-pms.git
cd cgc-pms
```

### 4.2 配置阶段

**步骤 3：配置 .env 环境变量**

```bash
cd deploy
cp .env.example .env
```

编辑 `.env`，替换所有占位值为随机生成的强密码：

- `MYSQL_ROOT_PASSWORD`：数据库 root 密码
- `MYSQL_PASSWORD`：应用数据库用户密码
- `REDIS_PASSWORD`：Redis 密码
- `JWT_SECRET`：JWT 签名密钥（256 位以上）
- `JASYPT_ENCRYPTOR_PASSWORD`：Jasypt 配置加密密钥
- `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`：MinIO 管理员凭据
- `CORS_ALLOWED_ORIGINS`：允许的跨域来源，设置为 `https://cgc.bzywc.cn`

### 4.3 构建阶段

**步骤 4：构建后端镜像**

```bash
docker build -t cgc-pms-backend:deploy -f backend/Dockerfile backend/
```

后端 Dockerfile 采用多阶段构建：

- **Stage 1（builder）**：maven:3.9-eclipse-temurin-21，利用 Docker layer caching 先下载依赖再编译
- **Stage 2（runtime）**：eclipse-temurin:21-jre，最小化运行镜像，非 root 用户运行，配置 JVM 参数

**步骤 5：构建前端镜像**

```bash
docker build -t cgc-pms-frontend:deploy -f frontend-admin/Dockerfile frontend-admin/
```

前端 Dockerfile 同样多阶段构建：

- **Stage 1（builder）**：node:22-alpine，corepack 安装 pnpm，`pnpm install` + `pnpm build`
- **Stage 2（runtime）**：nginx:1.27-alpine，复制构建产物和 Nginx 配置模板

### 4.4 部署阶段

**步骤 6：创建 docker-compose.deploy.yml**

生产部署适配文件，在 docker-compose.prod.yml 基础上调整：

- 设置 `BACKEND_TAG=deploy` 和 `FRONTEND_TAG=deploy`
- 生成自签 SSL 证书到 `deploy/ssl/` 目录
- 前端端口映射改为 `127.0.0.1:8081:80`（仅本地反代访问）

```bash
# 生成自签 SSL 证书
mkdir -p ssl
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout ssl/server.key -out ssl/server.crt \
  -subj "/CN=cgc.bzywc.cn"
```

**步骤 7：启动服务**

```bash
docker compose -f docker-compose.deploy.yml up -d
```

启动顺序依据 `depends_on` 和健康检查：

1. preflight（退出型，校验配置通过后退出）
2. mysql → redis → minio（并行启动，等待健康检查通过）
3. backend（等待 MySQL、Redis、MinIO 全部健康）
4. frontend（等待 backend 健康）

### 4.5 反向代理配置

**步骤 8：配置宝塔 Nginx 站点**

在宝塔面板中创建站点，指向 `cgc.bzywc.cn`，配置反向代理：

```nginx
# 宝塔 Nginx 站点配置（cgc.bzywc.cn）
server {
    listen 443 ssl;
    server_name cgc.bzywc.cn;
    
    ssl_certificate /path/to/ssl/fullchain.pem;
    ssl_certificate_key /path/to/ssl/privkey.pem;
    
    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

server {
    listen 80;
    server_name cgc.bzywc.cn;
    return 301 https://$host$request_uri;
}
```

### 4.6 验证阶段

**步骤 9-12：问题修复与验证**

按顺序处理部署过程中出现的 10 个问题（详见第 5 节），最终完成全链路验证：

- 浏览器访问 https://cgc.bzywc.cn → 前端页面正常加载
- 登录验证 → 输入凭据后成功进入控制台
- API 调用验证 → 接口正常返回数据，无 CORS 错误

---

## 5. 中间问题与解决方案

### 问题 1：宝塔面板 404

**症状**：通过域名访问宝塔面板时返回 404

**原因**：宝塔面板的 `domain.conf` 中绑定的域名与实际访问域名不一致

**解决方案**：编辑宝塔面板配置文件，将 `bind_domain` 修改为正确的 `bt.bzywc.cn`，保存后重启面板服务：

```bash
# 修改 /www/server/panel/data/domain.conf
echo "bt.bzywc.cn" > /www/server/panel/data/domain.conf
/etc/init.d/bt restart
```

### 问题 2：Nginx 反代配置使面板可用无端口访问

**症状**：希望直接通过 `bt.bzywc.cn` 访问面板，而不需要在 URL 后加端口号

**原因**：宝塔面板默认监听非标准端口，Nginx 需要额外的反代配置来隐藏端口

**解决方案**：在宝塔 Nginx 中为 `bt.bzywc.cn` 配置反向代理，将 443 端口的请求转发到面板监听端口（8916）：

```nginx
location / {
    proxy_pass http://127.0.0.1:8916;
    proxy_set_header Host $host;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
```

### 问题 3：前端 Docker 构建失败

**症状**：执行 `docker build` 构建前端镜像时失败，错误信息显示 Docker Hub 层拉取失败

**原因**：Docker Hub 镜像层临时不可用，`node:22-alpine` 和 `nginx:1.27-alpine` 基础镜像拉取超时

**解决方案**：等待网络恢复后重试。可采取以下措施降低复现概率：

```bash
# 重试构建
docker build -t cgc-pms-frontend:deploy -f frontend-admin/Dockerfile frontend-admin/
```

> 建议：配置阿里云 Docker 镜像加速器以提高拉取稳定性。

### 问题 4：docker compose pull 失败

**症状**：`docker compose up` 时自动拉取镜像失败，显示网络超时

**原因**：阿里云 ECS 连接到 Docker Hub 的网络不稳定，`mysql:8.0`、`redis:7-alpine`、`minio/minio` 等镜像拉取超时

**解决方案**：手动逐个拉取并重试：

```bash
docker pull mysql:8.0
docker pull redis:7-alpine
docker pull minio/minio:RELEASE.2024-10-13T13-34-11Z
docker pull busybox:1.36
```

> 建议：首次部署时提前执行 `docker compose pull`，或配置阿里云 ACR 镜像仓库作为镜像源。

### 问题 5：Flyway V89 列名不匹配

**症状**：后端容器启动后日志报错，V89 迁移脚本执行失败，错误信息为 `Unknown column 'subject_level'`

**原因**：V89 迁移脚本中使用了 `subject_level` 列名，但数据库中实际列名为 `level`。这是 V78 种子数据迁移的历史遗留问题。

**解决方案**：修复 SQL 脚本中的列名引用，将所有 `subject_level` 改为 `level`，然后重建后端镜像：

```bash
# 修复并重新构建后端镜像
docker build -t cgc-pms-backend:deploy -f backend/Dockerfile backend/
docker compose -f docker-compose.deploy.yml up -d backend
```

### 问题 6：Flyway 校验失败

**症状**：后端容器重启后 Flyway 校验报错，阻止应用启动

**原因**：V89 迁移脚本之前执行了一半（部分写入），导致 Flyway 的迁移记录表（`flyway_schema_history`）中存在已执行的记录，但重新构建后 checksum 与数据库记录不匹配

**解决方案**：删除失败的迁移记录，并配置 Flyway 跳过校验：

```sql
-- 登录 MySQL 容器
docker exec -it cgc-pms-mysql mysql -uroot -p

-- 检查并清理失败的迁移记录
USE cgc_pms;
DELETE FROM flyway_schema_history WHERE version = '89';

-- 或者在 application-prod.yml 中暂时配置
-- spring.flyway.validate-on-migrate=false
-- 迁移成功后再恢复
```

### 问题 7：前端 SSL 证书缺失

**症状**：前端容器启动失败，Nginx 报错 `ssl_certificate` 文件不存在

**原因**：前端 Dockerfile 中 Nginx 配置加载了 `/etc/nginx/ssl/server.crt`，但部署时 `deploy/ssl/` 目录为空（仅含 `.gitkeep`），未生成证书文件

**解决方案**：生成自签证书并挂载到容器：

```bash
cd /opt/cgc-pms/deploy
mkdir -p ssl
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout ssl/server.key -out ssl/server.crt \
  -subj "/CN=cgc.bzywc.cn"
chmod 600 ssl/server.key

# 重启前端容器以重新挂载证书
docker compose -f docker-compose.deploy.yml up -d frontend
```

自签证书仅作为容器内部 Nginx 的降级方案，生产环境 SSL 终结在宝塔 Nginx，使用托管证书。

### 问题 8：CORS 跨域错误

**症状**：前端页面可以加载，但所有 API 请求被浏览器拦截，控制台报 CORS 错误

**原因**：`.env` 文件中 `CORS_ALLOWED_ORIGINS` 配置错误：

- 写成了 `https://cgc.bzywc.cn` 但缺少正确格式
- 或者填写了不一致的值，导致后端拒绝跨域请求

**解决方案**：修正 `.env` 中的 `CORS_ALLOWED_ORIGINS` 为正确的域名值，然后重启后端容器：

```bash
# .env 文件中修正
CORS_ALLOWED_ORIGINS=https://cgc.bzywc.cn

# 重启后端
docker compose -f docker-compose.deploy.yml restart backend
```

后端 Spring Boot 应用会从环境变量读取该配置，设置 `Access-Control-Allow-Origin` 响应头。

### 问题 9：管理员登录失败

**症状**：使用 admin 账号登录提示用户名或密码错误

**原因**：Flyway V85 迁移脚本（`V85__remove_default_admin.sql`）出于安全考虑，删除了默认管理员账号：

```sql
DELETE FROM sys_user WHERE id = 1 AND username = 'admin'
  AND password = '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2';
```

该脚本在生产数据库首次部署时执行，删除了仅存在于开发环境的默认凭据 `admin/admin123`。

**解决方案**：手动重建管理员账号：

```sql
-- 登录 MySQL 容器
docker exec -it cgc-pms-mysql mysql -uroot -p

USE cgc_pms;

-- 插入管理员用户（密码为 BCrypt 加密后的值）
INSERT INTO sys_user (id, username, password, real_name, status, deleted_flag, created_at, updated_at)
VALUES (1, 'admin', '$2a$10$...BCRYPT_HASH...', '系统管理员', 'ENABLE', 0, NOW(), NOW());

-- 分配管理员角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
```

> 注：实际部署中使用修改后的密码，BCrypt 哈希由后端 `PasswordEncoder` 生成，不采用默认的 `admin123`。

### 问题 10：登录锁定

**症状**：多次登录失败后，即使输入正确凭据也无法登录，系统提示"操作过于频繁"

**原因**：后端内置了登录失败次数限制和速率保护机制。连续多次登录失败触发了安全策略，导致账号或 IP 被临时锁定

**解决方案**：重启后端容器以清除内存中的登录失败计数：

```bash
docker compose -f docker-compose.deploy.yml restart backend
```

> 建议：后续使用固定凭据一次性登录成功即可避免此问题。

---

## 6. 最终结果

### 6.1 系统状况

系统已成功部署并可正常访问：

| 验证项 | 结果 |
|--------|------|
| 前端页面 | ✅ https://cgc.bzywc.cn 正常加载 |
| 后端 API | ✅ `/api/actuator/health` 返回 UP |
| 登录认证 | ✅ 输入凭据后成功进入管理控制台 |
| CORS 配置 | ✅ 跨域请求正常，无报错 |
| 全链路验证 | ✅ 页面操作 → API → 数据库 完整通路 |

### 6.2 容器状态

所有 5 个 Docker 容器均健康运行：

```bash
$ docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
NAMES                STATUS                  PORTS
cgc-pms-frontend     Up 2 hours              127.0.0.1:8081->80/tcp, 443/tcp
cgc-pms-backend      Up 2 hours (healthy)    8080/tcp
cgc-pms-mysql        Up 2 hours (healthy)    3306/tcp
cgc-pms-redis        Up 2 hours (healthy)    6379/tcp
cgc-pms-minio        Up 2 hours (healthy)    9000/tcp, 9001/tcp
```

### 6.3 访问入口

| 服务 | 地址 | 说明 |
|------|------|------|
| 系统前端 | https://cgc.bzywc.cn | 生产访问入口，宝塔 Nginx 反代 |
| 后端 API | https://cgc.bzywc.cn/api | 通过前端 Nginx 反代访问 |
| 健康检查 | https://cgc.bzywc.cn/api/actuator/health | 后端健康状态 |
| 宝塔面板 | https://bt.bzywc.cn:8916 | 服务器管理面板 |

### 6.4 登录凭据

| 角色 | 用户名 | 说明 |
|------|--------|------|
| 系统管理员 | admin | 初始管理员账号，建议首次登录后修改密码 |

> 密码为部署时设置的强密码，通过安全渠道传递。

---

## 7. 后续建议

### 7.1 SSL 证书

- 当前使用宝塔面板托管的免费 SSL 证书，有效期通常为 3 个月
- 建议申请 **Let's Encrypt 正式 SSL 证书**并通过 certbot 自动续签
- 或使用宝塔面板内置的 SSL 自动续签功能

### 7.2 DNS 配置

- 当前 `cgc.bzywc.cn` 可能通过 hosts 测试或临时 DNS 记录解析
- 建议为 `cgc.bzywc.cn` 正式配置 DNS A 记录指向 `121.41.73.124`
- 如使用 CDN，还需配置 CNAME 记录

### 7.3 数据库备份

- 配置定期 MySQL 全量备份调度
- 参考 `docs/10-部署运维手册.md` 中的 systemd timer 或 cron 方案
- 建议备份策略：每日全量备份 + 保留最近 7 天
- 同步备份 MinIO 对象存储数据

### 7.4 安全加固

- **修改 admin 默认密码**：首次登录后立即更换为复杂密码
- 考虑为管理员账号启用两步验证（如有相关功能）
- 定期审计系统用户和权限分配
- 监控后端日志中的异常登录记录

### 7.5 CI/CD 持续集成

- 当前为手动构建部署，后续可考虑：
  - 搭建 GitHub Actions 或 GitLab CI 流水线
  - 构建镜像推送到阿里云 ACR（容器镜像服务）
  - 自动化测试 → 构建 → 部署到服务器的流程
- 引入蓝绿部署或滚动更新策略，减少停机时间

### 7.6 监控告警

- 配置后端 Actuator 健康检查监控
- 监控关键指标：MySQL 连接数、Redis 可用性、磁盘使用率
- 对接 Prometheus + Grafana 实现可视化监控（项目已内置 Micrometer Prometheus 指标导出）
- 设置日志告警规则，尤其是 `BLACKLIST_UNAVAILABLE`、`TOKEN_BLACKLIST_WRITE_FAILED` 等关键日志模式

### 7.7 性能优化

- 当前 JVM 堆配置为 512M ~ 1G，可根据实际负载调整
- 监控前端静态资源缓存命中率
- MySQL 慢查询日志分析和索引优化
- 考虑引入 CDN 加速前端静态资源

---

## 附录 A：部署文件清单

| 文件 | 说明 |
|------|------|
| `deploy/docker-compose.prod.yml` | 生产 Docker Compose 模板 |
| `deploy/docker-compose.deploy.yml` | 部署适配版 Compose 文件 |
| `deploy/.env` | 环境变量配置（已配置随机密码） |
| `backend/Dockerfile` | 后端多阶段构建镜像 |
| `frontend-admin/Dockerfile` | 前端多阶段构建镜像 |
| `frontend-admin/nginx.conf` | 前端 Nginx 配置（含 SSL 和 API 反代） |
| `docs/10-部署运维手册.md` | 部署运维参考文档 |

## 附录 B：Docker 常用命令速查

```bash
# 查看容器状态
docker ps -a

# 查看日志
docker logs cgc-pms-backend
docker logs -f cgc-pms-backend     # 实时跟踪日志

# 重启服务
docker compose -f docker-compose.deploy.yml restart backend

# 停止所有服务
docker compose -f docker-compose.deploy.yml down

# 停止并删除数据卷（危险，会丢失数据）
docker compose -f docker-compose.deploy.yml down -v

# 查看资源占用
docker stats

# 进入容器内
docker exec -it cgc-pms-mysql mysql -uroot -p
docker exec -it cgc-pms-backend sh
```

---

> **报告编写日期**：2026-06-30
> **部署负责人**：运维团队
> **状态**：部署完成，系统正常运行
