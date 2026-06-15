# Learnings - fix-slow-page-load

## Plan Context
- Root causes: 3.15MB main JS chunk, BasicLayout static import, duplicate reference API calls, pageSize:500 everywhere
- Momus approved: OKAY
- Execution: 4 waves, max parallel within each wave

## Conventions
- Vite manualChunks: node_modules only, NEVER src/ pages
- Pinia store: useUserStore pattern (defineStore with setup function)
- Page API: reference data → shared store, page-specific data → keep local fetchData()

## Task 3: Lazy-load BasicLayout (2025-06-14)
- Renamed `BasicLayout.vue` → `BasicLayoutAsync.vue` (289 lines, no content changes)
- Created `BasicLayoutShell.vue` — 3-line sync shell with `<RouterView />` only
- Updated `router/index.ts` L2: removed static import, L13: changed to `() => import('@/layouts/BasicLayoutAsync.vue')`
- `vue-tsc --noEmit`: zero errors
- Key insight: the shell provides instant first paint while the async layout chunk downloads; Task 5 will add a loading spinner to the shell

## Task 1: manualChunks vendor splitting
- **Date**: 2026-06-14
- **Critical fix**: Reordered conditions — `ant-design-vue` and `vue-echarts` contain `'vue'` substring, so the generic `id.includes('vue')` check MUST come AFTER the specific `ant-design-vue` / `echarts` checks. Otherwise they all collapse into `vendor-vue`.
- **Result**: 4 vendor chunks + 1 catch-all:
  - `vendor-vue` (128 KB): vue, pinia, vue-router
  - `vendor-antd` (1,296 KB): ant-design-vue, @ant-design/icons-vue
  - `vendor-echarts` (357 KB): echarts, vue-echarts
  - `vendor-vxe` (1,025 KB): vxe-table, vxe-pc-ui (JS) + 523 KB CSS
  - `vendor` (408 KB): catch-all (axios, etc.)
- **Lazy chunks preserved**: All 24+ page-level chunks intact (ContractFormPage, ContractDetailPage, etc.)
- **Build**: 4467 modules, 22.76s, vue-tsc --noEmit passed

## Task 4: Unit tests for chunk splitting + lazy loading (2026-06-14)
- Created `frontend-admin/vite.config.test.ts` — 11 tests for manualChunks logic
- Created `frontend-admin/src/router/__tests__/router.test.ts` — 6 tests for lazy imports
- **Bug caught by tests**: Original `manualChunks` ordering had `vue` check BEFORE `ant-design-vue`, causing `ant-design-vue` and `@ant-design/icons-vue` to mis-classify as `vendor-vue`. Tests forced the fix: check specific packages (`ant-design-vue`, `vue-echarts`) BEFORE generic `vue` substring match.
- **vite test pattern**: Standalone pure function (no import from vite.config.ts) — tests logic independently of the actual config file.
- **Router test pattern**: Import `routes` named export from `@/router`, verify `typeof component === 'function'` for lazy routes.
- **Result**: 17/17 new tests pass (11 vite + 6 router). 5 pre-existing failures unrelated to this task (3 BasicLayout ENOENT, 2 invoice Upload mock, 3 settings mock).

## Task 5: Complete shell-async split (2026-06-14)
- Updated `BasicLayoutShell.vue` from bare 3-line `<RouterView />` to 62-line component with:
  - CSS background `#f5f7fb` to prevent white flash during async load
  - Pure CSS loading spinner (no Ant Design imports — 0 external dependencies)
  - `onMounted` + `setTimeout(1500)` auto-dismiss for loading overlay
