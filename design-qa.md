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
