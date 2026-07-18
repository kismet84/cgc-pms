package com.cgcpms.document.provider;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.settlement.service.StlSettlementQueryService;
import com.cgcpms.settlement.vo.SettlementApprovalRecordVO;
import com.cgcpms.settlement.vo.SettlementAttachmentVO;
import com.cgcpms.settlement.vo.SettlementCostItemVO;
import com.cgcpms.settlement.vo.SettlementPaymentItemVO;
import com.cgcpms.settlement.vo.StlSettlementItemVO;
import com.cgcpms.settlement.vo.StlSettlementVO;
import com.cgcpms.variation.vo.VarOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 结算文档只消费结算查询服务已组装的权威读取模型。
 * 金额字段在此规范化展示，不在模板或Provider内重新计算。
 */
@Component
@Profile("!document-test-provider")
@RequiredArgsConstructor
public class SettlementDocumentDataProvider implements DocumentDataProvider {
    public static final String SCHEMA_VERSION = "settlement.v1";
    private static final Set<String> PREVIEW_APPROVAL_STATUSES = Set.of("APPROVING", "APPROVED");

    private final StlSettlementQueryService settlementQueryService;

    @Override
    public String businessType() {
        return "SETTLEMENT";
    }

    @Override
    public DocumentDataSnapshot load(Long businessId) {
        return loadSnapshot(businessId, true);
    }

    @Override
    public DocumentDataSnapshot loadPreview(Long businessId) {
        return loadSnapshot(businessId, false);
    }

