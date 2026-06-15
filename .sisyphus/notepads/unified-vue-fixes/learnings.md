# unified-vue-fixes Learnings

## Fix: bodyCell condition bug in org/index.vue

- **File**: `frontend-admin/src/pages/org/index.vue`
- **Line 676**: Changed `column.dataIndex === 'posStatus'` → `column.dataIndex === 'status'`
- **Root cause**: The position table column definition at line 84 uses `dataIndex: 'status'`, but the bodyCell template at line 676 was checking for `'posStatus'` — a column that doesn't exist. This made the `<a-tag>` status rendering dead code; the status column showed raw English values (ENABLED/DISABLED) instead of Chinese tags (启用/禁用).
- **Build**: `pnpm build` passed with zero errors.
- **Date**: 2026-06-15

## Fix: Register v-loading directive globally in main.ts

- **File**: `frontend-admin/src/main.ts` + new `frontend-admin/src/directives/loading.ts`
- **Problem**: `dict/index.vue:361` uses `v-loading="typeLoading"` but ant-design-vue 4.2.6 removed the `v-loading` directive (breaking change from v3). `import { loading } from 'ant-design-vue'` does NOT work — `loading` is not exported at all in 4.2.6.
- **Solution**: Created a custom directive at `src/directives/loading.ts` that uses `h(Spin, { spinning })` + `render()` to programmatically show/hide a Spin overlay inside the bound element. Registered globally via `app.directive('loading', vLoading)` in main.ts.
- **Key finding**: The `v-loading` directive in ant-design-vue 4.x was completely removed, not just unregistered. The official replacement is `<a-spin :spinning="...">` wrapping. Our custom directive bridges the gap without modifying the dict page template.
- **Build**: `pnpm build` passed with zero errors, zero type warnings.
- **Date**: 2026-06-15

## Fix: userInfo binding in BasicLayoutAsync.vue (storeToRefs)

- **File**: `frontend-admin/src/layouts/BasicLayoutAsync.vue`
- **Problem**: Template uses `userInfo?.realName` etc. but `userInfo` was never destructured from Pinia store, causing Vue warning: `Property "userInfo" was accessed during render but is not defined on instance`.
- **Root cause**: Only `userStore` was available as a top-level binding. `userInfo` is a ref inside the Pinia store — the template needs it exposed as a standalone reactive binding via `storeToRefs()`.
- **Fix**: Added `import { storeToRefs } from 'pinia'` (line 5) and `const { userInfo } = storeToRefs(userStore)` (line 12).
- **Why not plain destructuring**: `const { userInfo } = userStore` extracts the raw value at destructure time, breaking Pinia reactivity. `storeToRefs()` properly wraps it as `Ref<UserInfo | null>`.
- **Build**: `pnpm build` — exit 0, zero type errors.
- **Tests**: `pnpm test:unit` — 16 files passed, 70 tests passed. BasicLayout tests no longer emit `userInfo` warnings.
- **Date**: 2026-06-15

## Fix: SidebarMenu.vue computed→ref+watch (v-model setter warning)

- **File**: `frontend-admin/src/layouts/components/SidebarMenu.vue`
- **Problem**: Vue warning "Computed property was assigned to but it has no setter" — both `selectedKeys` and `openKeys` were read-only `computed` values but used with `v-model:selected-keys` and `v-model:open-keys`, which attempt to write back.
- **Fix (Part A — selectedKeys)**: Changed `v-model:selected-keys="selectedKeys"` → `:selected-keys="selectedKeys"` (one-way binding). Safe because `selectedKeys` is always `[route.path]`, never needs user writes.
- **Fix (Part B — openKeys)**: Replaced `computed` with `computeOpenKeys()` helper + `ref<string[]>(computeOpenKeys())` + `watch(() => route.path, ...)`. Preserved exact while-loop logic from original computed. Imported `ref` and `watch` from vue.
  - `ref` is writable → accepts manual submenu toggles via `v-model:open-keys`
  - `watch(route, { immediate: true })` recalculates on navigation (same behavior as computed)
  - `computeOpenKeys()` initial value prevents flash on first render (NOT empty `[]`)
- **Template**: `v-model:open-keys="openKeys"` stays as-is (now works because `openKeys` is writable `ref`)
- **Build**: `pnpm build` — exit 0, zero errors.
- **Tests**: `SidebarMenu.test.ts` — 2 tests passed.
- **Date**: 2026-06-15
