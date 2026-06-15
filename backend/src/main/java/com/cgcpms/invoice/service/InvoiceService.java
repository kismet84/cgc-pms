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
        vo.setSellerTaxNo(invoice.getSellerTaxNo());
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
        result.setTaxAmount(extractAmountMulti(text,
                "税额[:：].*?[¥￥]\\s*([\\d,]+\\.?\\d*)",
                "税额[:：]\\s*([\\d,]+\\.?\\d+)",
                "税额[\\s\\S]{0,500}?([\\d,]+\\.?\\d{2})",
                // Table row: tax rate % followed by amount (税率 13% 税额 19500.00 pattern)
                "税率[\\s\\S]{0,200}?%[\\s\\S]{0,200}?([\\d,]+\\.?\\d{2})"));
        result.setInvoiceDate(extractInvoiceDate(text));
        result.setSellerName(extractSellerName(text));
        result.setBuyerName(extractBuyerName(text));
        result.setBuyerTaxNo(extractBuyerTaxNo(text));
        result.setSellerTaxNo(extractSellerTaxNo(text));
        result.setRemark(null);

        log.info("PDF recognition result: invoiceNo={}, amount={}, taxRate={}, taxAmount={}, seller={}, buyer={}, sellerTaxNo={}",
                result.getInvoiceNo(), result.getInvoiceAmount(),
                result.getTaxRate(), result.getTaxAmount(),
                result.getSellerName(), result.getBuyerName(),
                result.getSellerTaxNo());
        log.info("Extracted PDF text (first 500 chars):\n{}",
                text.length() > 500 ? text.substring(0, 500) + "..." : text);

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

    /**
     * Try multiple amount regex patterns in order, returning first match with formatting stripped.
     */
    private String extractAmountMulti(String text, String... regexes) {
        for (String regex : regexes) {
            String raw = extractFirstDotAll(text, regex);
            if (raw != null) {
                return raw.replaceAll("[¥￥,\\s]", "");
            }
        }
        return null;
    }

    private String extractTaxRate(String text) {
        // Pattern 1: Explicit label with colon "税率：13%" or "税率:13%" (existing)
        String raw = extractFirst(text, "税率[:：]\\s*([\\d]+\\.?\\d*)%?");
        if (raw != null) {
            if (raw.endsWith("%")) raw = raw.substring(0, raw.length() - 1);
            return raw.trim();
        }
        // Pattern 2: Table format - label and value across lines (no colon)
        raw = extractFirstDotAll(text, "税率[\\s\\S]{0,80}?([\\d]+\\.?\\d*)\\s*%");
        if (raw != null) return raw.trim();
        // Pattern 3: "税率" followed by bare digits in table row
        raw = extractFirstDotAll(text, "税率[\\s\\S]{0,80}?([\\d]{1,2})[%\\s]");
        if (raw != null) return raw.trim();
        return null;
    }

    private String extractInvoiceDate(String text) {
        // Multiple date formats: 2024-01-15, 2024年01月15日, 2024/01/15
        String[] patterns = {
            "开票日期[:：]\\s*(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})",
            "开票日期[:：]\\s*(\\d{4}\\s*年\\s*\\d{1,2}\\s*月\\s*\\d{1,2})\\s*日?",
        };
        String raw = extractFirstMulti(text, patterns);
        if (raw == null) return null;
        raw = raw.replaceAll("[年月]", "-").replace("日", "").replaceAll("\\s+", "");
        String separator = raw.contains("/") ? "/" : "-";
        String[] parts = raw.split(separator);
        if (parts.length == 3) {
            return parts[0] + "-"
                    + (parts[1].length() == 1 ? "0" + parts[1] : parts[1]) + "-"
                    + (parts[2].length() == 1 ? "0" + parts[2] : parts[2]);
        }
        return raw;
    }

    private String extractSellerName(String text) {
        // CJK name pattern: Chinese chars, parens, Latin letters, digits, middle dot
        final String NAME_RE = "[\\u4e00-\\u9fff（）()A-Za-z\\d·]{2,40}";
        // Primary: explicit seller label patterns (existing)
        String[] primaryPatterns = {
            "销售方名称[:：]\\s*(" + NAME_RE + ")",
            "销货单位[:：]\\s*(" + NAME_RE + ")",
            "销货方名称[:：]\\s*(" + NAME_RE + ")",
            "卖方名称[:：]\\s*(" + NAME_RE + ")",
            "销方名称[:：]\\s*(" + NAME_RE + ")",
        };
        String name = extractFirstMulti(text, primaryPatterns);
        if (name != null) return name;

        // Fallback 1: generic "名称：" near seller context (no 单位 requirement)
        name = extractFirstDotAll(text, "(?:销货|销售|销方|卖方)(?:\\s*(?:单位|方))?[\\s\\S]{0,200}?名称[:：]\\s*(" + NAME_RE + ")");
        if (name != null && !name.trim().isEmpty()) return name.trim();

        // Fallback 2: seller name is near the SECOND tax ID
        name = extractNameNearTaxId(text, 2);
        if (name != null) return name;

        // Fallback 3: second "名称：XXX" match (seller typically comes after buyer)
        Matcher m = Pattern.compile("名称[:：]\\s*(" + NAME_RE + ")").matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
            if (count >= 2) {
                String candidate = m.group(1).trim();
                if (!candidate.matches("[\\d.,\\-\\s]+") && candidate.length() >= 2
                        && !candidate.contains("购") && !candidate.matches("[\\dA-Z\\-]{10,}")) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private String extractBuyerName(String text) {
        // CJK name pattern: Chinese chars, parens, Latin letters, digits, middle dot
        final String NAME_RE = "[\\u4e00-\\u9fff（）()A-Za-z\\d·]{2,40}";
        // Primary: explicit buyer label patterns (existing)
        String[] primaryPatterns = {
            "购买方名称[:：]\\s*(" + NAME_RE + ")",
            "购货单位[:：]\\s*(" + NAME_RE + ")",
            "购货方名称[:：]\\s*(" + NAME_RE + ")",
            "买方名称[:：]\\s*(" + NAME_RE + ")",
            "购方名称[:：]\\s*(" + NAME_RE + ")",
        };
        String name = extractFirstMulti(text, primaryPatterns);
        if (name != null) return name;

        // Fallback 1: generic "名称：" near buyer context (no 单位 requirement)
        name = extractFirstDotAll(text, "(?:购货|购买|购方|买方)(?:\\s*(?:单位|方))?[\\s\\S]{0,200}?名称[:：]\\s*(" + NAME_RE + ")");
        if (name != null && !name.trim().isEmpty()) return name.trim();

        // Fallback 2: buyer name is near the FIRST tax ID
        name = extractNameNearTaxId(text, 1);
        if (name != null) return name;

        // Fallback 3: first "名称：XXX" match that looks like a name (buyer appears first)
        Matcher m = Pattern.compile("名称[:：]\\s*(" + NAME_RE + ")").matcher(text);
        while (m.find()) {
            String candidate = m.group(1).trim();
            if (!candidate.matches("[\\dA-Z.,\\-\\s]+") && candidate.length() >= 2
                    && !candidate.contains("销") && !candidate.contains("售")) {
                return candidate;
            }
        }
        return null;
    }

    private String extractBuyerTaxNo(String text) {
        // Multiple tax ID label variants
        String[] patterns = {
            "纳税人识别号[:：]\\s*([\\dA-Z]{15,20})",
            "统一社会信用代码[:：]\\s*([\\dA-Z]{18})",
            "纳税人识别号[:：]\\s*([^\\n]{10,30})",
        };
        return extractFirstMulti(text, patterns);
    }

    /**
     * Extract seller tax number — the SECOND 纳税人识别号 in the invoice
     * (first = buyer, second = seller).
     */
    private String extractSellerTaxNo(String text) {
        Matcher m = Pattern.compile("纳税人识别号[:：]\\s*([\\dA-Z]{15,20})").matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
            if (count >= 2) return m.group(1).trim();
        }
        return null;
    }

    /**
     * Try multiple regex patterns in order, returning the first match.
     */
    private String extractFirstMulti(String text, String[] regexes) {
        for (String regex : regexes) {
            Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
            if (m.find()) {
                String value = m.group(1).trim();
                if (!value.isEmpty()) return value;
            }
        }
        return null;
    }

    /**
     * Find entity name near the nth occurrence of 纳税人识别号 (tax ID).
     * In Chinese invoices: 1st tax ID = buyer, 2nd = seller.
     * Looks backwards up to 300 chars from the tax ID for the nearest "名称：XXX".
     */
    private String extractNameNearTaxId(String text, int occurrence) {
        final String NAME_RE = "[\\u4e00-\\u9fff（）()A-Za-z\\d·]{2,40}";
        Matcher m = Pattern.compile("纳税人识别号[:：]\\s*([\\dA-Z]{15,20})").matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
            if (count == occurrence) {
                // Look backwards from this tax ID for the nearest "名称：XXX"
                int start = Math.max(0, m.start() - 300);
                String before = text.substring(start, m.start());
                Matcher nameM = Pattern.compile("名称[:：]\\s*(" + NAME_RE + ")").matcher(before);
                String lastName = null;
                while (nameM.find()) {
                    lastName = nameM.group(1).trim();
                }
                if (lastName != null && lastName.length() >= 2) return lastName;
            }
        }
        return null;
    }
}
