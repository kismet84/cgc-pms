package com.cgcpms.site.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.site.entity.SiteDailyLog;
import com.cgcpms.site.service.SiteDailyLogService;
import com.cgcpms.site.vo.SiteDailyLogVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/site-daily-logs")
@RequiredArgsConstructor
public class SiteDailyLogController {
    private final SiteDailyLogService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('site:daily:query')")
    public ApiResponse<PageResult<SiteDailyLogVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String status) {
        IPage<SiteDailyLogVO> page = service.getPage(pageNo, pageSize, projectId, startDate, endDate, status);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('site:daily:query')")
    public ApiResponse<SiteDailyLogVO> detail(@PathVariable Long id) {
        return ApiResponse.success(service.getById(id));
    }

    @PostMapping
    @AuditedOperation(type = "CREATE", businessType = "SITE_DAILY_LOG", businessIdExpression = "#log.id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('site:daily:edit')")
    public ApiResponse<Long> create(@Valid @RequestBody SiteDailyLog log) {
        return ApiResponse.success(service.create(log));
    }

    @PutMapping("/{id}")
    @AuditedOperation(type = "UPDATE", businessType = "SITE_DAILY_LOG", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('site:daily:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody SiteDailyLog log) {
        log.setId(id);
        service.update(log);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    @AuditedOperation(type = "SUBMIT", businessType = "SITE_DAILY_LOG", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('site:daily:edit')")
    public ApiResponse<Void> submit(@PathVariable Long id) {
        service.submit(id);
        return ApiResponse.success();
    }
}
