package com.cgcpms.cost.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.service.CostSubjectService;
import com.cgcpms.cost.vo.CostSubjectTreeNodeVO;
import com.cgcpms.cost.vo.CostSubjectVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.math.BigDecimal;

@RestController
@RequestMapping("/cost-subjects")
@RequiredArgsConstructor
public class CostSubjectController {

    private final CostSubjectService costSubjectService;

    @GetMapping("/tree")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:query')")
    public ApiResponse<List<CostSubjectTreeNodeVO>> getTree(
            @RequestParam(required = false) String category) {
        return ApiResponse.success(costSubjectService.getTree(category));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('cost:query','cost:target:query','budget:query')")
    public ApiResponse<List<CostSubjectVO>> getList(
            @RequestParam(required = false) String category) {
        return ApiResponse.success(costSubjectService.getList(category));
    }

    @GetMapping("/bid-options")
    @PreAuthorize("hasAuthority('bid:cost:query') or hasRole('SUPER_ADMIN')")
    public ApiResponse<List<CostSubjectVO>> getBidOptions() {
        return ApiResponse.success(costSubjectService.getBidOptions());
    }

    @PutMapping("/target-ratios")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:edit')")
    public ApiResponse<List<CostSubjectVO>> updateTargetRatios(
            @Valid @RequestBody @Size(min = 10, max = 10) List<@Valid TargetRatioRequest> requests) {
        return ApiResponse.success(costSubjectService.updateTargetRatios(requests.stream()
                .map(request -> new CostSubjectService.TargetRatio(request.subjectCode(), request.ratio()))
                .toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:query')")
    public ApiResponse<CostSubjectVO> getById(@PathVariable Long id) {
        return ApiResponse.success(costSubjectService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:add')")
    public ApiResponse<Long> create(@Valid @RequestBody CostSubject subject) {
        return ApiResponse.success(costSubjectService.create(subject));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody CostSubject subject) {
        subject.setId(id);
        costSubjectService.update(subject);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:edit')")
    public ApiResponse<Void> toggleStatus(@PathVariable Long id) {
        costSubjectService.toggleStatus(id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        costSubjectService.delete(id);
        return ApiResponse.success();
    }

    public record TargetRatioRequest(
            @NotBlank String subjectCode,
            @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000") BigDecimal ratio) {
    }
}
