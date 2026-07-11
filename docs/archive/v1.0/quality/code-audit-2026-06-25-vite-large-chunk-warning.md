# Vite 大 chunk 警告专项审计报告

审计时间：2026-06-25  
审计对象：`frontend-admin` 的 Vite 构建产物、`manualChunks` 配置、入口依赖与 CI 构建门禁  
审计结论基准：当前本地工作区。需要确认：`frontend-admin/vite.config.ts`、`frontend-admin/src/main.ts`、`frontend-admin/package.json` 当前均为未提交修改状态，远端主分支是否已经包含这些改动需要另行确认。

# 总体结论

当前工作区下，`Vite 大 chunk 警告` 已不再复现，`pnpm build` 可以通过，且 `dist/assets` 中没有超过 500KB 阈值的 JS chunk。本次最大 JS chunk 为 `vendor-vxe-table-DZ525CoP.js`，文件大小约 457.14 KiB；其次是通用 vendor chunk 约 394.71 KiB、ECharts chunk 约 349.03 KiB。现有路由基本采用动态导入，`vite.config.ts` 也已经将 `vxe-table`、`vxe-pc-ui`、`echarts`、Vue 生态分别拆分，这是当前警告消失的主要原因。  
本问题不构成生产阻断，但仍有两个中长期风险：`vxe-table` 仍是全局注册且接近阈值，CI 只执行构建，没有明确的 bundle size 预算门禁。建议本项可以合并；如目标是生产发布，需要确认当前未提交的拆分配置已进入目标分支。

# 评分

- 正确性：92/100
- 安全性：95/100
- 性能：82/100
- 可维护性：84/100
- 架构设计：82/100
- 测试与工程化：78/100
- 综合评分：85/100

# 风险统计

- Critical：0
- High：0
- Medium：1
- Low：2
- Info：1

# 关键问题

## 问题 1：当前工作区已不再复现 Vite 大 chunk 警告

- 类型：构建结果 / 性能风险复核
- 严重程度：Info
- 位置：
  - `frontend-admin/vite.config.ts:35`
  - `frontend-admin/vite.config.ts:39`
  - `frontend-admin/vite.config.ts:45`
  - `frontend-admin/vite.config.ts:48`
  - `frontend-admin/vite.config.ts:51`
  - `frontend-admin/vite.config.ts:54`
- 证据：
  - `frontend-admin/vite.config.ts:35` 保持 `chunkSizeWarningLimit: 500`，没有通过调高阈值掩盖问题。
  - `frontend-admin/vite.config.ts:45` 将 `echarts` / `vue-echarts` 拆到 `vendor-echarts`。
  - `frontend-admin/vite.config.ts:48` 将 `vxe-table` 拆到 `vendor-vxe-table`。
  - `frontend-admin/vite.config.ts:51` 将 `vxe-pc-ui` 拆到 `vendor-vxe-ui`。
  - `frontend-admin/vite.config.ts:54` 将 `vue`、`vue-router`、`pinia` 拆到 `vendor-vue`。
  - 本地复核 `dist/assets`：`OVER_500KB_JS: none`。
  - 最大 JS chunk：`vendor-vxe-table-DZ525CoP.js` 457.14 KiB，`vendor-p1MEr8pW.js` 394.71 KiB，`vendor-echarts-BNHASu6c.js` 349.03 KiB。
- 问题说明：历史审计报告中曾记录 `vendor-antd` 约 1.30MB、`vendor-vxe` 约 1.02MB，超过 500KB。当前配置已经把主要重依赖重新拆分，且没有直接调大阈值，因此问题在当前工作区已被实质缓解。
- 影响：当前不会因为 Vite 默认大 chunk 警告阻塞构建，也不会把多个重型库压进一个超大 vendor 包。
- 修复建议：无需针对该警告继续做阻断性修复。需要确认当前 `vite.config.ts` 改动已提交并进入目标发布分支。
- 示例修复代码：不需要代码修复。保留当前拆分策略即可。
- 优先级：P3

收益、成本和风险：
- 收益：维持当前拆包结果，避免重复优化。
- 成本：无新增成本。
- 风险：如果当前未提交改动未进入目标分支，远端构建仍可能复现历史大 chunk 警告。

## 问题 2：`vxe-table` 全局注册仍使最大 chunk 接近阈值

- 类型：性能 / 架构边界
- 严重程度：Medium
- 位置：
  - `frontend-admin/src/main.ts:11`
  - `frontend-admin/src/main.ts:13`
  - `frontend-admin/src/main.ts:14`
  - `frontend-admin/src/main.ts:51`
  - `frontend-admin/vite.config.ts:48`
