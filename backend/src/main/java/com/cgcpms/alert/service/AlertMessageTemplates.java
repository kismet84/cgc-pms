package com.cgcpms.alert.service;

import java.util.Map;

/**
 * Alert message templates and rule-type → domain/category mappings,
 * shared by {@link AlertRuleEvaluator} and {@link AlertEvaluationService}.
 */
final class AlertMessageTemplates {

    static final Map<String, String> TEMPLATES = Map.of(
            "DYNAMIC_COST_EXCEEDS_TARGET", "动态成本 %s 超出目标成本 %s，偏差 %s，请复核项目动态成本。",
            "MATERIAL_EXCEEDS_BUDGET", "材料验收金额 %s 超出合同 %s(%s) 金额 %s，请复核材料采购及损耗。",
            "SUBCONTRACT_EXCEEDS_CONTRACT", "分包计量累计金额 %s 超出合同 %s(%s) 金额 %s，请复核分包计量。",
            "CONTRACT_OVERDUE", "以下合同已超期：%s，请尽快处理合同履约进度。",
            "PAYMENT_EXCEEDS_RATIO", "合同 %s(%s) 累计付款 %s 超过合同金额 %s（比例 %.0f%%），请复核付款计划。",
            "WARRANTY_EARLY_RELEASE", "合同 %s(%s) 质保金 %.2f 已于 %s 定案，但保修期至 %s 尚未届满，请复核质保金释放。",
            "CONTRACT_EXPIRING", "以下合同即将到期（%d天内）：%s，请提前安排续签或收尾。",
            "VARIATION_UNCONFIRMED", "以下变更签证已审批超%d天仍未获甲方确认：%s，请跟进确认。",
            "PURCHASE_DELIVERY_OVERDUE", "采购订单 %s 交期已逾期至 %s，请跟进供应商交付。",
            "CASH_JOURNAL_ARCHIVE_OVERDUE", "资金流水 %s 超过24小时未归档，请补齐资金账户、附件并确认归档。"
    );

    private AlertMessageTemplates() {
    }

    static String format(String ruleType, Object... args) {
        return String.format(TEMPLATES.get(ruleType), args);
    }

    static String domain(String ruleType) {
        return switch (ruleType) {
            case "DYNAMIC_COST_EXCEEDS_TARGET", "MATERIAL_EXCEEDS_BUDGET",
                    "SUBCONTRACT_EXCEEDS_CONTRACT" -> "COST";
            case "CONTRACT_OVERDUE", "CONTRACT_EXPIRING", "WARRANTY_EARLY_RELEASE" -> "CONTRACT";
            case "PAYMENT_EXCEEDS_RATIO" -> "PAYMENT";
            case "VARIATION_UNCONFIRMED" -> "VARIATION";
            case "PURCHASE_DELIVERY_OVERDUE" -> "PURCHASE";
            case "CASH_JOURNAL_ARCHIVE_OVERDUE" -> "FINANCE";
            default -> "OTHER";
        };
    }

    static String category(String ruleType) {
        return switch (ruleType) {
            case "DYNAMIC_COST_EXCEEDS_TARGET" -> "COST_DYNAMIC";
            case "MATERIAL_EXCEEDS_BUDGET" -> "COST_MATERIAL";
            case "SUBCONTRACT_EXCEEDS_CONTRACT" -> "COST_SUBCONTRACT";
            case "CONTRACT_OVERDUE", "CONTRACT_EXPIRING" -> "CONTRACT_TERM";
            case "WARRANTY_EARLY_RELEASE" -> "CONTRACT_WARRANTY";
            case "PAYMENT_EXCEEDS_RATIO" -> "PAYMENT_RATIO";
            case "VARIATION_UNCONFIRMED" -> "VARIATION_CONFIRM";
            case "PURCHASE_DELIVERY_OVERDUE" -> "PURCHASE_DELIVERY";
            case "CASH_JOURNAL_ARCHIVE_OVERDUE" -> "CASH_JOURNAL_CLOSURE";
            default -> "OTHER";
        };
    }

    static String title(String ruleType) {
        return switch (ruleType) {
            case "DYNAMIC_COST_EXCEEDS_TARGET" -> "动态成本超目标预警";
            case "MATERIAL_EXCEEDS_BUDGET" -> "材料超预算预警";
            case "SUBCONTRACT_EXCEEDS_CONTRACT" -> "分包超合同预警";
            case "CONTRACT_OVERDUE" -> "合同超期预警";
            case "PAYMENT_EXCEEDS_RATIO" -> "付款超比例预警";
            case "WARRANTY_EARLY_RELEASE" -> "质保金提前释放预警";
            case "CONTRACT_EXPIRING" -> "合同到期预警";
            case "VARIATION_UNCONFIRMED" -> "变更未确认预警";
            case "PURCHASE_DELIVERY_OVERDUE" -> "采购交期逾期预警";
            case "CASH_JOURNAL_ARCHIVE_OVERDUE" -> "资金流水归档逾期预警";
            default -> "项目预警";
        };
    }
}
