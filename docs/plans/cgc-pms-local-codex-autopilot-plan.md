# CGC-PMS 本地 Codex AutoPilot 全托管自迭代方案

> 适用仓库：`kismet84/cgc-pms`  
> 运行方式：本地 Windows 设备 + Codex 桌面端  
> 版本：v1.0  
> 日期：2026-07-08  
> 目标：支持 Codex 在本地按规则自动规划、开发、测试、合并、归档和继续下一轮迭代

---

## 1. 背景与目标

当前 CGC-PMS 已经有长期生产级增强计划书，但中长期任务存在一定模糊性。如果直接让 Codex 执行“长期计划”，容易出现范围漂移、跨模块乱改、上下文失控、测试缺失和不可追溯的问题。

本方案的目标是建立一个 **本地 Codex AutoPilot 全托管自迭代系统**，让 Codex 可以在本地设备上按固定时间自动运行，也可以人工启动和停止。

最终运行模式：

```text
定时启动 / 人工启动
  -> Codex 自动规划
  -> Codex 自动拆任务
  -> Codex 自动写代码
  -> Codex 自动跑测试
  -> Codex 自动修改 / 删除 / 重建测试数据
  -> Codex 自动合并
  -> Codex 自动归档
  -> Codex 自动生成下一轮任务
  -> 定时终止 / 人工终止
```

---

## 2. 核心前提

本方案基于以下前提：

1. 当前项目中的数据均为测试数据，可以修改、删除、重建。
2. Codex 按规则自动合并，不需要等待人工审查。
3. Codex 在本地设备上通过 Codex 桌面端运行，不通过 GitHub Actions / CI/CD 云端运行。
4. 支持定时启动 / 定时终止。
5. 支持人工启动 / 人工终止。
6. 不自动发布生产环境。
7. 不连接生产数据库。
8. 每轮只处理一个明确任务，避免长任务失控。

---

## 3. 总体架构

### 3.1 本地 AutoPilot 架构

```text
Windows 任务计划程序 / 人工 PowerShell 脚本
  -> 写入 .codex-autopilot 控制文件
  -> Codex Desktop Automation 定时醒来
  -> 检查 enabled / stop / pause 状态
  -> 读取长期计划书、backlog、当前阶段
  -> 选择下一个 Ready Issue
  -> 创建分支或 worktree
  -> 自动开发
  -> 自动测试
  -> 自动自审
  -> 自动合并
  -> 自动更新 backlog
  -> 自动生成 iteration report
  -> 进入下一轮
```

### 3.2 两层控制

| 层级 | 作用 |
|---|---|
| Codex Desktop Automation | 负责真正的规划、开发、测试、合并、归档 |
| PowerShell 控制器 | 负责启动、停止、暂停、恢复、状态查看、定时注册 |

---

## 4. 状态机设计

AutoPilot 必须按明确状态流转：

```text
STOPPED
  -> STARTING
  -> PLANNING
  -> EXECUTING
  -> TESTING
  -> SELF_REVIEWING
  -> MERGING
  -> ARCHIVING
  -> NEXT_ROUND
  -> STOPPING
  -> STOPPED
```

状态记录文件：

```text
.codex-autopilot/state.json
```

示例：

```json
{
  "enabled": true,
  "status": "EXECUTING",
  "currentIssue": "ISSUE-002-003",
  "currentEpic": "EPIC-002",
  "branch": "codex/issue-002-003-alert-delivery-overdue",
  "worktree": ".worktrees/issue-002-003",
  "startedAt": "2026-07-08T09:00:00",
  "lastHeartbeatAt": "2026-07-08T09:27:30",
  "stopRequested": false,
  "autoMerge": true,
  "autoPush": false,
  "allowTestDataReset": true
}
```

---

## 5. 推荐目录结构

建议在仓库中新增：

```text
scripts/
  codex-autopilot/
    autopilot-start.ps1
    autopilot-stop.ps1
    autopilot-pause.ps1
    autopilot-resume.ps1
    autopilot-status.ps1
    autopilot-kill.ps1
    install-schedule.ps1
    uninstall-schedule.ps1
    codex-autopilot.config.json

.codex-autopilot/
  ALLOW_TEST_DATA_RESET
  logs/

docs/
  backlog/
    current-focus.md
    epics.md
    ready-issues.md
    blocked-issues.md
    done-issues.md

docs/
  iterations/
    README.md
```

