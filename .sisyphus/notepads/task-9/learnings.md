# Task 9 Learnings

## Key Patterns
- VarOrder.direction uses "COST"/"INCOME" (not "ADD"/"REDUCE" as V12 SQL comments indicate)
- Project-level fields (estimatedRemainingCost, contractIncome) are NOT aggregated across subjects in getProjectSummary — they're computed directly via helpers to avoid N× duplication
- MyBatis-Plus LambdaQueryWrapper works well for simple equality queries; complex aggregates use mapper.selectList + stream.reduce

## Data Sources
- contractIncome: ct_contract.contractAmount + var_order.approvedAmount (direction=INCOME, approved)
- estimatedRemainingCost: ct_contract.currentAmount - sub_measure.approvedAmount (approved) - mat_receipt.totalAmount (approved)
- dynamicCost: actualCost + estimatedRemainingCost (was: contractLockedCost + actualCost)
- expectedProfit: contractIncome - dynamicCost