- 证据：
  - `frontend-admin/src/main.ts:11` 从 `vxe-table` 引入 `VxeUITable`。
  - `frontend-admin/src/main.ts:13`、`frontend-admin/src/main.ts:14` 全局引入 `vxe-table` / `vxe-pc-ui` 样式。
  - `frontend-admin/src/main.ts:51` 执行 `app.use(VxeUITable)`，意味着 VXE 表格能力是应用级基础依赖。
  - `frontend-admin/vite.config.ts:48` 将 `vxe-table` 独立拆为 `vendor-vxe-table`，当前产物仍达到 457.14 KiB，是最大 JS chunk。
- 问题说明：当前 `vendor-vxe-table` 未超过阈值，但已经是最大 chunk。随着 VXE 功能、插件或表格相关代码增长，该 chunk 很容易重新触发 500KB 警告。考虑到后台管理系统大量页面使用表格，全局注册在当前阶段可以接受；问题不在“必须立即按需化”，而在于缺少增长约束和后续治理路径。
- 影响：首屏可能提前加载非首屏页面需要的表格能力；低速网络、弱缓存或首次访问下加载体验会受影响。长期看，继续全局注册会降低拆包收益。
- 修复建议：短期保持现状，不做高风险大改。若后续 `vendor-vxe-table` 超过 500KB，优先评估 VXE 是否可以按页面或功能模块注册，而不是继续调高 `chunkSizeWarningLimit`。
- 示例修复代码：

```ts
// 示例方向：仅在验证 VXE 支持局部注册后采用。
// 不建议未经完整回归直接替换当前 app.use(VxeUITable)。
import { Grid, Table, Column } from 'vxe-table'

export function registerVxeCore(app: App) {
  app.use(Grid)
  app.use(Table)
  app.use(Column)
}
```

- 优先级：P2

收益、成本和风险：
- 收益：降低最大 vendor chunk 继续膨胀的风险，改善首次访问体感。
- 成本：中等，需要确认 VXE 的组件注册方式、全局配置、现有 `<vxe-grid>` 页面兼容性。
- 风险：按需化可能引入表格组件缺失、样式缺失、插件能力缺失，必须配合核心表格页面回归。

## 问题 3：缺少明确的 bundle size 预算门禁，后续可能回归

- 类型：工程化 / 性能治理
- 严重程度：Low
- 位置：
  - `frontend-admin/package.json:8`
  - `.github/workflows/ci.yml:87`
  - `.github/workflows/ci.yml:88`
  - `.github/workflows/ci.yml:89`
  - `.github/workflows/ci.yml:93`
- 证据：
  - `frontend-admin/package.json:8` 的构建脚本为 `vue-tsc --noEmit && vite build`。
  - `.github/workflows/ci.yml:87-88` 的 `frontend-build` job 执行 `pnpm install --frozen-lockfile && pnpm build`。
  - `.github/workflows/ci.yml:89-93` 上传 `frontend-dist` 产物，但没有读取产物大小并设置失败阈值。
- 问题说明：目前 bundle 体积治理依赖 Vite 控制台警告。警告通常不会导致构建失败，后续依赖升级或页面引入新重库时，CI 可能仍然显示成功，但产物体积已经明显退化。
- 影响：性能回归可能在代码合并后才被人工发现；如果发布链路只看 CI 成功，bundle 体积问题容易被忽略。
- 修复建议：增加轻量产物大小检查脚本，短期只检查单个 JS chunk 是否超过 500 KiB 或 550 KiB。当前项目规模不建议先引入复杂 bundle 平台，避免工程化成本过高。
- 示例修复代码：

```json
{
  "scripts": {
    "build": "vue-tsc --noEmit && vite build",
    "check:bundle-size": "node scripts/check-bundle-size.mjs"
  }
}
```

```js
// frontend-admin/scripts/check-bundle-size.mjs
import { readdir, stat } from 'node:fs/promises'
import { join } from 'node:path'

const limit = 500 * 1024
const dir = new URL('../dist/assets/', import.meta.url)
const files = await readdir(dir)
const oversized = []

for (const file of files) {
  if (!file.endsWith('.js')) continue
  const size = (await stat(join(dir.pathname, file))).size
  if (size > limit) oversized.push({ file, size })
}

if (oversized.length > 0) {
  console.error('Oversized JS chunks:', oversized)
  process.exit(1)
}
```

- 优先级：P3

收益、成本和风险：
- 收益：将性能回归从人工发现转为 CI 发现。
- 成本：低，一个小脚本和 CI 增量命令即可。
- 风险：阈值过紧可能导致依赖升级时误阻塞；建议先使用与 Vite 一致的 500 KiB，必要时按业务首屏指标调整。

## 问题 4：ECharts 在入口注册，图表能力仍是应用级依赖

- 类型：性能 / 依赖边界
- 严重程度：Low
- 位置：
  - `frontend-admin/src/main.ts:17`
  - `frontend-admin/src/main.ts:25`
  - `frontend-admin/src/main.ts:33`
  - `frontend-admin/vite.config.ts:45`