---

## 6. 自动合并策略

### 6.1 自动合并目标

本方案中 Codex 完成任务后，不等待人工审查。只要通过规则门禁，即可自动合并。

推荐合并方式：

```bash
git checkout master
git merge --squash <当前任务分支>
git commit -m "[Codex][ISSUE-ID] 任务标题"
```

是否自动推送远程由配置决定：

```json
{
  "autoPush": false
}
```

建议初期使用：

```text
autoMerge = true
autoPush = false
```

稳定后再改为：

```text
autoPush = true
```

---

### 6.2 自动合并条件

Codex 每轮完成后，必须满足以下条件才允许自动合并：

```text
1. 当前任务来自 docs/backlog/ready-issues.md
2. 当前任务属于 docs/backlog/current-focus.md 允许范围
3. 没有 .codex-autopilot/stop.flag
4. 没有 .codex-autopilot/pause.flag
5. git diff --check 通过
6. 后端任务必须执行后端测试或说明原因
7. 前端任务必须执行 type-check / build 或说明原因
8. migration 任务必须同时考虑 MySQL / H2
9. 测试数据重置任务必须确认是 dev/test/demo 环境
10. 自审结果为 PASS
11. iteration report 已更新
12. backlog 已更新
```

---

### 6.3 自动合并失败处理

如果测试失败：

```text
1. Codex 最多尝试自修 2 次
2. 仍失败则停止当前 Issue
3. 不合并
4. 写入 docs/backlog/blocked-issues.md
5. 写入 docs/iterations/iteration-YYYY-MM-DD-report.md
6. 检查 stop.flag
7. 如未停止，进入下一个可执行 Issue
```

---

## 7. 测试数据策略

### 7.1 允许操作

由于当前项目数据都是测试数据，Codex 允许执行：

```text
- 修改测试数据
- 删除测试数据
- 重建 dev/test/demo 数据库
- 清理本地 MinIO 测试文件
- 重建 demo seed 数据
- 执行 docker compose down -v
- 执行本地测试环境初始化脚本
- 修改测试 seed SQL
- 修改 H2 测试数据
- 新增 Flyway migration
```

### 7.2 安全限制

即使数据是测试数据，也必须避免误删非项目数据。

Codex 必须遵守：

```text
1. 只允许 dev/test/demo 环境
2. 数据库 host 必须是 localhost 或 127.0.0.1
3. 不允许连接生产数据库
4. 不允许删除 Git 仓库外文件
5. 不允许删除 .git
6. 不允许删除用户目录
7. 不允许删除 C:\Windows
8. 执行清库前必须检查 .codex-autopilot/ALLOW_TEST_DATA_RESET 是否存在
```

---

## 8. 核心配置文件

文件路径：

```text
scripts/codex-autopilot/codex-autopilot.config.json
```

内容示例：

```json
{
  "repoRoot": "D:\\projects-test\\cgc-pms",
  "baseBranch": "master",
  "worktreeRoot": "D:\\projects-test\\cgc-pms\\.worktrees",
  "autopilotDir": "D:\\projects-test\\cgc-pms\\.codex-autopilot",

  "mode": "codex-desktop-automation",
  "autoMerge": true,
  "autoPush": false,

  "allowTestDataMutation": true,
  "allowTestDataDeletion": true,
  "requireTestDataResetMarker": true,

  "maxIssuesPerRun": 1,
  "maxFilesChangedPerIssue": 20,
  "maxRunMinutes": 90,
  "cycleIntervalMinutes": 30,

  "allowedEpics": [
    "EPIC-001",
    "EPIC-002",
    "EPIC-003",
    "EPIC-004",
    "EPIC-005",
    "EPIC-006",
    "EPIC-007"
  ],

  "forbiddenPaths": [
    ".git/**",
    "%USERPROFILE%/**",
    "C:\\Windows\\**"
  ],

  "devDataResetAllowedWhen": {
    "springProfiles": ["dev", "test", "demo"],
    "databaseHosts": ["localhost", "127.0.0.1"],
    "dockerComposeProject": "cgc-pms"
  },

  "backendValidation": [
    "cd backend && mvn test"
  ],

  "frontendValidation": [
    "cd frontend-admin && pnpm type-check",
    "cd frontend-admin && pnpm build"
  ],

  "fullValidation": [
    "cd backend && mvn test",
    "cd frontend-admin && pnpm type-check",
    "cd frontend-admin && pnpm build"
  ]
}
```

