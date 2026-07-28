package com.cgcpms.file.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.annotation.RateLimit;
import com.cgcpms.common.annotation.RateLimitKey;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.file.service.FileService;
import com.cgcpms.file.vo.SysFileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = true)
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    @AuditedOperation(type = "UPLOAD", businessType = "FILE", businessIdExpression = "#businessId")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('file:upload')"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('CASH_JOURNAL')"
            + " and hasAuthority('cashbook:journal:maintain'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('SITE_DAILY_LOG')"
            + " and hasAuthority('site:daily:edit'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('SUBCONTRACT')"
            + " and hasAuthority('subcontract:measure:edit'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('SETTLEMENT')"
            + " and hasAuthority('settlement:edit'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('EXPENSE')"
            + " and hasAuthority('expense:edit'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('PAYMENT')"
            + " and hasAuthority('payment:app:edit'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('INVOICE')"
            + " and hasAuthority('invoice:edit'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('PURCHASE_REQUEST')"
            + " and hasAuthority('purchase:request:edit'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('PURCHASE_ORDER')"
            + " and hasAuthority('purchase:order:edit'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('MATERIAL_RECEIPT')"
            + " and hasAuthority('receipt:edit'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('PRODUCTION_MEASUREMENT') and ("
            + " ((#documentType.equalsIgnoreCase('MEASUREMENT_GENERAL') or #documentType.toUpperCase().startsWith('ML_')) and hasAuthority('measurement:submit'))"
            + " or (#documentType.equalsIgnoreCase('OWNER_SUBMISSION') and hasAuthority('measurement:owner:submit'))))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('OWNER_MEASUREMENT_SUBMISSION')"
            + " and #documentType.equalsIgnoreCase('OWNER_CONFIRMATION') and hasAuthority('measurement:owner:review'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('VARIATION') and ("
            + " ((#documentType.equalsIgnoreCase('SITE_EVIDENCE') or #documentType.equalsIgnoreCase('COST_ESTIMATE')) and hasAuthority('variation:order:edit'))"
            + " or (#documentType.equalsIgnoreCase('OWNER_SUBMISSION') and hasAuthority('variation:owner:submit'))"
            + " or (#documentType.equalsIgnoreCase('OWNER_CONFIRMATION') and hasAuthority('variation:owner:review'))))")
    @RateLimit(maxRequests = 20, windowSeconds = 60, key = RateLimitKey.USER)
    public ApiResponse<SysFileVO> upload(
            @RequestParam MultipartFile file,
            @RequestParam String businessType,
            @RequestParam Long businessId,
            @RequestParam(defaultValue = "OTHER") String documentType) {
        return ApiResponse.success(fileService.upload(file, businessType, businessId, documentType));
    }

    @GetMapping("/{id}/url")
    @AuditedOperation(type = "DOWNLOAD", businessType = "FILE", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('file:query')"
            + " or hasAuthority('cashbook:journal:query') or hasAuthority('site:daily:query')"
            + " or hasAuthority('measurement:query')"
            + " or hasAuthority('subcontract:measure:query')"
            + " or hasAuthority('settlement:query')"
            + " or hasAuthority('expense:query') or hasAuthority('payment:app:query')"
            + " or hasAuthority('invoice:query')"
            + " or hasAuthority('purchase:request:list') or hasAuthority('purchase:order:query')"
            + " or hasAuthority('receipt:query')"
            + " or hasAuthority('variation:order:query') or hasAuthority('variation:trace')")
    public ApiResponse<String> getUrl(@PathVariable Long id) {
        return ApiResponse.success(fileService.getPresignedUrl(id));
    }

    @DeleteMapping("/{id}")
    @AuditedOperation(type = "DELETE", businessType = "FILE", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('file:delete')"
            + " or hasAuthority('cashbook:journal:maintain') or hasAuthority('site:daily:edit')"
            + " or hasAuthority('subcontract:measure:edit')"
            + " or hasAuthority('settlement:edit')"
            + " or hasAuthority('expense:edit') or hasAuthority('payment:app:edit')"
            + " or hasAuthority('invoice:edit')"
            + " or hasAuthority('purchase:request:edit') or hasAuthority('purchase:order:edit')"
            + " or hasAuthority('receipt:edit')"
            + " or hasAuthority('variation:order:edit') or hasAuthority('variation:owner:submit')"
            + " or hasAuthority('variation:owner:review')"
            + " or hasAuthority('measurement:submit') or hasAuthority('measurement:owner:submit')"
            + " or hasAuthority('measurement:owner:review')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return ApiResponse.success();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('file:query')"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('CASH_JOURNAL')"
            + " and hasAuthority('cashbook:journal:query'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('SITE_DAILY_LOG')"
            + " and hasAuthority('site:daily:query'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('SUBCONTRACT')"
            + " and hasAuthority('subcontract:measure:query'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('SETTLEMENT')"
            + " and hasAuthority('settlement:query'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('EXPENSE')"
            + " and hasAuthority('expense:query'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('PAYMENT')"
            + " and hasAuthority('payment:app:query'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('INVOICE')"
            + " and hasAuthority('invoice:query'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('PURCHASE_REQUEST')"
            + " and hasAuthority('purchase:request:list'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('PURCHASE_ORDER')"
            + " and hasAuthority('purchase:order:query'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('MATERIAL_RECEIPT')"
            + " and hasAuthority('receipt:query'))"
            + " or (#businessType != null and (#businessType.equalsIgnoreCase('PRODUCTION_MEASUREMENT')"
            + " or #businessType.equalsIgnoreCase('OWNER_MEASUREMENT_SUBMISSION')) and hasAuthority('measurement:query'))"
            + " or (#businessType != null and #businessType.equalsIgnoreCase('VARIATION')"
            + " and (hasAuthority('variation:order:query') or hasAuthority('variation:trace')))")
    public ApiResponse<List<SysFileVO>> listByBusiness(
            @RequestParam String businessType,
            @RequestParam Long businessId) {
        // 业务对象读权限校验
        fileService.checkBizReadPermission(businessType, businessId);
        return ApiResponse.success(fileService.listByBusiness(businessType, businessId));
    }
}
