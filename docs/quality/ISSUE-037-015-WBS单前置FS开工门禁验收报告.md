# ISSUE-037-015 WBS 单前置 FS 开工门禁验收报告

## 结论

- 结论：通过
- 阻塞：无
- 上线范围：本报告仅裁决 Ready Issue 的代码与自动化验证，不包含生产发布。

## 实现与边界

- 创建或更新任务时，统一 Service 使用最终有效 `predecessorTaskId` 与状态；前置未完成则拒绝后续任务进入 `IN_PROGRESS` / `COMPLETED`。
- 省略、更换、显式清空前置均按最终值处理；租户、项目、环和 FS 日期校验继续 fail-close。
- 前端只禁用对应状态并展示原因，服务端仍是可信门禁。
- 未新增状态、关系表、migration、多前置、自动排程、拖拽或关键路径。

## 验收证据

- `TEST_JWT_SECRET=<test-only-strong-secret>; .\mvnw.cmd "-Dtest=SubTaskControllerTest,TenantBoundaryTask2Test" test`：通过，26 tests，0 failures，0 errors。
- `pnpm test:unit src/pages/subcontract/__tests__/task.test.ts`：通过，9 tests。
- `pnpm type-check`：通过。
- `git diff --check`：通过。
- 组合测试首次出现 H2 软删除唯一键碰撞，发生在一次性测试对象清理阶段；移除两处非业务清理后原始组合命令稳定通过，未放宽业务断言。

## 独立复核

- 首轮结论：FAIL，仅因缺少 `COMPLETED` 拒绝与跨项目 predecessor 的硬性测试覆盖。
- 补修：增加完整完成态拒绝断言与跨项目 `SUB_TASK_DEPENDENCY_INVALID` 断言。
- 最终结论：PASS；阻塞 findings：无。

## 剩余风险与回滚

- 非阻塞：请求层与页面 catch 可能重复展示服务端错误；前端测试为源码契约断言，未执行真实响应式交互或浏览器验收。
- 回滚：移除统一 Service 状态门禁、前端状态禁用/提示和新增测试；无数据回滚。
