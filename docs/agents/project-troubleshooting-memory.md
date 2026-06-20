# Project Troubleshooting Memory

This document records recurring project-specific mistakes and their verified fixes. Read it before debugging runtime, API, approval workflow, dictionary, contract, or multi-agent issues.

## Backend Runtime And Docker

### New API Returns `No static resource ...`

Symptom:

- Backend log shows `NoResourceFoundException: No static resource workflow/templates`.
- A controller class exists in `target/classes`, but the API still returns 404.

Root cause:

- The Docker backend runs `java -jar target/cgc-pms-backend.jar`.
- `target/classes` may be current while the executable jar is stale.
- The backend context path is `/api`, so direct host checks must use `/api/...`.

Verified fix:

```powershell
docker exec cgc-pms-backend-dev sh -lc "MAVEN_CONFIG= ./mvnw -q -DskipTests clean package"
docker restart cgc-pms-backend-dev
Invoke-WebRequest -UseBasicParsing 'http://localhost:8080/api/workflow/templates?pageNo=1&pageSize=20'
```

Expected result:

- Without login, a protected endpoint should return auth failure such as `401`, not `No static resource ...`.
- With admin login, it should return business JSON.

Important details:

- The Maven image sets `MAVEN_CONFIG=/root/.m2`; Maven wrapper can misread it as a lifecycle phase.
- Clear it only for the command: `MAVEN_CONFIG= ./mvnw ...`.
- If `package` fails during Spring Boot repackage with `jar.original`, run `clean package`.

### PowerShell Maven Wrapper Command Error

Symptom:

- Running `MAVEN_CONFIG= ./mvnw ...` in PowerShell fails with `The term 'MAVEN_CONFIG=' is not recognized`.

Root cause:

- `MAVEN_CONFIG=` is a POSIX shell prefix, not PowerShell syntax.

Verified fix:

```powershell
$env:MAVEN_CONFIG=''
.\\mvnw -q -DskipTests clean package
```

Rule:

- Use PowerShell environment assignment when running local tests on Windows.
- Keep the `MAVEN_CONFIG=` workaround only for Linux container shells.

### Backend Context Path

Symptom:

- `http://localhost:8080/workflow/templates` returns Tomcat 404.

Root cause:

- Backend context path is `/api`.

Verified fix:

- Use `http://localhost:8080/api/workflow/templates`.
- Frontend API modules should keep URLs like `/workflow/templates`; Axios `baseURL` is `/api`.

### Wait After Docker Rebuild Or Restart

Symptom:

- Tests fail immediately after rebuilding or restarting Docker frontend/backend even though the code is correct.
- API, frontend route, Flyway migration, or login checks may intermittently fail during startup.

Root cause:

- Docker containers can report as running before Spring Boot, Vite, migrations, proxy, and app routes are fully ready.

Verified fix:

```powershell
docker restart cgc-pms-backend-dev cgc-pms-frontend-dev
Start-Sleep -Seconds 120
```

Rule:

- After any Docker frontend/backend rebuild or restart, wait 2 minutes before browser/API testing.
- Only start verification after Docker is fully started and backend logs show Spring Boot has completed startup.

## PowerShell Command Safety

### Regex Alternation Breaks Commands

Symptom:

- Commands like `rg -n "foo|bar"` unexpectedly fail with messages such as `'bar' is not recognized`.

Root cause:

- PowerShell may interpret `|` as a pipeline when quoting/escaping is wrong.

Verified fix:

```powershell
rg -n 'workflow/templates|WorkflowTemplateController|getWorkflowTemplates' backend frontend-admin -S
```

Rules:

- Wrap regex patterns in single quotes.
- For complex filters, prefer `Select-String -Pattern '...'`.

## Dictionary And Encoding Issues

### Dictionary Labels Show Mojibake

Symptom:

- Labels such as `åˆä½œæ–¹ç±»åž‹` appear in dictionary pages or approval pages.

Root cause:

- Seed/migration text was saved or inserted with the wrong encoding.

Verified fix:

- Normalize seed data to UTF-8 Chinese text.
- Add an idempotent migration to repair existing bad rows.
- Restart/rebuild backend if the migration is newly added.

### Page Still Shows Raw Codes

Symptom:

- Partner list shows `PARTY_A` instead of `甲方`.
- New partner type dropdown is empty.

Root cause:

- Frontend page is not loading the dictionary by code, or the dictionary data was not seeded/loaded.

Verified fix:

- Confirm dictionary API returns the relevant type.
- Bind partner/cost subject type display to dictionary labels, not hardcoded raw codes.
- After backend migration changes, rebuild/restart backend and refresh frontend session.

## Contract Module

### Contract Save Duplicate Code

Symptom:

- `Duplicate entry '0-CT-YYYYMMDD-002' for key 'ct_contract.uk_ct_contract_code'`.

Root cause:

- Contract code generation queried only non-deleted records or reused a code after manual/test data changes.

Verified fix:

- Generate the next contract code from the actual max existing code for the day.
- Do not assume a deleted/manual row is safe to reuse if a unique key still blocks it.
- If the user manually deletes a row, confirm directly in DB/logs before retesting.

### Contract Details Or Payment Terms Not Saved

Symptom:

- Draft contract is created but detail lines and payment conditions are missing.

Root cause:

