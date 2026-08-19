# 命令执行日志

时间均为 2026-08-19 Asia/Shanghai。完整高噪声测试输出未写入长期文档，只保留命令、退出码和可复核摘要。

## 工具与目录预检

执行目的：确认真实目录和工具版本。
执行目录：`D:\projects-test\cgc-pms`
命令：`node --version`、`pnpm --version`、`java -version`、`git --version`、`docker --version`
退出码：0
结果摘要：Node v22.23.1、pnpm 11.0.9、Java 21.0.11、Git 2.55.0、Docker 29.6.1。
审计结论：工具链可用。

## Codemap 验证

执行目的：验证代码地图与当前基线。
执行目录：仓库根
命令：`node scripts/codemap/generate-codemap.mjs --verify`
时间：23:15:24–23:15:25
退出码：0
结果摘要：`generation_base_commit=8ff20a3...`，`input_scope_dirty=true`。
审计结论：当前脏输入快照与锁文件一致。

## 后端完整验证

执行目的：编译、单元/集成测试、打包、JaCoCo。
执行目录：`backend`
命令：`.\mvnw.cmd -C verify "-Djasypt.encryptor.password=dev-jasypt-key"`
时间：23:15:24–23:24:49
退出码：0
结果摘要：2,941 tests，0 failure，0 error，28 skipped；833 classes；coverage checks met；BUILD SUCCESS。
完整错误信息：无。编译器报告已弃用 API 与 unchecked 警告，不影响退出码。
审计结论：本地后端门禁通过。

## 前端静态门禁

执行目的：Lint、应用类型与共享契约类型。
执行目录：`frontend-admin-v2`
命令：`pnpm lint:check`、`pnpm type-check`、`pnpm type-check:contracts`
时间：23:15:24–23:16:31
退出码：0
结果摘要：0 error、33 Prettier warning；两项类型检查通过。
审计结论：门禁通过，warning 登记为 `STYLE-001`。

## 前端单元测试

执行目的：执行全部 Vitest 单测。
执行目录：`frontend-admin-v2`
命令：`pnpm test:unit`
时间：23:16:57–23:17:47
退出码：0
结果摘要：89 files、644 tests 全部通过。
审计结论：前端单测通过。

## 前端构建与契约

执行目的：生产构建、路由账本、Bundle 预算。
执行目录：`frontend-admin-v2`
命令：`pnpm build`、`pnpm check:route-ledger`、`pnpm check:bundle-size`
时间：23:16:57–23:17:10
退出码：0
结果摘要：466 modules；87 routes、73 views、65 unique views；121 JS assets 通过预算。
审计结论：构建与静态契约通过。

## 前端依赖漏洞

执行目的：检查 high 及以上已知漏洞。
执行目录：`frontend-admin-v2`
命令：`pnpm audit --audit-level high --registry=https://registry.npmjs.org`
时间：23:20:21–23:20:23
退出码：0
结果摘要：No known vulnerabilities found。
审计结论：前端当前锁文件通过；结果具有时间性。

## 运行部署静态契约

执行目的：检查容器内存、JVM、时区、heapdump 持久化与数据源 fail-closed。
执行目录：仓库根
命令：`.\scripts\ci\test-runtime-deployment-contract.ps1`
时间：23:20:21
退出码：0（PowerShell 对象输出未设置 `$LASTEXITCODE`，宿主进程退出码为 0）
结果摘要：MaxRAMPercentage 65、heapdump path、1G limit、Asia/Shanghai、无 datasource fallback 全部通过。
审计结论：该静态契约通过；未覆盖 `MYSQL_DATABASE` 覆盖场景。

## 模板 XSS 动态最小复现

执行目的：验证无 sandbox `srcdoc` 是否可访问父页面。
执行目录：前端本地 Node/Playwright 环境
命令摘要：加载包含 `<img src=x onerror="parent.document.body.dataset.auditXss='confirmed'">` 的 `iframe srcdoc`。
退出码：0
结果摘要：`unsandboxed-srcdoc-parent-access=confirmed`。
审计结论：与源码链共同确认 `SEC-001`。未访问业务运行态、未写业务数据。

## 未执行及分类

- 后端 Trivy：未执行；需拉取/使用 Docker 镜像与缓存，且本轮禁止改变运行环境。分类：证据不足，`DEP-001` 待验证。
- 备份恢复 drill：未执行；会创建/删除隔离容器和临时备份。分类：授权边界，`DR-001` 待验证。
- 浏览器 live E2E、真实负载和容器构建：未执行；计划明确不得擅自启动/刷新运行态。分类：授权边界，不判业务失败。
- 非本地/生产验证：不适用；根规则明确当前不存在目标环境。

## 报告与范围收尾

