package com.cgcpms.bid.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.bid.dto.BidCostCreateRequest;
import com.cgcpms.bid.dto.BidCostUpdateRequest;
import com.cgcpms.bid.dto.BidOwnerOption;
import com.cgcpms.bid.dto.BidCostOption;
import com.cgcpms.bid.dto.BidStatusUpdateRequest;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.service.BidCostService;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/bid-cost")
@RequiredArgsConstructor
public class BidCostController {

    private final BidCostService service;

    @GetMapping
    @PreAuthorize("hasAuthority('bid:query') or hasRole('SUPER_ADMIN')")
    public ApiResponse<PageResult<BidCost>> getPage(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String bidStatus,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deadlineFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deadlineTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        IPage<BidCost> page = service.getPage(
                pageNo, pageSize, bidStatus, result, keyword, projectId, ownerId,
                deadlineFrom, deadlineTo, startDate, endDate);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/owners")
    @PreAuthorize("hasAuthority('bid:query') or hasRole('SUPER_ADMIN')")
    public ApiResponse<List<BidOwnerOption>> getOwnerOptions() {
        return ApiResponse.success(service.listOwnerOptions());
    }

    @GetMapping("/cost-options")
    @PreAuthorize("hasAuthority('bid:cost:query') or hasRole('SUPER_ADMIN')")
    public ApiResponse<List<BidCostOption>> getCostOptions() {
        return ApiResponse.success(service.listCostOptions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('bid:query') or hasRole('SUPER_ADMIN')")
    public ApiResponse<BidCost> getById(@PathVariable Long id) {
        return ApiResponse.success(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('bid:add') or hasRole('SUPER_ADMIN')")
    @AuditedOperation(type = "CREATE", businessType = "BID_COST")
    public ApiResponse<Long> create(@Valid @RequestBody BidCostCreateRequest request) {
        return ApiResponse.success(service.create(request.toEntity()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('bid:edit') or hasRole('SUPER_ADMIN')")
    @AuditedOperation(type = "UPDATE", businessType = "BID_COST", businessIdExpression = "#id")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody BidCostUpdateRequest request) {
        service.update(request.toEntity(id));
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('bid:delete') or hasRole('SUPER_ADMIN')")
    @AuditedOperation(type = "DELETE", businessType = "BID_COST", businessIdExpression = "#id")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('bid:status') or hasRole('SUPER_ADMIN')")
    @AuditedOperation(type = "STATUS_CHANGE", businessType = "BID_COST", businessIdExpression = "#id")
    public ApiResponse<Long> changeStatus(@PathVariable Long id, @Valid @RequestBody BidStatusUpdateRequest request) {
        return ApiResponse.success(service.changeStatus(
                id, request.expectedStatus(), request.targetStatus(), request.reason()));
    }

    @PutMapping("/{id}/won")
    @PreAuthorize("hasAuthority('bid:status') or hasRole('SUPER_ADMIN')")
    @AuditedOperation(type = "STATUS_CHANGE", businessType = "BID_COST", businessIdExpression = "#id")
    public ApiResponse<Void> markAsWon(@PathVariable Long id,
                                       @RequestParam(required = false) Long projectId) {
        service.markAsWon(id, projectId);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/lost")
    @PreAuthorize("hasAuthority('bid:status') or hasRole('SUPER_ADMIN')")
    @AuditedOperation(type = "STATUS_CHANGE", businessType = "BID_COST", businessIdExpression = "#id")
    public ApiResponse<Void> markAsLost(@PathVariable Long id) {
        service.markAsLost(id);
        return ApiResponse.success();
    }
}