---

## 9. PowerShell 控制脚本

### 9.1 人工启动脚本

文件：

```text
scripts/codex-autopilot/autopilot-start.ps1
```

```powershell
$Repo = "D:\projects-test\cgc-pms"
$AutoDir = Join-Path $Repo ".codex-autopilot"

Set-Location $Repo

if (!(Test-Path $AutoDir)) {
  New-Item -ItemType Directory -Path $AutoDir | Out-Null
}

Remove-Item "$AutoDir\stop.flag" -ErrorAction SilentlyContinue
Remove-Item "$AutoDir\pause.flag" -ErrorAction SilentlyContinue

"started at $(Get-Date -Format s)" | Out-File -Encoding UTF8 "$AutoDir\start.flag"
"enabled" | Out-File -Encoding UTF8 "$AutoDir\enabled.flag"

@{
  enabled = $true
  status = "STARTING"
  startedAt = (Get-Date -Format s)
  stopRequested = $false
  autoMerge = $true
  autoPush = $false
  allowTestDataReset = $true
} | ConvertTo-Json | Out-File -Encoding UTF8 "$AutoDir\state.json"

Write-Host "CGC-PMS Codex AutoPilot started."
```

---

### 9.2 人工停止脚本

文件：

```text
scripts/codex-autopilot/autopilot-stop.ps1
```

```powershell
$Repo = "D:\projects-test\cgc-pms"
$AutoDir = Join-Path $Repo ".codex-autopilot"

if (!(Test-Path $AutoDir)) {
  New-Item -ItemType Directory -Path $AutoDir | Out-Null
}

"stop requested at $(Get-Date -Format s)" | Out-File -Encoding UTF8 "$AutoDir\stop.flag"
Remove-Item "$AutoDir\enabled.flag" -ErrorAction SilentlyContinue

Write-Host "Stop requested. Codex will stop at the next checkpoint."
```

说明：

```text
这是软停止。
不会强杀 Codex。
当前任务到下一个 checkpoint 后停止。
不会进入下一轮。
不会破坏正在写的文件。
```

---

### 9.3 暂停脚本

文件：

```text
scripts/codex-autopilot/autopilot-pause.ps1
```

```powershell
$Repo = "D:\projects-test\cgc-pms"
$AutoDir = Join-Path $Repo ".codex-autopilot"

if (!(Test-Path $AutoDir)) {
  New-Item -ItemType Directory -Path $AutoDir | Out-Null
}

"paused at $(Get-Date -Format s)" | Out-File -Encoding UTF8 "$AutoDir\pause.flag"

Write-Host "AutoPilot paused."
```

---

### 9.4 恢复脚本

文件：

```text
scripts/codex-autopilot/autopilot-resume.ps1
```

```powershell
$Repo = "D:\projects-test\cgc-pms"
$AutoDir = Join-Path $Repo ".codex-autopilot"

Remove-Item "$AutoDir\pause.flag" -ErrorAction SilentlyContinue

Write-Host "AutoPilot resumed."
```

---

### 9.5 状态查看脚本

文件：

```text
scripts/codex-autopilot/autopilot-status.ps1
```

```powershell
$Repo = "D:\projects-test\cgc-pms"
$State = Join-Path $Repo ".codex-autopilot\state.json"

if (Test-Path $State) {
  Get-Content $State
} else {
  Write-Host "No AutoPilot state found."
}
```

---

### 9.6 强制终止脚本

文件：

```text
scripts/codex-autopilot/autopilot-kill.ps1
```

```powershell
$Repo = "D:\projects-test\cgc-pms"
$AutoDir = Join-Path $Repo ".codex-autopilot"

if (!(Test-Path $AutoDir)) {
  New-Item -ItemType Directory -Path $AutoDir | Out-Null
}

"force kill requested at $(Get-Date -Format s)" | Out-File -Encoding UTF8 "$AutoDir\stop.flag"

Write-Host "Trying to stop Codex-related processes..."

Get-Process | Where-Object {
  $_.ProcessName -like "*codex*"
} | Stop-Process -Force -ErrorAction SilentlyContinue

Write-Host "Codex processes killed. Check git status before restarting."
```

