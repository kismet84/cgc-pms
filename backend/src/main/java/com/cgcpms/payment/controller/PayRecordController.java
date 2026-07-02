package com.cgcpms.payment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.service.PayRecordService;
import com.cgcpms.payment.vo.PayRecordVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pay-records")
@RequiredArgsConstructor
public class PayRecordController {

    private final PayRecordService payRecordService;

    @GetMapping
    @PreAuthorize("hasAuthority('payment:record:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PageResult<PayRecordVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long payApplicationId,
            @RequestParam(required = false) Long contractId) {
        IPage<PayRecordVO> page = payRecordService.getPage(pageNo, pageSize, payApplicationId, contractId);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('payment:record:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PayRecordVO> getById(@PathVariable Long id) {
        return ApiResponse.success(payRecordService.getById(id));
    }

    /** POST /pay-records now delegates to authoritative writeback */
    // 已移除重复的 POST /pay-records 端点，统一使用 /pay-records/writeback
    @PostMapping("/writeback")
    @AuditedOperation(type = "CREATE", businessType = "PAYMENT", businessIdExpression = "#input.payApplicationId")
    @PreAuthorize("hasAuthority('payment:record:writeback') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PayRecordVO> writeback(@Valid @RequestBody PayRecord input) {
        return ApiResponse.success(payRecordService.writeback(input));
    }
}
