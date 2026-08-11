package com.cgcpms.purchase.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.dto.PurchaseRequestCreateCommand;
import com.cgcpms.purchase.dto.PurchaseRequestApprovalCommand;
import com.cgcpms.purchase.service.MatPurchaseRequestService;
import com.cgcpms.purchase.service.PurchaseRequestApprovalService;
import com.cgcpms.purchase.vo.MatPurchaseRequestItemVO;
import com.cgcpms.purchase.vo.MatPurchaseRequestVO;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/purchase-requests")
@RequiredArgsConstructor
public class MatPurchaseRequestController {

    private final MatPurchaseRequestService requestService;
    private final Validator validator;
    private final PurchaseRequestApprovalService approvalService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('purchase:request:list','purchase:request:self')")
    public ApiResponse<PageResult<MatPurchaseRequestVO>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestCode) {
        PageResult<MatPurchaseRequestVO> page = requestService.getPage(pageNum, pageSize, projectId,
                approvalStatus, status, requestCode);
        return ApiResponse.success(page);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('purchase:request:list','purchase:request:self')")
    public ApiResponse<MatPurchaseRequestVO> getById(@PathVariable Long id) {
        return ApiResponse.success(requestService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('purchase:request:add','purchase:request:self')")
    public ApiResponse<String> create(@Valid @RequestBody MatPurchaseRequest request) {
        return ApiResponse.success(String.valueOf(requestService.create(request)));
    }

    @PostMapping("/with-items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('purchase:request:add','purchase:request:self')")
    public ApiResponse<String> createWithItems(@Valid @RequestBody PurchaseRequestCreateCommand command) {
        return ApiResponse.success(String.valueOf(requestService.create(command.header(), command.items())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('purchase:request:edit','purchase:request:self')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody MatPurchaseRequest request) {
        request.setId(id);
        requestService.update(request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('purchase:request:delete','purchase:request:self')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        requestService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('purchase:request:submit','purchase:request:self') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> submitForApproval(@PathVariable Long id) {
        requestService.submitForApproval(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/resubmit")
    @PreAuthorize("hasAnyAuthority('purchase:request:submit','purchase:request:self') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> resubmitForApproval(@PathVariable Long id, @RequestParam Long instanceId) {
        requestService.resubmitForApproval(id, instanceId);
        return ApiResponse.success();
    }

    @PostMapping("/{requestId}/approval-tasks/{taskId}/approve")
    @PreAuthorize("hasAuthority('workflow:approve') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> approve(@PathVariable Long requestId, @PathVariable Long taskId,
                                     @Valid @RequestBody PurchaseRequestApprovalCommand command) {
        approvalService.approve(requestId, taskId, command);
        return ApiResponse.success();
    }

    @GetMapping("/{requestId}/approval-tasks/{taskId}/items")
    @PreAuthorize("hasAuthority('workflow:approve') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<MatPurchaseRequestItemVO>> getApprovalItems(
            @PathVariable Long requestId, @PathVariable Long taskId) {
        return ApiResponse.success(approvalService.getItemsForApproval(requestId, taskId));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('purchase:request:list','purchase:request:self')")
    public ApiResponse<List<MatPurchaseRequestItemVO>> getItems(@PathVariable Long id) {
        return ApiResponse.success(requestService.getItems(id));
    }

    @PostMapping("/{id}/items/batch")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('purchase:request:edit','purchase:request:self')")
    public ApiResponse<Void> saveItemsBatch(@PathVariable Long id,
                                             @Valid @Size(max = 200, message = "批量明细不能超过200条")
                                             @RequestBody List<MatPurchaseRequestItem> items) {
        for (int i = 0; i < items.size(); i++) {
            var violations = validator.validate(items.get(i));
            if (!violations.isEmpty()) {
                return ApiResponse.fail("400", "第" + (i + 1) + "条记录校验失败: " +
                        violations.iterator().next().getMessage());
            }
        }
        requestService.saveItemsBatch(id, items);
        return ApiResponse.success();
    }

    @GetMapping("/form-options")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('purchase:request:add','purchase:request:self')")
    public ApiResponse<Map<String, Object>> formOptions(@RequestParam Long projectId) {
        return ApiResponse.success(requestService.formOptions(projectId));
    }
}
