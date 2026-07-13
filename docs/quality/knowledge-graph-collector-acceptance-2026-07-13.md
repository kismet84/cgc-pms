# 第39条主线：项目知识图谱采集机制验收报告

## 结论

- 结论：通过。
- 阻塞：非阻塞。
- 使用范围：可用于 `develop/1.5` 本地项目知识管理；不构成生产部署授权。
- 验收日期：2026-07-13。
- 验收对象：`tools/knowledge-graph` 0.2.0、本机 Neo4j Community 5.26、Codex stdio MCP、本地 Windows 定时对账任务。

## 正式交付

- 增加 `Source`、`SourceCursor`、`CollectionRun`、`ArtifactVersion`、`Evidence`、`Decision` Schema、约束和索引。
- 文档按 SHA-256 增量采集，保留历史版本并维护唯一 `CURRENT_VERSION`。
- Markdown 引用按变化文件增量更新；无法解析的引用进入运行指标，不伪造目标节点。
- Git 由初始历史窗口切换为 commit 游标增量，并建立 `GitCommit-[:CHANGES]->Artifact`。
- 会话、运行和日志摘要使用固定 Schema 受控写入；敏感字段和内联凭据写入前脱敏。
- MCP 增加采集运行和未解析引用查询，任意 Cypher 写入仍被禁止。
- 本地调度每 30 分钟对账一次，`MultipleInstances=IgnoreNew`，脚本另有文件锁保护。

## 验收证据

| 项目 | 结果 |
| --- | --- |
| `npm audit --audit-level=high` | 0 漏洞 |
| Schema 连续应用两次 | 21 条语句，两次均成功 |
| Node Test Runner | 13 tests，0 failure |
| MCP smoke（新 stdio 进程） | 8 个工具发现，`kg_status`、`kg_collection_runs(limit=5)` 与 `kg_unresolved_references` 真实调用成功 |
| 连续第二次采集 | 102 processed、102 skipped、0 added、0 updated、Git 0 增量 |
| Artifact 当前版本覆盖 | 103 Artifact / 103 CURRENT_VERSION |
| ArtifactVersion 来源覆盖 | 缺失 0 |
| Decision/Evidence 来源覆盖 | 缺失 0 |
| 敏感测试值残留 | 0 |
| 禁止区边界 | 所有配置禁止段哨兵测试通过 |
| 失败恢复 | 单来源失败可记录 `PARTIAL`，且测试运行后清理测试状态 |
| 定时任务 | 真实运行结果 0，状态 Ready，间隔 PT30M |
| 单实例锁 | 并发入口稳定返回专用退出码 2 |

## 失败分类记录

1. PowerShell 内联 JavaScript/Cypher 验收命令出现解析错误，分类为 `tool_config`；改为仓库内可复用 `src/acceptance.js` 后通过。
2. 单实例锁首次返回退出码 1，分类为 `real_quality`；根因是 `Write-Error` 在 `$ErrorActionPreference=Stop` 下提前终止，改为标准错误输出并显式 `exit 2` 后复验通过。
3. Episode 集成测试曾在删除测试节点后保留测试游标，分类为 `real_quality`；已改为测试前保存、测试后恢复游标，并由正式收口 Episode 推进真实游标。
4. `kg_collection_runs` 与 `kg_unresolved_references` 曾把 JavaScript Number 直接绑定到 Cypher `LIMIT`，Neo4j 按浮点数拒绝执行，分类为 `real_quality`；两个查询均显式使用 `toInteger($limit)`，并由 MCP smoke 覆盖显式 limit 与默认 limit 调用。

## 数据与安全边界

- 仓库文件和 Git 仍是事实源，Neo4j 只保存索引、版本、摘要和关系。
- 原始日志正文、截图、缓存、临时 run id 不进入正式文档或图谱正文。
- 不扫描 Codex 全局私有会话目录；只接收任务收口的结构化摘要。
- `.omc/`、`.omo/`、`.opencode/`、`.claude/`、`.mimocode/`、`graphify-out/`、`.sisyphus/`、`.archive/`、`archive/v1.0/private/` 和 `docs/archive/v1.0/` 继续禁止内容读取。
- 定时任务只更新本地 Neo4j，不启动业务任务、不提交、不 push、不发布生产。

## 剩余风险

- Neo4j 容器停止时定时任务会按环境前置失败退出；不会自动重建数据库。
- 当前只抽取确定性路径、章节和显式链接，不执行 LLM 语义关系抽取。
- Git 文件节点包含变更路径元数据，但禁止区路径会被过滤，文件正文只从采集白名单读取。
- 长期版本保留策略尚未立项；当前不自动删除历史版本，优先保证可追溯性。

以上风险均为非阻塞边界，未被包装为产品能力或生产可用性结论。
