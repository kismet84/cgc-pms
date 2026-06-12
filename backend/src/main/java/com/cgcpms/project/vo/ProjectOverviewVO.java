package com.cgcpms.project.vo;

import lombok.Data;

import java.util.List;

/**
 * Project overview aggregation — single endpoint replacing N per-entity queries.
 * All monetary amounts use BigDecimal plain string for consistency.
 */
@Data
public class ProjectOverviewVO {

    /** Project ID for correlation. */
    private String projectId;

    /** Total number of contracts under this project. */
    private String contractCount;

    /** SUM of ct_contract.contractAmount for this project. */
    private String totalContractAmount;

    /** SUM of cost_summary.dynamicCost (latest summary_date) for this project. */
    private String dynamicCost;

    /** SUM of pay_record.payAmount WHERE payStatus = 'SUCCESS'. */
    private String paidAmount;

    /** COUNT of alert_log this month. */
    private String warningCount;

    /** Total number of project members. */
    private String memberCount;

    /** Member list with userId, userName, roleCode batch-loaded. */
    private List<MemberBriefVO> members;

    @Data
    public static class MemberBriefVO {

        /** User ID as string (matches convention of all VO id fields). */
        private String userId;

        /** User's real name from sys_user (batch-loaded). */
        private String userName;

        /** Role code: PM / CM / CSTM / FIN / SUBC / MAT / etc. */
        private String roleCode;
    }
}
