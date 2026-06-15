# Decisions - fix-slow-page-load

## Metis Correction
- Pages do NOT have sequential await waterfalls
- Pages use fire-and-forget concurrent pattern
- Real problem: duplicate API calls across pages + no shared cache
- Wave 3 redefined: migrate reference fetches to shared store (not add Promise.all)

## Task 2: Shared reference store created
- File: `frontend-admin/src/stores/reference.ts`
- Store: `useReferenceStore` via `defineStore('reference', ...)` with setup function pattern
- Four data refs: `projects`, `contracts`, `partners`, `materials` — typed arrays, initialized `null`
- Four fetch methods with in-flight dedup (`loadingPromise` pattern)
- Four invalidate methods (sets ref to `null`)
- ContractQueryParams uses `pageNo` (not `pageNum`) — handled correctly in `fetchContracts()`
- Response unwrapping: `res.records ?? res.data ?? res` (defensive)
- `vue-tsc --noEmit` passes with zero errors
- Default pageSize: 50
