package com.cgcpms.subcontract.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubMeasureItem;
import com.cgcpms.subcontract.service.SubMeasureService;
import com.cgcpms.subcontract.vo.SubMeasureItemVO;
import com.cgcpms.subcontract.vo.SubMeasureVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sub-measures")
@RequiredArgsConstructor
public class SubMeasureController {

    private final SubMeasureService subMeasureService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('subcontract:measure:query')")
    public ApiResponse<PageResult<SubMeasureVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String measureCode) {
        IPage<SubMeasureVO> page = subMeasureService.getPage(pageNo, pageSize, projectId, contractId,
                partnerId, status, measureCode);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('subcontract:measure:query')")
    public ApiResponse<SubMeasureVO> getById(@PathVariable Long id) {
        return ApiResponse.success(subMeasureService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('subcontract:measure:add')")
    public ApiResponse<Long> create(@Valid @RequestBody SubMeasure measure) {
        return ApiResponse.success(subMeasureService.create(measure));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('subcontract:measure:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody SubMeasure measure) {
        measure.setId(id);
        subMeasureService.update(measure);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('subcontract:measure:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        subMeasureService.delete(id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('subcontract:measure:query')")
    public ApiResponse<List<SubMeasureItemVO>> listItems(@PathVariable Long id) {
        SubMeasureVO vo = subMeasureService.getById(id);
        return ApiResponse.success(vo.getItems());
    }

    @PostMapping("/{id}/items/batch")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('subcontract:measure:edit')")
    public ApiResponse<Void> batchSaveItems(@PathVariable Long id, @Valid @RequestBody List<SubMeasureItem> items) {
        subMeasureService.saveItems(id, items);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('subcontract:measure:submit')")
    public ApiResponse<Void> submit(@PathVariable Long id) {
        subMeasureService.submitForApproval(id);
        return ApiResponse.success();
    }
}
