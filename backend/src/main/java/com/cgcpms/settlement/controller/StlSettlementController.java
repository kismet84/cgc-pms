package com.cgcpms.settlement.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.entity.StlSettlementItem;
import com.cgcpms.settlement.service.StlSettlementQueryService;
import com.cgcpms.settlement.service.StlSettlementWriteService;
import com.cgcpms.settlement.vo.SettlementApprovalRecordVO;
import com.cgcpms.settlement.vo.SettlementAttachmentVO;
import com.cgcpms.settlement.vo.SettlementCostItemVO;
import com.cgcpms.settlement.vo.SettlementSourcesVO;
import com.cgcpms.settlement.vo.StlSettlementItemVO;
import com.cgcpms.settlement.vo.StlSettlementVO;
import com.cgcpms.variation.vo.VarOrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/settlements")
@RequiredArgsConstructor
public class StlSettlementController {

    private final StlSettlementQueryService queryService;
    private final StlSettlementWriteService writeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<PageResult<StlSettlementVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String settlementCode,
            @RequestParam(required = false) String settlementType,
            @RequestParam(required = false) String keyword) {
        IPage<StlSettlementVO> page = queryService.getPage(pageNo, pageSize,
                projectId, contractId, partnerId, settlementCode, settlementType, keyword);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/kpi")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<Map<String, Object>> kpi(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String settlementCode,
            @RequestParam(required = false) String settlementType) {
        return ApiResponse.success(queryService.getKpi(projectId, contractId,
                partnerId, settlementCode, settlementType));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<StlSettlementVO> getById(@PathVariable Long id) {
        return ApiResponse.success(queryService.getById(id));
    }

    @PostMapping
    @AuditedOperation(type = "CREATE", businessType = "SETTLEMENT", businessIdExpression = "#settlement.id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:add')")
    public ApiResponse<String> create(@Valid @RequestBody StlSettlement settlement) {
        return ApiResponse.success(String.valueOf(writeService.create(settlement)));
    }

    @PutMapping("/{id}")
    @AuditedOperation(type = "UPDATE", businessType = "SETTLEMENT", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody StlSettlement settlement) {
        settlement.setId(id);
        writeService.update(settlement);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @AuditedOperation(type = "DELETE", businessType = "SETTLEMENT", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        writeService.delete(id);
        return ApiResponse.success();
    }

    // ---- Items ----

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<List<StlSettlementItemVO>> listItems(@PathVariable Long id) {
        StlSettlementVO vo = queryService.getById(id);
        return ApiResponse.success(vo.getItems());
    }

    @PostMapping("/{id}/items/batch")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:edit')")
    public ApiResponse<Void> batchSaveItems(@PathVariable Long id, @Valid @RequestBody List<StlSettlementItem> items) {
        writeService.saveItems(id, items);
        return ApiResponse.success();
    }

    // ---- Compute (read-only) ----

    @GetMapping("/compute/{contractId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<StlSettlementVO> computeSettlementAmount(@PathVariable Long contractId) {
        return ApiResponse.success(queryService.computeSettlementAmount(contractId));
    }

    @GetMapping("/{id}/sources")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<SettlementSourcesVO> getSources(@PathVariable Long id) {
        return ApiResponse.success(queryService.getSources(id));
    }

    // ---- Related data queries (read-only) ----

    @GetMapping("/{id}/variations")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<List<VarOrderVO>> getVariations(@PathVariable Long id) {
        return ApiResponse.success(queryService.getVariations(id));
    }

    // TODO: 返回 PayRecordVO 而非直接暴露 Entity（前端已定义 SettlementPaymentItemVO）
    @GetMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<List<PayRecord>> getPayments(@PathVariable Long id) {
        return ApiResponse.success(queryService.getPayments(id));
    }

    @GetMapping("/{id}/costs")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<List<SettlementCostItemVO>> getCosts(@PathVariable Long id) {
        return ApiResponse.success(queryService.getCosts(id));
    }

    @GetMapping("/{id}/attachments")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<List<SettlementAttachmentVO>> getAttachments(@PathVariable Long id) {
        return ApiResponse.success(queryService.getAttachments(id));
    }

    @GetMapping("/{id}/approval-records")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<List<SettlementApprovalRecordVO>> getApprovalRecords(@PathVariable Long id) {
        return ApiResponse.success(queryService.getApprovalRecords(id));
    }

    // ---- Workflow ----

    @PostMapping("/{id}/submit")
    @AuditedOperation(type = "SUBMIT", businessType = "SETTLEMENT", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('settlement:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> submitForApproval(@PathVariable Long id) {
        writeService.submitForApproval(id);
        return ApiResponse.success();
    }
}
