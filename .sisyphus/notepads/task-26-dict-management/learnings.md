# Task 26: Dictionary Management — Learnings

## Patterns Used
- **Page pattern**: Followed `material/dictionary.vue` for modal CRUD pattern
- **Styling**: Used shared `.xx-page`, `.xx-card`, `.xx-filter`, `.xx-table-wrap`, `.xx-pagination` conventions
- **API pattern**: `PageParams` + `PageResult<T>` from `@/types/api`, `request()` from `@/api/request`
- **Modal pattern**: `v-model:open`, `@ok`, `@cancel`, reactive form data with `Object.assign` for reset
- **Split-pane**: Used `a-layout` + `a-layout-sider` + `a-layout-content` for left-right split

## Design System Tokens (Consistent)
- Page bg: `#f6f8fc`
- Card bg: `#fff`, border: `#e5eaf3`, radius: `10px`, shadow: `0 10px 30px rgba(17,24,39,0.05)`
- Link color: `#1677ff`, label: `#374151`, muted: `#9ca3af`, total count: `#4b5563`
- Card padding: `20px 22px`, card margin-bottom: `14px`, pagination: `12px 0 0`

## Backend API Mapping
- Dict Type CRUD: `/system/dict/types` (GET/POST/PUT/DELETE)
- Dict Data CRUD: `/system/dict/data?typeId=` (GET/POST/PUT/DELETE)
- Permissions: `system:dict:list`, `system:dict:add`, `system:dict:edit`, `system:dict:delete`
