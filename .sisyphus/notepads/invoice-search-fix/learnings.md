# Learnings — invoice-search-fix

## 2026-06-15T08:45 Session Start
- Plan tasks: 6 (T1-T6) + Final Wave (F1-F4)
- Wave 1 (parallel): T1 (Controller), T2 (Service), T3 (IntegrationTest)
- Wave 2 (parallel, after Wave 1): T4 (MockMvc tests), T5 (regression), T6 (E2E)
- Guardrails: No frontend changes, no new migrations, no verifyStatus validation
- Pattern reference: VarOrderService.java:55-57, PayApplicationService.java:104-106

## 2026-06-15T08:48 Wave 1 Complete
- T1 (Controller), T2 (Service), T3 (IntegrationTest) all done, verified via manual code review + compile
- Compile: PASSES (mvnw compile -q)
- InvoiceServiceTest: FAILS due to pre-existing Flyway migration issue (V50 H2 index conflict)
  - This is NOT caused by our changes — ApplicationContext fails to load for ALL @SpringBootTest tests
  - Root cause: FlywayInitializer fails on migration-h2/V50__fix_invoice_unique_with_deleted_flag.sql
  - Decision: Proceed with code changes; test infra issue is out of scope

## T1: InvoiceController.list() completed
- Added `@RequestParam(required = false) String invoiceNo` (line 32)
- Added `@RequestParam(required = false) String verifyStatus` (line 33)
- Updated `getPage()` call: `invoiceService.getPage(pageNo, pageSize, payRecordId, payApplicationId, invoiceNo, verifyStatus)` (line 34)
- All other methods untouched; `@PreAuthorize` unchanged
- LSP (jdtls) not installed — manual verification only

## T2: InvoiceService.java completed
- Added `import org.springframework.util.StringUtils;` (line 20, after `@Transactional` import)
- `getPage()` signature updated: added `String invoiceNo, String verifyStatus` params
- Added `if (StringUtils.hasText(invoiceNo)) wrapper.like(PayInvoice::getInvoiceNo, invoiceNo);` (line 45)
- Added `if (StringUtils.hasText(verifyStatus)) wrapper.eq(PayInvoice::getVerifyStatus, verifyStatus);` (line 46)
- All existing conditions unchanged; `orderByDesc` stays last (line 47)
- Matches canonical pattern: VarOrderService:55-57, PayApplicationService:104-106
- LSP (jdtls) not installed — manual verification via read-back confirmed correct

## T3: Phase4IntegrationTest line 610 completed
- Changed `invoiceService.getPage(1, 20, null, null)` → `invoiceService.getPage(1, 20, null, null, null, null)`
- New null args: invoiceNo, verifyStatus (unused for tenant isolation test)
- No other lines modified; test semantics unchanged

## T4: InvoiceValidationTest.java (3 MockMvc filter tests) completed
- Import added: `import static ...MockMvcRequestBuilders.get;` (line 23)
- Test 1 `shouldFilterByInvoiceNoPartialMatch` (lines 140-168): POST invoice, then GET with `?invoiceNo=FILTER-PARTIAL`, asserts records > 0 and invoiceNo contains "FILTER-PARTIAL"
- Test 2 `shouldFilterByVerifyStatus` (lines 170-198): POST invoice, then GET with `?verifyStatus=PENDING`, asserts records > 0 and verifyStatus = "PENDING"
- Test 3 `shouldReturnEmptyForNonMatchingVerifyStatus` (lines 200-211): GET with `?verifyStatus=NONEXISTENT_STATUS`, asserts code=0 and total=0 (no error)
- Uses `org.hamcrest.Matchers.greaterThan` and `org.hamcrest.Matchers.containsString` for assertions
- Existing 4 tests (lines 50-138) untouched

## T6: E2E filter assertions strengthened (invoice.spec.ts)

- **Change 1 — create invoice filter (lines 143-148)**: Replaced weak if (hasInvoice) conditional with:
  - 	oHaveCount(1) — asserts exactly 1 row after filtering by invoice number
  - 	oContainText(invoiceNo) — validates the single row contains the created invoice number
  - .ant-tag visibility — validates PENDING status tag is visible
- **Change 2 — verify status filter (lines 284-296, 311-318)**: Added assertions after each status filter:
  - After "待核验": asserts pendingCount > 0, erifiedTags count = 0, bnormalTags count = 0
  - After "已认证": asserts erifiedCount2 > 0, pendingTags2 count = 0
- CSS selectors used: .ant-table-tbody tr.ant-table-row .ant-tag:has-text("待核验") etc.
- Screenshots preserved — assertions added before screenshot calls
- LSP diagnostics: clean (0 issues)
- File grew from 306 → 322 lines (+16 lines of assertions)
