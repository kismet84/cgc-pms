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
    /** 总工程师技术事项 */
    public static final String TECH_ITEM = "TECH_ITEM";
    /** 项目资金闭环：项目预算版本审批 */
    public static final String PROJECT_BUDGET = "PROJECT_BUDGET";
    /** 项目资金闭环：费用申请审批 */
    public static final String EXPENSE = "EXPENSE";
    /** 项目收入闭环：业主结算审批 */
    public static final String OWNER_SETTLEMENT = "OWNER_SETTLEMENT";
    /** 产值计量闭环：内部产值审批 */
    public static final String PRODUCTION_MEASUREMENT = "PRODUCTION_MEASUREMENT";
}