    private DocumentDataSnapshot loadSnapshot(Long businessId, boolean formal) {
        StlSettlementVO settlement = settlementQueryService.getById(businessId);
        if (settlement == null) {
            throw new BusinessException("DOCUMENT_BUSINESS_NOT_FOUND", "结算单不存在");
        }
        if (formal && (!"APPROVED".equals(settlement.getApprovalStatus())
                || !"FINALIZED".equals(settlement.getSettlementStatus()))) {
            throw new BusinessException("DOCUMENT_SETTLEMENT_NOT_FINALIZED", "正式结算单仅允许审批通过且定案后生成");
        }
        if (!formal && !PREVIEW_APPROVAL_STATUSES.contains(settlement.getApprovalStatus())) {
            throw new BusinessException("DOCUMENT_SETTLEMENT_PREVIEW_STATE_INVALID", "结算单仅允许审批中或审批通过后预览");
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("settlement", settlement(settlement));
        root.put("project", namedObject(settlement.getProjectId(), settlement.getProjectName()));
        root.put("contract", namedObject(settlement.getContractId(), settlement.getContractName()));
        root.put("partner", namedObject(settlement.getPartnerId(), settlement.getPartnerName()));
        root.put("audit", audit(settlement));
        return new DocumentDataSnapshot(SCHEMA_VERSION, root);
    }

    private Map<String, Object> settlement(StlSettlementVO value) {
        Map<String, Object> row = row();
        put(row, "id", value.getId());
        put(row, "code", value.getSettlementCode());
        put(row, "type", value.getSettlementType());
        put(row, "status", value.getStatus());
        put(row, "approvalStatus", value.getApprovalStatus());
        put(row, "finalStatus", value.getSettlementStatus());
        put(row, "amountFormulaVersion", value.getAmountFormulaVersion());
        row.put("amount", amounts(value));
        row.put("items", rows(value.getItems(), StlSettlementItemVO::getId, this::item));
        row.put("variations", rows(settlementQueryService.getVariations(id(value)), VarOrderVO::getId, this::variation));
        row.put("payments", rows(settlementQueryService.getPayments(id(value)), SettlementPaymentItemVO::getId,
                this::payment));
        row.put("costs", rows(settlementQueryService.getCosts(id(value)), SettlementCostItemVO::getId, this::cost));
        row.put("attachments", rows(settlementQueryService.getAttachments(id(value)), SettlementAttachmentVO::getId,
                this::attachment));
        row.put("approvalRecords", rows(settlementQueryService.getApprovalRecords(id(value)),
                SettlementApprovalRecordVO::getId, this::approvalRecord));
        return row;
    }

    private Map<String, Object> amounts(StlSettlementVO value) {
        Map<String, Object> row = row();
        money(row, "contract", value.getContractAmount());
        money(row, "change", value.getChangeAmount());
        money(row, "measured", value.getMeasuredAmount());
        money(row, "deduction", value.getDeductionAmount());
        money(row, "paid", value.getPaidAmount());
        money(row, "final", value.getFinalAmount());
        money(row, "unpaid", value.getUnpaidAmount());
        money(row, "warranty", value.getWarrantyAmount());
        return row;
    }

    private Map<String, Object> item(StlSettlementItemVO value) {
        Map<String, Object> row = row();
        put(row, "name", value.getItemName());
        put(row, "unit", value.getUnit());
        decimal(row, "quantity", value.getQuantity());
        money(row, "unitPrice", value.getUnitPrice());
        money(row, "amount", value.getAmount());
        put(row, "sourceType", value.getSourceType());
        put(row, "sourceId", value.getSourceId());
        put(row, "remark", value.getRemark());
        return row;
    }

    private Map<String, Object> variation(VarOrderVO value) {
        Map<String, Object> row = row();
        put(row, "code", value.getVarCode());
        put(row, "name", value.getVarName());
        put(row, "type", value.getVarType());
        put(row, "direction", value.getDirection());
        money(row, "confirmedAmount", value.getConfirmedAmount());
        put(row, "status", value.getApprovalStatus());
        return row;
    }

    private Map<String, Object> payment(SettlementPaymentItemVO value) {
        Map<String, Object> row = row();
        put(row, "applicationCode", value.getApplyCode());
        put(row, "type", value.getPayType());
        money(row, "applyAmount", value.getApplyAmount());
        money(row, "approvedAmount", value.getApprovedAmount());
        money(row, "actualPayAmount", value.getActualPayAmount());
        put(row, "status", value.getPayStatus());
        put(row, "payDate", value.getPayDate());
        put(row, "voucherNo", value.getVoucherNo());
        return row;
    }

    private Map<String, Object> cost(SettlementCostItemVO value) {
        Map<String, Object> row = row();
        put(row, "subjectName", value.getCostSubjectName());
        put(row, "type", value.getCostType());
        put(row, "sourceType", value.getSourceType());
        put(row, "sourceId", value.getSourceId());
        money(row, "amount", value.getAmount());
        money(row, "taxAmount", value.getTaxAmount());
        money(row, "amountWithoutTax", value.getAmountWithoutTax());
        put(row, "date", value.getCostDate());
        put(row, "status", value.getCostStatus());
        return row;
    }

    private Map<String, Object> attachment(SettlementAttachmentVO value) {
        Map<String, Object> row = row();
        put(row, "name", value.getOriginalName());
        put(row, "type", value.getFileType());
        put(row, "size", value.getFileSize());
        put(row, "uploadedBy", value.getUploadedBy());
        put(row, "uploadedAt", value.getUploadedAt());
        return row;
    }

    private Map<String, Object> approvalRecord(SettlementApprovalRecordVO value) {
        Map<String, Object> row = row();
        put(row, "node", value.getNodeName());
        put(row, "action", value.getActionName());
        put(row, "operator", value.getOperatorName());
        put(row, "comment", value.getComment());
        put(row, "time", value.getCreatedAt());
        return row;
    }

    private Map<String, Object> audit(StlSettlementVO value) {
        Map<String, Object> row = row();
        put(row, "finalizedAt", value.getFinalizedAt());
        put(row, "createdBy", value.getCreatedBy());
        put(row, "createdAt", value.getCreatedAt());
        put(row, "updatedAt", value.getUpdatedAt());
        return row;
    }

    private Map<String, Object> namedObject(String id, String name) {
        Map<String, Object> row = row();
        put(row, "id", id);
        put(row, "name", name);
        return row;
    }

    private <T> List<Map<String, Object>> rows(List<T> values, Function<T, String> stableKey,
                                                 Function<T, Map<String, Object>> mapper) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(stableKey, Comparator.nullsLast(String::compareTo)))
                .map(mapper)
                .toList();
    }

    private Long id(StlSettlementVO value) {
        try {
            return Long.valueOf(value.getId());
        } catch (NumberFormatException exception) {
            throw new BusinessException("DOCUMENT_SETTLEMENT_ID_INVALID", "结算单标识非法", exception);
        }
    }

    private Map<String, Object> row() {
        return new LinkedHashMap<>();
    }

    private void put(Map<String, Object> target, String key, Object value) {
        target.put(key, value == null ? "" : String.valueOf(value));
    }

    private void money(Map<String, Object> target, String key, String value) {
        target.put(key, normalizeDecimal(value, 2));
    }

    private void decimal(Map<String, Object> target, String key, String value) {
        target.put(key, normalizeDecimal(value, 2));
    }

    private String normalizeDecimal(String value, int scale) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO.setScale(scale).toPlainString();
        }
        try {
            return new BigDecimal(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException exception) {
            throw new BusinessException("DOCUMENT_SETTLEMENT_AMOUNT_INVALID", "结算金额字段格式非法", exception);
        }
    }
}
