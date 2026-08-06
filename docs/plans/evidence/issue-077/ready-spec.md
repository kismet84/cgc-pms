# ISSUE-077-001 ReadySpec

> 状态：`APPROVED / READY / LOCAL_ONLY`
> 批准依据：用户明确授权实施、阻塞研判、全量收口及通过后的受保护 Git 交付。

## 目标与最小范围

- 修复通知事务提交前推送和同用户单连接覆盖。
- 分离普通 API、通知/通讯 SSE 和文件请求代理策略。
- 在现有 V2 内提供可关闭 PWA 壳层、静态资源离线、IndexedDB 草稿/队列；不启用 `mobile/` 第二代码线。
- 复用既有日报、质量安全、文件、权限和状态机；只补离线写入所需幂等、冲突、读回与客户端入口。
- 不建设通用可靠任务平台；现有文件对象任务和文档生成记录已持久化，其他任务可补算或属于提示。

## 用户、频次与权限

- 日报施工/技术/项目经理：`site:daily:query`、`site:daily:edit`；每日创建、编辑、同步和提交。
- 质量安全检查：`quality:safety:inspection:maintain`；问题创建。
- 整改责任：`quality:safety:rectify`；整改回复。
- 独立复验：`quality:safety:reinspect`；复验和关闭必须在线。
- 管理/查询：`quality:safety:query`、`notification:view`；列表补读。
- 无项目范围、只读或无对应动作权限角色必须拒绝；客户端状态不扩张服务端授权。

## 字段、状态与接口

- 日报沿用项目、日期、施工内容、问题/延误、次日计划、人工天气、现场人数、附件；新增可空 `clientRequestId` 和服务端版本。
- 质量问题/整改沿用现有命令字段、附件和 `@Version`；新增可空 `clientRequestId`，不新增业务状态。
- 本地状态：`DRAFT/PENDING/SYNCING/RETRYABLE/CONFLICT/REJECTED/SYNCED`；服务端状态仍以现有日报 `DRAFT/SUBMITTED` 和质量安全状态机为准。
- 创建请求携带 `clientRequestId`；同租户、操作者、操作类型、键和同一请求返回同一结果，不同请求返回 `IDEMPOTENCY_CONFLICT`。
- 更新/提交携带版本；旧版本返回 `VERSION_CONFLICT`，禁止静默覆盖。
- SSE `/notifications/stream?clientId=...`；clientId 只区分连接，不参与认证。连接后立即补读未读数/列表。

## 离线、附件、隐私与容量

- 离线允许：日报草稿、质量问题草稿、整改回复草稿、照片暂存。
- 必须在线：日报正式提交、问题派发后的高风险状态、责任变更、严重度升级、复验、关闭/重开、经营后果。
- IndexedDB 命名空间：`tenantId:userId:schemaVersion`；TTL 7 天；最多 50 个操作和 20 个附件引用；超限拒绝新增并提示。
- 不保存令牌、Cookie、完整用户资料；退出清理未同步本地数据。账号或租户变化不得读取旧命名空间。
- 照片只本地暂存；复用 `/site-files` 上传，后端类型/大小/病毒扫描/权限为准，CLEAN 后才可成为业务证据。

## 特性开关与回滚

- 集中开关：`pwa.enabled`、`offlineDraft.enabled`、`offlineSync.enabled`、`fieldDailyLog.enabled`、`fieldQualitySafety.enabled`、`notificationMultiClient.enabled`。
- 关闭 PWA：停止新注册并注销 Service Worker/清缓存；普通在线页面继续可用。
- 关闭离线/现场写入：不回滚已提交事实；未同步草稿可查看摘要并删除，恢复开关后可继续。
- 通知多连接失败：关闭多连接，仅保留权威列表补读；通知数据库事实不删除。
- 不提供 `reliableTask.enabled`，因为条件包关闭且不产生代码。

## 验收

- 通知外层事务回滚 0 SSE；提交后 DB 可读且两个客户端均收；关闭一个不影响另一个。
- Service Worker 不缓存 `/api/**`、认证、附件；首次在线后离线壳层可开。
- 日报/质量草稿刷新可恢复；账号切换不可见；相同请求重放 10 次不重复；旧版本冲突可见。
- 390/768/1440 视口、键盘/标签/状态文本、桌面回归、控制台 0 严重错误。
- MySQL/H2、租户/项目/职责分离、文件 CLEAN、全量后端与前端门禁通过。

## 范围批准

用户授权记录替代未提供姓名的人工签字；不虚构负责人姓名。主线负责人由当前执行线程承担，业务/后端/前端/测试责任以本 ReadySpec、代码评审和客观门禁留痕。若新增领域字段、状态、角色、定位、BIM、外部系统或非本地环境，必须回到 G1。
