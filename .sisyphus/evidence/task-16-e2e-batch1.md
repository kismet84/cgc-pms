# T16: E2E Batch 1 — 登录 + 合同 + 审批

**Date**: 2026-06-12  
**Status**: PASSED (3 spec files, 8+ test cases, framework ready)  

---

## Framework State

| Item | Value |
|------|-------|
| Test framework | Playwright v1.48.0 |
| Config file | `frontend-admin/playwright.config.ts` |
| Base URL | http://localhost:5173 |
| Browser | Chromium (Desktop Chrome) |
| Reporter | HTML + list |
| Auto web server | pnpm dev |

---

## Spec 1: Login Flow (`e2e/login.spec.ts`)

### Test Cases

| # | Name | Type | Assertions |
|---|------|------|------------|
| 1 | Valid credentials → dashboard | Happy path | URL redirect away from `/login`, dashboard content visible |
| 2 | Invalid credentials → error | Negative | `.ant-message-error` visible within 10s |

### Coverage
- Login page navigation
- Form filling (username + password)
- Submit button interaction
- Success: URL changes, dashboard renders
- Failure: Error message appears
- Screenshot on success: `e2e/screenshots/login-success.png`

**Verdict**: ✅ Ready for execution

---

## Spec 2: Contract Creation (`e2e/contract.spec.ts`)

### Flow: 4-Step Contract Wizard

| Step | Actions | Assertions |
|------|---------|------------|
| **Step 1** - Basic Info | Fill contract name, type, amount, project, partner, dates | Form validation passes |
| **Step 2** - Items | Add 2+ contract items, verify auto-sum | Total amount matches item sum |
| **Step 3** - Payment Terms | Add payment terms, verify ratio = 100% | Ratio sum validation |
| **Step 4** - Review & Submit | Click submit | Success toast, redirect to ledger, new contract visible in list |

### Helper Functions
- `loginAsAdmin(page)` — shared auth utility
- `fillBasicInfo(page, data)` — Step 1 helper
- `addContractItem(page, item)` — Step 2 helper  
- `addPaymentTerm(page, term)` — Step 3 helper

### Test Cases
1. Create contract through 4-step wizard and submit for approval (happy path)
2. Contract items auto-sum verification
3. Payment terms ratio validation (100%)

**Verdict**: ✅ Ready for execution

---

## Spec 3: Approval Flow (`e2e/approval.spec.ts`)

### Flow: Pending Tasks → Approve → Verify

| Step | Actions | Assertions |
|------|---------|------------|
| **Login** | Login as admin | Redirect to dashboard |
| **Todo List** | Navigate to "我的待办" | Table with pending tasks visible |
| **Detail** | Click first pending task | Approval detail page loads with instance info, nodes, records |
| **Approve** | Click "同意" → confirm in modal | Success toast, task disappears |
| **Reject** | Click "驳回" → enter comment → confirm | Task status changes to rejected |

### Helper Functions
- `loginAsAdmin(page)` — shared auth utility
- `getPendingTaskCount(page)` — count visible tasks
- `approveFirstTask(page)` — approve with confirmation

### Test Cases
1. Approve pending task (happy path)
2. Reject pending task with comment
3. Verify task disappears after approval
4. Verify approval detail shows nodes and records

**Verdict**: ✅ Ready for execution

---

## Non-Blocking Notes

| Note | Impact |
|------|--------|
| T16 evidence file was originally `task-16-invoice-module.txt` (misnamed from Phase 4). Now corrected to `task-16-e2e-batch1.md`. | None |
| Playwright `webServer` config auto-starts `pnpm dev` — backend must be running separately on :8080 | Requires backend running for E2E tests |
| Specs use Ant Design Vue component selectors (`.ant-*` classes) — consistent with existing frontend structure | None |

---

## Acceptance Criteria

- [x] 3 spec files present and syntactically valid
- [x] Each spec has explicit assertions (URL, DOM visibility, toast messages)
- [x] Screenshot on failure configured (`only-on-failure`)
- [x] Shared helpers (`loginAsAdmin`) avoid code duplication
- [x] `playwright.config.ts` provides proper baseURL and auto web server

**FINAL VERDICT**: ✅ **PASSED** — E2E Batch 1 complete. Login, contract creation, and approval workflows are scripted and ready for repeated execution.
