# 工作区 Sentry 接入与治理规则交付核验

> 日期：2026-09-06；基线：`master@d88c8569495d4ca4a9fdd51a5a66ca41863144c4`。
> 范围：用户明确选择核验并提交交付既有23项改动；不是丢弃改动，不新增主线或AutoPilot任务。
> Git终态：本报告作为待交付内容提交；最终同SHA CI、PR、合并和工作区清理证明随受保护PR记录提供，不循环追加自引用提交。

## 内容与边界

- 既有11份Sentry接入/测试/依赖文件、9份治理规则、3份Codemap纳入本次；另补直接缺口的测试、README、通知手册与本报告。
- Sentry只从环境变量启用，空DSN禁用；既有受控远端事件为历史接入证据，本次不使用真实凭据、不发送外部事件，也不验证任何非本地环境。
- 后端未预期异常从`GlobalExceptionHandler`上报；前端`main.ts`初始化后由`clientErrorReporter`统一捕获，复用现有去重及5次/分钟限制。原API响应、权限、租户和金额语义不变。
- PII/用户信息/HTTP Body自动采集默认关闭，不启用Tracing或Replay；异常message/stack是诊断内容，不宣称任意异常文本自动脱敏。详情见[README](../../README.md)。
- 治理规则澄清只读/测试/实施授权、主线门禁适用性、历史耗时来源、发布动作批准和通知开关作用域；不修改运行Hook、收件人、AutoPilot状态或执行权限。

## 直接发现与处置

| 发现 | 分类 | 本轮处置与验证 |
| --- | --- | --- |
| 新规则与静态契约仍要求旧英文Codemap句子及旧耗时表述 | quality_or_security | 补全根规则路径，更新契约为当前语义并保留失败关闭/禁止伪N/A断言；策略契约通过 |
| SDK默认BrowserApiErrors自动捕获可能绕过项目上报器限流 | quality_or_security | 同时禁用GlobalHandlers、BrowserApiErrors和Vue自动error handler；配置、跨来源同异常去重与SDK失败不阻断API回归通过 |
| 后端SDK RuntimeException可打断稳定SYSTEM_ERROR响应 | quality_or_security | 新测试先复现SDK callback failed；窄catch RuntimeException隔离观测失败，只记录traceId告警，不吞JVM Error |

独立只读安全复核完成；主线程核对本地SDK实际集成名称与调用链。捕获原异常并非“自动脱敏”，已修正文档与Codemap边界。没有通过降低测试断言或关闭门禁解决失败。

## 验证与交付门禁

- 前端聚焦9项通过，含Sentry配置、去重、限流、关闭和失败隔离；类型检查通过。
- 修改前后端相关8项通过；新增SDK失败用例先红，修复后重新执行`ApplicationConfigUnitTest,GlobalExceptionHandlerTest`，9项、0失败、0错误、0跳过，BUILD SUCCESS。旧JaCoCo数据出现类指纹告警，因此不将该增量报告视为全量覆盖率通过；完整CI会重建同SHA覆盖率。
- 工作流契约、README同步测试14项、长任务通知测试15项、策略契约与控制面指纹自测通过。通知测试只用夹具，不发真实消息。
- 提交前重建并验证HTML/JSON/lock；普通pre-push执行前端完整门禁，不绕过Hook。最终功能分支完整Push CI、pre-PR verifier、受保护PR与post-merge分别绑定精确SHA。
- 控制面指纹按现有算法重新计算为`b7c453b785edec15e6bc9d96e718fbefd02cdbd3ae304dc31de6dbf7be54588b`。根规则与技能已在fingerprintPaths中，旧金丝雀证据不可沿用；后续多Issue/无界执行前仍须用户明确`启动迭代-1`。本次不启动或伪造金丝雀，不修改批准状态。

## 恢复与剩余边界

- 本轮三项直接发现全部在当前任务修复复验，不延期；新增正式后续项0、关闭正式后续项0、净变化0。
- 实际dev数据库、未跟踪/忽略文件、其他工作树不动；此前恢复备份及stash保留。本次“干净工作区”只指已跟踪改动交付后`git status --short`为空，不意味着删除缓存或备份。
- 恢复优先将DSN置空以禁用监控；需要代码回退时通过正常受保护revert交付，不强推或改写历史。治理回退保持授权、local-only和失败关闭边界；实际故障需重开相应门禁。
