# ISSUE-037-016 WBS 软删除编号冲突修复验收报告

## 结论

- 结论：通过
- 阻塞：无
- 上线范围：本报告仅裁决 Ready Issue 的代码与自动化验证，不包含生产发布。

## 实现与边界

- `SubTaskService.delete()` 继续先做租户校验、项目访问与后续引用保护。
- 引用保护通过后，在同一事务内把任务编号改为 `DELETED-<id>`，再执行既有逻辑删除。
- 墓碑编号按任务 ID 唯一且不超过 64 字符；不修改生成格式、数据库结构或其他业务表。

## 验收证据

- `TEST_JWT_SECRET=<test-only-strong-secret>; .\mvnw.cmd "-Dtest=SubTaskControllerTest,TenantBoundaryTask2Test" test`：通过，27 tests，0 failures，0 errors。
- 新增创建→删除→业务编号复用→再次删除回归；恢复 `ISSUE-037-015` 移除的两处测试清理。
- `git diff --check`：通过。
- 执行器末次自检因未继承测试 JWT 而触发 144-bit WeakKey；归类环境前置，注入合规测试密钥后通过。

## 独立复核

- 结论：PASS；阻塞 findings：无。
- 覆盖：事务顺序、引用保护、租户/项目访问、墓碑唯一性/长度、修改范围和测试回归。

## 剩余风险与回滚

- 非阻塞：未做“墓碑更新后逻辑删除失败”的故障注入；由现有 Spring 事务回滚保证原子性。
- 非阻塞：若未来开放任务编号编辑，应保留 `DELETED-` 命名空间或增加格式校验。
- 回滚：移除删除前墓碑编号更新与专项测试；无 schema 或历史数据回滚。
