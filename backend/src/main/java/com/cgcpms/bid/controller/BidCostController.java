package com.cgcpms.bid.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.service.BidCostService;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bid-cost")
@RequiredArgsConstructor
public class BidCostController {

    private final BidCostService service;

    @GetMapping
    @PreAuthorize("hasAuthority('bid:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PageResult<BidCost>> getPage(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String bidStatus,
            @RequestParam(required = false) String keyword) {
        IPage<BidCost> page = service.getPage(pageNo, pageSize, bidStatus, keyword);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('bid:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<BidCost> getById(@PathVariable Long id) {
        return ApiResponse.success(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('bid:add') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Long> create(@Valid @RequestBody BidCost bid) {
        return ApiResponse.success(service.create(bid));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('bid:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody BidCost bid) {
        bid.setId(id);
        service.update(bid);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('bid:delete') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/won")
    @PreAuthorize("hasAuthority('bid:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> markAsWon(@PathVariable Long id, @RequestParam Long projectId) {
        service.markAsWon(id, projectId);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/lost")
    @PreAuthorize("hasAuthority('bid:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> markAsLost(@PathVariable Long id) {
        service.markAsLost(id);
        return ApiResponse.success();
    }
}
