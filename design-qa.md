# 第53条主线 M2 驾驶舱工作区 Design QA

- source visual truth path: `C:\Users\SUMMAD~1\AppData\Local\Temp\codex-clipboard-bf7110e7-c69e-4e8d-9a4c-eee7b8d0f7a7.png`
- implementation screenshot path: `C:\Users\summade87114\AppData\Local\Temp\cgc-pms-dashboard-trend-final.png`
- full-view comparison evidence: `C:\Users\summade87114\AppData\Local\Temp\cgc-pms-dashboard-comparison-final.png`
- focused comparison evidence: `C:\Users\summade87114\AppData\Local\Temp\cgc-pms-dashboard-comparison-top.png`、`C:\Users\summade87114\AppData\Local\Temp\cgc-pms-dashboard-comparison-middle.png`
- annotation comparison evidence: `C:\Users\summade87114\AppData\Local\Temp\cgc-pms-dashboard-compact-comparison.png`
- viewport: 参考图 `1232 × 933`；实现内容区 `1240 × 1004`，对比时归一化为 `1232 × 933`
- state: 本地 `cgc_pms_demo_v2`，`demo.cost` 成本经理，全部项目、全部报告期，真实接口数据

## Findings

- 无剩余 P0/P1/P2。
- 参考图健康度为 76 分红色关注态；当前真实数据为 100 分稳健态，因此圆环使用蓝色成功语义。结构、尺寸和数字层级一致，差异来自数据状态。
- 参考图与当前接口均可出现空趋势、零预警；实现保留完整卡片、筛选、表头和真实空状态，不注入模拟数据。
- 成本科目分解是 M2 已验收能力，保留在参考工作区下方；它位于参考图首屏之后，不改变参考布局主层级。
- 快捷入口沿用参考图的七列结构，但仅链接当前页已验收区块，未开放尚未迁移的旧版业务路由。
- 标注复验：最高风险入口已移至健康卡 Header 的刷新按钮右侧；健康内容区实测 `168px`，趋势/预警双栏实测 `330px`，页面宽度 `1440px` 时无横向溢出。

## Required Fidelity Surfaces

- Fonts and typography: 使用 V2 中文系统字体栈；标题、说明、指标、单位、表头层级与参考图一致。金额压缩为万元后无折行、裁切或溢出。
- Spacing and layout rhythm: 健康度横向总览、趋势/预警双栏、快捷入口/最近打开三层结构一致；卡片间距 12px，主区域比例和参考图一致。
- Colors and visual tokens: 主色蓝、成本目标蓝、动态成本青、正向绿、负向红均映射 V2 语义令牌；健康圆环根据真实健康状态变化。
- Image quality and asset fidelity: 参考工作区无业务位图。圆环为真实数据驱动的原生 Canvas 图表；快捷入口复用项目现有 `currentColor` 导航图标，无 Emoji 或占位图。
- Copy and content: 保留项目经营健康度、经营趋势、经营预警与待办、快捷入口、最近打开等参考文案；项目、角色、金额和风险内容来自真实接口。

## Full-view Comparison Evidence

- 参考图和最终实现已置于同一 `2464 × 933` 对比输入中检查。
- 主要区域顺序、左右栏比例、卡片高度、指标分栏和底部工具区无可执行 P0/P1/P2 差异。

## Focused Region Comparison Evidence

- 顶部 `290px` 对比确认健康圆环、最高风险、四项指标、边框和基线对齐。
- 中部 `480px` 对比确认趋势/预警双栏比例、工具栏、表头、空状态和底部指标区对齐。

## Primary Interactions Tested

- 成本经理真实账号加载、全部项目/全部报告期默认值、具体项目切换和恢复全部。
- 趋势时间范围切换、预警筛选、页内快捷入口、刷新、管理员八角色切换。
- 1440 × 900、1024 × 768、390 × 844 三个视口无页面级横向溢出。
- 浏览器控制台无 warning/error；Axe serious/critical 违规为 0。

## Comparison History

