package com.cgcpms.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.vo.PayRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import com.cgcpms.common.util.DateTimeUtils;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayRecordService {

    private final PayRecordMapper payRecordMapper;
    private final PayApplicationMapper payApplicationMapper;
    private final CtContractMapper ctContractMapper;
    private final PayApplicationService payApplicationService;
    private final CostSummaryService costSummaryService;

    // ---- Query ----

    public IPage<PayRecordVO> getPage(long pageNo, long pageSize, Long payApplicationId, Long contractId) {
        LambdaQueryWrapper<PayRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayRecord::getTenantId, UserContext.getCurrentTenantId());
        if (payApplicationId != null) wrapper.eq(PayRecord::getPayApplicationId, payApplicationId);
        if (contractId != null) wrapper.eq(PayRecord::getContractId, contractId);
        wrapper.orderByDesc(PayRecord::getCreatedAt);

        Page<PayRecord> page = payRecordMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    public PayRecordVO getById(Long id) {
        PayRecord record = payRecordMapper.selectById(id);
        if (record == null || !record.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PAY_RECORD_NOT_FOUND", "付款记录不存在");
        return toVO(record);
    }

    // ---- Authoritative Writeback (single entry point) ----

    /**
     * Authoritative payment writeback — the ONLY path to create a pay_record.
     * Idempotent by externalTxnNo: duplicate returns existing record without double-posting.
     */
    @Transactional(rollbackFor = Exception.class)
    public PayRecordVO writeback(PayRecord input) {
        Long payApplicationId = input.getPayApplicationId();
        if (payApplicationId == null)
            throw new BusinessException("MISSING_APP_ID", "付款申请ID不能为空");
        if (input.getExternalTxnNo() == null || input.getExternalTxnNo().isBlank())
            throw new BusinessException("EXTERNAL_TXN_NO_REQUIRED", "外部交易流水号不能为空");

        // Lookup and lock the pay application to prevent concurrent writeback TOCTOU
        PayApplication app = payApplicationMapper.selectByIdForUpdate(payApplicationId, UserContext.getCurrentTenantId());
        if (app == null || !app.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PAY_APP_NOT_FOUND", "付款申请单不存在");
        if (!"APPROVED".equals(app.getApprovalStatus()))
            throw new BusinessException("PAY_APP_NOT_APPROVED", "仅审批通过的付款申请可付款");

        List<PayRecord> existing = payRecordMapper.selectList(
            new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, UserContext.getCurrentTenantId())
                .eq(PayRecord::getExternalTxnNo, input.getExternalTxnNo()));
        if (!existing.isEmpty()) {
            log.info("Idempotent writeback hit: duplicate external transaction detected, returning existing record id={}",
                existing.get(0).getId());
            return toVO(existing.get(0));
        }

        // Check contract balance before payment — include pendingAmount to prevent concurrent overpay
        BigDecimal pendingAmount = input.getPayAmount() != null ? input.getPayAmount() : BigDecimal.ZERO;
        payApplicationService.checkContractBalance(app, pendingAmount);

        // Check overpayment: sum of existing SUCCESS pay_records for this application
        List<PayRecord> existingRecords = payRecordMapper.selectList(
            new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getPayApplicationId, payApplicationId)
                .eq(PayRecord::getPayStatus, "SUCCESS"));
        BigDecimal alreadyPaid = existingRecords.stream()
            .map(r -> r.getPayAmount() == null ? BigDecimal.ZERO : r.getPayAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = app.getApplyAmount().subtract(alreadyPaid);
        if (input.getPayAmount().compareTo(remaining) > 0) {
            throw new BusinessException("PAY_OVERPAYMENT",
                "付款金额(" + input.getPayAmount() + ")超过剩余可付金额(" + remaining + ")");
        }

        // Build the pay record
        PayRecord record = new PayRecord();
        record.setTenantId(UserContext.getCurrentTenantId());
        record.setPayApplicationId(payApplicationId);
        record.setContractId(app.getContractId());
        record.setPartnerId(app.getPartnerId());
        record.setProjectId(app.getProjectId());
        record.setPayAmount(input.getPayAmount() != null ? input.getPayAmount() : BigDecimal.ZERO);
        record.setPayDate(input.getPayDate());
        record.setPayMethod(input.getPayMethod());
        record.setVoucherNo(input.getVoucherNo());
        record.setExternalTxnNo(input.getExternalTxnNo());
        record.setPayStatus("SUCCESS");

        payRecordMapper.insert(record);
        log.info("Authoritative writeback: pay_record created, id={}, amount={}",
            record.getId(), record.getPayAmount());

        // D4 linkage: cascade updates
        updateContractPaidAmount(app.getContractId());
        payApplicationService.updatePayStatus(payApplicationId);
        costSummaryService.updatePaidAmount(app.getProjectId());

        return toVO(record);
    }

    // ---- D4: update contract paid_amount ----

    void updateContractPaidAmount(Long contractId) {
        if (contractId == null) return;

        // Use SELECT FOR UPDATE to lock the contract row, preventing concurrent
        // writebacks from reading stale paidAmount and losing updates (see A-P1-1).
        CtContract contract = ctContractMapper.selectByIdForUpdate(contractId, UserContext.getCurrentTenantId());
        if (contract == null) return;

        // Sum all pay_record.pay_amount for this contract with status SUCCESS
        LambdaQueryWrapper<PayRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayRecord::getContractId, contractId)
                .eq(PayRecord::getPayStatus, "SUCCESS");
        List<PayRecord> records = payRecordMapper.selectList(wrapper);

        BigDecimal totalPaid = records.stream()
                .map(r -> r.getPayAmount() != null ? r.getPayAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        contract.setPaidAmount(totalPaid);
        ctContractMapper.updateById(contract);
        log.info("Contract paid_amount updated: contractId={}, paidAmount={}", contractId, totalPaid);
    }

    // ---- CRUD removed — all writes MUST go through authoritative writeback() ----

    // ---- VO conversion ----

    private PayRecordVO toVO(PayRecord record) {
        PayRecordVO vo = new PayRecordVO();
        vo.setId(record.getId() != null ? record.getId().toString() : null);
        vo.setTenantId(record.getTenantId() != null ? record.getTenantId().toString() : null);
        vo.setPayApplicationId(record.getPayApplicationId() != null ? record.getPayApplicationId().toString() : null);
        vo.setContractId(record.getContractId() != null ? record.getContractId().toString() : null);
        vo.setPartnerId(record.getPartnerId() != null ? record.getPartnerId().toString() : null);
        vo.setPayAmount(record.getPayAmount() != null ? record.getPayAmount().toPlainString() : null);
        vo.setPayDate(record.getPayDate() != null ? record.getPayDate().toString() : null);
        vo.setPayMethod(record.getPayMethod());
        vo.setVoucherNo(record.getVoucherNo());
        vo.setPayStatus(record.getPayStatus());
        vo.setCreatedBy(record.getCreatedBy() != null ? record.getCreatedBy().toString() : null);
        vo.setCreatedAt(record.getCreatedAt() != null ? record.getCreatedAt().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedAt(record.getUpdatedAt() != null ? record.getUpdatedAt().format(DateTimeUtils.DTF) : null);
        vo.setRemark(record.getRemark());
        return vo;
    }
}
