package com.cgcpms.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.service.FileTypeValidator;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.entity.MdMaterialCategory;
import com.cgcpms.material.mapper.MdMaterialCategoryMapper;
import com.cgcpms.material.mapper.MdMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MdMaterialImportService {

    public static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String FILE_NAME = "材料字典导入模板.xlsx";
    private static final String DATA_SHEET = "材料导入";
    private static final List<String> HEADERS = List.of(
            "材料编码", "材料名称", "一级分类", "二级分类", "规格型号", "计量单位", "品牌",
            "含税信息价", "信息价月份", "信息价来源", "校核状态", "默认税率", "状态", "外部行标识", "备注");
    private static final Set<String> STATUSES = Set.of("ENABLE", "DISABLE");

    private final MdMaterialMapper materialMapper;
    private final MdMaterialCategoryMapper categoryMapper;
    private final PlatformTransactionManager transactionManager;
    private final FileTypeValidator fileTypeValidator = new FileTypeValidator();

    public byte[] template() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet data = workbook.createSheet(DATA_SHEET);
            Row header = data.createRow(0);
            for (int index = 0; index < HEADERS.size(); index++) {
                header.createCell(index).setCellValue(HEADERS.get(index));
                data.setColumnWidth(index, Math.min(40, Math.max(12, HEADERS.get(index).length() * 3)) * 256);
            }
            data.createFreezePane(0, 1);
            Sheet instructions = workbook.createSheet("填写说明");
            String[] lines = {
                    "仅填写“材料导入”工作表，不得修改表头。",
                    "必填：材料名称、含税信息价；含税信息价必须为正数且最多2位小数。",
                    "信息价月份格式为YYYY-MM；状态为空时默认ENABLE。",
                    "材料编码为空时系统确定性生成；已有编码只更新当前信息价及来源。",
                    "只有二级分类没有一级分类时该行失败；一级、二级分类可自动创建。",
                    "同键不同价独立建档并标记待复核；合法行不因其他行失败回滚。"
            };
            for (int index = 0; index < lines.length; index++) {
                instructions.createRow(index).createCell(0).setCellValue(lines[index]);
            }
            instructions.setColumnWidth(0, 100 * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new BusinessException("MATERIAL_IMPORT_TEMPLATE_FAILED", "材料导入模板生成失败", error);
        }
    }

    public ImportResult importFile(MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException error) {
            throw new BusinessException("MATERIAL_IMPORT_FILE_READ_FAILED", "材料导入文件读取失败", error);
        }
        FileTypeValidator.ValidationResult validation = fileTypeValidator.validate(
                file.getOriginalFilename(), file.getContentType(), content);
        if (!".xlsx".equals(validation.extension())) {
            throw new BusinessException("MATERIAL_IMPORT_XLSX_REQUIRED", "材料导入只支持.xlsx文件");
        }
        List<RawRow> rows = readRows(content);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int created = 0;
        int priceUpdated = 0;
        int conflictsCreated = 0;
        int skipped = 0;
        List<ImportError> errors = new ArrayList<>();
        Map<String, BigDecimal> seenPrices = new HashMap<>();
        for (RawRow raw : rows) {
            try {
                MaterialRow value = validate(raw, validation.sanitizedName());
                String key = value.codeSource();
                BigDecimal previousPrice = seenPrices.get(key);
                boolean priceConflict = previousPrice != null && previousPrice.compareTo(value.infoPrice()) != 0;
                RowOutcome outcome = transaction.execute(status -> importRow(value, priceConflict));
                if (outcome == null) throw new BusinessException("MATERIAL_IMPORT_ROW_FAILED", "材料导入行处理失败");
                created += outcome.created() ? 1 : 0;
                priceUpdated += outcome.updated() ? 1 : 0;
                conflictsCreated += outcome.conflict() ? 1 : 0;
                skipped += outcome.skipped() ? 1 : 0;
                seenPrices.putIfAbsent(key, value.infoPrice());
            } catch (BusinessException error) {
                errors.add(new ImportError(raw.rowNumber(), error.getCode(), error.getMessage()));
            } catch (RuntimeException error) {
                errors.add(new ImportError(raw.rowNumber(), "MATERIAL_IMPORT_ROW_FAILED", "该行写入失败"));
            }
        }
        return new ImportResult(rows.size(), created, priceUpdated, conflictsCreated, skipped,
                errors.size(), errors);
    }

    private List<RawRow> readRows(byte[] content) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheet(DATA_SHEET);
            if (sheet == null) throw new BusinessException("MATERIAL_IMPORT_SHEET_MISSING", "缺少“材料导入”工作表");
            validateHeaders(sheet.getRow(0));
            if (sheet.getLastRowNum() > 10_000) {
                throw new BusinessException("MATERIAL_IMPORT_ROW_LIMIT", "单次导入不得超过10000行");
            }
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<RawRow> rows = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                List<String> values = new ArrayList<>(HEADERS.size());
                boolean populated = false;
                for (int column = 0; column < HEADERS.size(); column++) {
                    Cell cell = row.getCell(column);
                    if (cell != null && cell.getCellType() == CellType.FORMULA) {
                        throw new BusinessException("MATERIAL_IMPORT_FORMULA_FORBIDDEN", "材料导入不接受公式单元格");
                    }
                    String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                    populated |= !value.isEmpty();
                    values.add(value);
                }
                if (populated) rows.add(new RawRow(rowIndex + 1, values));
            }
            return rows;
        } catch (BusinessException error) {
            throw error;
        } catch (IOException | RuntimeException error) {
            throw new BusinessException("MATERIAL_IMPORT_WORKBOOK_INVALID", "材料导入工作簿损坏或格式不受支持", error);
        }
    }

    private void validateHeaders(Row header) {
        if (header == null) throw new BusinessException("MATERIAL_IMPORT_HEADER_INVALID", "材料导入表头缺失");
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (int index = 0; index < HEADERS.size(); index++) {
            String actual = formatter.formatCellValue(header.getCell(index)).trim();
            if (!HEADERS.get(index).equals(actual)) {
                throw new BusinessException("MATERIAL_IMPORT_HEADER_INVALID",
                        "材料导入表头必须保持系统模板固定顺序");
            }
        }
        for (int index = HEADERS.size(); index < header.getLastCellNum(); index++) {
            if (StringUtils.hasText(formatter.formatCellValue(header.getCell(index)))) {
                throw new BusinessException("MATERIAL_IMPORT_HEADER_INVALID", "材料导入表头包含未支持列");
            }
        }
    }

    private MaterialRow validate(RawRow row, String sourceFile) {
        List<String> v = row.values();
        String code = optional(v.get(0), 64, "材料编码");
        String name = required(v.get(1), 200, "MATERIAL_IMPORT_NAME_REQUIRED", "材料名称不能为空");
        String category1 = optional(v.get(2), 128, "一级分类");
        String category2 = optional(v.get(3), 128, "二级分类");
        if (category1 == null && category2 != null) {
            throw new BusinessException("MATERIAL_IMPORT_CATEGORY_PATH_INVALID", "只有二级分类时必须补充一级分类");
        }
        String specification = optional(v.get(4), 200, "规格型号");
        String unit = optional(v.get(5), 20, "计量单位");
        String brand = optional(v.get(6), 100, "品牌");
        BigDecimal infoPrice = decimal(v.get(7), 19, true,
                "MATERIAL_IMPORT_INFO_PRICE_INVALID", "含税信息价必须为正数且最多2位小数");
        String period = optional(v.get(8), 7, "信息价月份");
        if (period != null && !period.matches("^[0-9]{4}-(0[1-9]|1[0-2])$")) {
            throw new BusinessException("MATERIAL_IMPORT_PERIOD_INVALID", "信息价月份必须为YYYY-MM");
        }
        String source = optional(v.get(9), 255, "信息价来源");
        String verification = optional(v.get(10), 32, "校核状态");
        BigDecimal taxRate = decimal(v.get(11), 6, false,
                "MATERIAL_IMPORT_TAX_RATE_INVALID", "默认税率必须为0到100且最多2位小数");
        if (taxRate != null && taxRate.compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessException("MATERIAL_IMPORT_TAX_RATE_INVALID", "默认税率必须为0到100且最多2位小数");
        }
        String status = optional(v.get(12), 16, "状态");
        status = status == null ? "ENABLE" : status.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw new BusinessException("MATERIAL_IMPORT_STATUS_INVALID", "状态只允许ENABLE或DISABLE");
        }
        String externalKey = optional(v.get(13), 128, "外部行标识");
        String remark = optional(v.get(14), 500, "备注");
        String fallbackSource = optional(sourceFile.replaceAll("[\\r\\n\\t]", "_"), 255, "信息价来源");
        return new MaterialRow(row.rowNumber(), code, name, category1, category2, specification, unit,
                brand, infoPrice, period, source == null ? fallbackSource : source, verification,
                taxRate, status, externalKey, remark);
    }

    private RowOutcome importRow(MaterialRow row, boolean priceConflict) {
        Long tenantId = UserContext.getCurrentTenantId();
        MdMaterial existing = row.code() == null ? null : findByCode(tenantId, row.code());
        if (existing != null) return updatePrice(existing, row, false);
        Long categoryId = resolveCategory(row, tenantId);
        boolean conflict = priceConflict;
        if (existing == null && row.code() == null && !priceConflict) {
            List<MdMaterial> candidates = materialMapper.selectList(new LambdaQueryWrapper<MdMaterial>()
                            .eq(MdMaterial::getTenantId, tenantId))
                    .stream().filter(material -> row.matchKey().equals(MatchKey.of(material))).toList();
            if (candidates.size() == 1) {
                existing = candidates.getFirst();
            } else if (candidates.size() > 1) {
                List<MdMaterial> exact = candidates.stream()
                        .filter(material -> Objects.equals(material.getCategoryId(), categoryId))
                        .filter(material -> row.externalKey() != null
                                && row.externalKey().equals(material.getInfoPriceExternalRowKey()))
                        .toList();
                if (exact.size() == 1) existing = exact.getFirst();
                else conflict = true;
            }
        }
        if (existing != null) return updatePrice(existing, row, conflict);

        String code = row.code();
        if (code == null) {
            String suffix = conflict ? row.externalKeyOrRow() : "";
            code = uniqueMaterialCode(tenantId, row.codeSource() + "|" + suffix,
                    row.matchKey(), conflict ? row.externalKeyOrRow() : null);
            MdMaterial generated = findByCode(tenantId, code);
            if (generated != null) return updatePrice(generated, row, conflict);
        }
        MdMaterial material = new MdMaterial();
        material.setId(IdWorker.getId());
        material.setTenantId(tenantId);
        material.setMaterialCode(code);
        material.setMaterialName(row.name().trim());
        material.setCategoryId(categoryId);
        material.setSpecification(row.specification());
        material.setUnit(row.unit());
        material.setBrand(row.brand());
        material.setDefaultTaxRate(row.taxRate());
        material.setTaxInclusiveInfoPrice(row.infoPrice());
        material.setInfoPricePeriod(row.period());
        material.setInfoPriceSource(row.source());
        material.setInfoPriceVerificationStatus(row.verification());
        material.setInfoPriceExternalRowKey(conflict ? row.externalKeyOrRow() : row.externalKey());
        material.setInfoPriceReviewRequired(conflict ? 1 : 0);
        material.setStatus(row.status());
        material.setRemark(row.remark());
        try {
            materialMapper.insert(material);
        } catch (DuplicateKeyException error) {
            throw new BusinessException("MATERIAL_IMPORT_CODE_CONFLICT", "材料编码已存在且不能安全消歧");
        }
        verifyReadback(material.getId(), tenantId, row.infoPrice());
        return new RowOutcome(true, false, conflict, false);
    }

    private RowOutcome updatePrice(MdMaterial existing, MaterialRow row, boolean conflict) {
        int reviewRequired = conflict || Integer.valueOf(1).equals(existing.getInfoPriceReviewRequired()) ? 1 : 0;
        String externalKey = conflict ? row.externalKeyOrRow() : row.externalKey();
        boolean same = existing.getTaxInclusiveInfoPrice() != null
                && existing.getTaxInclusiveInfoPrice().compareTo(row.infoPrice()) == 0
                && Objects.equals(existing.getInfoPricePeriod(), row.period())
                && Objects.equals(existing.getInfoPriceSource(), row.source())
                && Objects.equals(existing.getInfoPriceVerificationStatus(), row.verification())
                && Objects.equals(existing.getInfoPriceExternalRowKey(), externalKey)
                && Objects.equals(existing.getInfoPriceReviewRequired(), reviewRequired);
        if (same) return new RowOutcome(false, false, false, true);
        int updated = materialMapper.update(null, new LambdaUpdateWrapper<MdMaterial>()
                .eq(MdMaterial::getId, existing.getId())
                .eq(MdMaterial::getTenantId, existing.getTenantId())
                .set(MdMaterial::getTaxInclusiveInfoPrice, row.infoPrice())
                .set(MdMaterial::getInfoPricePeriod, row.period())
                .set(MdMaterial::getInfoPriceSource, row.source())
                .set(MdMaterial::getInfoPriceVerificationStatus, row.verification())
                .set(MdMaterial::getInfoPriceExternalRowKey, externalKey)
                .set(MdMaterial::getInfoPriceReviewRequired, reviewRequired));
        if (updated != 1) throw new BusinessException("MATERIAL_IMPORT_WRITE_CONFLICT", "材料价格更新冲突");
        verifyReadback(existing.getId(), existing.getTenantId(), row.infoPrice());
        return new RowOutcome(false, true, false, false);
    }

    private Long resolveCategory(MaterialRow row, Long tenantId) {
        if (row.category1() == null) {
            MdMaterialCategory fallback = categoryMapper.selectOne(new LambdaQueryWrapper<MdMaterialCategory>()
                    .eq(MdMaterialCategory::getTenantId, tenantId)
                    .eq(MdMaterialCategory::getCategoryCode, "UNCATEGORIZED")
                    .eq(MdMaterialCategory::getStatus, "ENABLE"));
            if (fallback == null) throw new BusinessException("MATERIAL_CATEGORY_DEFAULT_MISSING", "租户默认材料分类不存在");
            return fallback.getId();
        }
        String rootPath = normalize(row.category1());
        MdMaterialCategory root = category(row.category1(), null, 1, tenantId, rootPath);
        return row.category2() == null ? root.getId()
                : category(row.category2(), root.getId(), 2, tenantId,
                        rootPath + "/" + normalize(row.category2())).getId();
    }

    private MdMaterialCategory category(String name, Long parentId, int level, Long tenantId, String path) {
        List<MdMaterialCategory> matches = categoryMapper.selectList(new LambdaQueryWrapper<MdMaterialCategory>()
                        .eq(MdMaterialCategory::getTenantId, tenantId)
                        .isNull(parentId == null, MdMaterialCategory::getParentId)
                        .eq(parentId != null, MdMaterialCategory::getParentId, parentId))
                .stream().filter(value -> normalize(value.getCategoryName()).equals(normalize(name))).toList();
        if (matches.size() > 1) throw new BusinessException("MATERIAL_IMPORT_CATEGORY_AMBIGUOUS", "材料分类名称存在歧义");
        if (matches.size() == 1) return matches.getFirst();
        MdMaterialCategory created = new MdMaterialCategory();
        created.setId(IdWorker.getId());
        created.setTenantId(tenantId);
        created.setParentId(parentId);
        created.setCategoryCode("IMP-CAT-" + digest(path, 24));
        created.setCategoryName(name.trim());
        created.setLevelNo(level);
        created.setOrderNum(0);
        created.setStatus("ENABLE");
        try {
            categoryMapper.insert(created);
        } catch (DuplicateKeyException error) {
            MdMaterialCategory concurrent = categoryMapper.selectOne(new LambdaQueryWrapper<MdMaterialCategory>()
                    .eq(MdMaterialCategory::getTenantId, tenantId)
                    .eq(MdMaterialCategory::getCategoryCode, created.getCategoryCode()));
            if (concurrent == null) throw error;
            return concurrent;
        }
        return created;
    }

    private MdMaterial findByCode(Long tenantId, String code) {
        return materialMapper.selectOne(new LambdaQueryWrapper<MdMaterial>()
                .eq(MdMaterial::getTenantId, tenantId)
                .eq(MdMaterial::getMaterialCode, code));
    }

    private String uniqueMaterialCode(Long tenantId, String source, MatchKey expectedKey, String externalKey) {
        String base = "MAT-IMP-" + digest(source, 24);
        for (int suffix = 0; suffix < 100; suffix++) {
            String code = suffix == 0 ? base : base + "-" + suffix;
            MdMaterial existing = findByCode(tenantId, code);
            if (existing == null) return code;
            if (expectedKey.equals(MatchKey.of(existing))
                    && (externalKey == null || externalKey.equals(existing.getInfoPriceExternalRowKey()))) return code;
        }
        throw new BusinessException("MATERIAL_IMPORT_CODE_CONFLICT", "自动材料编码碰撞过多");
    }

    private void verifyReadback(Long id, Long tenantId, BigDecimal expectedPrice) {
        MdMaterial saved = materialMapper.selectById(id);
        if (saved == null || !tenantId.equals(saved.getTenantId()) || saved.getTaxInclusiveInfoPrice() == null
                || saved.getTaxInclusiveInfoPrice().compareTo(expectedPrice) != 0) {
            throw new BusinessException("MATERIAL_IMPORT_READBACK_FAILED", "材料价格写后回读不一致");
        }
    }

    private BigDecimal decimal(String value, int maxPrecision, boolean positive, String code, String message) {
        if (!StringUtils.hasText(value)) {
            if (positive) throw new BusinessException(code, message);
            return null;
        }
        try {
            BigDecimal parsed = new BigDecimal(value.trim());
            if (parsed.stripTrailingZeros().scale() > 2 || parsed.precision() > maxPrecision
                    || parsed.compareTo(BigDecimal.ZERO) < (positive ? 1 : 0)) {
                throw new BusinessException(code, message);
            }
            return parsed.setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException error) {
            throw new BusinessException(code, message);
        }
    }

    private String required(String value, int max, String code, String message) {
        String normalized = optional(value, max, message);
        if (normalized == null) throw new BusinessException(code, message);
        return normalized;
    }

    private String optional(String value, int max, String label) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new BusinessException("MATERIAL_IMPORT_FIELD_TOO_LONG", label + "长度不得超过" + max);
        }
        return trimmed;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim()
                .replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String digest(String value, int length) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte current : hash) hex.append(String.format("%02x", current));
            return hex.substring(0, length);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record RawRow(int rowNumber, List<String> values) {
    }

    private record MaterialRow(int rowNumber, String code, String name, String category1, String category2,
                               String specification, String unit, String brand, BigDecimal infoPrice,
                               String period, String source, String verification, BigDecimal taxRate,
                               String status, String externalKey, String remark) {
        MatchKey matchKey() {
            return new MatchKey(normalize(name) + "|" + normalize(specification) + "|" + normalize(unit));
        }

        String externalKeyOrRow() {
            return externalKey == null ? "ROW-" + rowNumber : externalKey;
        }

        String codeSource() {
            return normalize(category1) + "|" + normalize(category2) + "|" + matchKey().value();
        }
    }

    private record MatchKey(String value) {
        static MatchKey of(MdMaterial material) {
            return new MatchKey(normalize(material.getMaterialName()) + "|"
                    + normalize(material.getSpecification()) + "|" + normalize(material.getUnit()));
        }
    }

    private record RowOutcome(boolean created, boolean updated, boolean conflict, boolean skipped) {
    }

    public record ImportError(int row, String code, String message) {
    }

    public record ImportResult(int total, int created, int priceUpdated, int conflictsCreated,
                               int skipped, int failed, List<ImportError> errors) {
    }
}