1. 首次实现 P1：隐藏标题和表格说明外露，金额以元显示造成折行。修复隐藏工具类，并统一金额为万元。
2. 首次构建门禁：通用图表依赖使 vendor 包达到 644KB。移除依赖，改用原生 Canvas 健康圆环，最终最大 vendor 包 111.33KB。
3. 首次浏览器复验 P1：负利润错误使用成功色。修复成本偏差和利润的正负语义映射。
4. 首次 Axe 复验 P1：空状态辅助文字对比度不足。由 disabled 色调整为 muted 色，复验 serious/critical 为 0。
5. 最终同视口全图和重点区域对比无剩余 P0/P1/P2。
6. 浏览器标注复验：风险入口从正文移至 Header；健康总览由 `222px` 压缩至 `168px`，趋势空态由 `292px` 压缩至 `180px`。实际双栏总高度 `330px`，控制台无 warning/error。
7. 分数标注复验：移除圆环内 `/100` 比例文字；浏览器实测仅保留分数和健康状态，无横向溢出及控制台 warning/error。
8. 趋势联动复验：补入项目级 7 个月真实演示快照并使用原生 Canvas 绘图；全部项目/在建项目均为 7 点，近 6 个月为 6 点，近 3 个月为 3 点。删除趋势卡片底部重复 KPI，保留健康总览唯一展示。

## Implementation Checklist

- [x] 参考图三层信息架构
- [x] 真实数据健康度、指标、风险和成本趋势
- [x] 桌面、平板、移动端响应式
- [x] 颜色语义、键盘语义和对比度
- [x] 单测、Lint、类型、构建、包体积、Clean-room、真实浏览器验收

## Follow-up Polish

- 无需新增后续项。

final result: passed

---

# 审批工作台移动端筛选与详情弹窗设计 QA

- source visual truth: 2026-07-20 浏览器标注截图（430 x 932，`/v2/approval/done` 与审批详情）
- viewport: 430 x 932
- states: `/v2/approval/done`；`/v2/approval/instances/520000000000009704?returnTab=done`

## Findings

- 无剩余 P0/P1/P2。
- 移动端筛选仅保留实例状态与重置；状态控件和重置按钮底边对齐并保持单行，状态选择后即时查询。
- 详情弹窗顶部坐标为 `0px`，不再使用公共移动端底部抽屉定位；宽度不超出视口，内容区保持纵向滚动。
- 弹窗标题调整为 `16px`，说明为 `12px`；卡片标题、正文层级与内边距同步收紧，信息完整无截断。
- 公共下拉选项统一为 `12px`；审批详情主要内容与节点名称为 `13px`，时间及备注为 `11px`。
- 桌面端筛选项、查询按钮和公共弹窗行为保持不变。

## Comparison History

1. 初始 P2：移动端完整筛选表单纵向堆叠，占用首屏高度。
2. 修复：隐藏关键词、业务类型和查询按钮，仅保留状态与重置；状态切换直接重新读取列表。
3. 初始 P2：审批详情受公共规则影响从视口底部弹出，标题和详情文字偏大。
4. 修复：仅审批详情弹窗顶对齐；收紧标题、说明、卡片标题、正文和内边距。
5. 复验：390px E2E 与 430 x 932 视觉检查通过；页面无横向溢出。
6. 用户校正：所有下拉选项统一为12px，并再次收紧悬浮窗内容字号；430 x 932 复验无截断、重叠或横向溢出。

final result: passed

---

# CGC-PMS 经营指挥台 Design QA

- source visual truth path: `C:\Users\SUMMAD~1\AppData\Local\Temp\codex-clipboard-4041f8cd-86ec-4841-a6fe-6b00d89798a1.png`
- implementation screenshot path: `D:\projects-test\cgc-pms\tmp\design-qa\dashboard-command-current.png`
- viewport: `1488 × 1058`, DPR `1.25`
- state: 本地开发环境，超级管理员，成本经理视角，接口返回真实当前数据；无本地模拟数据

**Findings**

- 无 P0/P1/P2 视觉或交互阻断项。
- 参考图中的趋势点和七条预警属于展示数据；当前环境对应接口返回空趋势和零预警，因此实现保留真实空状态。该差异为数据状态差异，不是布局漂移。
- 工作台子导航在当前项目导航架构中保持展开，参考图只显示一级域导航。该差异保留现有 8 域 / workspace 导航能力，不影响经营指挥台主层级。

**Required Fidelity Surfaces**

