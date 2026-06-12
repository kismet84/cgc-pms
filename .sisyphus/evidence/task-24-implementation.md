# Task 24: Project Member Management — Implementation Evidence

**Date**: 2026-06-12  
**Type-check**: ✅ `pnpm type-check` (vue-tsc --noEmit) passed with zero errors  
**LSP diagnostics**: Clean on all modified files

## Files Created / Modified

| File | Action | Lines |
|------|--------|-------|
| `frontend-admin/src/pages/project/members.vue` | **Created** | 286 |
| `frontend-admin/src/stores/project.ts` | **Created** | 97 |
| `frontend-admin/src/api/modules/project.ts` | **Extended** | +44 |
| `frontend-admin/src/api/modules/system.ts` | **Extended** | +18 |
| `frontend-admin/src/types/project.ts` | **Extended** | +30 |
| `frontend-admin/src/router/index.ts` | **Modified** | ~12 |

## Implementation Details

### 1. Types (`types/project.ts`)
- `MemberVO`: mirrors backend `PmProjectMemberVO` (id, projectId, userId, roleCode, positionName, startDate, endDate, status, etc.)
- `MemberFormParams`: for add/update member API payloads

### 2. API (`api/modules/project.ts`)
- `getMemberList(projectId, params)` → GET `/projects/{projectId}/members`
- `addMember(projectId, data)` → POST `/projects/{projectId}/members`
- `updateMember(projectId, memberId, data)` → PUT `/projects/{projectId}/members/{memberId}`
- `removeMember(projectId, memberId)` → DELETE `/projects/{projectId}/members/{memberId}`

### 3. API (`api/modules/system.ts`)
- `getUserList(params)` → GET `/system/users` (for user selector in add member dialog)
- `SysUserBrief` interface with id, username, realName, phone, status

### 4. Store (`stores/project.ts`)
Following the contract store pattern (`stores/contract.ts`):
- State: `currentProject`, `members`, `membersTotal`, `loading`, `saving`, `membersLoading`
- Actions: `fetchProject`, `fetchMembers`, `addMember`, `updateMember`, `removeMember`, `resetState`

### 5. Page (`pages/project/members.vue`)
- Route: `/project/:projectId/members`
- **Header**: back-to-list button, project name, "添加成员" button
- **Table**: columns for name (with avatar), role (inline a-select), position, start date, status, actions
- **Role editing**: Inline `a-select` with 7 role options (PM/CM/CSTM/MAT/SUBC/FIN/OTH)
- **Add modal**: user search-select, role select, position name input, date picker
- **Delete**: popconfirm with member name
- **User lookup**: Fetches system users list for name resolution

### 6. Router (`router/index.ts`)
- Project route restructured with children: `/project/list` (list) + `/project/:projectId/members` (members)
- Redirect from `/project` to `/project/list` preserves existing navigation

### Role Codes
| Code | Label | Tag Color |
|------|-------|-----------|
| PM   | 项目经理 | blue |
| CM   | 商务经理 | green |
| CSTM | 成本经理 | orange |
| MAT  | 材料员 | purple |
| SUBC | 分包管理员 | cyan |
| FIN  | 财务 | red |
| OTH  | 其他 | default |
