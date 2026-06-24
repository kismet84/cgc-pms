package com.cgcpms.workflow;

public final class WorkflowBusinessTypes {
    private WorkflowBusinessTypes() {}

    public static final String CONTRACT_APPROVAL = "CONTRACT_APPROVAL";
    public static final String PURCHASE_ORDER = "PURCHASE_ORDER";
    public static final String MATERIAL_RECEIPT = "MATERIAL_RECEIPT";
    public static final String SUB_MEASURE = "SUB_MEASURE";
    public static final String PAY_REQUEST = "PAY_REQUEST";
    public static final String VAR_ORDER = "VAR_ORDER";
    /** Phase 4: 采购申请 */
    public static final String PURCHASE_REQUEST = "PURCHASE_REQUEST";
    /** Phase 3: 合同变更 */
    public static final String CT_CHANGE = "CT_CHANGE";
    /** Phase 3: 结算 */
    public static final String SETTLEMENT = "SETTLEMENT";
    /** Phase 3: 成本目标 */
    public static final String COST_TARGET = "COST_TARGET";
    /** Phase 5: 业主收入确认 */
    public static final String CONTRACT_REVENUE = "CONTRACT_REVENUE";
    /** 领料申请 */
    public static final String MATERIAL_REQUISITION = "MATERIAL_REQUISITION";
}