- Fonts and typography: 延续项目现有中文系统字体栈；标题、指标、说明和表格层级与参考图一致，无裁切或不可读文本。
- Spacing and layout rhythm: 侧栏、全局头部、健康度主卡、左右分析区、底部快捷入口比例一致；页面无横向溢出。页面内容高度 `1126px`，比参考视口多 `68px`，由现有导航与真实空状态占位产生，可接受。
- Colors and visual tokens: 保留项目主题变量，主色蓝、风险红、预警橙、成功绿与参考图语义一致；边框和阴影保持轻量。
- Image quality and asset fidelity: 参考图无业务位图资产；品牌和所有操作图标使用现有 Ant Design 图标组件，未使用占位图、Emoji、手绘 SVG 或 CSS 图形替代。
- Copy and content: 页面名称、健康度、核心经营指标、经营趋势、预警待办、快捷入口和最近打开均使用真实项目语义；金额统一以万元展示。

**Full-view Comparison Evidence**

- 参考图与实现截图在同一比较输入中以相同 `1488 × 1058` 视口检查。
- 实现保持相同视觉顺序：全局上下文头部 → 经营健康度 → 趋势 / 预警双栏 → 快捷入口 / 最近打开。
- 主区域比例、卡片边界、信息层级和风险色彩没有可执行的 P0/P1/P2 差异。

**Focused Region Comparison Evidence**

- 无需额外裁切。两张全尺寸图在原始分辨率下可清晰读取健康度、四项指标、趋势图例、表头、快捷入口和全局头部，重要密集区域已可直接判读。

**Primary Interactions Tested**

- 全局搜索输入可编辑并路由到报表目录。
- 项目选择、报告期选择、刷新、经营分析、风险筛选和快捷入口均绑定真实状态或路由。
- 页面加载后浏览器控制台无 `error` / `warn`。

**Comparison History**

1. 首次浏览器截图仍显示旧驾驶舱，归类为本地前端运行态陈旧；重启 `cgc-pms-frontend-dev` 后重新加载。
2. 复验截图显示新经营指挥台，确认无横向溢出、控制台错误或 P0/P1/P2 差异。

**Implementation Checklist**

- [x] 真实项目数据驱动健康度、指标、风险和最近打开。
- [x] 主要入口和筛选具有可执行交互。
- [x] 1488 × 1058 桌面视口无横向溢出。
- [x] 类型检查、目标单测、Lint 与生产构建通过。

**Follow-up Polish**

- 无需建立后续项。参考图中的非空趋势与预警数据应由业务数据产生，不在前端写入模拟数据。

final result: passed

---

# 公共壳定稿 Design QA

- source visual truth: `public-shell-audit/07-annotation-baseline.png`、标签参考图与用户提供的八域 SVG 定义
- desktop implementation: `public-shell-audit/45-public-shell-final-desktop.png`
- mobile implementation: `public-shell-audit/49-public-shell-final-mobile-single-row.png`
- mobile before/after comparison: `public-shell-audit/50-public-shell-mobile-final-comparison.png`
- viewports: 1490 x 1114、1024 x 768、390 x 844
- states: `/v2/dashboard`、`/v2/system/users`、`/v2/project-schedule`、`/v2/site/daily-log`
- focused region evidence: 移动端 Header 与 Tab 在 before/after 对比中可直接判读；桌面侧栏、Header、工作区栏和图标在全视图中清晰可读。

## Findings

- 无剩余 P0/P1/P2。
- 字体与排版：公共壳沿用 V2 中文系统字体栈；桌面标签 12px，移动端上下文值和标签文案均完整可读。
- 间距与布局：桌面品牌区/Header 65px、工作区栏 50px、侧栏 200px/折叠 80px；移动端 Header 内容自适应且页面无横向溢出。
- 颜色与视觉令牌：导航默认/选中态使用 text-secondary/primary；Tab 使用冷灰默认底、浅红选中底与 danger 红线。
- 图像与资产：八域导航完整复用用户提供的 24px `currentColor` SVG；无单字占位、Emoji 或临时图形。
- 文案与内容：项目、报告期、域、工作区、Tab 和空状态文案均保持真实路由语义。
- 响应式：移动端 Header 保持单行；390px 下两个上下文控件各约 109px，430px 下各约 129px；Tab 按文字宽度自适应，项目计划/现场日报各约 114px；原生滚动条隐藏但保留触摸滚动。
- 可访问性：导航、折叠、移动端抽屉、上下文控件和标签均保留语义名称、键盘焦点与状态属性。

