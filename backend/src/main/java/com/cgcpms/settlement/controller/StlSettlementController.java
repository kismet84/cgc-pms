package com.cgcpms.settlement.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.entity.StlSettlementItem;
import com.cgcpms.settlement.service.StlSettlementService;
import com.cgcpms.settlement.vo.SettlementApprovalRecordVO;
import com.cgcpms.settlement.vo.SettlementSourcesVO;
import com.cgcpms.settlement.vo.StlSettlementItemVO;
import com.cgcpms.settlement.vo.StlSettlementVO;
import com.cgcpms.variation.entity.VarOrder;
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

    private final StlSettlementService stlSettlementService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<PageResult<StlSettlementVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String settlementCode,
            @RequestParam(required = false) String settlementType) {
        IPage<StlSettlementVO> page = stlSettlementService.getPage(pageNo, pageSize,
                projectId, contractId, partnerId, settlementCode, settlementType);
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
        return ApiResponse.success(stlSettlementService.getKpi(projectId, contractId,
                partnerId, settlementCode, settlementType));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<StlSettlementVO> getById(@PathVariable Long id) {
        return ApiResponse.success(stlSettlementService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:add')")
    public ApiResponse<Long> create(@Valid @RequestBody StlSettlement settlement) {
        return ApiResponse.success(stlSettlementService.create(settlement));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody StlSettlement settlement) {
        settlement.setId(id);
        stlSettlementService.update(settlement);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        stlSettlementService.delete(id);
        return ApiResponse.success();
    }

    // ---- Items ----

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<List<StlSettlementItemVO>> listItems(@PathVariable Long id) {
        StlSettlementVO vo = stlSettlementService.getById(id);
        return ApiResponse.success(vo.getItems());
    }

    @PostMapping("/{id}/items/batch")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:edit')")
    public ApiResponse<Void> batchSaveItems(@PathVariable Long id, @Valid @RequestBody List<StlSettlementItem> items) {
        stlSettlementService.saveItems(id, items);
        return ApiResponse.success();
    }

    // ---- Compute (read-only) ----

    @GetMapping("/compute/{contractId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<StlSettlementVO> computeSettlementAmount(@PathVariable Long contractId) {
        return ApiResponse.success(stlSettlementService.computeSettlementAmount(contractId));
    }

    @GetMapping("/{id}/sources")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<SettlementSourcesVO> getSources(@PathVariable Long id) {
        return ApiResponse.success(stlSettlementService.getSources(id));
    }

    // ---- Related data queries (read-only) ----

    @GetMapping("/{id}/variations")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<List<VarOrder>> getVariations(@PathVariable Long id) {
        return ApiResponse.success(stlSettlementService.getVariations(id));
    }

    @GetMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<List<PayRecord>> getPayments(@PathVariable Long id) {
        return ApiResponse.success(stlSettlementService.getPayments(id));
    }

    @GetMapping("/{id}/costs")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<List<CostItem>> getCosts(@PathVariable Long id) {
        return ApiResponse.success(stlSettlementService.getCosts(id));
    }

    @GetMapping("/{id}/attachments")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<List<SysFile>> getAttachments(@PathVariable Long id) {
        return ApiResponse.success(stlSettlementService.getAttachments(id));
    }

    @GetMapping("/{id}/approval-records")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('settlement:query')")
    public ApiResponse<List<SettlementApprovalRecordVO>> getApprovalRecords(@PathVariable Long id) {
        return ApiResponse.success(stlSettlementService.getApprovalRecords(id));
    }

    // ---- Workflow ----

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('settlement:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> submitForApproval(@PathVariable Long id) {
        stlSettlementService.submitForApproval(id);
        return ApiResponse.success();
    }
}
