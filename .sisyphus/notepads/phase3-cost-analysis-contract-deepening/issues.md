
## MySQL 8.0 全栈验证发现 (2026-06-12)

### 已修复问题
1. **V26 重复列**: V24 已添加 cost_target_id，V26 重复 ALTER TABLE 导致失败，阻塞 V27-V30
2. **V29/V30 ID 冲突**: 两个迁移使用相同 template_id=50008 和节点 ID 50801-50803
3. **business_summary JSON 类型**: MySQL 8.0 严格校验 JSON，WorkflowEngine 传入纯文本字符串导致 Invalid JSON text 错误，需改为 TEXT
4. **MetaObjectHandler 字段缺失**: MyMetaObjectHandler 仅处理 createdAt/updatedAt，CtContractChange 使用 createdTime/updatedTime 导致 NOT NULL 约束违反

### 关键发现
- pplication-local.yml 实际上连接 MySQL 而非 H2
- Phase3IntegrationTest 使用 @ActiveProfiles("local")，在 MySQL 环境直接运行
- 预警引擎 24h 去重 + 8 条规则依赖真实业务数据状态，集成测试环境难以全部触发

## F4 范围保真度检查发现 (2026-06-12)

### 无法归因的变更
1. **根级 `database/migration/V21__add_submit_permissions.sql`**: T25 在遗留目录创建了镜像副本，后端实际路径为 `backend/src/main/resources/db/migration/`。Flyway 仅读取后端路径，根级文件冗余。
2. **根级 `database/migration/V29__init_settlement_approval_template.sql`**: 同上，T25 创建的遗留目录镜像副本。

### 轻微污染
3. **MyMetaObjectHandler.java 延迟**: CtContractChange 使用 `createdTime`/`updatedTime` 命名（非 `createdAt`/`updatedAt`），common handler 应在 T6-T8 提交中修改，实际在 T25 才补上。
4. **V30 ID 冲突修复**: T11-T15 创建的 V30 模板 ID=50008，T25 修复为 50009（避免与其他模板冲突）。

### T23 P18 vite 升级
- 计划列为待办 `[ ]`，实际已在 Phase 3 启动前完成（commit `5038490`, vite 6.4.3）
- 是计划准确性问题，非执行遗漏

### Must-NOT-do 护栏
- 10/10 全部通过（CT_CHANGE 不改 contractAmount、结算不调 CostGenerationService、预警无邮件/短信、驾驶舱无 WebSocket、双公式已统一、estimatedRemainingCost 非硬编码0、驾驶舱用 cost_summary 非 cost_item）
