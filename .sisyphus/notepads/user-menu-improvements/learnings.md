# Learnings - User Menu Improvements

## V47 Migration: sys_user_preference table

- Migration created: `V47__add_user_preference.sql`
- Follows existing V1/V37 comment style: Chinese header block, database version, table description
- H2+MySQL dual compatibility: NO `ENGINE=InnoDB`, NO `CHARSET/COLLATE`, NO backticks
- Uses `AUTO_INCREMENT` for id (compatible with both H2 MySQL mode and MySQL)
- DATETIME type for created_at/updated_at (matches project convention from BaseEntity)
- UNIQUE constraint via named CONSTRAINT `uk_tenant_user` (standard SQL, works in both)
- All 208 tests pass: BUILD SUCCESS

## 2026-06-13: Logout fix + UserInfo type extension

### Logout Fire-and-Forget Pattern
- The auth module in `@/api/modules/auth.ts` already exports a `logout()` function that POSTs to `/auth/logout`.
- The store's `logout()` action was only clearing local state; now it fires `authLogout()` without `await` and always clears `userInfo` + `localStorage` regardless.
- Key: call the API first (fire), then clear local state synchronously. The API result is irrelevant for local cleanup.

### UserInfo Type Alignment
- Backend `UserInfo.java` DTO includes `phone` and `email` fields.
- Added `phone?: string` and `email?: string` as optional fields to the frontend `UserInfo` interface in `types/user.ts`.
- Using `?` to avoid breaking existing code that doesn't populate these fields.

### Vitest Mock Hoisting (vi.hoisted)
- When using `vi.mock()` with a factory that references top-level variables, those variables are hoisted before `const` declarations, causing "Cannot access before initialization" errors.
- **Fix**: Use `vi.hoisted(() => ({ mockFn: vi.fn() }))` to create mock functions that are available when the hoisted `vi.mock` factory runs.
- Pattern:
  ```ts
  const { mockFn } = vi.hoisted(() => ({
    mockFn: vi.fn(),
  }))
  vi.mock('@/module', () => ({
    exportedFn: mockFn,
  }))
  ```

### Test File Location
- Co-located in `__tests__/` directory next to source files (consistent with existing project tests).
- Use `setActivePinia(createPinia())` + `vi.resetModules()` in `beforeEach` for clean store state per test.

### Build Verification
- `pnpm build` runs `vue-tsc --noEmit && vite build` — passing means zero TypeScript errors.

## Wire Click Handlers for Menu Items (2026-06-13)

### Changes Made
- `BasicLayout.vue`: Added `@click="router.push('/profile')"` to profile menu item (line 61)
- `BasicLayout.vue`: Added `@click="router.push('/settings')"` to settings menu item (line 62)
- `BasicLayout.vue`: Added `@click="router.push('/help')"` to QuestionCircleOutlined icon (line 45)

### Test Patterns Learned
- `key` is Vue's reserved special attribute — cannot be used as a prop name in stub components. Use text-based matching via `findAll('.stub-menu-item').find(item => item.text() === '...')` instead.
- Named slots (`#overlay`) in stubbed components: use `slots.overlay?.()` in `defineComponent` setup to render slot content.
- For click handler tests on icons: `@click` on a mocked component falls through as native DOM event when the mock doesn't define `emits: ['click']` and has `inheritAttrs: true` (default). Use `wrapper.find('[aria-label="..."]').trigger('click')` to test.
- Mock `router-view` with a simple template stub to avoid "Failed to resolve component" warnings.
- Follow existing test patterns: `vi.mock` for vue-router/stores/icons, `defineComponent` for stubs that need click emitters, simple template objects for passive stubs.

### Verification
- `pnpm test:unit`: 27/27 tests pass (7 test files)
- `pnpm build`: vue-tsc --noEmit + vite build — zero TypeScript errors, build successful

## Profile Self-Service APIs (2026-06-13)

### TDD Approach
- RED → GREEN → REFACTOR flow worked cleanly. Test file written first, verified 5/7 failures (2 unauthorized tests passed via JWT filter before controller existed).
- MockMvc + cookie-based JWT auth pattern from `AuthEndpointSecurityTest` was the right choice for integration-level controller tests.

### Patterns Used
- **Controller**: `@RestController` + `@RequestMapping("/profile")` + `@PreAuthorize("isAuthenticated()")` + `@RequiredArgsConstructor`
- **Auth**: `UserContext.getCurrentUserId()` for server-side user identification (never trust client-provided userId)
- **Response**: `ApiResponse<T>` wrapper, consistent with existing controllers
- **Error handling**: `BusinessException` + `@ResponseStatus(HttpStatus.BAD_REQUEST)` on GlobalExceptionHandler for 400 responses
- **Password security**: BCrypt via `passwordEncoder.matches()` for verification, `passwordEncoder.encode()` for new passwords
- **Whitelist pattern**: Explicit field-by-field null checks for allowed fields (realName, phone, email, avatar); all other fields ignored server-side

### Key Decisions
- Added `@ResponseStatus(HttpStatus.BAD_REQUEST)` to `GlobalExceptionHandler.handleBusinessException()` — semantically correct and zero regressions (215 tests pass)
- Password updates use direct `sysUserMapper.updateById()` with ONLY id+password set — NOT routed through `SysUserService.update()` to avoid accidental field updates
- Role/permission loading duplicated from AuthService in ProfileService for loose coupling (system → auth dependency avoided)
- Test tokens generated with `jwtUtils.generateToken()` using admin ID=1, roles=["ADMIN"], tenantId=0L