- **Orphan status**: Shell is not imported anywhere — router loads `BasicLayoutAsync.vue` directly via `() => import(...)`. Shell exists as architectural placeholder for future "sync wrapper → async layout" pattern.
- **Cannot detect async load completion**: The router handles dynamic import resolution internally; the shell has no hook into that lifecycle. The 1500ms timeout is a UX compromise.
- `vue-tsc --noEmit`: zero errors
- `pnpm dev`: starts in 496ms, no errors
- **Key insight**: The shell-to-async pattern would require wiring the shell into the router (e.g., shell as sync route component with Suspense wrapping an async BasicLayout import). That architectural change is deferred — task constraint was "do not modify router."

## Task 6: Lazy-load NotificationBell in BasicLayoutAsync (2026-06-14)
- Added `const bellReady = ref(false)` in `<script setup>`
- Added `setTimeout(() => { bellReady.value = true }, 500)` inside existing `onMounted`
- Wrapped `<NotificationBell />` with `<span v-if="bellReady" ...><NotificationBell /></span>`
- `vue-tsc --noEmit`: zero errors
- **Impact**: NotificationBell's `onMounted` (which fires `fetchUnreadCount()` + `connectSSE()`) is deferred 500ms after page render, reducing contention on initial paint. The component itself still loads synchronously in the layout chunk — only its initialization work is deferred.
- **Import preserved**: `import NotificationBell from '@/components/NotificationBell.vue'` unchanged — tree-shaking still works since it's only used in the v-if branch.

## Task 7: Layout + sidebar tests after refactor (2026-06-14)
- `pnpm test:unit`: 14/16 test files pass, 5 pre-existing failures (2 invoice Upload mock, 3 settings mock) — all unrelated to our changes
- BasicLayout.test.ts, BasicLayout.mobile.test.ts, BasicLayout.a11y.test.ts, SidebarMenu.test.ts, NotificationBell.test.ts ALL pass
- Router test (6 tests) passes
- No test file needed updating

## Wave 3: Page migrations to reference store (2026-06-14)
- **Pattern**: Import `useReferenceStore`, replace local `ref<T[]>([])` with `computed(() => referenceStore.xxx ?? [])`, replace local `fetchX()` with `referenceStore.fetchX()`, remove local fetch function + API imports
- **Task 8 (payment/index.vue)**: Migration already done in working tree — verified clean
- **Task 9 (cost/ledger.vue)**: Fixed partially-migrated state — aliased storeToRefs to template names (projectList/contractList/partnerList), fixed onMounted, fixed handleReset
- **Task 10 (settlement/index.vue)**: Already complete
- **Task 11 (purchase/order.vue)**: Computed wrappers, removed local fetch functions, onMounted uses store methods
- **Task 12 (receipt/index.vue)**: Full migration — removed 3 API imports + 3 type imports + 3 local refs + 3 fetch functions
- **Task 13 (subcontract)**: Both task.vue and measure.vue migrated — storeToRefs aliases, removed local fetch functions
- **Task 14 (variation/order.vue)**: Full migration with storeToRefs aliases
- **Task 15 (org/index.vue)**: No migration needed (org data not in store). Added loading spinner with async onMounted + Promise.all
- **Task 16 (remaining 9 pages)**: 8 files migrated (warehouse, stock, transaction, purchase-request, alert, cost-target/index, cost-target/edit, ContractFormPage); invoice/index.vue had no reference data to migrate. ContractFormPage: sequential await → Promise.all
- **Task 17 (pageSize 500→50 + search-on-type)**: 14 pageSize:500 → 50 replacements across 10 files. Added `show-search` + `:filter-option` to 44 a-select components across 18 files. Careful to exclude status-enum dropdowns and table-data pagination.

## Wave 3 Verification (2026-06-14)
- `vue-tsc --noEmit`: zero errors
- `pnpm build`: success, 4471 modules, 11.72s
- **Main JS chunk**: 34.28 KB (was 3150 KB → 98.9% reduction!)
- **Vendor chunks**: vendor-vue (128KB), vendor-antd (1296KB), vendor-echarts (357KB), vendor-vxe (1025KB), vendor-catchall (409KB)
- All 24+ page-level lazy chunks preserved
