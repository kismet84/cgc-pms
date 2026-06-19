package com.cgcpms.revenue.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.revenue.entity.ContractRevenue;
import com.cgcpms.revenue.service.ContractRevenueService;
import com.cgcpms.revenue.vo.ContractRevenueBalanceVO;
import com.cgcpms.revenue.vo.ContractRevenueVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contract-revenue")
@RequiredArgsConstructor
public class ContractRevenueController {

    private final ContractRevenueService service;

    @GetMapping
    @PreAuthorize("hasAuthority('revenue:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PageResult<ContractRevenueVO>> getPage(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String approvalStatus) {
        IPage<ContractRevenueVO> page = service.getPage(pageNo, pageSize,
                projectId, contractId, startDate, endDate, approvalStatus);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('revenue:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<ContractRevenueVO> getById(@PathVariable Long id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping("/balance/{contractId}")
    @PreAuthorize("hasAuthority('revenue:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<ContractRevenueBalanceVO> getBalance(@PathVariable Long contractId) {
        return ApiResponse.success(service.getBalance(contractId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('revenue:add') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Long> create(@Valid @RequestBody ContractRevenue revenue) {
        return ApiResponse.success(service.create(revenue));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('revenue:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody ContractRevenue revenue) {
        revenue.setId(id);
        service.update(revenue);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('revenue:delete') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('revenue:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> submit(@PathVariable Long id) {
        service.submitForApproval(id);
        return ApiResponse.success();
    }
}