### Test Cases Implemented
1. `testUnauthorized_ProfileUpdate` → 401 (no JWT)
2. `testUnauthorized_PasswordChange` → 401 (no JWT)
3. `testUpdateProfile_Success` → 200 + updated UserInfo
4. `testUpdateProfile_IgnoresRestrictedFields` → username blocked, realName allowed
5. `testChangePassword_Success` → 200
6. `testChangePassword_WrongOldPassword` → 400 + "旧密码不正确"
7. `testChangePassword_ShortNewPassword` → 400 + VALIDATION_ERROR

### Files Created/Modified
1. `backend/src/main/java/com/cgcpms/system/controller/ProfileController.java` — NEW
2. `backend/src/main/java/com/cgcpms/system/service/ProfileService.java` — NEW
3. `backend/src/main/java/com/cgcpms/system/dto/UpdateProfileRequest.java` — NEW
4. `backend/src/main/java/com/cgcpms/system/dto/ChangePasswordRequest.java` — NEW
5. `backend/src/test/java/com/cgcpms/system/controller/ProfileControllerTest.java` — NEW
6. `backend/src/main/java/com/cgcpms/common/exception/GlobalExceptionHandler.java` — MODIFIED (added @ResponseStatus(BAD_REQUEST) to BusinessException handler)

## SysUserPreference Entity/Mapper/VO (2026-06-13)

### Files Created
- SysUserPreference.java: Extends BaseEntity, @TableName("sys_user_preference"), @TableId(ASSIGN_ID), fields: id, tenantId, userId, preferences (String/JSON)
- SysUserPreferenceMapper.java: @Mapper, extends BaseMapper<SysUserPreference>
- SysUserPreferenceVO.java: @Data, fields: id, tenantId, userId, preferences

### Conventions Followed
- @Data + @EqualsAndHashCode(callSuper=true) on entity (matches SysUser)
- @JsonProperty(READ_ONLY) on id, tenantId, userId (matches SysUser pattern)
- Standard Java types (Long for IDs, String for preferences JSON text)
- BaseEntity inheritance provides: createdBy, createdAt, updatedBy, updatedAt, deletedFlag, remark
- ./mvnw compile passes with zero errors

## PreferenceController + PreferenceService CRUD (2026-06-13)

### TDD Approach
- RED: Wrote PreferenceControllerTest first with 6 test cases
- GREEN: Implemented PreferenceService + PreferenceController
- REFACTOR: Extracted `parsePreferencesJson()` helper to eliminate duplicate JSON parsing logic
- Result: 6/6 tests pass, BUILD SUCCESS

### Patterns Used
- **Controller**: `@RestController` + `@RequestMapping("/profile/preferences")` + `@PreAuthorize("isAuthenticated()")` + `@RequiredArgsConstructor`
- **Auth**: `UserContext.getCurrentUserId()` and `UserContext.getCurrentTenantId()` server-side — never trust client
- **Response**: `ApiResponse.success(Map)` wrapping preferences as Map<String, Object>
- **Merge strategy**: New preferences are MERGED onto existing values. PUT replaces only specified keys, preserving unspecified ones.
- **Default preferences**: `sidebarCollapsed: false, notificationEnabled: true, theme: "light", tableDensity: "middle"`
- **JSON handling**: Jackson ObjectMapper for serialize/deserialize; `TypeReference<Map<String, Object>>()` for generic type

### Key Decisions
- **saveOrUpdate**: Used `insertOrUpdate()` — MyBatis-Plus determines insert vs update based on id presence
- **Merge-on-update**: Query existing record → merge defaults → apply existing values → apply new values → save
- **JSON parse failure**: Graceful fallback to defaults with WARN log — never throws 500 for corrupted preferences
- **H2 migration required**: V47 had to be added to `db/migration-h2/` as well as `db/migration/`. H2 local profile uses a separate migration directory.
- **BaseEntity columns**: Both MySQL and H2 V47 migrations needed `created_by`, `updated_by`, `deleted_flag`, `remark` columns to match BaseEntity fields — these were missing from the initial MySQL V47.

### Test Cases (6)
1. `testUnauthorized_Get` → 401 (GET without JWT)
2. `testUnauthorized_Put` → 401 (PUT without JWT)
3. `testGetPreferences_Default` → 200 + default values (no saved record)
4. `testPutPreferences_Update` → PUT saves, GET returns saved values
5. `testPutPreferences_Partial` → PUT only theme → other keys preserve existing values
6. `testGetPreferences_Existing` → full roundtrip: PUT then GET returns exact values

### Files Created/Modified
1. `backend/src/main/java/com/cgcpms/system/controller/PreferenceController.java` — NEW
2. `backend/src/main/java/com/cgcpms/system/service/PreferenceService.java` — NEW
3. `backend/src/test/java/com/cgcpms/system/controller/PreferenceControllerTest.java` — NEW
4. `backend/src/main/resources/db/migration/V47__add_user_preference.sql` — MODIFIED (added BaseEntity columns)
5. `backend/src/main/resources/db/migration-h2/V47__add_user_preference.sql` — NEW (H2-compatible migration)

### Gotchas
- **H2 migration directory**: `application-local.yml` uses `classpath:db/migration-h2`, NOT `classpath:db/migration`. Always add migrations to BOTH directories.
- **H2 `ON UPDATE CURRENT_TIMESTAMP`**: Not supported. Use `DEFAULT CURRENT_TIMESTAMP` without `ON UPDATE` in H2 migrations.
- **`insertOrUpdate()` behavior**: Queries by id first. If id exists → UPDATE; if not → INSERT. The entity must have an id (auto-generated via ASSIGN_ID) for insert to work correctly.
- **`@TestMethodOrder(OrderAnnotation.class)`**: Needed because later tests depend on state from earlier tests (PUT before GET). Tests are ordered 1-5.
