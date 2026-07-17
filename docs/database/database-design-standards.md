# CGC-PMS 数据库设计与演进规范

> 状态：自 V195 起强制执行。既有结构按风险和业务改动渐进治理，不允许修改已经发布的 migration。

## 1. 权威来源与迁移规则

1. MySQL migration 位于 `backend/src/main/resources/db/migration/`，H2 镜像位于 `backend/src/main/resources/db/migration-h2/`；版本号、业务语义和关键约束必须对称。
2. 已发布 migration 永不修改。修复只能追加新版本，并在迁移前执行只读 preflight。
3. `local`、`dev`、`prod` 均开启 `validate-on-migrate`；CI 必须从空库迁移到最新版本并执行版本唯一性、约束完整性测试。
4. migration 是物理结构权威；本规范是设计权威；生成的数据字典和 ER 图是只读派生产物，不得手工维护结构副本。

## 2. 标准字段词典

|语义|标准定义|约束与例外|
|---|---|---|
|主键 `id`|`BIGINT`|应用分布式 ID；禁止自增与业务编码混用|
|租户 `tenant_id`|`BIGINT NOT NULL`|租户业务表必备；核心 FK 优先使用 `(tenant_id,id)` 组合关系|
|业务状态 `status`|`VARCHAR(32) NOT NULL`|必须有 CHECK、注册表或代码契约；审批状态统一为 `approval_status`|
|业务编码|`VARCHAR(64)`|租户内唯一；允许删除后重建时使用活动唯一键|
|金额|`DECIMAL(18,2)`|人民币元；禁止 `FLOAT/DOUBLE`；汇率计算中间值可提高精度但入账时统一舍入|
|数量|`DECIMAL(18,4)`|库存、计量数量；单位成本使用 `DECIMAL(18,6)`|
|比例/税率|`DECIMAL(8,4)` 或明确更高精度|必须说明是百分数还是小数，不得混用|
|业务日期|`DATE`|仅表达自然日，不以零点时间代替|
|时间点|`DATETIME`|应用和数据库统一 Asia/Shanghai 业务口径；跨系统接口必须记录时区|
|审计时间|`created_at/updated_at DATETIME`|禁止同一模型再引入 `create_time/update_time`|
|审计用户|`created_by/updated_by BIGINT`|系统任务使用约定系统用户，不允许无语义的 0|
|软删除|`deleted_flag TINYINT NOT NULL DEFAULT 0`|仅 0/1；可复用业务键配活动唯一键|
|备注 `remark`|`VARCHAR(500)`|不承载状态、关联键、审批配置或结构化明细|
|多态类型|`business_type/source_type VARCHAR(64)`|必须登记到 `sys_type_registry`；核心链仍需显式 FK|
|JSON 文档|`LONGTEXT` + `JSON_VALID` + 1 MiB 上限|必须有 schema/version；敏感字段禁止进入通用 JSON|

## 3. 唯一性分类

新增唯一约束前必须归入以下且仅以下一类：

1. **可复用业务编码**：例如租户内编号。采用生成列 `active_unique_token`，活动行固定为 0，已删除行使用自身 `id`，支持“创建—删除—重建”。
2. **永久幂等事实**：例如外部事件号、来源明细生成的会计/成本事实。唯一键永久有效，不因软删释放；纠错使用冲销事实，不删除原事实。
3. **关系边**：例如用户—角色、角色—菜单、用户—岗位。唯一键必须包含租户和两端 ID，并以 tenant-aware FK 防止跨租户关系。
4. **历史快照**：不得用当前态唯一键覆盖历史。使用版本号/生效区间，确保同一时点只有一个有效版本。

禁止把 `deleted_flag` 直接放入可复用编码唯一键，因为第二条已删除历史会冲突；也禁止 nullable token 作为活动行唯一性依据。

## 4. 状态、枚举与多态键

- 审批生命周期只使用 `approval_status`；领域状态仅在存在独立状态机时保留，并以事件或测试证明其独立推进。
- Java 常量、工作流注册表、`sys_type_registry` 和 migration seed 必须通过契约测试保持一致。
- 新状态值必须同时更新 CHECK/注册表、代码枚举、接口文档和状态迁移测试。
- 不允许通过备注、JSON、CSS 类名或前端字典值暗示后端业务状态。

## 5. JSON 使用边界

- JSON 只用于外部原文、可演进配置或低频审计快照；可查询、可关联、影响金额/权限/状态的字段必须拆成类型化列或子表。
- 每个 JSON 对象顶层必须包含 `schemaVersion`；版本升级应向后兼容或提供显式转换器。
- 单字段上限 1 MiB。更大文件进入对象存储，只在数据库保存摘要、哈希、对象键和处理状态。
- 数据库执行合法性和大小 CHECK；应用执行业务 schema 校验。任何一层失败都必须拒绝写入。

## 6. FK、索引与查询投影

- 核心资金、权限、库存、合同、项目关系使用 `(tenant_id, foreign_id)` 指向父表 `(tenant_id,id)`，数据库层拒绝跨租户关联。
- 外键列必须有满足左前缀的索引；高频列表索引按“租户、过滤状态、业务时间、主键”设计。
- Repository/Mapper 禁止新增裸 `SELECT *`；所有跨租户绕过拦截器的 SQL 必须显式列投影并显式包含租户条件。
- 面向 API 的原始 `Map<String,Object>` 只能作为兼容边界，核心事实应使用 Entity/DTO/RowMapper。当前裸 `SELECT *` 基线由 `DatabaseGovernanceStaticTest` 锁定，只允许下降。

## 7. 注释与可维护性

- 新表必须有中文表注释；新字段必须说明业务语义、单位、枚举、是否快照、是否可变。
- V195+ 由静态测试强制检查注释。既有缺失注释以生成数据字典补足可检索性，并在对应模型发生业务变更时随 migration 补齐物理 COMMENT。
- “需要人工确认”的推断不得写成数据库 COMMENT；必须先由领域负责人确认业务定义。

## 8. 容量、归档与分区触发线

默认不提前分区。满足下列任一条件时，必须提交带生产证据的容量评审：

- 单表超过 1,000 万行或每月增长超过 100 万行；
- 热数据超过 365 天且 90% 查询只访问最近 90 天；
- 关键查询在合理索引下 P95 超过 500 ms，或同比增长超过 2 倍；
- 单表超过 50 GiB，备份恢复时间无法满足 RTO；
- 审计/导入/OCR 原文超过 1 MiB 或显著推高 buffer pool 压力。

评审顺序为：修正查询和索引 → 冷热归档 → 按业务时间分区。归档必须定义保留期、检索 SLA、法律/审计依据、校验哈希、恢复演练和删除审批；未取得生产容量与合规证据时标记“需要人工确认”。

## 9. 发布门禁

1. 在 V194 发布基线执行 `scripts/database/database-remediation-preflight-v194.sql`。
2. 从空库分别执行 MySQL/H2 全量 migration。
3. 在升级副本执行 `scripts/database/database-remediation-postflight.sql`，所有 `BLOCK` 项必须为 0；`REVIEW` 项必须由业务/数据负责人签字。
4. 执行 migration、契约、并发、权限、金额守恒和运行态冒烟测试。
5. 使用 `scripts/database/generate-schema-docs.ps1` 重新生成数据字典和 ER 关系视图，并审查差异。