- 证据：
  - `frontend-admin/src/main.ts:17-25` 在应用入口引入 ECharts core、chart、component、renderer。
  - `frontend-admin/src/main.ts:33` 执行 `use([...])` 注册图表能力。
  - `frontend-admin/vite.config.ts:45` 已将 `echarts` / `vue-echarts` 拆到 `vendor-echarts`，当前产物约 349.03 KiB。
- 问题说明：当前 ECharts chunk 没有超阈值，且按需注册比全量导入更好。但从依赖边界看，图表能力仍在入口初始化，非图表页面也会受入口依赖关系影响。考虑到项目有 dashboard、成本汇总等图表页面，这不是必须修复项。
- 影响：首屏如果不是图表页，仍可能承担部分图表基础依赖成本。随着图表类型增加，`vendor-echarts` 也可能继续增长。
- 修复建议：只有在首屏性能指标不达标时，再把 ECharts 注册下沉到图表组件或图表插件模块。当前阶段不建议为追求极致拆包而大规模改造。
- 示例修复代码：

```ts
// 示例方向：图表模块本地注册，避免入口承担全部图表初始化。
// 需要确认 vue-echarts 使用方式和重复注册行为后再落地。
import { use } from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, PieChart, CanvasRenderer])
```

- 优先级：P3

收益、成本和风险：
- 收益：进一步收敛入口依赖，改善非图表首屏路径。
- 成本：中低，需要梳理图表组件的公共注册点。
- 风险：如果分散注册不规范，可能出现图表组件缺少 renderer 或 component 的运行时错误。

# 必须修复

本专项没有上线或合并前必须修复的问题。  
前提条件：目标分支必须包含当前 `frontend-admin/vite.config.ts` 的拆包配置，并且发布构建结果与本地复核一致。

# 建议优化

- 保留当前 `chunkSizeWarningLimit: 500`，不要通过调高阈值掩盖体积问题。收益是保持性能警戒线；成本为零；风险是后续依赖升级可能再次触发警告，但这是合理反馈。
- 增加轻量 bundle size CI 检查。收益是防止回归；成本低；风险是阈值策略需要团队共识。
- 暂不立即重构 `vxe-table` 全局注册。收益是避免影响大量表格页面；成本为零；风险是最大 chunk 会继续接近阈值，因此需要通过门禁监控。
- 若后续首屏性能不达标，再评估 ECharts 注册下沉。收益是进一步减少入口依赖；成本中低；风险是需要补图表页面回归。

# 长期演进建议

- 建立前端性能预算：单 JS chunk、总 JS、首屏路由资源、gzip 后体积分别设软阈值和硬阈值。当前项目阶段可以先只做单 JS chunk 阈值，避免过度工程化。
- 对重依赖建立 owner 规则：`vxe-table`、`ant-design-vue`、`echarts` 新增用法需要说明是否进入入口、是否影响公共 vendor。
- 对核心路由保持动态导入约束。当前 `src/router/index.ts` 大量页面使用 `() => import(...)`，这是做得好的地方，应作为后续页面开发规范。
- 每次升级 `ant-design-vue`、`vxe-table`、`echarts` 后固定执行一次 bundle 体积复核，避免依赖升级造成隐性膨胀。

# 测试建议

- 构建测试：继续执行 `cd frontend-admin && pnpm build`，确认无 Vite 大 chunk 警告。
- 产物体积测试：构建后检查 `dist/assets/*.js`，确认没有单个 JS 文件超过 500 KiB。
- 回归测试：如果改动 `vxe-table` 注册方式，必须覆盖合同台账、成本台账、库存台账、采购订单等使用 `<vxe-grid>` 的核心页面。
- 图表测试：如果改动 ECharts 注册位置，必须覆盖 dashboard、成本汇总、项目概览等图表页面。
- CI 测试：如果新增 `check:bundle-size`，需要在 CI 中验证阈值超限时 job 会失败，正常构建时通过。

# 验收标准

- `cd frontend-admin && pnpm build` 成功，输出中不出现 `Some chunks are larger than 500 kB after minification`。
- `dist/assets/*.js` 中不存在超过 500 KiB 的 JS 文件。
- 最大 JS chunk 名称和大小记录在审查或 CI 日志中，便于后续对比。
- `frontend-admin/vite.config.ts` 保持 `chunkSizeWarningLimit: 500`，不得通过调高阈值规避问题。
- 若新增 bundle size 门禁，CI 的 `frontend-build` job 必须在构建后执行体积检查，并在超限时失败。
- 若调整 VXE 或 ECharts 注册方式，核心表格页面和图表页面必须完成浏览器或组件级回归。

# 最终建议

可以合并。  
本结论仅针对 `Vite 大 chunk 警告` 专项；当前构建产物没有超过 500KB 的 JS chunk，历史警告在当前工作区已经被实质缓解。发布前需要确认当前拆包配置已进入目标分支，并建议后续补一个轻量 bundle size 门禁，防止依赖升级或新增页面后回归。