执行目的：验证链接、统计、空白、分支、HEAD、范围与既有内容指纹。
执行目录：仓库根
命令摘要：Markdown 相对链接检查、风险/评分一致性检查、`git diff --check`、`git status --short`、7 个既有文件 SHA-256 复核。
退出码：0
结果摘要：链接、风险数量、82 分总分和 diff whitespace 均一致；仍为 `master@8ff20a3...`；7 个既有文件 SHA-256 与审计开始值逐一相同。
审计结论：本轮 tracked 写入仅 `docs/quality/README.md`；新增目录仅 `docs/quality/full-audit/`。原有 6 个 tracked 脏文件与 1 个未跟踪文件内容未变。

---

# 第98条整改执行日志（2026-08-20）

## 安全、API、审计定向验证

- 目录：`backend`
- 命令：`mvnw -Dtest=SecurityConfigMonitoringPasswordTest,ActuatorMetricsTest,OperationAuditServiceTest,RestrictedTemplateEngineTest,FileServiceTest test`，随后补跑 `AsyncConfigTest`。
- 退出码：0。
- 结果：机器 Basic 正向 200；空/错/JWT 拒绝；密码文件读取、缺失 fail-closed、prod 内联口令拒绝；事件属性变体拒绝；文件内部异常不外泄；审计失败计数通过。
- 失败分类：首次引用未安装的 `spring-security-test` 为 `quality_or_security`，改用 JDK Base64；JWT 过滤器误拦 Basic 为 `quality_or_security`，按 context path 规范化跳过机器端点后通过。

## 前端全量门禁

- 命令：`pnpm lint:check`、`pnpm test:unit`、`pnpm build`、`pnpm type-check:contracts`、`pnpm check:boundary`、`pnpm check:route-ledger`、`pnpm check:design-system`、`pnpm check:bundle-size`。
- 退出码：最终全部 0。
- 结果：89 files、644 tests；lint 0 error/0 warning；466 modules；87 routes、73 views、65 unique views；121 JS assets。
- 失败分类：第一次构建在 `SupplierSourcingPage.vue:575` 发现 Prettier 展开的内联多语句无法解析，`quality_or_security`；改为具名分页处理函数后构建通过。

## 部署与治理契约

- 命令：`test-runtime-deployment-contract.ps1`、Compose dev+monitoring `config --quiet`、`test-workflow-contract.ps1`、Codemap generator self-test。
- 退出码：全部 0。
- 结果：数据库名单一事实源、日志持久卷、Prometheus 机器认证/只读密钥/UI 回环、Git secret ignore、JVM/容器契约与 16 个 required jobs 均通过。
- 失败分类：首次用子 PowerShell 启动脚本导致 `$PSScriptRoot` 为空，`tool_invocation`；显式 `-RepoRoot` 后通过。

## 备份恢复与性能

- 恢复命令：`scripts/ci/test-backup-restore-drill.ps1`；退出码 0；MySQL 行 1、MinIO 对象 1、SHA 与交叉引用通过，23.9 秒。
- 性能命令：固定 JVM `DocumentPdfPerformanceTest` 与 Dashboard 三类查询预算测试；退出码 0。
- 结果：PDF 20/120/200 页各 30 次无失败，p50/p95 分别 84.287/102.392、92.811/117.515、99.384/112.121 ms；Dashboard 4 tests 通过。

## 后端全量验证

- 目录：`backend`
- 命令：`mvnw -C verify`；退出码 0。
- 结果：376 reports、2,946 tests、0 failure、0 error、28 skipped；833 classes；JaCoCo 全部门槛通过。
- 新发现：首次全量日志出现 `executor.completed` 同名异标签 Prometheus 告警，登记 `OBS-003/P2`；删除重复手工 binder 后定向测试和最终全量验证均无该告警。

## 本地运行态与浏览器

- 刷新：`python scripts/rebuild.py backend`；退出码 0；仅重建/重启本地 backend，不重置数据。
- HTTP：`/api/actuator/health` 200/UP；未配置本地机器密钥时 `/api/actuator/prometheus` 403 fail-closed；`/@vite/client` 与 `/src/main.ts` 均 200。
- 浏览器入口：应用内浏览器控制工具在当前会话不可调用，按技能允许回退仓库 Playwright。
- Playwright：dev-login 200；页面标题 `CGC-PMS V2`；30 个模板条目；选择交互成功；iframe sandbox 为空；恶意 `onerror` 未执行；应用 console 0；Vite overlay 0。安全探针产生的唯一 console error 是浏览器明确记录 sandbox 阻止脚本执行。
- 失败分类：首次使用未安装包名 `playwright`、首次非 exact heading 触发 strict selector，均为 `tool_invocation`；改用仓库 `@playwright/test` 和 exact selector 后通过。

## 依赖扫描

