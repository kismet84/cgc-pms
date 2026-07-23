package com.cgcpms.cost.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.entity.CostTargetItem;
import com.cgcpms.cost.service.CostTargetService;
import com.cgcpms.cost.vo.CostTargetItemVO;
import com.cgcpms.cost.vo.CostTargetVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/cost-targets")
@RequiredArgsConstructor
public class CostTargetController {

    private final CostTargetService costTargetService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:query')")
    public ApiResponse<PageResult<CostTargetVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String versionNo,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) Integer isActive) {
        IPage<CostTarget> page = costTargetService.getPage(pageNo, pageSize,
                projectId, versionNo, approvalStatus, isActive);
        PageResult<CostTargetVO> result = new PageResult<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:query')")
    public ApiResponse<CostTargetVO> getById(@PathVariable Long id) {
        return ApiResponse.success(toVO(costTargetService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:add')")
    public ApiResponse<String> create(@Valid @RequestBody CostTarget target) {
        return ApiResponse.success(String.valueOf(costTargetService.create(target)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody CostTargetUpdateRequest request) {
        CostTarget target = request.toEntity();
        target.setId(id);
        costTargetService.update(target);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        costTargetService.delete(id, version);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:activate')")
    public ApiResponse<Void> activate(@PathVariable Long id, @RequestParam Integer version) {
        costTargetService.activate(id, version);
        return ApiResponse.success();
    }

    // ── Items ──

    @GetMapping("/{targetId}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:query')")
    public ApiResponse<List<CostTargetItemVO>> getItems(@PathVariable Long targetId) {
        log.info("GET /cost-targets/{}/items", targetId);
        return ApiResponse.success(costTargetService.getItems(targetId).stream()
                .map(this::toItemVO)
                .collect(Collectors.toList()));
    }

    @PostMapping("/{targetId}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:edit')")
    public ApiResponse<Void> batchSaveItems(@PathVariable Long targetId,
                                            @Valid @Size(max = 200, message = "批量明细不能超过200条")
                                            @RequestBody List<@Valid CostTargetItem> items,
                                            @RequestParam Integer version) {
        log.info("POST /cost-targets/{}/items — batch save {} items", targetId, items != null ? items.size() : 0);
        costTargetService.batchSaveItems(targetId, version, items);
        return ApiResponse.success();
    }

    // ── Submit ──

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:target:submit')")
    public ApiResponse<Void> submitForApproval(@PathVariable Long id, @RequestParam Integer version) {
        log.info("POST /cost-targets/{}/submit", id);
        costTargetService.submitForApproval(id, version);
        return ApiResponse.success();
    }

    private CostTargetVO toVO(CostTarget target) {
        CostTargetVO vo = new CostTargetVO();
        vo.setId(target.getId());
        vo.setProjectId(target.getProjectId());
        vo.setVersionNo(target.getVersionNo());
        vo.setVersionName(target.getVersionName());
        vo.setTotalTargetAmount(target.getTotalTargetAmount());
        vo.setTotalBidCostAmount(target.getTotalBidCostAmount());
        vo.setTotalResponsibilityAmount(target.getTotalResponsibilityAmount());
        vo.setIsActive(target.getIsActive());
        vo.setApprovalStatus(target.getApprovalStatus());
        vo.setEffectiveDate(target.getEffectiveDate());
        vo.setStatus(target.getStatus());
        vo.setApprovalInstanceId(target.getApprovalInstanceId());
        vo.setVersion(target.getVersion());
        vo.setRemark(target.getRemark());
        vo.setCreatedBy(target.getCreatedBy());
        vo.setCreatedTime(target.getCreatedTime());
        vo.setUpdatedTime(target.getUpdatedTime());
        return vo;
    }

    private CostTargetItemVO toItemVO(CostTargetItem item) {
        CostTargetItemVO vo = new CostTargetItemVO();
        vo.setId(item.getId());
        vo.setTargetId(item.getTargetId());
        vo.setProjectId(item.getProjectId());
        vo.setCostSubjectId(item.getCostSubjectId());
        vo.setTargetAmount(item.getTargetAmount());
        vo.setBidCostAmount(item.getBidCostAmount());
        vo.setResponsibilityAmount(item.getResponsibilityAmount());
        vo.setResponsibleUserId(item.getResponsibleUserId());
        vo.setResponsibilityUnit(item.getResponsibilityUnit());
        vo.setSortOrder(item.getSortOrder());
        vo.setRemark(item.getRemark());
        return vo;
    }

    /** Update request keeps client version writable while entity version stays response-only. */
    public record CostTargetUpdateRequest(
            @NotNull(message = "所属项目不能为空") Long projectId,
            @NotBlank(message = "版本号不能为空") String versionNo,
            @NotBlank(message = "版本名称不能为空") String versionName,
            @NotNull(message = "成本目标总额不能为空")
            @DecimalMin(value = "0.00", message = "成本目标总额不能为负数") BigDecimal totalTargetAmount,
            @DecimalMin(value = "0.00", message = "投标成本总额不能为负数") BigDecimal totalBidCostAmount,
            @DecimalMin(value = "0.00", message = "责任预算总额不能为负数") BigDecimal totalResponsibilityAmount,
            LocalDate effectiveDate,
            @NotNull(message = "缺少最新版本，请刷新后重试") Integer version,
            String remark) {

        CostTarget toEntity() {
            CostTarget target = new CostTarget();
            target.setProjectId(projectId);
            target.setVersionNo(versionNo);
            target.setVersionName(versionName);
            target.setTotalTargetAmount(totalTargetAmount);
            target.setTotalBidCostAmount(totalBidCostAmount);
            target.setTotalResponsibilityAmount(totalResponsibilityAmount);
            target.setEffectiveDate(effectiveDate);
            target.setVersion(version);
            target.setRemark(remark);
            return target;
        }
    }
}
