package com.cgcpms.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.invoice.vo.InvoiceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PayInvoiceMapper payInvoiceMapper;

    // ── Query ──

    public IPage<InvoiceVO> getPage(long pageNo, long pageSize, Long payRecordId, Long payApplicationId) {
        LambdaQueryWrapper<PayInvoice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayInvoice::getTenantId, UserContext.getCurrentTenantId());
        if (payRecordId != null) wrapper.eq(PayInvoice::getPayRecordId, payRecordId);
        if (payApplicationId != null) wrapper.eq(PayInvoice::getPayApplicationId, payApplicationId);
        wrapper.orderByDesc(PayInvoice::getCreatedTime);

        Page<PayInvoice> page = payInvoiceMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    public InvoiceVO getById(Long id) {
        PayInvoice invoice = payInvoiceMapper.selectById(id);
        if (invoice == null || !invoice.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("INVOICE_NOT_FOUND", "发票不存在");
        return toVO(invoice);
    }

    // ── CRUD ──

    @Transactional
    public Long create(PayInvoice invoice) {
        invoice.setTenantId(UserContext.getCurrentTenantId());
        if (invoice.getInvoiceType() == null || invoice.getInvoiceType().isBlank()) {
            invoice.setInvoiceType("VAT_SPECIAL");
        }
        if (invoice.getVerifyStatus() == null || invoice.getVerifyStatus().isBlank()) {
            invoice.setVerifyStatus("PENDING");
        }
        try {
            payInvoiceMapper.insert(invoice);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("INVOICE_NO_DUPLICATE",
                    "发票号码(" + invoice.getInvoiceNo() + ")已存在，同一租户下发票号码不可重复");
        }
        log.info("Invoice created: id={}, invoiceNo={}", invoice.getId(), invoice.getInvoiceNo());
        return invoice.getId();
    }

    @Transactional
    public void update(PayInvoice invoice) {
        PayInvoice existing = payInvoiceMapper.selectById(invoice.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("INVOICE_NOT_FOUND", "发票不存在");

        // If invoice_no is being changed, check for duplicate
        if (invoice.getInvoiceNo() != null && !invoice.getInvoiceNo().equals(existing.getInvoiceNo())) {
            Long count = payInvoiceMapper.selectCount(
                    new LambdaQueryWrapper<PayInvoice>()
                            .eq(PayInvoice::getTenantId, UserContext.getCurrentTenantId())
                            .eq(PayInvoice::getInvoiceNo, invoice.getInvoiceNo())
                            .ne(PayInvoice::getId, invoice.getId()));
            if (count > 0) {
                throw new BusinessException("INVOICE_NO_DUPLICATE",
                        "发票号码(" + invoice.getInvoiceNo() + ")已存在，同一租户下发票号码不可重复");
            }
        }

        try {
            payInvoiceMapper.updateById(invoice);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("INVOICE_NO_DUPLICATE",
                    "发票号码(" + invoice.getInvoiceNo() + ")已存在，同一租户下发票号码不可重复");
        }
    }

    @Transactional
    public void delete(Long id) {
        PayInvoice existing = payInvoiceMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("INVOICE_NOT_FOUND", "发票不存在");
        payInvoiceMapper.deleteById(id);
    }

    // ── Verify: status toggle ──

    @Transactional
    public void verify(Long id, String targetStatus) {
        if (!"VERIFIED".equals(targetStatus) && !"ABNORMAL".equals(targetStatus)) {
            throw new BusinessException("INVALID_VERIFY_STATUS",
                    "核验状态只能为 VERIFIED 或 ABNORMAL，当前值: " + targetStatus);
        }

        PayInvoice invoice = payInvoiceMapper.selectById(id);
        if (invoice == null || !invoice.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("INVOICE_NOT_FOUND", "发票不存在");

        if (!"PENDING".equals(invoice.getVerifyStatus())) {
            throw new BusinessException("VERIFY_STATUS_CONFLICT",
                    "当前核验状态为 " + invoice.getVerifyStatus() + "，仅 PENDING 状态的发票可核验");
        }

        invoice.setVerifyStatus(targetStatus);
        payInvoiceMapper.updateById(invoice);
        log.info("Invoice verified: id={}, status={}→{}", id, "PENDING", targetStatus);
    }

    // ── Register: link to PayRecord ──

    @Transactional
    public Long register(PayInvoice invoice) {
        // Register is same as create but ensures pay_record_id linkage
        if (invoice.getPayRecordId() == null) {
            throw new BusinessException("MISSING_PAY_RECORD_ID", "登记发票时必须关联付款记录");
        }
        return create(invoice);
    }

    // ── VO conversion ──

    private InvoiceVO toVO(PayInvoice invoice) {
        InvoiceVO vo = new InvoiceVO();
        vo.setId(invoice.getId() != null ? invoice.getId().toString() : null);
        vo.setTenantId(invoice.getTenantId() != null ? invoice.getTenantId().toString() : null);
        vo.setPayRecordId(invoice.getPayRecordId() != null ? invoice.getPayRecordId().toString() : null);
        vo.setPayApplicationId(invoice.getPayApplicationId() != null ? invoice.getPayApplicationId().toString() : null);
        vo.setInvoiceNo(invoice.getInvoiceNo());
        vo.setInvoiceType(invoice.getInvoiceType());
        vo.setInvoiceAmount(invoice.getInvoiceAmount() != null ? invoice.getInvoiceAmount().toPlainString() : null);
        vo.setTaxRate(invoice.getTaxRate() != null ? invoice.getTaxRate().toPlainString() : null);
        vo.setTaxAmount(invoice.getTaxAmount() != null ? invoice.getTaxAmount().toPlainString() : null);
        vo.setInvoiceDate(invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toString() : null);
        vo.setVerifyStatus(invoice.getVerifyStatus());
        vo.setCreatedBy(invoice.getCreatedBy() != null ? invoice.getCreatedBy().toString() : null);
        vo.setCreatedAt(invoice.getCreatedTime() != null ? invoice.getCreatedTime().format(DTF) : null);
        vo.setUpdatedAt(invoice.getUpdatedTime() != null ? invoice.getUpdatedTime().format(DTF) : null);
        vo.setRemark(invoice.getRemark());
        return vo;
    }
}
