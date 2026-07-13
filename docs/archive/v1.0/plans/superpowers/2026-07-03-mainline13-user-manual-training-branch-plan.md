# 第13条主线延期项支线计划：用户手册和培训材料最小交付包

**计划属性**：支线计划，不是已执行结果  
**计划日期**：2026-07-03  
**选题**：用户手册和培训材料最小交付包  
**当前定位**：
- 对第 13 条主线当前上线收口：非阻塞
- 对试点交付与培训落地：阻塞

**编写依据**：
- 当前有效入口以 `D:\projects-test\cgc-pms\docs\README.md` 为准。
- 第 13 条主线当前有效收口结论以 `D:\projects-test\cgc-pms\docs\quality\mainline-13-closeout-2026-07-03.md` 为准，结论为“通过 / 非阻塞 / 可进入上线准备”。
- `D:\projects-test\cgc-pms\docs\plans\第13条-主线后续任务计划-2026-07-03.md` 已将“用户手册和培训材料”列为延期处理项。
- `.archive\doc\06-用户手册\用户操作手册.md` 与 `.archive\doc\06-用户手册\管理员手册.md` 仅可作为历史骨架参考，不得直接视为当前现行手册。

## 一、任务拆解

### 模块 A：文档基线校准

#### 任务 A1：现行口径与历史口径切分
- 任务名称与简要说明：先建立“当前有效手册基线”，明确哪些内容来自当前有效文档，哪些内容只是历史参考，避免把 `.archive` 旧手册直接挂成现行版本。
- 输入：
  - `D:\projects-test\cgc-pms\docs\README.md`
  - `D:\projects-test\cgc-pms\docs\03-业务模块说明.md`
  - `D:\projects-test\cgc-pms\docs\08-权限与审批流程.md`
  - `D:\projects-test\cgc-pms\docs\10-部署运维手册.md`
  - 历史参考：`.archive\doc\06-用户手册\用户操作手册.md`、`.archive\doc\06-用户手册\管理员手册.md`
- 输出：
  - 一份手册基线说明，定义“现行依据”“历史参考”“待确认缺口”
- 涉及的文件建议：
  - ✨ `D:\projects-test\cgc-pms\docs\manuals\README.md`
- 复杂度：P1（重要）
- 验收标准：
  - Given 当前有效文档与历史文档并存，When 编写基线说明，Then 明确区分“当前有效依据”和“历史参考”，不得把 `.archive` 内容直接写成现状事实。
  - Given 某模块只有历史手册无当前依据，When 列入手册范围，Then 该项必须标记为“需要确认”或“待补证据”。
  - Given 第 13 条主线收口已通过，When 描述本支线定位，Then 必须写明“对上线收口非阻塞、对试点交付与培训阻塞”。

#### 任务 A2：最小交付边界定义
- 任务名称与简要说明：将文档范围限制在最小交付包，不扩成完整课程体系、视频体系或知识库平台建设。
- 输入：
  - A1 的基线说明
  - `D:\projects-test\cgc-pms\docs\quality\mainline-13-closeout-2026-07-03.md`
- 输出：
  - 一份范围声明，约束本轮只做终端用户手册、管理员手册、角色化培训脚本、FAQ 回流与入口挂接
- 涉及的文件建议：
  - 🔨 `D:\projects-test\cgc-pms\docs\manuals\README.md`
  - ✨ `D:\projects-test\cgc-pms\docs\training\README.md`
- 复杂度：P1（重要）
- 验收标准：
  - Given 试点需要培训材料，When 定义范围，Then 只覆盖文档交付，不包含录屏、考试题库、LMS、自动化知识库等扩展工程。
  - Given 范围裁剪，When 输出计划，Then 每个产物都能落到明确文件，而不是抽象到“后续补充”。
- 依赖：任务 A1

### 模块 B：终端用户手册