## Primary Interactions Tested

- 侧栏展开 200px、折叠 80px并可恢复。
- 项目计划切换到现场日报，选中底边遮挡红线，未选中标签继续显示红线。
- 移动端导航打开、焦点进入、关闭后恢复；抽屉内当前域工作区可见。
- 8 个域图标全部渲染，系统管理选中态跟随主色。
- 桌面、窄屏、移动端均无页面级横向溢出；浏览器控制台无 warning/error。

## Comparison History

1. 侧栏宽度、品牌/Header 高度、工作区栏高度、折叠入口和导航间距按用户标注逐项校正。
2. Tab 轮廓、尺寸、字号、右上圆角、状态底色和选中遮线按参考图与用户标注逐项校正。
3. 八域单字占位替换为用户提供的 `currentColor` SVG。
4. 最终 P2：移动端上下文控件继承桌面双列字段模板，被压缩到约 46px；先修复字段宽度，再按用户要求把菜单、项目、报告期、通知和头像收敛到单行 Header。
5. 最终优化：移动端 Tab 改为文字宽度自适应，并隐藏可见滚动条；复验无溢出。

## Implementation Checklist

- [x] 桌面、窄屏、移动端尺寸与溢出复验
- [x] 侧栏折叠/恢复与移动抽屉复验
- [x] 标签切换、动态宽度和选中遮线复验
- [x] 八域图标与状态色复验
- [x] Lint、单测、类型、构建、边界、路由账本和包体积门禁

final result: passed

---

# 八域主导航图标设计 QA

- source asset definition: `C:/Users/summade87114/.codex/attachments/90a51f01-88d7-4731-8df3-3ffb4cfe105a/pasted-text.txt`
- implementation screenshot: `C:/Users/summade87114/.codex/visualizations/2026/07/19/019f79fb-2bf9-7b52-b36a-99f900490804/public-shell-audit/43-eight-domain-icons.png`
- full-view comparison evidence: `C:/Users/summade87114/.codex/visualizations/2026/07/19/019f79fb-2bf9-7b52-b36a-99f900490804/public-shell-audit/44-eight-domain-icons-comparison.png`
- viewport: 1280 x 720
- state: `/v2/system/users`，系统管理选中
- focused region evidence: 左侧完整八域导航在全视图中清晰可读，无需额外裁切。

## Findings

- 无剩余 P0/P1/P2。
- 字体与排版：导航文案、字号、行高均未改变。
- 间距与布局：8 个图标均为 24 x 24px，沿用原 28px 图标槽位；侧栏间距未改变。
- 颜色与视觉令牌：SVG 使用 `currentColor`；默认态为次级文字色，选中态随域链接变为主色蓝。
- 图像与资产：完整复用用户提供的 8 套 SVG 几何，不使用单字占位或替代图形。
- 文案：8 个导航项及可访问名称保持不变。

## Comparison History

1. 初始 P2：八域主导航使用“台、项、商、供、分、财、基、系”单字占位。
2. 修复：替换为附件提供的 8 个 `currentColor` SVG 图标，未改变导航结构、权限与路由。
3. 复验：8 个图标均渲染为 24 x 24px；默认态和选中态颜色正确；页面无横向溢出。

final result: passed

---

# 公共壳侧栏标注设计 QA

- source visual truth: `C:/Users/summade87114/.codex/visualizations/2026/07/19/019f79fb-2bf9-7b52-b36a-99f900490804/public-shell-audit/07-annotation-baseline.png`
- implementation screenshot: `C:/Users/summade87114/.codex/visualizations/2026/07/19/019f79fb-2bf9-7b52-b36a-99f900490804/public-shell-audit/41-shell-heights-65-65-50.png`
- full-view comparison evidence: `C:/Users/summade87114/.codex/visualizations/2026/07/19/019f79fb-2bf9-7b52-b36a-99f900490804/public-shell-audit/42-shell-heights-comparison.png`
- viewport: 1440 x 900
- state: `/v2/project-schedule`，侧栏展开，平台管理员
- focused region evidence: 全视图中品牌区、header、完整侧栏及底部按钮均清晰可读，无需额外裁切。

