# 主线16：项目台账页面生产化增强质量报告

## 最终结论

- 结论：通过
- 阻塞：否
- 可上线：是

## 变更范围

- `frontend-admin/src/pages/project/index.vue`
- `frontend-admin/src/pages/project/__tests__/ProjectLedgerProduction.test.ts`

## 验收证据

- 类型检查：
  - `cd frontend-admin && pnpm type-check`
  - 结果：通过
- 构建：
  - `cd frontend-admin && pnpm build`
  - 结果：通过
- 单元测试：
  - `cd frontend-admin && pnpm exec vitest run src/pages/project/__tests__/ProjectLedgerProduction.test.ts src/pages/project/__tests__/ProjectNav.test.ts`
  - 结果：通过，`Test Files 2 passed (2)`，`Tests 7 passed (7)`
- 浏览器桌面验收：
  - 登录后访问 `http://localhost:5173/project/list`
  - 页面标题：`项目列表 - 建筑工程总包项目管理系统`
  - 面包屑：`项目管理/项目列表/`
  - 工具栏提示：`当前页结果 / 金额单位万元 / 编号可查看总览`
  - 查询区可见 `项目类型`、`项目状态` 两个筛选下拉
  - `新建项目` 弹窗可打开
  - 行操作菜单可打开并出现 `编辑`
  - 金额回归样本：
    - 用登录态接口创建测试项目 `M16-AMT-1783144356265`
    - 列表展示包含 `123.45 万元`
    - 编辑弹窗金额输入框回填值为 `123.45`
- 浏览器移动验收：
  - `375x812` 视口下存在 `.project-mobile-list` 和 `.project-mobile-card`
  - `编辑`、`删除` 按钮可见
  - `document.documentElement.scrollWidth > window.innerWidth` 为 `false`
- API 请求证据：
  - 项目台账主列表请求：`/api/projects?pageNo=1&pageSize=20`
  - 未发现项目台账主列表仍使用 `pageNum`
  - 页面打开时同步出现的 `/api/projects?pageNum=1&pageSize=50` 已定位为其他参考数据/首页链路的既有请求，不属于本次项目台账主列表回归
- 运行态刷新：
  - 前端运行态曾与工作区源码不一致
  - 经 `python scripts/rebuild.py frontend` 刷新后，`5173` 提供的新模块已切到 `pageNo` 和新提示文案
- 测试数据清理：
  - 测试项目 `M16-AMT-1783144356265` 已删除
  - 清理后再次在项目列表搜索，结果不存在该项目

## 阻塞项

- 无

## 非阻塞项

- KPI 仍是当前页/当前结果口径，不是全量经营统计；如需全量统计，需要单独补后端聚合接口。
- 项目页打开时仍可观察到其他链路触发的 `pageNum=1&pageSize=50` 项目请求，这不是本次项目台账主列表问题，但说明仓库其他页面/组合逻辑仍存在旧分页别名使用。
- 金额单位一致性已在前端创建、编辑、回填、列表展示链路收口；历史存量数据是否存在单位污染，本次未做数据抽样清洗。

## 后端查询接口与前端控件对应关系

- `keyword`：已有前端关键字输入框
- `projectCode`：后端支持，前端按浏览器批注已删除独立输入框
- `projectName`：后端支持，前端按浏览器批注已删除独立输入框
- `projectType`：已有前端“项目类型”下拉
- `status`：已有前端“项目状态”下拉

处理结论：

- 原状态属于非阻塞能力缺口：后端支持 `projectCode`、`projectName` 精确筛选入口，但前端只有 `keyword` 模糊检索。
- 中间态曾短暂补齐 `projectCode`、`projectName` 两个输入框。
- 最终按浏览器批注删除这两个输入框和页头副标题，保留 `keyword + projectType + status` 查询组合。
- 当前结论：这是用户确认接受的非阻塞取舍，不再视为主线16阻塞项。

## 模型分档复盘

- 实现任务使用 `gpt-5.4 / medium`：足够。问题集中在单页前端状态、筛选、金额口径和移动端展示，不需要升档到高推理。
- 验收任务使用 `gpt-5.4-mini / medium` 命令验证 + 主线程证据裁决：足够。命令校验是固定动作，主线程结合浏览器与运行态证据完成最终裁决。
- 运维刷新任务使用 `gpt-5.4 / low`：合理。问题根因是前端运行态陈旧，不是代码实现错误。
- 原分档问题与修正动作：
  - 原计划未显式预判“源码已更新但 `5173` 仍跑旧前端”的运行态偏差。
  - 修正动作：新增 `M16-Ops` 子任务，只刷新前端运行态，不触碰后端与源码。

## 裁决依据

- 计划书要求的分页参数修正、筛选项补齐、金额口径统一、移动端最小可用视图、最小测试、类型检查、构建和真实浏览器验收均已完成。
- 最终运行态与工作区源码一致，桌面和移动端都能看到新文案、新筛选和移动卡片。
- 未发现与本次改动直接相关的失败项、控制台红错或项目台账主列表请求异常。
