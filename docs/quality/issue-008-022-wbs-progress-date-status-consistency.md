# ISSUE-008-022 WBS 计划/实际日期、进度与状态一致性回归

## 结论

通过。共享分包任务 create/update 链路已补齐计划日期、实际日期、进度百分比与状态的一致性校验；无 migration、无新增 schedule 模块。

## 验收证据

- D 验收：`SubTaskControllerTest` 组合 22 个用例通过，失败 0、错误 0、跳过 0，exit 0。
- 非法进度、日期组合、完成态组合返回 400 且不落库；显式空白 status 返回 400，原 `IN_PROGRESS` 状态保持不变。
- 合法 partial update、项目/租户过滤与 WBS 编码生成继续通过。
- E 审查：Critical/Important/Minor 均为 0，无需补修。
- `git diff --check`：通过。

## 失败分类与范围

无失败。Ready 验证命令真实存在并按现有测试类执行；本 Issue 仅声明现有 WBS 载体一致性闭环，不等于完整甘特平台完成。

## 收口

- 是否自动合并：否（本地 commit only）
- 是否推送：否
- 阻塞：无
- 剩余风险：未覆盖前端只读甘特展示，后续由 `ISSUE-008-023` 单独处理。
