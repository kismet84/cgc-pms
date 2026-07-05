package com.cgcpms.settlement.constant;

import com.cgcpms.system.dict.util.DictUtils;

/**
 * 结算模块状态常量 — 统一管理 settlement lifecycle/approval/settlement 三个状态维度的枚举值。
 * <p>
 * 三个独立状态字段：
 * <ul>
 *   <li>{@code status} — 结算生命周期状态，由业务操作驱动</li>
 *   <li>{@code approvalStatus} — 审批流状态，由工作流引擎驱动</li>
 *   <li>{@code settlementStatus} — 结算定案状态，审批通过后自动锁定</li>
 * </ul>
 */
public final class SettlementStatusConstants {

    private SettlementStatusConstants() {
        // utility class
    }

    // ── 生命周期状态 (status) ──

    /** 草稿 — 初始状态，可编辑 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 已提交 */
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    /** 已审批 */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 已驳回 */
    public static final String STATUS_REJECTED = "REJECTED";
    /** 已作废 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    // ── 审批流状态 (approvalStatus) ──

    /** 审批中 */
    public static final String APPROVAL_APPROVING = "APPROVING";
    /** 审批已通过 */
    public static final String APPROVAL_APPROVED = "APPROVED";
    /** 审批已驳回 */
    public static final String APPROVAL_REJECTED = "REJECTED";
    /** 审批草稿/未提交 */
    public static final String APPROVAL_DRAFT = "DRAFT";

    // ── 定案状态 (settlementStatus) ──

    /** 草稿 — 尚未定案 */
    public static final String SETTLEMENT_DRAFT = "DRAFT";
    /** 已计算 — 金额已汇总但未锁定 */
    public static final String SETTLEMENT_CALCULATED = "CALCULATED";
    /** 已定案 — 金额锁定不可编辑（审批通过后自动置此状态） */
    public static final String SETTLEMENT_FINALIZED = "FINALIZED";

    // ── 业务类型 ──

    /** 结算审批工作流业务类型 */
    public static final String BUSINESS_TYPE_SETTLEMENT = "SETTLEMENT";

    // ── 字典查询方法 ──

    /**
     * 获取结算生命周期状态标签
     */
    public static String getSettlementStatusLabel(String value) {
        return DictUtils.getLabelByValue("settlement_status", value);
    }

    /**
     * 获取结算定案状态标签
     */
    public static String getSettlementFinalStatusLabel(String value) {
        return DictUtils.getLabelByValue("settlement_final_status", value);
    }

    /**
     * 获取审批状态标签
     */
    public static String getApprovalStatusLabel(String value) {
        return DictUtils.getLabelByValue("approval_status", value);
    }
}