- The create API persisted only `ct_contract`, not child detail/term payloads, or frontend did not send them in the expected shape.

Verified fix:

- Trace frontend payload at `ContractFormPage.vue`.
- Verify backend create endpoint persists contract header, detail lines, and payment terms transactionally.
- Validate by querying child tables after save, not only by seeing contract header success.

## Cost Target Module

### Cost Subject Duplicate Code Throws Database Error

Symptom:

- Creating a cost subject fails with `Duplicate entry '0-001002' for key 'cost_subject.uk_cost_subject_code'`.
- Logs may show a prior `selectCount` line, then MySQL still throws `DuplicateKeyException`.

Root cause:

- `cost_subject.uk_cost_subject_code` is unique on `(tenant_id, subject_code)`.
- MyBatis-Plus normal queries auto-filter `deleted_flag=0`.
- A logically deleted subject can still occupy the database unique key, so checking only active rows misses the conflict.

Verified fix:

- Check subject code uniqueness against all rows, including logically deleted rows.
- Use a mapper SQL method that does not apply the `deleted_flag=0` logic-delete filter.
- Return a business error such as `SUBJECT_CODE_DUPLICATE` before insert/update.

Rule:

- When a table uses logical delete and a unique index does not include `deleted_flag`, duplicate checks must match the database unique index, not just active rows.

### New Target ID Loses Precision

Symptom:

- Creating a target cost returns a 19-digit ID, but the follow-up save-items request says the target does not exist.

Root cause:

- The backend returns Snowflake-style `Long` IDs.
- If the frontend treats the ID as a JS `number`, precision is lost and the follow-up request uses the wrong path id.

Verified fix:

- Treat created IDs as strings in the frontend API and page state.
- Keep route IDs, detail IDs, and save-items IDs as strings end-to-end.
- Only convert to `Long` on the backend boundary.

Rule:

- Never pass long snowflake IDs through JS `number` if the value may exceed safe integer precision.

### Cost Ledger Page Returns 500

Symptom:

- Entering the cost ledger page shows a load error.
- `GET /api/cost-ledger?pageNum=1&pageSize=20` returns `500 SYSTEM_ERROR`.
- Backend stack trace points to `CostLedgerService.toVO` and `ImmutableCollections$MapN.get`.

Root cause:

- Some cost item relation IDs are optional, such as `contract_id`, `partner_id`, or `cost_subject_id`.
- Empty name maps may be created with `Map.of()`.
- Calling `Map.of().get(null)` throws `NullPointerException`, unlike `HashMap#get(null)`.

Verified fix:

- Use a null-safe helper for all optional relation-name lookups before converting ledger rows to VO.
- Add a regression test with a `cost_item` row whose optional relation IDs are `null`.

Rule:

- Never call `Map.of().get(id)` when `id` can be `null`; guard `id == null` first.

### Contract Ledger Project Selector Empty

Symptom:

- Contract ledger project selector has no project options.

Root cause:

- Frontend selector not bound to the active project list API, or API request requires correct `/api` base and login cookies.

Verified fix:

- Check network request for project list.
- Confirm project API returns records in logged-in browser session.
- Map selector value/label from project `id` and project name fields.

## Approval Workflow

### Approval Flow Text Is Garbled

Symptom:

- Approval nodes display text like `é¡¹ç›®ç»ç†å®¡æ‰¹`.

Root cause:

- Workflow template seed data contains mojibake.

Verified fix:

- Add migration to normalize workflow template and node names.
- Restart/rebuild backend so migrations run.
- Confirm with API response, not only UI display.

### Approval Template Page Load Error

Symptom:

- Approval process management page reports template load error.
- Backend log shows `No static resource workflow/templates`.

Root cause:

- Stale backend jar or missing `/api` context path during direct verification.

Verified fix:

- Rebuild backend jar with:

```powershell
docker exec cgc-pms-backend-dev sh -lc "MAVEN_CONFIG= ./mvnw -q -DskipTests clean package"
docker restart cgc-pms-backend-dev
```

- Verify:

```powershell
Invoke-WebRequest -UseBasicParsing 'http://localhost:8080/api/workflow/templates?pageNo=1&pageSize=20'
```

## Frontend Session And Browser

### Backend Works But UI Still Errors

Symptom:

- API works in PowerShell after login, but browser still shows errors.

Root cause:

- Browser may hold stale cookies/session or stale frontend state.

Verified fix:

- Refresh page.
- Re-login as `admin / admin123` in local dev.
- Recheck browser network tab after reload.

## Multi-Agent Workflow

### Child Agents Do Not Report Completion

Symptom:

- Development/testing work finishes but main agent is not notified.

Root cause:

- Child agent did not send the required document-link notification to the main agent session.

Verified fix:

- Every task file must include the main agent session ID.
- After writing a report, child agent must send only:
  - report path,
  - one short sentence.
- If direct send is unavailable, write a completion marker under `.agent-runtime/messages/`.

## Before Claiming A Fix

Always verify with the narrowest real check:

- API route fixes: call the exact `/api/...` endpoint.
- Docker backend fixes: confirm jar contents with `jar tf target/cgc-pms-backend.jar`.
- UI data fixes: confirm backend JSON and frontend network request.
- Encoding fixes: confirm stored DB/API values are valid Chinese, not only the rendered page.
- Contract save fixes: confirm parent and child rows are persisted.