#### 任务 B1：终端用户手册最小版
- 任务名称与简要说明：面向试点终端用户编写最小操作手册，覆盖登录、导航、核心模块入口、主路径操作和常见错误提示。
- 输入：
  - A1/A2 的基线与范围
  - `D:\projects-test\cgc-pms\docs\03-业务模块说明.md`
  - `D:\projects-test\cgc-pms\docs\08-权限与审批流程.md`
  - 历史骨架参考：`.archive\doc\06-用户手册\用户操作手册.md`
- 输出：
  - 一份现行终端用户手册最小版
- 涉及的文件建议：
  - ✨ `D:\projects-test\cgc-pms\docs\manuals\end-user-manual.md`
- 复杂度：P0（核心阻塞）
- 验收标准：
  - Given 新试点用户首次进入系统，When 阅读手册，Then 能理解登录入口、菜单定位、角色边界和最小主路径。
  - Given 某功能当前不在第 13 条主线收口范围，When 手册提及该能力，Then 必须标注“非本轮最小交付范围”或不纳入正文。
  - Given 界面、权限或流程仍需当前证据确认，When 写入手册，Then 必须标成“需要确认”，不能伪造稳定截图结论。
  - Given 用户操作失败场景，When 手册描述异常提示，Then 至少覆盖权限不足、审批未配置、列表无数据三类常见问题。
- 依赖：任务 A1、任务 A2

### 模块 C：管理员手册

#### 任务 C1：管理员手册最小版
- 任务名称与简要说明：面向系统管理员/实施管理员编写最小操作手册，重点覆盖账号权限、角色开通、审批配置入口、常见交付前检查项。
- 输入：
  - A1/A2 的基线与范围
  - `D:\projects-test\cgc-pms\docs\08-权限与审批流程.md`
  - `D:\projects-test\cgc-pms\docs\10-部署运维手册.md`
  - `D:\projects-test\cgc-pms\docs\README.md`
  - 历史骨架参考：`.archive\doc\06-用户手册\管理员手册.md`
- 输出：
  - 一份现行管理员手册最小版
- 涉及的文件建议：
  - ✨ `D:\projects-test\cgc-pms\docs\manuals\admin-manual.md`
- 复杂度：P0（核心阻塞）
- 验收标准：
  - Given 试点交付前需要开通与配置，When 管理员阅读手册，Then 能找到角色权限、审批配置、基础检查入口。
  - Given 历史管理员手册包含旧阶段迁移和旧版本步骤，When 编写当前手册，Then 必须剔除明显阶段化旧内容，只保留现行适用项或标注历史属性。
  - Given 管理员执行最小交付准备，When 按手册核对，Then 至少有“账号/角色”“菜单权限”“审批配置”“交付前检查”四个章节。
  - Given 某运维动作超出本轮范围，When 手册涉及，Then 统一跳转到当前有效 `10-部署运维手册`，不在本手册重复展开。
- 依赖：任务 A1、任务 A2

### 模块 D：角色化培训脚本

#### 任务 D1：角色化培训脚本最小包
- 任务名称与简要说明：按试点培训的最小需要整理角色化培训脚本，重点覆盖演示顺序、每个角色的目标动作、常见提问点和讲解时长。
- 输入：
  - B1 终端用户手册
  - C1 管理员手册
  - `D:\projects-test\cgc-pms\docs\03-业务模块说明.md`
- 输出：
  - 一份角色化培训脚本
- 涉及的文件建议：
  - ✨ `D:\projects-test\cgc-pms\docs\training\role-based-training-script.md`
- 复杂度：P0（核心阻塞）
- 验收标准：
  - Given 试点培训需要快速开场，When 使用脚本，Then 至少能覆盖终端用户、管理员两大类对象。
  - Given 终端用户角色不同，When 拆分培训脚本，Then 至少区分项目经理/商务经理/采购经理/生产经理/系统管理员五类角色的培训入口或讲解重点。
  - Given 培训时间有限，When 编排脚本，Then 每个角色培训段必须包含目标、关键演示步骤、常见疑问、建议时长。
  - Given 某角色当前未列入主线收口可演示范围，When 编排脚本，Then 不能强行纳入正式试点培训主清单。
- 依赖：任务 B1、任务 C1

