package com.cgcpms.variation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.entity.VarOrderItem;
import com.cgcpms.variation.service.VarOrderService;
import com.cgcpms.variation.vo.VarOrderItemVO;
import com.cgcpms.variation.vo.VarOrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/var-orders")
@RequiredArgsConstructor
public class VarOrderController {

    private final VarOrderService varOrderService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:order:query')")
    public ApiResponse<PageResult<VarOrderVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String varType,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String varCode) {
        IPage<VarOrderVO> page = varOrderService.getPage(pageNo, pageSize, projectId, contractId,
                partnerId, varType, direction, varCode);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:order:query')")
    public ApiResponse<VarOrderVO> getById(@PathVariable Long id) {
        return ApiResponse.success(varOrderService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:order:add')")
    public ApiResponse<Long> create(@Valid @RequestBody VarOrder order) {
        return ApiResponse.success(varOrderService.create(order));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:order:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody VarOrder order) {
        order.setId(id);
        varOrderService.update(order);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:order:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        varOrderService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('variation:order:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> submitForApproval(@PathVariable Long id) {
        varOrderService.submitForApproval(id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:order:query')")
    public ApiResponse<List<VarOrderItemVO>> listItems(@PathVariable Long id) {
        VarOrderVO vo = varOrderService.getById(id);
        return ApiResponse.success(vo.getItems());
    }

    @PostMapping("/{id}/items/batch")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:order:edit')")
    public ApiResponse<Void> batchSaveItems(@PathVariable Long id, @Valid @RequestBody List<VarOrderItem> items) {
        varOrderService.saveItems(id, items);
        return ApiResponse.success();
    }
}