- 前端：`pnpm audit --audit-level high --registry=https://registry.npmjs.org`，退出码 0，0 known vulnerabilities。
- 后端扫描器：固定 `aquasec/trivy:0.65.0@sha256:a22415a...`。
- 失败分类：GHCR 第一次下载因默认 5 分钟 timeout 中断；第二次显式 45 分钟在 10 MiB 处被对端 HTTP/2 `PROTOCOL_ERROR` 中断，均为 `environment_prerequisite`，不是漏洞命中。
- 最终：改用 Trivy 官方 ECR 数据库镜像完整下载当前漏洞库，再以 `--skip-db-update` 执行 CI `backend-dependency-scan` 等价 manifest 扫描；`pom.xml` HIGH/CRITICAL 0，退出码 0。Trivy 明确提示 BOM 管理的空版本依赖不能在该阶段展开。
- JAR 二次扫描：首次运行需另下载 908 MiB Java DB；当前镜像链估算 7–14 小时，停止并归类 `environment_prerequisite`。准确 JAR 扫描保留给受保护同 SHA `supply-chain-security`，不伪报本地通过。

## G5 最终治理收口

- 静态一致性：Markdown 相对链接检查覆盖 21 个报告文件，断链 0；`current-issues.json` 可解析，现有 Issue 11，`AUDIT-PROMETHEUS-SCRAPE-AUTH` 计数 0；评分、风险和零悬空统计一致。
- 差异质量：`git diff --check` 退出码 0；Trivy JAR 未本地通过的边界在最终报告、风险登记和命令日志保持一致。
- 本地运行态：实际绑定端口为 backend `127.0.0.1:8080`、frontend `127.0.0.1:5173`；health `UP`，Vite 两个就绪资源 200，未配置机器密钥时 Prometheus 403 fail-closed。首次沿用旧证据端口 `18080/4173` 被拒绝，分类 `tool_invocation`，通过 `docker port` 读取实际监听后复验通过。
- 临时产物：精确删除本轮 lint 生成的未跟踪 `frontend-admin-v2/.ci-artifacts/lint-check.txt`；无业务数据或用户成果受影响。
- Codemap：在全部文档写入完成后重新生成 `html/json/lock`，随后执行 `--verify`；退出码 0。

## 受保护 Git 交付（2026-08-20）

- 实现分支：`codex/mainline-98-full-audit-remediation`；最终源 SHA `aaf8e478b2f6063effcc1a38b8cfe0b59586361b`。
- 实现提交：`abf4bdb0 fix(audit): close full-repository findings`、`4224e45d fix(ci): allow cold-cache order checks`、`44e0caa8 test(e2e): wait for requisition list readiness`、`aaf8e478 test(audit): initialize lazy metrics fixture`。
- 本地提交前/推送前门禁：README sync、`git diff --check`、Codemap `--verify`、前端 lint/build、644 单测、98/98 浏览器契约通过；最终后端 `mvnw -C verify` 为 2,946 tests、0 failure、0 error、28 skipped，JaCoCo 通过。
- 失败分类：Push run `32278404439` 的 `backend-order-sensitive` 在冷缓存依赖下载阶段超过原 10 分钟，归类 `environment_prerequisite`，门禁 timeout 调整为 25 分钟并由 workflow contract 固化；该旧运行随后被新提交取消，不作证据。
- 失败分类：Push run `32280263719` 的 `OperationAuditServiceTest` 在 Spring 懒加载下先读取未注册指标，归类 `quality_or_security`；测试夹具显式初始化指标所有者后，定向 5/5 与完整 2,946 项回归通过。该失败运行不作交付证据。
- Push CI：[32282560741](https://github.com/kismet84/cgc-pms/actions/runs/32282560741)，事件 `push`，HEAD `aaf8e478b2f6063effcc1a38b8cfe0b59586361b`，16 个 required jobs 全部成功；含准确构建 JAR 的 `supply-chain-security`。
- Pre-PR：`verify-pre-pr-ci.ps1 -HeadSha aaf8e478...` 返回 `PASS`，绑定 Push run `32282560741`。
- PR CI：[32282564507](https://github.com/kismet84/cgc-pms/actions/runs/32282564507)，事件 `pull_request`，同一 HEAD，独立全量运行成功；未复用为控制面变更的替代证据。
- 合并：[PR #458](https://github.com/kismet84/cgc-pms/pull/458) 受保护 squash 合并；merge SHA `45b457d45abedebc0a09358eece6d8b60801c6ee`。
- Post-merge：`verify-post-merge-ci.ps1 -Repository kismet84/cgc-pms -MergeSha 45b457d4...` 返回 `PASS / REUSED_PUSH_CI`，源树与合并树一致，并同时绑定 Push run `32282560741`、PR run `32282564507`。
- 边界：未执行 Tag、Release、部署、生产或任何非本地环境操作。