### 模块 E：FAQ / 问题回流

#### 任务 E1：FAQ 与问题回流机制最小化
- 任务名称与简要说明：定义培训现场和试点早期的常见问题记录方式，确保问题能回流为后续文档补充、测试补充或需求澄清，而不是散落在聊天记录中。
- 输入：
  - B1 终端用户手册
  - C1 管理员手册
  - D1 角色化培训脚本
  - 当前有效质量结论与未来计划入口
- 输出：
  - 一份 FAQ 与问题回流说明
- 涉及的文件建议：
  - ✨ `D:\projects-test\cgc-pms\docs\training\faq-feedback-loop.md`
- 复杂度：P1（重要）
- 验收标准：
  - Given 培训中出现高频问题，When 记录 FAQ，Then 每条至少包含问题、适用角色、临时答案、是否需要回流处理。
  - Given 问题需要继续处理，When 回流，Then 至少能分流到“文档补充”“测试补充”“产品/流程确认”三类。
  - Given 问题尚未核实，When 写入 FAQ，Then 必须标记“需要确认”，不得写成确定结论。
- 依赖：任务 D1

### 模块 F：入口挂接

#### 任务 F1：文档入口挂接与可发现性
- 任务名称与简要说明：把手册与培训材料挂到当前有效文档入口，降低试点交付时“找不到文档”的问题。
- 输入：
  - A1-A2 的基线说明
  - B1/C1/D1/E1 产物路径
  - `D:\projects-test\cgc-pms\docs\README.md`
- 输出：
  - `docs/README.md` 中新增“用户手册与培训”入口
  - `docs/manuals/README.md` 与 `docs/training/README.md` 形成子索引
- 涉及的文件建议：
  - 🔨 `D:\projects-test\cgc-pms\docs\README.md`
  - 🔨 `D:\projects-test\cgc-pms\docs\manuals\README.md`
  - 🔨 `D:\projects-test\cgc-pms\docs\training\README.md`
- 复杂度：P1（重要）
- 验收标准：
  - Given 交付人员只知道从 `docs/README.md` 进入，When 打开文档中心，Then 能直接看到手册与培训入口。
  - Given 现行与历史材料同时存在，When 点击入口，Then 默认只进入当前有效目录，不直接跳转 `.archive`。
  - Given 某文档尚未编写完，When 入口挂接，Then 必须写清“待补齐”状态，不能伪装成已完成。
- 依赖：任务 A1、任务 B1、任务 C1、任务 D1、任务 E1

## 二、改动文件清单与计划

- ✨ `D:\projects-test\cgc-pms\docs\manuals\README.md`
  - 作为当前手册基线入口，说明适用范围、现行依据、历史参考边界。
  - 不承诺业务实现状态，只承诺文档索引和口径统一。

- ✨ `D:\projects-test\cgc-pms\docs\manuals\end-user-manual.md`
  - 面向终端用户的最小操作手册，按角色常用路径组织，不追求全模块百科全书式覆盖。
  - 内容优先引用当前有效文档，历史手册只作章节骨架参考。

- ✨ `D:\projects-test\cgc-pms\docs\manuals\admin-manual.md`
  - 面向管理员的最小交付手册，聚焦权限、审批、交付前检查和跳转现行运维文档。
  - 不复制部署大段细节，避免与现行运维文档双维护。

- ✨ `D:\projects-test\cgc-pms\docs\training\README.md`
  - 作为培训材料入口，说明培训对象、文件清单、FAQ 回流方式。
  - 保持最小交付包定位，不扩成长期培训体系。

- ✨ `D:\projects-test\cgc-pms\docs\training\role-based-training-script.md`
  - 提供按角色分段的培训脚本，覆盖目标、演示步骤、时长和常见问题。
  - 优先保证试点培训可执行，而不是追求完美课程设计。

- ✨ `D:\projects-test\cgc-pms\docs\training\faq-feedback-loop.md`
  - 提供 FAQ 模板和问题回流规则，作为培训后续补文档和补验证的入口。
  - 明确哪些问题只是记录，哪些问题需要升级到计划或质量结论。

