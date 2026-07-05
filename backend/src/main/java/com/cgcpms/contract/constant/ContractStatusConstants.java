package com.cgcpms.contract.constant;

import com.cgcpms.system.dict.util.DictUtils;

public final class ContractStatusConstants {

    private ContractStatusConstants() {
    }

    // ── 审批状态 (approval_status) ──
    public static final String APPROVAL_DRAFT = "DRAFT";
    public static final String APPROVAL_APPROVING = "APPROVING";
    public static final String APPROVAL_APPROVED = "APPROVED";
    public static final String APPROVAL_REJECTED = "REJECTED";
    public static final String APPROVAL_WITHDRAWN = "WITHDRAWN";

    // ── 合同业务状态 (contract_status) ──
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PERFORMING = "PERFORMING";
    public static final String STATUS_SETTLED = "SETTLED";
    public static final String STATUS_TERMINATED = "TERMINATED";

    // ── 审批业务类型 ──
    public static final String BUSINESS_TYPE_CONTRACT_APPROVAL = "CONTRACT_APPROVAL";

    // ── 字典查询方法 ──

    /**
     * 获取审批状态标签
     */
    public static String getApprovalStatusLabel(String value) {
        return DictUtils.getLabelByValue("approval_status", value);
    }

    /**
     * 获取合同状态标签
     */
    public static String getContractStatusLabel(String value) {
        return DictUtils.getLabelByValue("contract_status", value);
    }
}
