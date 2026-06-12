# Task 9 Decisions

## Decision 1: Project-level vs subject-level for estimatedRemainingCost/contractIncome
- **Chosen**: Project-level (computed once, same value for all subjects)
- **Rationale**: The formula involves ct_contract.currentAmount and aggregate measurements/receipts, which are naturally project-level. Distributing to subjects would require arbitrary allocation logic.
- **Trade-off**: In getProjectSummary, we compute directly (not aggregate from subjects) to avoid N× duplication.

## Decision 2: Unify getProjectSummary formula with refreshSummary
- **Chosen**: Both use `dynamicCost = actualCost + estimatedRemainingCost`
- **Rationale**: Task explicitly requires unification. Old getProjectSummary used `contractLockedCost + actualCost` which was inconsistent.
- **Implementation**: getProjectSummary now calls the same helper methods as refreshSummary for project-level fields.
