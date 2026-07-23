---
name: cgc-pms-mainline-owner-flow
description: Use only when the user explicitly requests cgc-pms mainline, Backlog or AutoPilot governance, formal acceptance/release adjudication, a governed plan, or cross-module closeout.
---

# cgc-pms 主线、计划与收口

根规则由 Codex 自动加载。本 Skill 只保存主线、计划、阶段控制和正式收口规则。

## 触发边界

- 用户明确要求按主线、Backlog 或 AutoPilot 治理推进。
- 用户要求正式计划书、验收、上线裁决或跨模块收口。
- 普通问答、审查、单文件修复或普通交互实施不因出现“计划/实施”字样自动触发。

## 主线启动

1. 明确目标、范围、非目标、验收标准、风险和回滚边界。
2. 计划书写入 `docs/plans/第N条主线-<主题>任务计划书.md`；阶段计划可用 `第N条主线-Mx-<主题>任务计划书-YYYY-MM-DD.md`。
3. 第一段必须包含 `**Goal:**` 与 `**Architecture:**`；Architecture 写明复用边界、禁止扩展范围和最小可行方案。
4. 轻量计划保留目标、架构、范围/非目标、任务、验收、风险/回滚；跨模块增加阶段门和文件范围；权限、金额、租户、数据一致性或状态机再增加恢复矩阵与金丝雀。
5. 授权通过后由主线程实施；重新评估点限于阶段切换、首次不通过、实施转验收或用户改变约束。

## 专项路由

- 失败分类、CI、PR 与同 SHA 门禁：`../cgc-pms-ci-gate-triage/SKILL.md`。
- 运行态与浏览器验收：`../cgc-pms-runtime-refresh/SKILL.md`。
- Git 提交、推送、合并与清理：`git-publish-and-cleanup`。
- AutoPilot 触发、Ready、checkpoint、恢复、评分和金丝雀：`../../../plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md` 及其 references/config/Schema。

普通主线不读取 AutoPilot checkpoint、fencing、评分或控制面文件；只有 AutoPilot 触发或计划明确涉及控制面时按需读取。

## 阶段与验证

- 阶段输出必须绑定当前分支、工作区、实际 diff、验证命令和正式交付物。
- 验证强度按风险递增：目标静态/单测 → 模块回归/类型/构建 → 批次验收 → 发布门禁；仍绑定当前代码和环境的证据可复用。
- 工具或测试失败先走统一分类；参数修正、环境恢复或新证据出现后才允许复跑。
- 长任务只在开始、方案/裁决变化、阶段切换、真实阻塞或超过 60 秒且有新进展时播报；相同状态与纯等待保持静默。
- 中断前记录目标、授权、分支/工作区、已改文件、有效验证、失败分类、剩余步骤和 Git 权限；恢复时先核验事实再继续。

## 正式验收与零悬空

- 主线程只采信绑定当前事实的交付物、验证、Git 状态和正式问题载体；临时日志、截图名、run id 和会话草稿不入长期规则。
- 每个发现项按根规则归类；无唯一载体的问题不得判定通过。
- 输出按任务类型最小包含：
  - 计划：计划书、范围、验收、实施前置。
  - 实施：修改、验证、Git 状态、剩余风险。
  - 验收：通过/不通过、阻塞/非阻塞、依据、剩余风险。
  - 上线裁决：是否可上线、目标环境证据、回滚与数据风险。
- 收口必须给出新增后续项、关闭后续项、净变化；主线或 Ready 完成后按权威产品/AutoPilot 入口回写项目地图及相关状态。

## 维护检查

- 所有引用存在；计划命名与 profile 测试可执行。
- 不复制根硬门禁、失败分类、运行态、Git 发布或 AutoPilot 动态值。
- 行为性控制面修改纳入指纹，并停在用户明确启动的单 Issue 金丝雀前。