注意：

```text
autopilot-kill.ps1 只用于 Codex 卡死时。
正常情况下优先使用 autopilot-stop.ps1。
```

---

## 10. Windows 定时任务脚本

### 10.1 安装定时任务

文件：

```text
scripts/codex-autopilot/install-schedule.ps1
```

```powershell
param(
  [string]$Repo = "D:\projects-test\cgc-pms",
  [string]$StartTime = "09:00",
  [string]$StopTime = "23:30"
)

$StartScript = Join-Path $Repo "scripts\codex-autopilot\autopilot-start.ps1"
$StopScript = Join-Path $Repo "scripts\codex-autopilot\autopilot-stop.ps1"

$StartAction = New-ScheduledTaskAction `
  -Execute "powershell.exe" `
  -Argument "-ExecutionPolicy Bypass -File `"$StartScript`""

$StartTrigger = New-ScheduledTaskTrigger -Daily -At $StartTime

Register-ScheduledTask `
  -TaskName "CGC-PMS Codex AutoPilot Start" `
  -Action $StartAction `
  -Trigger $StartTrigger `
  -Description "Start CGC-PMS Codex AutoPilot" `
  -Force

$StopAction = New-ScheduledTaskAction `
  -Execute "powershell.exe" `
  -Argument "-ExecutionPolicy Bypass -File `"$StopScript`""

$StopTrigger = New-ScheduledTaskTrigger -Daily -At $StopTime

Register-ScheduledTask `
  -TaskName "CGC-PMS Codex AutoPilot Stop" `
  -Action $StopAction `
  -Trigger $StopTrigger `
  -Description "Stop CGC-PMS Codex AutoPilot" `
  -Force

Write-Host "AutoPilot schedule installed."
Write-Host "Start time: $StartTime"
Write-Host "Stop time:  $StopTime"
```

使用：

```powershell
powershell -ExecutionPolicy Bypass -File D:\projects-test\cgc-pms\scripts\codex-autopilot\install-schedule.ps1 -StartTime "09:00" -StopTime "23:30"
```

---

### 10.2 卸载定时任务

文件：

```text
scripts/codex-autopilot/uninstall-schedule.ps1
```

```powershell
Unregister-ScheduledTask -TaskName "CGC-PMS Codex AutoPilot Start" -Confirm:$false -ErrorAction SilentlyContinue
Unregister-ScheduledTask -TaskName "CGC-PMS Codex AutoPilot Stop" -Confirm:$false -ErrorAction SilentlyContinue

Write-Host "AutoPilot schedule uninstalled."
```

使用：

```powershell
powershell -ExecutionPolicy Bypass -File D:\projects-test\cgc-pms\scripts\codex-autopilot\uninstall-schedule.ps1
```

---

## 11. Codex Desktop Automation Prompt

在 Codex 桌面端创建 Automation 时，可以使用以下 Prompt。

