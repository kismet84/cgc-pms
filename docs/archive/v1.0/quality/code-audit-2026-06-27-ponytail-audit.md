# 代码仓库过度工程化审计（Ponytail）

## 结论
本次按 ponytail 口径做了全仓库扫描，结论是：**前端工程中存在明显可删可简化的重复实现**，值得优先清理。  
本版本不做功能正确性、生产可用性优先级判定，仅列出可缩减项。

## 可裁剪项（按收益排序）

1. `yagni` 列表页列可见性实现有两套重复系统，继续保留会增加跨页差异和维护成本。  
`project/index.vue` 与 `variation/order.vue` 使用手写 `COLS_KEY/defaultCols/saved/colVisible/toggleCol`，并且仍有大量页面使用 `if (!localStorage.getItem(...))` 风格的局部列设置实现，缺少统一入口。  
`[D:\projects-test\cgc-pms\frontend-admin\src\pages\project\index.vue:37-76, D:\projects-test\cgc-pms\frontend-admin\src\pages\variation\order.vue:116-142, D:\projects-test\cgc-pms\frontend-admin\src\pages\requisition\index.vue:50, D:\projects-test\cgc-pms\frontend-admin\src\pages\purchase\order.vue:134, D:\projects-test\cgc-pms\frontend-admin\src\pages\inventory\purchase-request.vue:116, D:\projects-test\cgc-pms\frontend-admin\src\pages\material\dictionary.vue:98, D:\projects-test\cgc-pms\frontend-admin\src\pages\receipt\index.vue:55, D:\projects-test\cgc-pms\frontend-admin\src\pages\subcontract\task.vue:119, D:\projects-test\cgc-pms\frontend-admin\src\pages\subcontract\measure.vue:135, D:\projects-test\cgc-pms\frontend-admin\src\pages\settlement\index.vue:256, D:\projects-test\cgc-pms\frontend-admin\src\pages\inventory\composables\useStockLedger.ts:111-120, D:\projects-test\cgc-pms\frontend-admin\src\composables\useColumnSettings.ts:1-68]`  
收益：统一行为，减少回归点；  
成本：中（跨页面替换）；  
风险：首次统一时需确认默认列可见策略兼容性。

2. `delete` 删除 `frontend-admin/src/stores/user.ts` 中不再使用的兼容桩。  
`setToken`、`setRefreshToken` 已写成 no-op 注释兼容，未发现仓库内调用。  
`[D:\projects-test\cgc-pms\frontend-admin\src\stores\user.ts:33-39]`  
收益：去除“功能已过时但代码还在”的误导性噪声；  
成本：低；  
风险：若存在外部依赖该接口，发布前需确认。

3. `delete` 抽取重复的 `normalizeArray` 工具。  
`alert.ts`、`contract.ts`、`project.ts` 内重复实现同一解析逻辑，可合并为共享 util。  
`[D:\projects-test\cgc-pms\frontend-admin\src\stores\alert.ts:11-16, D:\projects-test\cgc-pms\frontend-admin\src\stores\contract.ts:20-25, D:\projects-test\cgc-pms\frontend-admin\src\stores\project.ts:13-18]`  
收益：删掉重复样板，降低未来改动成本；  
成本：低；  
风险：几乎无。

4. `native` 自研 401 刷新队列可替换为成熟库能力。  
`request.ts` 当前同时维护刷新并发队列、超时、重试、独立 refreshClient、统一跳登录，属于可外包给库的能力。  
`[D:\projects-test\cgc-pms\frontend-admin\src\api\request.ts:40-118]`  
收益：把细节维护成本下沉，减少边界 bug 风险；  
成本：中（引入依赖、重写拦截器）；  
风险：依赖行为差异带来的错误文案与跳转时序差异。

5. `shrink` 精简 `reference` 的重复状态机样板。  
`fetchProjects/fetchContracts/fetchPartners/fetchMaterials` 在成功/失败与 Promise 去重路径上重复。  
`[D:\projects-test\cgc-pms\frontend-admin\src\stores\reference.ts:73-165]`  
收益：后续新增基准字典更快；  
成本：中（抽象一层 + 联调）；  
风险：统一抽象后排障路径不如当前“展开式”直观。

## 结尾
net: -130 lines, -1 deps possible.

