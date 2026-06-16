package com.cgcpms.cost.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.entity.CostTargetItem;
import com.cgcpms.cost.service.CostTargetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/cost-targets")
@RequiredArgsConstructor
public class CostTargetController {

    private final CostTargetService costTargetService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:query')")
    public ApiResponse<PageResult<CostTarget>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String versionNo,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) Integer isActive) {
        IPage<CostTarget> page = costTargetService.getPage(pageNo, pageSize,
                projectId, versionNo, approvalStatus, isActive);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:query')")
    public ApiResponse<CostTarget> getById(@PathVariable Long id) {
        return ApiResponse.success(costTargetService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:add')")
    public ApiResponse<Long> create(@Valid @RequestBody CostTarget target) {
        return ApiResponse.success(costTargetService.create(target));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody CostTarget target) {
        target.setId(id);
        costTargetService.update(target);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        costTargetService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:activate')")
    public ApiResponse<Void> activate(@PathVariable Long id) {
        costTargetService.activate(id);
        return ApiResponse.success();
    }

    // ── Items ──

    @GetMapping("/{targetId}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:query')")
    public ApiResponse<List<CostTargetItem>> getItems(@PathVariable Long targetId) {
        log.info("GET /cost-targets/{}/items", targetId);
        return ApiResponse.success(costTargetService.getItems(targetId));
    }

    @PostMapping("/{targetId}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:edit')")
    public ApiResponse<Void> batchSaveItems(@PathVariable Long targetId,
                                            @RequestBody List<CostTargetItem> items) {
        log.info("POST /cost-targets/{}/items — batch save {} items", targetId, items != null ? items.size() : 0);
        costTargetService.batchSaveItems(targetId, items);
        return ApiResponse.success();
    }

    // ── Submit ──

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:submit')")
    public ApiResponse<Void> submitForApproval(@PathVariable Long id) {
        log.info("POST /cost-targets/{}/submit", id);
        costTargetService.submitForApproval(id);
        return ApiResponse.success();
    }
}
