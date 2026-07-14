package com.cgcpms.overhead.vo;

/** 手工/定时分摊的可观测结果；金额使用字符串避免前端精度损失。 */
public record OverheadAllocationExecutionResult(
        String period,
        int ruleCount,
        int createdRunCount,
        int duplicateRunCount,
        int costItemCount,
        String allocatedAmount,
        boolean idempotent) {
}
