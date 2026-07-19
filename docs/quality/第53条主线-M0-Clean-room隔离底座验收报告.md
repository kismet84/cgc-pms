# 第53条主线 M0：Clean-room 隔离底座验收报告

结论：**通过**。阻塞项：**0**。M1 未授权、未启动；本报告不构成生产发布或整站切换授权。

## 1. 验收范围

- 盘点 Legacy route name、URL、页面视图、permission、adminOnly、驾驶舱角色权限与主要登录/驾驶舱 API。
- 新建独立 V2 应用、无 UI 契约包、迁移台账、Clean-room 门禁、CI job、dev Docker service、Dockerfile、Nginx 与静态健康页。
- 验证 V2 独立安装/测试/构建、Legacy 无回归、5174 真实可达、API 代理只读可达、镜像可构建和回滚边界。
- 不验收 M1 设计系统/认证/应用壳，不验收 M2 真实驾驶舱，不修改数据库、业务 API、Legacy 页面或正式入口。

## 2. 交付事实

| 项目 | 结果 |
|---|---|
| 路由台账 | 87 个命名路由；73 个视图引用；65 个独立页面模块 |
| 迁移状态 | 86 `LEGACY_ONLY`；1 `V2_SOURCE_AVAILABLE`（Dashboard） |
| V2 工程 | 独立 package、lockfile、TS、ESLint、Vitest、Playwright、Vite |
| 共享契约 | 登录/用户/驾驶舱最小纯 TypeScript 契约；4 个源码文件 |
| 隔离门禁 | 扫描 15 个 V2 文件与 4 个契约文件；0 个 Legacy/UI 边界违规 |
| 开发运行态 | `cgc-pms-frontend-v2-dev`；5174；同一 backend dev 网络 |
| 生产镜像 | 本地 `cgc-pms-frontend-v2:m0` 成功构建；未发布 |

## 3. 验证证据

| 验证 | 结果 |
|---|---|
| `pnpm check:boundary` | 通过；故意 Legacy Vue import fixture 被规则识别 |
| `pnpm check:route-ledger` | 通过；当前 router 与 JSON/Markdown 台账一致 |
| `pnpm test:unit`（V2） | 4 个测试文件、7 项通过 |
| `pnpm type-check:contracts` | 通过 |
| `pnpm type-check`、`pnpm lint:check`（V2） | 通过 |
| `pnpm build`、`pnpm check:bundle-size`（V2） | 通过；2 个 JS 资源，最大约 87.87 kB |
| `pnpm audit --audit-level high`（V2） | 通过；无已知漏洞 |
| `PLAYWRIGHT_CHANNEL=msedge pnpm test:e2e:health` | 1 项通过；实际访问 `http://127.0.0.1:5174/v2/health` |
| 180 秒运行态稳定门 | V2 页面连续 200；V2→backend 健康代理连续 UP；Legacy 5173 连续 200；V2 容器连续 healthy |
| Legacy `pnpm test:unit` | 129 个测试文件、727 项通过 |
| Legacy `pnpm type-check`、`pnpm build` | 通过 |
| `docker build -f frontend-admin-v2/Dockerfile -t cgc-pms-frontend-v2:m0 .` | 通过；专用上下文 1.64 MB |
| 临时镜像烟测 | dev 网络内 `/v2/health` 200、`/healthz` 200；临时容器已移除 |
| `git diff --check` | 收口复验见第 5 节 |

## 4. 异常与修复

1. 首次主机 pnpm 安装因 `vue-demi` 构建脚本未显式允许而失败；补充与 Legacy 一致的精确 allowlist 后冻结安装通过。
2. 首轮 Clean-room 扫描误扫 ESLint 禁止规则自身；收紧扫描对象并增加契约 Vue/DOM/CSS 规则后通过。
3. V2 dev 容器首次从 npmjs 下载 Linux 依赖超时；复用 Legacy 已验证的镜像源、安装戳与 `--ignore-scripts` 后容器转 healthy。
4. Playwright 自带 Chromium 下载达到工具上限；改用本机 Edge channel，实浏览器用例通过。CI 默认 Chromium 路径未改变。
5. 首次镜像构建在 npmjs 322/329 包处失败；增加 Dockerfile 专用 ignore、镜像源与 BuildKit pnpm cache 后重试通过。构建上下文从 303 MB 降为 1.64 MB。

上述异常均已本轮修复并复验，无遗留环境或质量阻塞。

## 5. 安全、数据与回滚裁决

- V2 M0 运行时唯一 API 是 `GET /api/actuator/health`，无业务写入。
- V2 不复制认证 token，不接入 localStorage，不实现临时弱化守卫。
- 未执行数据库 migration、数据回填、测试数据重置、业务写请求、生产连接、发布、提交、push 或合并。
- Legacy 源码未修改；Legacy 5173 和后端 8080 在 V2 稳定观察中保持可达。
- 回滚仅需停止/删除 V2 dev service，并回退 V2 目录、契约包、CI/Compose 增量；Legacy 与数据无需回滚。
- 收口必须再次执行 `git diff --check`、V2 核心门禁、Compose 配置与三 URL 健康检查；结果写入最终交付回报。

## 6. 视觉与 Stitch 边界

M0 只交付技术健康页，不实施业务视觉，故视觉保真门不适用。本阶段未调用 Stitch 生成新设计。用户已选新版经营驾驶舱概念继续作为 M2 唯一视觉基线；M1/M2 必须另获授权并重新执行可编辑设计、响应式和视觉 QA 门。

## 7. 后续项收口

- 新增后续项：0。
- 关闭后续项：0。
- 后续项净变化：0。
- M1—M8 是第53条主线既定阶段，不作为本轮新发现或悬空缺陷重复登记。
