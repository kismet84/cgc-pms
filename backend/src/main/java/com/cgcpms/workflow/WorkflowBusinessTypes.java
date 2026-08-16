package com.cgcpms.workflow;

public final class WorkflowBusinessTypes {
    private WorkflowBusinessTypes() {}

    public static final String CONTRACT_APPROVAL = "CONTRACT_APPROVAL";
    /** 项目主数据审批 */
    public static final String PROJECT_APPROVAL = "PROJECT_APPROVAL";
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
    /** 项目计划履约闭环：基线与修订计划审批 */
    public static final String PROJECT_SCHEDULE = "PROJECT_SCHEDULE";
    /** 项目开工准入审批 */
    public static final String PROJECT_COMMENCEMENT = "PROJECT_COMMENCEMENT";
    /** 项目计划履约闭环：月计划与周计划审批 */
    public static final String PROJECT_PERIOD_PLAN = "PROJECT_PERIOD_PLAN";
    /** 项目计划履约闭环：延期纠偏审批 */
    public static final String PROJECT_CORRECTIVE_ACTION = "PROJECT_CORRECTIVE_ACTION";
    /** 目标成本与动态利润闭环：成本偏差纠偏审批 */
    public static final String COST_CORRECTIVE_ACTION = "COST_CORRECTIVE_ACTION";
    /** 图纸、RFI与技术方案闭环：技术方案审批 */
    public static final String TECHNICAL_SCHEME = "TECHNICAL_SCHEME";
    /** 项目竣工与收尾闭环：竣工验收审批 */
    public static final String PROJECT_FINAL_ACCEPTANCE = "PROJECT_FINAL_ACCEPTANCE";
    /** 投标成本移交审批命令 */
    public static final String BID_COST_TARGET_TRANSFER = "BID_COST_TARGET_TRANSFER";
    /** 财务成本分摊审批命令 */
    public static final String FINANCE_COST_ALLOCATION = "FINANCE_COST_ALLOCATION";
    /** 质量安全整改审批 */
    public static final String QS_RECTIFICATION = "QS_RECTIFICATION";
    /** 质量安全金额后果审批 */
    public static final String QS_CONSEQUENCE = "QS_CONSEQUENCE";
    /** 成本规则方案审批 */
    public static final String COST_RULE_PLAN = "COST_RULE_PLAN";
    /** 项目成本配置审批 */
    public static final String COST_PROJECT_CONFIG = "COST_PROJECT_CONFIG";
    /** 全量历史重算审批 */
    public static final String COST_RECALCULATION = "COST_RECALCULATION";
    /** 关闭项目后的财务调整审批 */
    public static final String COST_POST_CLOSE_ADJUSTMENT = "COST_POST_CLOSE_ADJUSTMENT";
    /** 投标转入、财务分摊或重算冲销审批 */
    public static final String COST_REVERSAL = "COST_REVERSAL";
}
