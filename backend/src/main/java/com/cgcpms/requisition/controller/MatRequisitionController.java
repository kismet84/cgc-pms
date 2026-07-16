package com.cgcpms.requisition.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import com.cgcpms.requisition.service.MatRequisitionService;
import com.cgcpms.requisition.vo.MatRequisitionItemVO;
import com.cgcpms.requisition.vo.MatRequisitionVO;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/requisitions")
@RequiredArgsConstructor
public class MatRequisitionController {

    private final MatRequisitionService requisitionService;
    private final Validator validator;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('requisition:query')")
    public ApiResponse<PageResult<MatRequisitionVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String requisitionCode) {
        PageResult<MatRequisitionVO> page = requisitionService.getPage(pageNo, pageSize, projectId,
                contractId, warehouseId, approvalStatus, requisitionCode);
        return ApiResponse.success(page);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('requisition:query')")
    public ApiResponse<MatRequisitionVO> getById(@PathVariable Long id) {
        return ApiResponse.success(requisitionService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('requisition:add')")
    public ApiResponse<String> create(@Valid @RequestBody MatRequisition requisition) {
        return ApiResponse.success(String.valueOf(requisitionService.create(requisition)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('requisition:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody MatRequisition requisition) {
        requisition.setId(id);
        requisitionService.update(requisition);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('requisition:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        requisitionService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('requisition:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> submitForApproval(@PathVariable Long id) {
        requisitionService.submitForApproval(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/stock-out")
    @AuditedOperation(type = "STOCK_OUT", businessType = "REQUISITION", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('requisition:stock-out') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> executeStockOut(@PathVariable Long id) {
        requisitionService.executeStockOut(id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('requisition:query')")
    public ApiResponse<List<MatRequisitionItemVO>> getItems(@PathVariable Long id) {
        return ApiResponse.success(requisitionService.getItems(id));
    }

    @PostMapping("/{id}/items/batch")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('requisition:edit')")
    public ApiResponse<Void> saveItemsBatch(@PathVariable Long id,
                                             @Valid @Size(max = 200, message = "批量明细不能超过200条")
                                             @RequestBody List<MatRequisitionItem> items) {
        for (int i = 0; i < items.size(); i++) {
            var violations = validator.validate(items.get(i));
            if (!violations.isEmpty()) {
                return ApiResponse.fail("400", "第" + (i + 1) + "条记录校验失败: "
                        + violations.iterator().next().getMessage());
            }
        }
        requisitionService.saveItemsBatch(id, items);
        return ApiResponse.success();
    }
}
