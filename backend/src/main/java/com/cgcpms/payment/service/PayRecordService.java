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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayRecordService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    // ---- Writeback (mock) ----

    @Transactional
    public PayRecordVO writeback(PayRecord input) {
        Long payApplicationId = input.getPayApplicationId();
        if (payApplicationId == null)
            throw new BusinessException("MISSING_APP_ID", "付款申请ID不能为空");

        // Lookup the pay application to get contractId, partnerId, projectId
        PayApplication app = payApplicationMapper.selectById(payApplicationId);
        if (app == null || !app.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PAY_APP_NOT_FOUND", "付款申请单不存在");

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
        record.setPayStatus("SUCCESS");

        payRecordMapper.insert(record);
        log.info("Mock writeback: pay_record created, id={}, amount={}", record.getId(), record.getPayAmount());

        // D4 linkage: cascade updates
        updateContractPaidAmount(app.getContractId());
        payApplicationService.updatePayStatus(payApplicationId);
        costSummaryService.updatePaidAmount(app.getProjectId());

        return toVO(record);
    }

    // ---- D4: update contract paid_amount ----

    void updateContractPaidAmount(Long contractId) {
        if (contractId == null) return;

        // Sum all pay_record.pay_amount for this contract with status SUCCESS
        LambdaQueryWrapper<PayRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayRecord::getContractId, contractId)
                .eq(PayRecord::getPayStatus, "SUCCESS");
        List<PayRecord> records = payRecordMapper.selectList(wrapper);

        BigDecimal totalPaid = records.stream()
                .map(r -> r.getPayAmount() != null ? r.getPayAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CtContract contract = ctContractMapper.selectById(contractId);
        if (contract != null) {
            contract.setPaidAmount(totalPaid);
            ctContractMapper.updateById(contract);
            log.info("Contract paid_amount updated: contractId={}, paidAmount={}", contractId, totalPaid);
        }
    }

    // ---- CRUD (basic) ----

    @Transactional
    public Long create(PayRecord record) {
        record.setTenantId(UserContext.getCurrentTenantId());
        if (record.getPayStatus() == null || record.getPayStatus().isBlank()) {
            record.setPayStatus("SUCCESS");
        }
        payRecordMapper.insert(record);
        return record.getId();
    }

    @Transactional
    public void update(PayRecord record) {
        PayRecord existing = payRecordMapper.selectById(record.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PAY_RECORD_NOT_FOUND", "付款记录不存在");
        payRecordMapper.updateById(record);
    }

    @Transactional
    public void delete(Long id) {
        PayRecord existing = payRecordMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PAY_RECORD_NOT_FOUND", "付款记录不存在");
        payRecordMapper.deleteById(id);
    }

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
        vo.setCreatedAt(record.getCreatedAt() != null ? record.getCreatedAt().format(DTF) : null);
        vo.setUpdatedAt(record.getUpdatedAt() != null ? record.getUpdatedAt().format(DTF) : null);
        vo.setRemark(record.getRemark());
        return vo;
    }
}
