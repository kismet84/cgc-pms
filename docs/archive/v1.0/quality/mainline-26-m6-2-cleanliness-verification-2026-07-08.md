# 第26条主线 M6.2 洁净度复核验收报告（2026-07-08）

## 1. 结论

- 结论：**B2 不通过**
- 阻塞性：**阻塞**
- 依据：`HEAD` 版本树仍跟踪 `output/playwright/**`、`output/playwright/**/admin-state.json`、`frontend-admin/coverage-result.json` 等运行态产物；当前仅能证明“本地已准备删除并补充忽略规则”，不能证明“版本库已不再跟踪”。

## 2. 复核范围与限制

- 仓库：`D:\projects-test\cgc-pms`
- 只读输入：`AGENTS.override.md`、`AGENTS.md`、`docs/plans/第26条主线-M6-阻塞项整改任务计划书-2026-07-08.md`、`.gitignore`
- 执行方式：仅运行只读 Git 核查命令；未执行 `git clean`、`git rm`、`git add`、`git commit`
- 禁止目录：未读取、未扫描 `.omc/`、`.omo/`、`.opencode/`、`.claude/`、`.mimocode/`、`graphify-out/`、`.sisyphus/`、`.archive/`

## 3. 计划口径摘录

依据 `docs/plans/第26条主线-M6-阻塞项整改任务计划书-2026-07-08.md`：

- M6.2 目标：清掉进入版本库和交付面的测试/浏览器运行态产物。
- 阻塞判定：若 `admin-state.json` 或同类运行态浏览器状态文件仍在版本库中，视为未通过。
- 验收标准：
  - 版本库不再跟踪浏览器状态文件、测试截图、覆盖率中间产物等运行态文件。
  - `admin-state.json` 不再以运行态状态文件形式保留在版本库。
  - 需保留的正式证据已脱敏并迁移到合规归档位置。
  - 忽略规则已覆盖本轮确认的问题目录或文件类型。

## 4. 关键命令与结果

### 4.1 忽略规则核对

命令：

```powershell
git check-ignore -v output/ output/playwright/foo.txt admin-state.json frontend-admin/coverage-result.json playwright-report/index.html test-results/results.json coverage/index.html frontend-admin/playwright-report/index.html frontend-admin/test-results/results.json frontend-admin/coverage/index.html
git check-ignore -v output/playwright/m22-5-acceptance/admin-state.json
```

结果：

- `.gitignore:61:output/` 命中 `output/` 与 `output/playwright/**`
- `.gitignore:66:admin-state.json` 命中根级 `admin-state.json`
- `.gitignore:65:coverage-result.json` 命中 `frontend-admin/coverage-result.json`
- `.gitignore:62:playwright-report/`、`.gitignore:63:test-results/`、`.gitignore:64:coverage/` 分别命中对应目录
- `output/playwright/m22-5-acceptance/admin-state.json` 也被 `.gitignore:61:output/` 覆盖

判定：

- 验收标准 2 的“忽略规则覆盖同类产物”已满足。

### 4.2 当前工作区状态核对

命令：

```powershell
git status --short
git status --short -- output/playwright frontend-admin/coverage-result.json .gitignore
git status --porcelain=v1 -- output/playwright frontend-admin/coverage-result.json .gitignore
```

结果：

- `.gitignore` 为已修改：` M .gitignore`
- `frontend-admin/coverage-result.json`、`output/playwright/**`、`output/playwright/m22-5-acceptance/admin-state.json` 均显示为 `D`
- 仓库同时存在与 B2 无关的既有源码改动和文档新增，未见本次范围内误删 `README.md`、`AGENTS.md`、`docs/**`、`.github/workflows/**`、`deploy/.env.example` 等项目资产

判定：

- 从 B2 范围看，当前状态更像“已做删除准备”，不是“已完成版本库脱钩并稳定落地”。
- 验收标准 3 仅能部分满足：未见误删项目资产，但存在大量未提交删除，仍需最终落地。

### 4.3 版本树是否仍在跟踪目标产物

命令：

```powershell
git ls-tree -r --name-only HEAD -- output/playwright frontend-admin/coverage-result.json | Select-Object -First 50
git diff --cached --name-status -- output/playwright frontend-admin/coverage-result.json .gitignore
```

结果：

- `git ls-tree -r --name-only HEAD -- ...` 仍列出：
  - `frontend-admin/coverage-result.json`
  - 多个 `output/playwright/**` 截图与 `.playwright-cli/*.yml`
  - `output/playwright/m22-5-acceptance/admin-state.json`
- `git diff --cached --name-status -- ...` 显示上述文件目前处于 staged deletion，`.gitignore` 处于修改态

判定：

- `HEAD` 仍跟踪目标运行态产物，说明当前分支的稳定版本事实尚未达到“版本库不再跟踪”。
- staged deletion 只能证明“准备移除”，不能替代“已从版本库交付面退出”的验收结论。

## 5. 对照验收标准

### 标准 1

> `git ls-files` 不再列出 `output/playwright/**`、`admin-state.json`、`frontend-admin/coverage-result.json`

- 结论：**不满足**
- 说明：本次复核以 `git ls-tree -r --name-only HEAD -- ...` 与 `git diff --cached --name-status -- ...` 作为更稳的提交态证据；`HEAD` 仍明确列出上述运行态产物。

### 标准 2

> `git check-ignore` 命中 `output/`、`admin-state.json`、`coverage-result.json`、`playwright-report/test-results/coverage`

- 结论：**满足**
- 说明：`.gitignore` 已覆盖本轮确认的运行态目录和文件类型。

### 标准 3

> `git status` 仅显示预期删除/修改和既有文档，不包含误删项目资产

- 结论：**部分满足，但不足以判定通过**
- 说明：B2 范围内未发现误删项目资产；但当前仍是未提交删除态，且仓库存在其他源码改动，无法据此宣称 B2 已稳定关闭。

## 6. 阻塞项与剩余风险

- 阻塞项 1：`HEAD` 仍跟踪 `output/playwright/**`、`output/playwright/**/admin-state.json`、`frontend-admin/coverage-result.json`
- 阻塞项 2：当前证据停留在“已 staged 删除”，未形成新的稳定版本事实
- 剩余风险 1：若直接以当前工作区状态宣称通过，主负责人会误把“本地待提交状态”当作“版本库已完成治理”
- 剩余风险 2：若其中部分截图/状态文件仍需正式留档，当前报告未看到其脱敏迁移去向证据

## 7. 验收建议

- 本轮裁决：**不通过 / 阻塞**
- 建议下一步仅做最小闭环：
  - 将 staged deletion 与 `.gitignore` 变更形成正式提交
  - 若需保留正式证据，先迁移到合规归档位置再提交
  - 提交后重新执行同口径只读复核，重点看 `HEAD` 是否仍列出目标产物

