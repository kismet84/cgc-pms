# ISSUE-037-017 BaseEntity 备注写入契约修复验收报告

## 结论

- 结论：通过。
- 阻塞：非阻塞。
- 上线裁决：代码具备本地合并条件；生产上线仍需随版本执行既有发布门禁。

## 正式交付物

- `BaseEntity.remark` 恢复客户端 JSON 写入能力。
- 新增共享 JSON 契约测试，保护备注读写及 ID、租户、审计、逻辑删除字段的只读边界。

## 验收证据

- `cd backend; .\mvnw.cmd "-Dtest=BaseEntityJsonContractTest" test`：1 tests，0 failures，0 errors。
- `git diff --check`：通过。
- 独立审查：PASS；仅移除 `remark` 的 `READ_ONLY`，其余受保护字段注解不变。
- 范围：仅生产共享实体、契约测试及正式收口文档；无 Controller、Service、前端、数据库或 migration 变更。

## 图谱检索证据

- CodeGraph 查询目的：定位 `BaseEntity.remark` 的共享继承面及实体直绑创建/更新路径；命中合同、仓库、物料、成本等代表调用链。
- `codebase-memory-mcp` 查询目的：交叉核验备注在后端服务与前端页面的跨层使用；确认该字段是业务输入而非审计字段。
- 交叉核验：当前 diff、Jackson 专项测试与独立审查一致。

## 失败分类与剩余风险

- 首次 AutoPilot 阻塞分类为 `tool_config`：scope gate 将未跟踪目录折叠为路径，误判测试文件越界；不属于业务代码失败。
- 非阻塞风险：契约测试使用 `MdMaterial` 作为代表子类，未逐一实例化全部实体；共享父类单点修复和只读字段断言已覆盖本轮风险。
- 回滚：恢复 `remark` 的 `READ_ONLY` 并移除契约测试；无数据回滚。