## Findings

- 无剩余 P0/P1/P2。
- 字体与排版：沿用现有 V2 字体、字重与字号，无漂移。
- 间距与布局：品牌区和 header 均为 65px，工作区栏为 50px；侧栏展开 200px、折叠 80px；无横向溢出。
- 颜色与视觉令牌：仅复用现有 surface、border、primary 令牌。
- 图像与资产：未新增或替换资产；现有品牌标识与导航图形保持不变。
- 文案：新增“收起侧栏 / 展开侧栏”可访问名称，状态语义明确。

## Comparison History

1. 初始 P2：品牌区与右侧 header 高度观感未对齐。最终按标注统一为 65px，浏览器计算值均为 `65px`。
2. 初始 P2：侧栏宽于目标且缺少折叠入口。修复：展开宽度改为 `200px`，底部增加可逆折叠按钮，折叠宽度 `80px`。
3. 复验：展开/折叠交互通过，页面无横向溢出，浏览器 warn/error 为 0。
4. 标注校正：工作区栏固定为 50px；移动端继续使用内容自适应高度。

## Implementation Checklist

- [x] 品牌区与 header 等高
- [x] 侧栏展开宽度 200px
- [x] 底部折叠按钮
- [x] 折叠与恢复交互
- [x] 无横向溢出

final result: passed

---

# 公共壳梯形 Tab 设计 QA

- source visual truth: `C:/Users/SUMMAD~1/AppData/Local/Temp/codex-clipboard-f3601fa7-7b94-4ace-bc28-7a2d87404118.png`
- implementation screenshot: `C:/Users/summade87114/.codex/visualizations/2026/07/19/019f79fb-2bf9-7b52-b36a-99f900490804/public-shell-audit/39-tabs-180x40.png`
- full-view comparison evidence: `C:/Users/summade87114/.codex/visualizations/2026/07/19/019f79fb-2bf9-7b52-b36a-99f900490804/public-shell-audit/40-tabs-180x40-comparison.png`
- viewport: 1440 x 900
- state: `/v2/project-schedule`，项目计划选中
- focused region evidence: 对比图中的标签条可清晰识别默认态与选中态，无需额外裁切。

## Findings

- 无剩余 P0/P1/P2。
- 字体与排版：沿用 V2 字体栈；标签字重与参考图一致。
- 间距与布局：所有标签统一为 `180 x 40px` 非对称梯形；左边严格垂直，右侧斜切并在右上角使用贝塞尔圆角过渡，标签重叠 14px；横向溢出由原生滚动承接。
- 字号：标签文字统一为 `12px`。
- 颜色与视觉令牌：冷灰默认态、浅灰悬停态、极浅红底红字选中态，状态均复用 V2 语义令牌；红色底线保持贯穿。
- 图像与资产：参考组件无图片资产；实现未新增图标或替代资产。
- 文案：保留真实工作区标签，无参考图示例文案渗入。

## Comparison History

1. 初版 P2：标签过窄，且误实现为对称梯形。
2. 二次 P2：后续标签误做成平行四边形，左右两侧都发生倾斜。
3. 用户校正：所有标签采用同一轮廓，左边直边、右边斜切。
4. 修复：统一右侧顶部内收 25px、重叠 14px；左侧顶部内收归零。
5. 用户校正：标签尺寸统一调整为 180 x 50px，文字调整为 12px。
6. 用户校正：右上角增加圆角。修复为固定尺寸路径中的贝塞尔圆滑过渡，未改变左侧直边。
7. 用户校正：提高默认态与选中态底色辨识度。修复为冷灰默认底、极浅红选中底，悬停态使用现有 surface-hover 令牌。
8. 用户校正：选中标签必须遮挡底部红线。修复为各未选中标签自行绘制底线，标签组尾部伪元素补齐剩余底线；选中标签用同色底边自然断开红线。
9. 用户校正：标签尺寸调整为 180 x 40px；宽度、12px 字号与其余交互样式不变。
10. 复验：标签切换、选中态、390px 响应式和页面无横向溢出均通过；浏览器 warn/error 为 0。

final result: passed
