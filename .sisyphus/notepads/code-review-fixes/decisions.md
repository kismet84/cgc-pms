# Decisions - Code Review Fixes

## Fix Strategy
- Follow the suggested fix order from the report
- Fix backend + frontend for each issue pair where both sides are affected
- Add regression tests where indicated in the report

## Contract Alignment Approach
- For CostSummary: Backend will return project-level aggregate object (方案 A) — cleaner contract
- For Payment Basis: Backend `/{id}/basis` will return `List<PayApplicationBasisVO>` directly
