package com.cgcpms.invoice.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.invoice.vo.InvoiceRecognizeResultVO;
import com.cgcpms.invoice.vo.InvoiceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

import com.cgcpms.common.util.DateTimeUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

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
        vo.setCreatedAt(invoice.getCreatedTime() != null ? invoice.getCreatedTime().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedAt(invoice.getUpdatedTime() != null ? invoice.getUpdatedTime().format(DateTimeUtils.DTF) : null);
        vo.setRemark(invoice.getRemark());
        vo.setSellerName(invoice.getSellerName());
        vo.setBuyerName(invoice.getBuyerName());
        vo.setBuyerTaxNo(invoice.getBuyerTaxNo());
        return vo;
    }

    // ── PDF Recognition ──

    /**
     * Recognize invoice fields from uploaded PDF using PDFBox text extraction.
     * Best-effort: returns null for fields that cannot be extracted.
     */
    public InvoiceRecognizeResultVO recognize(MultipartFile file) {
        // Validation
        if (file.isEmpty()) {
            throw new BusinessException("FILE_EMPTY", "上传文件不能为空");
        }
        if (!"application/pdf".equals(file.getContentType())) {
            throw new BusinessException("FILE_TYPE_NOT_ALLOWED", "仅支持PDF格式");
        }
        if (file.getSize() > 50 * 1024 * 1024) {
            throw new BusinessException("FILE_TOO_LARGE", "文件大小不能超过50MB");
        }

        // PDF text extraction
        PDDocument document = null;
        String text;
        try {
            document = Loader.loadPDF(file.getBytes());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(document);
        } catch (InvalidPasswordException e) {
            throw new BusinessException("PDF_ENCRYPTED", "PDF文件已加密，无法识别");
        } catch (Exception e) {
            throw new BusinessException("PDF_RECOGNIZE_FAILED", "PDF识别失败: " + e.getMessage());
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (Exception ignored) {
                    // ignore close errors
                }
            }
        }

        // Regex extraction
        InvoiceRecognizeResultVO result = new InvoiceRecognizeResultVO();
        result.setInvoiceNo(extractInvoiceNo(text));
        result.setInvoiceType(extractInvoiceType(text));
        result.setInvoiceAmount(extractAmount(text, "价税合计.*?[¥￥]\\s*([\\d,]+\\.?\\d*)"));
        result.setTaxRate(extractTaxRate(text));
        result.setTaxAmount(extractAmount(text, "税额[:：].*?[¥￥]\\s*([\\d,]+\\.?\\d*)"));
        result.setInvoiceDate(extractInvoiceDate(text));
        result.setSellerName(extractSellerName(text));
        result.setBuyerName(extractFirst(text, "购买方名称[:：]\\s*(.+)"));
        result.setBuyerTaxNo(extractFirst(text, "纳税人识别号[:：]\\s*([A-Z0-9]+)"));
        result.setRemark(null);

        log.info("PDF recognition result: invoiceNo={}, amount={}", result.getInvoiceNo(), result.getInvoiceAmount());

        return result;
    }

    // ── Regex helpers ──

    private String extractFirst(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractFirstDotAll(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractInvoiceNo(String text) {
        String no = extractFirst(text, "发票号码[:：]\\s*([\\d]+)");
        if (no != null) return no;
        return extractFirst(text, "发票代码[:：]\\s*([\\d]+)");
    }

    private String extractInvoiceType(String text) {
        if (text.contains("增值税专用发票")) return "VAT_SPECIAL";
        if (text.contains("增值税普通发票")) return "VAT_NORMAL";
        return null;
    }

    private String extractAmount(String text, String regex) {
        String raw = extractFirstDotAll(text, regex);
        if (raw == null) return null;
        return raw.replaceAll("[¥￥,\\s]", "");
    }

    private String extractTaxRate(String text) {
        String raw = extractFirst(text, "税率[:：]\\s*([\\d]+\\.?\\d*)%?");
        if (raw == null) return null;
        if (raw.endsWith("%")) raw = raw.substring(0, raw.length() - 1);
        return raw.trim();
    }

    private String extractInvoiceDate(String text) {
        String raw = extractFirst(text, "开票日期[:：]\\s*(\\d{4}[-年]\\d{1,2}[-月]\\d{1,2})");
        if (raw == null) return null;
        raw = raw.replace("年", "-").replace("月", "-").replace("日", "");
        String[] parts = raw.split("-");
        if (parts.length == 3) {
            return parts[0] + "-"
                    + (parts[1].length() == 1 ? "0" + parts[1] : parts[1]) + "-"
                    + (parts[2].length() == 1 ? "0" + parts[2] : parts[2]);
        }
        return raw;
    }

    private String extractSellerName(String text) {
        String name = extractFirst(text, "销售方名称[:：]\\s*(.+)");
        if (name != null) return name;
        Matcher m = Pattern.compile("名称[:：]\\s*(.+公司)").matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
            if (count == 2) return m.group(1).trim();
        }
        return null;
    }
}
