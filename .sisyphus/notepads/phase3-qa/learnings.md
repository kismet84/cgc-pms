# F3 代码级 QA 审查 — 学习记录

## 2026-06-12

### 执行方式
- 纯代码级静态审查，无需启动服务器（无 MySQL/Redis）
- 使用 glob + grep + Read 工具组合验证文件存在性和代码内容

### 关键发现
1. **前端目录命名**：前端是 `frontend-admin`（非 `frontend`），页面在 `src/pages`（非 `src/views`）
2. **Handler 命名约定**：`*WorkflowHandler.java`（非 `*Handler.java`）
3. **Entity 位置**：CtContractChange 在 `contract/entity/` 下（非 `contract/change/entity/`）
4. **代码规范**：护栏规则通过 JavaDoc 注释 + grep 验证双重确认，0 违规

### 护栏验证方法
- Settlement/CostGenerationService：grep `import.*CostGenerationService` 在 settlement/ → 0 matches
- CT_CHANGE/contractAmount：grep `contractAmount` 在 change/ → 仅文档注释，实际代码用 `currentAmount`