```markdown
# CGC-PMS Local AutoPilot

你是 CGC-PMS 项目的本地全托管自迭代执行 Agent。

## 运行模式

当前运行在本地 Codex Desktop Automation 中。

本项目允许全托管自动迭代：

- 可以自动规划
- 可以自动拆任务
- 可以自动写代码
- 可以自动运行测试
- 可以自动修改、删除、重建测试数据
- 可以按规则自动合并
- 不需要等待人工审查

## 必须先检查控制文件

在任何操作前，必须检查：

- `.codex-autopilot/enabled.flag`
- `.codex-autopilot/stop.flag`
- `.codex-autopilot/pause.flag`
- `.codex-autopilot/state.json`

如果不存在 `enabled.flag`，立即停止本轮，不修改代码。

如果存在 `stop.flag`，立即停止本轮，不修改代码。

如果存在 `pause.flag`，立即停止本轮，不修改代码。

## 必读文件

请先阅读：

- AGENTS.override.md
- AGENTS.md
- docs/backlog/cgc-pms-production-enhancement-plan.md
- docs/backlog/current-focus.md
- docs/backlog/ready-issues.md
- docs/backlog/blocked-issues.md
- scripts/codex-autopilot/codex-autopilot.config.json

## 每轮任务规则

每轮只能选择 1 个 Ready Issue。

选择规则：

1. 优先级最高。
2. 属于 current-focus 允许范围。
3. 有明确目标。
4. 有明确允许修改范围。
5. 有明确验收标准。
6. 有验证命令。
7. 可以在本轮完成。

如果没有合格任务，只更新 backlog，不写业务代码。

## 允许测试数据操作

本项目当前数据均为测试数据。

允许：

- 修改测试数据
- 删除测试数据
- 重建 dev/test/demo 数据库
- 清理本地 MinIO 测试文件
- 重建 demo seed 数据
- 执行 docker compose down -v
- 执行本地测试环境初始化脚本

但必须满足：

1. 只允许 dev/test/demo 环境。
2. 数据库 host 必须是 localhost 或 127.0.0.1。
3. 不允许连接生产数据库。
4. 不允许删除 Git 仓库外文件。
5. 不允许删除 `.git`。
6. 不允许删除用户目录。
7. 执行清库前必须检查 `.codex-autopilot/ALLOW_TEST_DATA_RESET` 是否存在。

## 自动合并规则

任务完成后，按规则自动合并，不等待人工审查。

自动合并前必须：

1. 运行对应验证命令。
2. 运行 `git diff --check`。
3. 自审变更范围。
4. 更新 iteration 报告。
5. 更新 done-issues 或 blocked-issues。
6. 确认没有 stop.flag。
7. 确认没有 pause.flag。

合并方式：

```bash
git checkout master
git merge --squash <当前任务分支>
git commit -m "[Codex][ISSUE-ID] 任务标题"
```

如果配置允许 `autoPush=true`，再执行：

```bash
git push origin master
```

## 失败处理

如果测试失败：

1. 尝试最多 2 轮修复。
2. 仍失败则停止当前 Issue。
3. 不合并。
4. 写入 `docs/backlog/blocked-issues.md`。
5. 写入 `docs/iterations/iteration-YYYY-MM-DD-report.md`。
6. 继续下一个可执行 Issue，除非存在 stop.flag。

## 每步 checkpoint

以下节点必须检查 stop.flag：

- 开始前
- 选任务后
- 修改代码前
- 执行测试前
- 合并前
- 合并后
- 进入下一轮前

如果发现 stop.flag，立即停止。

## 输出要求

每轮结束必须输出：

1. 本轮任务。
2. 修改摘要。
3. 修改文件。
4. 测试命令。
5. 测试结果。
6. 是否合并。
7. 是否推送。
8. 后续任务。
9. 当前状态。
```

---

## 12. AGENTS.override.md 追加规则

建议在 `AGENTS.override.md` 中追加：

```markdown
## Codex Local AutoPilot Rules

本仓库允许 Codex 在本地测试环境中进行全托管自迭代。

### 允许

- 自动拆解任务
- 自动开发
- 自动运行测试
- 自动修改测试数据
- 自动删除测试数据
- 自动重建 dev/test/demo 数据库
- 自动清理本地测试文件
- 自动合并通过规则门禁的任务
- 自动更新 backlog、iteration report、quality report

### 自动合并

本项目允许按规则自动合并，不等待人工审查。

合并前必须满足：

- 当前任务来自 ready-issues.md
- 当前任务属于 current-focus.md 允许范围
- 验证命令已执行
- git diff --check 通过
- 没有 stop.flag
- 没有 pause.flag
- 已更新 iteration report

### 测试数据

当前项目数据均为测试数据。允许 Codex 修改、删除、重建测试数据。

但必须确认：

- 运行环境是 dev/test/demo
- 数据库是 localhost 或 127.0.0.1
- 不连接生产库
- 不删除仓库外文件
- 不删除 .git
- 不删除用户目录

### 终止规则

每个关键节点必须检查：

- .codex-autopilot/stop.flag
- .codex-autopilot/pause.flag

发现 stop.flag 或 pause.flag 时，停止当前轮，不进入下一轮。

### 禁止

- 禁止发布生产环境
- 禁止连接生产数据库
- 禁止删除仓库外文件
- 禁止删除 .git
- 禁止删除用户目录
- 禁止在未运行验证命令时自动合并
- 禁止无 Issue 直接写代码
- 禁止跨 Epic 大范围修改
```

---

## 13. Backlog 文件模板

### 13.1 current-focus.md

