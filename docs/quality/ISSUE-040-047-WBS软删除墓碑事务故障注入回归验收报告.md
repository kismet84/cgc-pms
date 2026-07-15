# ISSUE-040-047 WBS软删除墓碑事务故障注入回归验收报告

**Goal:** 通过真实 Spring 事务故障注入，证明分包任务墓碑编号更新与逻辑删除具有原子性；删除第二步失败时不占用原编号或墓碑命名空间，正常路径仍释放业务编号。

**Architecture:** 仅新增独立 local H2 集成测试，复用 `SubTaskService.delete`、MyBatis 与既有 `@Transactional(rollbackFor = Exception.class)`；Mockito spy 只在真实 `updateById` 成功后、`deleteById` SQL 前注入异常，真实更新通过同事务 `SqlSessionTemplate` Mapper 执行。禁止修改生产删除逻辑、事务传播、数据模型、前端及 AutoPilot 控制面。

## 结论

- 验收结论：通过。
- 阻塞状态：无阻塞。
- 任务性质：回归证明，不是新增 WBS 能力。
- 剩余风险：本专项覆盖单库事务原子性；批量删除、物理删除、多前置 WBS 与跨服务事务不在当前能力范围，且没有证据支持在本轮扩项。

## 实现与边界

- 新增 `SubTaskDeleteTransactionTest`，使用项目10001及专用任务947001/947002。
- 故障路径让真实墓碑 UPDATE 返回1后，在逻辑删除 SQL 前抛出 `TEST_DELETE_BY_ID_FAILURE`；服务事务退出后直接 SQL 复读 task_code 和 deleted_flag。
- 正常路径复读 `DELETED-947001` 与 deleted_flag=1，并插入使用原编号的新活动任务，证明编号释放。
- 每项测试前后物理删除专用 ID，重置 spy 与 UserContext；生产源码、迁移、前端和控制面均未修改。

## 验收证据

- `cd backend; .\mvnw.cmd "-Dtest=SubTaskDeleteTransactionTest" test`：连续两次均为 Tests run: 2, Failures: 0, Errors: 0, Skipped: 0，BUILD SUCCESS。
- 故障路径 SQL 轨迹观测到真实 `UPDATE sub_task ... task_code=DELETED-947001` 更新1行，未执行真实 deleteById SQL；事务结束后断言原编号和 deleted_flag=0。
- 正常路径 SQL 轨迹观测到墓碑 UPDATE 与逻辑删除 UPDATE 各更新1行；原编号复用插入成功。
- Ready lint、JSON 解析、`git diff --check`、允许/禁止路径核对及结构化 Reviewer 结果随 AutoPilot 收口证据保存。

## Reviewer 裁决

- 故障点位由 AtomicBoolean 证明在真实墓碑更新成功之后，并明确位于 deleteById SQL 之前；复读在服务事务异常退出后执行。
- 成功与失败路径同时核对 task_code、deleted_flag 和编号唯一性，专用夹具与线程本地身份均清理。
- 未发现权限、租户、金额、生产数据、迁移或跨模块风险；结论 PASS，findings=无。

## 后续项收口

- 本轮新增后续项：0
- 本轮关闭后续项：1
- 关闭问题键：`OBS-WBS-TOMBSTONE-FAULT`
- 本轮后续项净变化：-1
