package com.cgcpms.audit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.audit.entity.OperationAuditLog;
import com.cgcpms.audit.mapper.OperationAuditLogMapper;
import com.cgcpms.audit.vo.OperationAuditLogVO;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/audit-logs")
public class OperationAuditController {

    private final OperationAuditLogMapper mapper;

    public OperationAuditController(OperationAuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:query')")
    public ApiResponse<PageResult<OperationAuditLogVO>> list(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String businessId,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {

        Long tenantId = UserContext.getCurrentTenantId();
        LambdaQueryWrapper<OperationAuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OperationAuditLog::getTenantId, tenantId);
        if (userId != null) {
            wrapper.eq(OperationAuditLog::getUserId, userId);
        }
        if (businessType != null && !businessType.isBlank()) {
            wrapper.eq(OperationAuditLog::getBusinessType, businessType);
        }
        if (businessId != null && !businessId.isBlank()) {
            wrapper.eq(OperationAuditLog::getBusinessId, businessId);
        }
        if (startTime != null && !startTime.isBlank()) {
            wrapper.ge(OperationAuditLog::getCreatedAt, startTime);
        }
        if (endTime != null && !endTime.isBlank()) {
            wrapper.le(OperationAuditLog::getCreatedAt, endTime);
        }
        wrapper.orderByDesc(OperationAuditLog::getCreatedAt);

        Page<OperationAuditLog> page = new Page<>(pageNo, pageSize);
        IPage<OperationAuditLog> result = mapper.selectPage(page, wrapper);

        List<OperationAuditLogVO> vos = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        PageResult<OperationAuditLogVO> pageResult = new PageResult<>();
        pageResult.setRecords(vos);
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNo(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        return ApiResponse.success(pageResult);
    }

    private OperationAuditLogVO toVO(OperationAuditLog entity) {
        OperationAuditLogVO vo = new OperationAuditLogVO();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setUserId(entity.getUserId());
        vo.setOperationType(entity.getOperationType());
        vo.setBusinessType(entity.getBusinessType());
        vo.setBusinessId(entity.getBusinessId());
        vo.setHttpMethod(entity.getHttpMethod());
        vo.setRequestPath(entity.getRequestPath());
        vo.setSuccessFlag(entity.getSuccessFlag());
        vo.setErrorCode(entity.getErrorCode());
        vo.setSourceIp(entity.getSourceIp());
        vo.setDurationMs(entity.getDurationMs());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