- 🔨 `D:\projects-test\cgc-pms\docs\README.md`
  - 新增“用户手册与培训”入口，挂接现行目录。
  - 强调 `.archive` 下旧手册属于历史资料，不替代当前材料。

## 三、数据流变化

本支线是文档交付计划，不涉及业务前后端接口、数据库结构或运行态配置变更。

本支线的“数据流”只涉及文档使用流与问题回流流：

1. 交付人员或试点用户进入 `docs/README.md`
2. 跳转到 `docs/manuals/README.md` 或 `docs/training/README.md`
3. 终端用户读取 `end-user-manual.md`，管理员读取 `admin-manual.md`
4. 培训执行人按 `role-based-training-script.md` 组织演示
5. 培训现场问题沉淀到 `faq-feedback-loop.md`
6. 回流问题再按性质进入文档补充、测试补充或“需要确认”清单

如需状态管理变化，仅限文档状态：
- “当前有效”
- “历史参考”
- “需要确认”
- “待补齐”

不得把文档状态词误写成系统功能状态词。

## 四、影响范围与回归测试建议

- 影响范围：`docs/README.md` 文档中心导航
  - 回归测试建议：检查新增入口后，原有“主线文档 / 质量与审计 / 其他资料”导航不被破坏，链接仍可打开。

- 影响范围：试点交付口径
  - 回归测试建议：核对支线计划与 `mainline-13-closeout-2026-07-03.md` 结论一致，必须保持“上线收口非阻塞、试点培训阻塞”。

- 影响范围：终端用户培训准备
  - 回归测试建议：随机抽取一个终端用户角色，检查是否能从手册找到登录、入口、主路径、异常提示四类信息。

- 影响范围：管理员交付准备
  - 回归测试建议：检查管理员手册是否包含账号角色、菜单权限、审批配置、交付前检查四类章节。

- 影响范围：FAQ 问题回流
  - 回归测试建议：模拟记录 3 条培训问题，确认都能落入“文档补充 / 测试补充 / 需要确认”之一，而不是悬空。

- 影响范围：历史文档使用边界
  - 回归测试建议：检查所有新增现行入口均未直接把 `.archive` 旧手册作为默认跳转目标。

## 五、冒烟测试方案

1. 打开 `docs/README.md`，应能找到“用户手册与培训”入口，且入口文本明确这是当前有效材料。
2. 打开 `docs/manuals/README.md`，应能看到现行依据、历史参考边界和待确认口径说明。
3. 打开 `docs/manuals/end-user-manual.md`，应能找到终端用户登录、菜单定位、核心操作路径和常见错误说明。
4. 打开 `docs/manuals/admin-manual.md`，应能找到角色权限、审批配置、交付前检查和运维文档跳转说明。
5. 打开 `docs/training/role-based-training-script.md`，应至少覆盖项目经理、商务经理、采购经理、生产经理、系统管理员的培训分段。
6. 打开 `docs/training/faq-feedback-loop.md`，应能看到 FAQ 记录字段和问题回流分类规则。
7. 抽查任一正文，若引用历史手册内容，应标注其历史属性，而不是写成“当前已确认事实”。
8. 抽查任一正文，若出现待确认项，应明确标注“需要确认”，不能使用模糊放行措辞。
9. 对照 `docs/quality/mainline-13-closeout-2026-07-03.md`，计划中的阻塞口径必须是“试点交付与培训阻塞”，不能误写成“上线阻塞”。

## 六、计划书写入

- 路径：`D:\projects-test\cgc-pms\docs\superpowers\plans`
- 文件名：`2026-07-03-mainline13-user-manual-training-branch-plan.md`
- 本文件绝对路径：`D:\projects-test\cgc-pms\docs\superpowers\plans\2026-07-03-mainline13-user-manual-training-branch-plan.md`
- 说明：本文件仅定义“用户手册和培训材料最小交付包”的执行计划，不代表 `docs/manuals`、`docs/training`、`docs/README.md` 已实际完成改造。