```markdown
# Current Focus

## 当前阶段

P0：生产准入

## 当前允许执行的 Epic

- EPIC-001：CI/CD 上线门禁
- EPIC-002：预警中心 M1
- EPIC-003：权限、租户、项目边界加固
- EPIC-004：主链路回归
- EPIC-005：前端列表页生产化
- EPIC-006：文件上传与发票识别安全
- EPIC-007：生产监控、日志、备份恢复

## 当前禁止执行

- 总工程师驾驶舱
- BIM
- AI 辅助
- 财务自动化
- 自动发布生产
- 连接生产数据库

## 每轮最大工作量

- 每轮最多处理 1 个 Issue
- 每轮最多创建 1 次合并
- 每轮修改文件不超过 20 个
- 不允许跨 Epic 修改

## 自动合并策略

允许自动合并：

- docs/**
- scripts/codex-autopilot/**
- 测试文件
- 非业务逻辑 lint 修复
- 后端 / 前端小范围明确任务

禁止自动执行：

- 发布生产
- 连接生产数据库
- 删除仓库外文件
```

---

### 13.2 epics.md

```markdown
# Epics

## P0：生产准入

- EPIC-001：CI/CD 上线门禁
- EPIC-002：预警中心 M1 生产化
- EPIC-003：权限、租户、项目边界加固
- EPIC-004：主链路回归

## P1：核心加固

- EPIC-005：前端列表页生产化
- EPIC-006：文件上传与发票识别安全
- EPIC-007：生产监控、日志、备份恢复

## P2：业务增强

- EPIC-008：报表中心
- EPIC-009：规则治理中心
- EPIC-010：通知平台
- EPIC-011：WBS、进度计划与甘特图
- EPIC-012：供应商评分与采购增强

## P3：行业深化

- EPIC-013：现场日报与移动端
- EPIC-014：财务集成与月结锁账
- EPIC-015：技术域与总工程师驾驶舱
- EPIC-016：BIM / IFC 与模型问题管理
- EPIC-017：AI 辅助能力
```

---

### 13.3 ready-issues.md

````markdown
# Ready Issues

## P0

### ISSUE-000-001：搭建本地 Codex AutoPilot 目录和控制脚本

优先级：P0  
类型：治理 / 脚本  
状态：Ready  
自动合并：允许  

#### 目标

搭建本地 Codex AutoPilot 全托管自迭代系统的基础目录、控制脚本和 backlog 文件。

#### 允许修改

- scripts/codex-autopilot/**
- .codex-autopilot/**
- docs/backlog/**
- docs/iterations/**
- AGENTS.override.md

#### 禁止修改

- backend/**
- frontend-admin/**
- 数据库 migration
- 业务代码

#### 验收标准

- [ ] start 脚本可写入 enabled.flag
- [ ] stop 脚本可写入 stop.flag
- [ ] pause / resume 可控制 pause.flag
- [ ] status 可读取 state.json
- [ ] install-schedule 可注册 Windows 定时任务
- [ ] uninstall-schedule 可删除 Windows 定时任务
- [ ] config 文件包含 autoMerge / autoPush / 测试数据策略
- [ ] AGENTS.override.md 追加 AutoPilot 规则

#### 验证命令

```powershell
powershell -ExecutionPolicy Bypass -File scripts/codex-autopilot/autopilot-start.ps1
powershell -ExecutionPolicy Bypass -File scripts/codex-autopilot/autopilot-status.ps1
powershell -ExecutionPolicy Bypass -File scripts/codex-autopilot/autopilot-stop.ps1
```
````

---

### 13.4 blocked-issues.md

```markdown
# Blocked Issues

## 当前阻塞任务

暂无。

## 示例

### ISSUE-015-001：总工程师驾驶舱

阻塞原因：

- 缺少技术方案业务对象
- 缺少设计协调业务对象
- 缺少技术审核业务对象
- 缺少重大技术问题闭环

解除条件：

- technical 模块完成
- 技术问题状态流转完成
- 技术域接口和页面完成
- 总工程师指标来自真实技术域数据
```

---

### 13.5 done-issues.md

```markdown
# Done Issues

记录已完成任务。

## 格式

### ISSUE-xxx：任务标题

完成日期：YYYY-MM-DD  
合并方式：auto-merge / manual  
验证结果：通过 / 部分通过 / 未执行  
相关报告：docs/iterations/iteration-YYYY-MM-DD-report.md
```

---

## 14. 推荐运行节奏

### 14.1 初期稳态模式

```text
每日 09:00 启动
每日 23:30 停止
每轮最多 1 个 Issue
每轮最长 90 分钟
每轮失败最多自修 2 次
autoMerge = true
autoPush = false
```

---

### 14.2 激进模式

```text
每日 09:00 启动
每日 23:30 停止
每 30 分钟跑一轮
每天最多 10 个 Issue
失败最多自修 2 次
所有通过门禁任务自动合并
autoMerge = true
autoPush = true
```

---

### 14.3 保守模式

```text
每日 10:00 启动
每日 18:00 停止
每 2 小时跑一轮
每天最多 4 个 Issue
失败任务不自修，直接 blocked
autoMerge = true
autoPush = false
```

---

## 15. 人工操作命令

### 15.1 启动 AutoPilot

```powershell
powershell -ExecutionPolicy Bypass -File D:\projects-test\cgc-pms\scripts\codex-autopilot\autopilot-start.ps1
```

### 15.2 停止 AutoPilot

```powershell
powershell -ExecutionPolicy Bypass -File D:\projects-test\cgc-pms\scripts\codex-autopilot\autopilot-stop.ps1
```

### 15.3 暂停 AutoPilot

```powershell
powershell -ExecutionPolicy Bypass -File D:\projects-test\cgc-pms\scripts\codex-autopilot\autopilot-pause.ps1
```

### 15.4 恢复 AutoPilot

```powershell
powershell -ExecutionPolicy Bypass -File D:\projects-test\cgc-pms\scripts\codex-autopilot\autopilot-resume.ps1
```

### 15.5 查看状态

```powershell
powershell -ExecutionPolicy Bypass -File D:\projects-test\cgc-pms\scripts\codex-autopilot\autopilot-status.ps1
```

### 15.6 强制终止

```powershell
powershell -ExecutionPolicy Bypass -File D:\projects-test\cgc-pms\scripts\codex-autopilot\autopilot-kill.ps1
```

### 15.7 安装定时任务

```powershell
powershell -ExecutionPolicy Bypass -File D:\projects-test\cgc-pms\scripts\codex-autopilot\install-schedule.ps1 -StartTime "09:00" -StopTime "23:30"
```

### 15.8 卸载定时任务

```powershell
powershell -ExecutionPolicy Bypass -File D:\projects-test\cgc-pms\scripts\codex-autopilot\uninstall-schedule.ps1
```

---

## 16. 第一轮 Codex 初始化任务

将下面内容直接发给 Codex 桌面端。

```text
请为 CGC-PMS 搭建本地 Codex AutoPilot 全托管自迭代系统。

背景：
我在本地 Windows 设备上使用 Codex 桌面端，不使用 GitHub Actions。
当前项目数据都是测试数据，允许修改、删除、重建。
我希望系统支持定时启动、定时终止、人工启动、人工终止。
我希望 Codex 按规则自动合并，不等待人工审查。

只允许新增或修改：

- scripts/codex-autopilot/**
- .codex-autopilot/**
- docs/backlog/**
- docs/iterations/**
- AGENTS.override.md

禁止修改：

- backend/**
- frontend-admin/**
- 数据库 migration
- 业务代码

目标：

1. 新增 autopilot-start.ps1。
2. 新增 autopilot-stop.ps1。
3. 新增 autopilot-pause.ps1。
4. 新增 autopilot-resume.ps1。
5. 新增 autopilot-status.ps1。
6. 新增 autopilot-kill.ps1。
7. 新增 install-schedule.ps1。
8. 新增 uninstall-schedule.ps1。
9. 新增 codex-autopilot.config.json。
10. 新增 .codex-autopilot/ALLOW_TEST_DATA_RESET。
11. 新增 docs/backlog/current-focus.md。
12. 新增 docs/backlog/epics.md。
13. 新增 docs/backlog/ready-issues.md。
14. 新增 docs/backlog/blocked-issues.md。
15. 新增 docs/backlog/done-issues.md。
16. 新增 docs/iterations/README.md。
17. 在 AGENTS.override.md 追加 Codex Local AutoPilot Rules。

实现要求：

- start 脚本写入 enabled.flag，删除 stop.flag 和 pause.flag。
- stop 脚本写入 stop.flag，删除 enabled.flag。
- pause 脚本写入 pause.flag。
- resume 脚本删除 pause.flag。
- status 脚本读取 state.json。
- kill 脚本强制终止 codex 相关进程，仅用于紧急情况。
- install-schedule.ps1 支持设置每天启动时间和停止时间。
- uninstall-schedule.ps1 支持删除计划任务。
- config 中包含 autoMerge=true、autoPush=false、allowTestDataMutation=true、allowTestDataDeletion=true。
- 所有脚本路径默认使用 D:\projects-test\cgc-pms。
- 不允许改业务代码。
- 不允许执行真实开发任务。
- 最后输出使用说明。
```

---

## 17. 第二轮 Codex 任务建议

第一轮完成 AutoPilot 框架后，第二轮可以启动治理任务：

```text
请基于 docs/backlog/cgc-pms-production-enhancement-plan.md 和 docs/backlog/epics.md，
将 P0 阶段拆成可执行 Ready Issues。

只允许修改：

- docs/backlog/ready-issues.md
- docs/backlog/blocked-issues.md
- docs/backlog/current-focus.md
- docs/iterations/iteration-YYYY-MM-DD-plan.md

禁止修改业务代码。

要求：

1. 每个 Issue 必须有目标、范围、禁止范围、验收标准、验证命令。
2. 每个 Issue 必须能在一轮 AutoPilot 中完成。
3. 模糊任务只能拆成产品澄清任务或技术设计任务。
4. 不得创建总工程师、BIM、AI、财务生产集成任务。
```

---

## 18. 第三轮 Codex 任务建议

第三轮再进入真实开发：

```text
请执行 docs/backlog/ready-issues.md 中优先级最高的 P0 Ready Issue。

要求：

1. 每轮只处理 1 个 Issue。
2. 严格遵守 current-focus。
3. 执行验证命令。
4. 测试失败最多自修 2 次。
5. 成功后按规则自动合并。
6. 更新 done-issues.md。
7. 更新 iteration report。
8. 如失败，写入 blocked-issues.md。
```

---

## 19. 最终定义

本方案中的“Codex 全托管自迭代”定义为：

```text
本地 Codex Desktop Automation
  + PowerShell 启停控制
  + start / stop / pause / resume flag
  + worktree 或分支隔离
  + 测试数据可重置
  + 自动测试
  + 自动合并
  + 自动归档
  + 自动下一轮
```

不再是：

```text
Codex 写完等人工审查
```

而是：

```text
Codex 按规则自己跑、自己测、自己合、自己继续
```

只保留两个硬边界：

```text
1. 不自动发布生产
2. 不连接生产数据库
```

---

## 20. 推荐落地顺序

```text
第 1 步：让 Codex 生成 AutoPilot 脚本和 backlog 框架
第 2 步：人工检查脚本路径和定时任务
第 3 步：运行 autopilot-start.ps1 / autopilot-status.ps1 / autopilot-stop.ps1
第 4 步：在 Codex 桌面端创建 Automation
第 5 步：让 Codex 每轮读取 enabled.flag 后执行
第 6 步：先 autoMerge=true、autoPush=false 跑 2～3 天
第 7 步：稳定后再开启 autoPush=true
第 8 步：逐步扩大 allowedEpics
```

---

## 21. 当前建议配置

建议初期使用：

```json
{
  "autoMerge": true,
  "autoPush": false,
  "allowTestDataMutation": true,
  "allowTestDataDeletion": true,
  "maxIssuesPerRun": 1,
  "maxFilesChangedPerIssue": 20,
  "maxRunMinutes": 90,
  "cycleIntervalMinutes": 30
}
```

等稳定后再改为：

```json
{
  "autoMerge": true,
  "autoPush": true
}
```

---

## 22. 一句话总结

CGC-PMS 的 Codex 全托管方案不是让 Codex 无限制长跑，而是让它在本地通过 AutoPilot 控制器执行：

```text
定时启动
  -> 单任务执行
  -> 自动测试
  -> 自动合并
  -> 自动归档
  -> 自动下一轮
  -> 定时停止
```

这样既能实现“自主迭代”，又能避免长期模糊任务失控。
