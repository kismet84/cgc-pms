package com.cgcpms.projectfile;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.service.FileObjectTaskService;
import com.cgcpms.file.service.FileService;
import com.cgcpms.file.vo.SysFileVO;
import com.cgcpms.project.auth.ProjectAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = true)
public class ProjectFileService {
    private static final DateTimeFormatter DATE_CODE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_BATCH = 500;
    private static final Map<String, String> BUSINESS_CATEGORIES = Map.ofEntries(
            Map.entry("BID_COST", "BID"),
            Map.entry("CONTRACT", "CONTRACT"), Map.entry("SUBCONTRACT", "CONTRACT"),
            Map.entry("TECH_DRAWING_VERSION", "DRAWING"), Map.entry("TECH_DRAWING_REVIEW", "DRAWING"),
            Map.entry("TECH_SCHEME", "TECHNICAL"), Map.entry("TECH_RFI", "TECHNICAL"),
            Map.entry("TECH_RFI_RESPONSE", "TECHNICAL"), Map.entry("TECH_DISCLOSURE", "TECHNICAL"),
            Map.entry("TECH_ARCHIVE", "TECHNICAL"),
            Map.entry("PROJECT_COMMENCEMENT", "CONSTRUCTION"), Map.entry("SITE_DAILY_LOG", "CONSTRUCTION"),
            Map.entry("PRODUCTION_MEASUREMENT", "CONSTRUCTION"),
            Map.entry("QS_INSPECTION", "QUALITY_SAFETY"), Map.entry("QS_ISSUE", "QUALITY_SAFETY"),
            Map.entry("QS_RECTIFICATION", "QUALITY_SAFETY"),
            Map.entry("RECEIPT", "PROCUREMENT"), Map.entry("MATERIAL_RECEIPT", "PROCUREMENT"),
            Map.entry("PURCHASE_REQUEST", "PROCUREMENT"), Map.entry("PURCHASE_ORDER", "PROCUREMENT"),
            Map.entry("SUPPLIER_SOURCING", "PROCUREMENT"), Map.entry("SUPPLIER_QUOTE", "PROCUREMENT"),
            Map.entry("PAYMENT", "FINANCE"), Map.entry("EXPENSE", "FINANCE"),
            Map.entry("CASH_JOURNAL", "FINANCE"), Map.entry("INVOICE", "FINANCE"),
            Map.entry("SALES_INVOICE", "FINANCE"), Map.entry("COLLECTION_RECORD", "FINANCE"),
            Map.entry("CONTRACT_REVENUE", "FINANCE"), Map.entry("OWNER_SETTLEMENT", "FINANCE"),
            Map.entry("SETTLEMENT", "FINANCE"), Map.entry("OWNER_MEASUREMENT_SUBMISSION", "FINANCE"),
            Map.entry("VARIATION", "APPROVAL"));
    private static final Map<String, String> DOCUMENT_CATEGORIES = Map.ofEntries(
            Map.entry("TENDER_DOCUMENT", "BID"), Map.entry("BILL_OF_QUANTITIES", "BID"),
            Map.entry("TENDER_DRAWING", "DRAWING"), Map.entry("BID_DRAWING", "DRAWING"),
            Map.entry("BID_PRICE", "BID"), Map.entry("TECHNICAL_DOCUMENT", "TECHNICAL"),
            Map.entry("CONTRACT_ATTACHMENT", "CONTRACT"), Map.entry("ELECTRONIC_INVOICE", "FINANCE"),
            Map.entry("SCANNED_INVOICE", "FINANCE"), Map.entry("BANK_RECEIPT", "FINANCE"),
            Map.entry("PAYMENT_PROOF", "FINANCE"));
    private static final Map<String, String> DIRECT_PROJECT_TABLES = Map.ofEntries(
            Map.entry("PROJECT_COMMENCEMENT", "project_commencement"), Map.entry("CONTRACT", "ct_contract"),
            Map.entry("RECEIPT", "mat_receipt"), Map.entry("MATERIAL_RECEIPT", "mat_receipt"),
            Map.entry("PURCHASE_REQUEST", "mat_purchase_request"), Map.entry("PURCHASE_ORDER", "mat_purchase_order"),
            Map.entry("PAYMENT", "pay_application"), Map.entry("EXPENSE", "expense_application"),
            Map.entry("SUBCONTRACT", "sub_measure"), Map.entry("SETTLEMENT", "stl_settlement"),
            Map.entry("VARIATION", "var_order"), Map.entry("BID_COST", "bid_cost"),
            Map.entry("CASH_JOURNAL", "cash_journal_entry"), Map.entry("SITE_DAILY_LOG", "site_daily_log"),
            Map.entry("CONTRACT_REVENUE", "contract_revenue"), Map.entry("OWNER_SETTLEMENT", "owner_settlement"),
            Map.entry("SALES_INVOICE", "sales_invoice"), Map.entry("COLLECTION_RECORD", "collection_record"),
            Map.entry("PRODUCTION_MEASUREMENT", "production_measurement"),
            Map.entry("OWNER_MEASUREMENT_SUBMISSION", "owner_measurement_submission"),
            Map.entry("QS_INSPECTION", "qs_inspection_record"), Map.entry("QS_RECTIFICATION", "qs_rectification"),
            Map.entry("SUPPLIER_SOURCING", "sp_sourcing_event"), Map.entry("TECH_SCHEME", "technical_scheme"),
            Map.entry("TECH_DRAWING_VERSION", "tech_drawing_version"),
            Map.entry("TECH_DRAWING_REVIEW", "tech_drawing_review"), Map.entry("TECH_RFI", "tech_rfi"),
            Map.entry("TECH_DISCLOSURE", "tech_disclosure"), Map.entry("TECH_ARCHIVE", "tech_acceptance_archive"),
            Map.entry("CLOSEOUT_SECTION_ACCEPTANCE", "closeout_section_acceptance"),
            Map.entry("CLOSEOUT_FINAL_ACCEPTANCE", "closeout_final_acceptance"),
            Map.entry("CLOSEOUT_DEFECT", "closeout_defect"), Map.entry("CLOSEOUT_WARRANTY", "closeout_warranty"),
            Map.entry("CLOSEOUT_ARCHIVE_TRANSFER", "closeout_archive_transfer"));

    private final JdbcTemplate jdbcTemplate;
    private final ProjectAccessChecker projectAccessChecker;
    private final BusinessObjectAuthorizer businessObjectAuthorizer;
    private final FileService fileService;
    private final FileObjectTaskService objectTaskService;
    private final OfficePreviewClient previewClient;

    /** Same-transaction projection for ordinary business attachments. */
    @Transactional(rollbackFor = Exception.class)
    public void indexBusinessFile(SysFile file) {
        String businessType = file.getBusinessType() == null ? "" : file.getBusinessType().trim().toUpperCase(Locale.ROOT);
        if (Set.of("PROJECT_FILE", "COMMUNICATION_MESSAGE", "PARTNER", "MATERIAL", "BID_COST")
                .contains(businessType) || alreadyLinked(file.getTenantId(), file.getId())) return;
        HistoricalResolution resolution = resolveHistoricalProject(businessType, file.getBusinessId(), file.getTenantId());
        if (resolution.projectId() == null) return;
        ProjectLock project = lockProject(resolution.projectId());
        long catalogId = IdWorker.getId();
        LocalDateTime createdAt = file.getCreatedAt();
        String fileCode = nextFileCode(project, createdAt == null ? LocalDate.now() : createdAt.toLocalDate());
        String category = categoryFor(businessType, file.getDocumentType());
        jdbcTemplate.update("""
                INSERT INTO project_file_catalog(
                    id,tenant_id,project_id,file_code,display_name,category_code,source_kind,
                    source_business_type,source_business_id,maintain_mode,created_by,updated_by,
                    created_at,updated_at,deleted_flag)
                VALUES(?,?,?,?,?,?, 'BUSINESS',?,?,'READ_ONLY',?,?,
                       COALESCE(?,CURRENT_TIMESTAMP),COALESCE(?,CURRENT_TIMESTAMP),0)
                """, catalogId, file.getTenantId(), resolution.projectId(), fileCode,
                Objects.requireNonNullElse(file.getOriginalName(), "业务附件"), category,
                businessType, file.getBusinessId(), file.getCreatedBy(), file.getCreatedBy(), createdAt, createdAt);
        insertVersion(catalogId, 1, file.getId(), "SYS_FILE", file.getId(),
                previewStatus(file.getOriginalName(), file.getContentType()), file.getCreatedBy());
        queueOfficePreviewIfNeeded(latestVersionId(catalogId), file.getOriginalName(), file.getContentType());
    }

    /** Preserve authoritative bid logical-name/version chain in the file-center projection. */
    @Transactional(rollbackFor = Exception.class)
    public void indexBidDocumentVersion(SysFile file, long bidCostId, long projectId, String logicalName,
                                        int versionNo, long sourceVersionId) {
        List<Long> catalogs = jdbcTemplate.queryForList("""
                SELECT id FROM project_file_catalog
                WHERE tenant_id=? AND source_kind='BUSINESS' AND source_business_type='BID_COST'
                  AND source_business_id=? AND display_name=? AND deleted_flag=0
                ORDER BY id LIMIT 1
                """, Long.class, file.getTenantId(), bidCostId, logicalName);
        long catalogId;
        if (catalogs.isEmpty()) {
            ProjectLock project = lockProject(projectId);
            catalogId = IdWorker.getId();
            String fileCode = nextFileCode(project, LocalDate.now());
            jdbcTemplate.update("""
                    INSERT INTO project_file_catalog(
                        id,tenant_id,project_id,file_code,display_name,category_code,source_kind,
                        source_business_type,source_business_id,maintain_mode,created_by,updated_by,
                        created_at,updated_at,deleted_flag)
                    VALUES(?,?,?,?,?,'BID','BUSINESS','BID_COST',?,'READ_ONLY',?,?,
                           CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                    """, catalogId, file.getTenantId(), projectId, fileCode, logicalName, bidCostId,
                    file.getCreatedBy(), file.getCreatedBy());
        } else {
            catalogId = catalogs.getFirst();
        }
        insertVersion(catalogId, versionNo, file.getId(), "BID_DOCUMENT_VERSION", sourceVersionId,
                previewStatus(file.getOriginalName(), file.getContentType()), file.getCreatedBy());
        queueOfficePreviewIfNeeded(latestVersionId(catalogId), file.getOriginalName(), file.getContentType());
    }

    /** Remove only the read-only projection; original business module remains deletion authority. */
    @Transactional(rollbackFor = Exception.class)
    public void invalidateBusinessFile(SysFile file) {
        List<Map<String, Object>> links = jdbcTemplate.queryForList("""
                SELECT v.id,v.catalog_id,v.preview_storage_path,c.source_kind
                FROM project_file_version_link v
                JOIN project_file_catalog c ON c.tenant_id=v.tenant_id AND c.id=v.catalog_id
                WHERE v.tenant_id=? AND v.sys_file_id=? AND v.deleted_flag=0 AND c.deleted_flag=0
                """, file.getTenantId(), file.getId());
        for (Map<String, Object> link : links) {
            if ("MANAGED".equals(link.get("source_kind"))) {
                throw new BusinessException("FILE_IMMUTABLE", "文件中心资料库版本不可删除");
            }
            long catalogId = number(link.get("catalog_id"));
            jdbcTemplate.update("""
                    UPDATE project_file_version_link SET deleted_flag=1,updated_at=CURRENT_TIMESTAMP
                    WHERE tenant_id=? AND id=? AND deleted_flag=0
                    """, file.getTenantId(), number(link.get("id")));
            jdbcTemplate.update("""
                    UPDATE project_file_catalog SET deleted_flag=1,updated_at=CURRENT_TIMESTAMP
                    WHERE tenant_id=? AND id=? AND deleted_flag=0
                      AND NOT EXISTS (SELECT 1 FROM project_file_version_link v
                                      WHERE v.tenant_id=? AND v.catalog_id=? AND v.deleted_flag=0)
                    """, file.getTenantId(), catalogId, file.getTenantId(), catalogId);
            String path = Objects.toString(link.get("preview_storage_path"), null);
            if (path != null) enqueuePreviewDeleteAfterCommit(file.getTenantId(), path);
        }
    }

    public PageResult<ProjectFileModels.Record> page(int pageNo, int pageSize, Long projectId,
                                                      String keyword, String categoryCode) {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.min(200, Math.max(1, pageSize));
        List<Long> projectIds = projectAccessChecker.accessibleProjectIds();
        if (projectId != null) {
            projectAccessChecker.checkAccess(projectId, "查看项目文件");
            projectIds = List.of(projectId);
        }
        if (projectIds.isEmpty()) {
            return new PageResult<>(safePageNo, safePageSize, 0, List.of());
        }

        QueryParts query = queryParts(projectIds, keyword, categoryCode);
        List<Long> authorizedIds = authorizedCatalogIds(query);
        long total = authorizedIds.size();
        if (total == 0) {
            return new PageResult<>(safePageNo, safePageSize, 0, List.of());
        }
        String authorizedPlaceholders = authorizedIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>(authorizedIds);
        args.add(safePageSize);
        args.add((safePageNo - 1) * safePageSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT c.id,c.project_id,p.project_name,c.file_code,c.display_name,c.category_code,
                       c.source_kind,c.maintain_mode,c.source_business_type,c.source_business_id,
                       COALESCE((SELECT d.dict_label
                           FROM sys_dict_type t JOIN sys_dict_data d ON d.dict_type_id=t.id
                           WHERE t.dict_code='file_category' AND t.status='ENABLE' AND d.status='ENABLE'
                             AND t.tenant_id IN (0,c.tenant_id) AND d.dict_value=c.category_code
                           ORDER BY t.tenant_id DESC LIMIT 1), c.category_code) AS category_name
                FROM project_file_catalog c
                JOIN pm_project p ON p.id=c.project_id AND p.tenant_id=c.tenant_id AND p.deleted_flag=0
                WHERE c.tenant_id=? AND c.deleted_flag=0 AND c.id IN (%s)
                ORDER BY c.updated_at DESC,c.id DESC
                LIMIT ? OFFSET ?
                """.formatted(authorizedPlaceholders), prepend(requireTenantId(), args).toArray());
        return new PageResult<>(safePageNo, safePageSize, total, assemble(rows));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectFileModels.Record create(Long projectId, String name, String categoryCode,
                                           MultipartFile file) {
        String safeName = requiredText(name, 200, "PROJECT_FILE_NAME_INVALID", "文件名称不能为空");
        String safeCategory = requiredText(categoryCode, 50,
                "PROJECT_FILE_CATEGORY_INVALID", "文件分类不能为空").toUpperCase(Locale.ROOT);
        projectAccessChecker.checkAccess(projectId, "新建项目文件");
        requireEnabledCategory(safeCategory);

        ProjectLock project = lockProject(projectId);
        long catalogId = IdWorker.getId();
        String fileCode = nextFileCode(project, LocalDate.now());
        Long tenantId = requireTenantId();
        Long userId = UserContext.getCurrentUserId();
        jdbcTemplate.update("""
                INSERT INTO project_file_catalog(
                    id,tenant_id,project_id,file_code,display_name,category_code,
                    source_kind,maintain_mode,created_by,updated_by,created_at,updated_at,deleted_flag)
                VALUES(?,?,?,?,?,?,'MANAGED','MANAGED',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """, catalogId, tenantId, projectId, fileCode, safeName, safeCategory, userId, userId);

        SysFileVO uploaded = fileService.upload(file, "PROJECT_FILE", catalogId, "OTHER");
        insertVersion(catalogId, 1, Long.valueOf(uploaded.getId()), null, null,
                previewStatus(uploaded.getOriginalName(), uploaded.getContentType()), userId);
        queueOfficePreviewIfNeeded(latestVersionId(catalogId), uploaded.getOriginalName(), uploaded.getContentType());
        return recordById(catalogId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectFileModels.Record appendVersion(Long catalogId, MultipartFile file) {
        CatalogLock catalog = lockCatalog(catalogId);
        if (!"MANAGED".equals(catalog.sourceKind()) || !"MANAGED".equals(catalog.maintainMode())) {
            throw new BusinessException("PROJECT_FILE_READ_ONLY", "业务来源文件只能在原业务模块维护");
        }
        projectAccessChecker.checkAccess(catalog.projectId(), "追加项目文件版本");
        int nextVersion = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(version_no),0)+1 FROM project_file_version_link
                WHERE tenant_id=? AND catalog_id=? AND deleted_flag=0
                """, Integer.class, requireTenantId(), catalogId);
        SysFileVO uploaded = fileService.upload(file, "PROJECT_FILE", catalogId, "OTHER");
        Long userId = UserContext.getCurrentUserId();
        long versionId = insertVersion(catalogId, nextVersion, Long.valueOf(uploaded.getId()), null, null,
                previewStatus(uploaded.getOriginalName(), uploaded.getContentType()), userId);
        jdbcTemplate.update("""
                UPDATE project_file_catalog SET updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND tenant_id=? AND deleted_flag=0
                """, userId, catalogId, requireTenantId());
        queueOfficePreviewIfNeeded(versionId, uploaded.getOriginalName(), uploaded.getContentType());
        return recordById(catalogId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectFileModels.Preview preview(Long versionId) {
        PreviewRow row = requirePreviewRow(versionId);
        businessObjectAuthorizer.checkReadAccess("PROJECT_FILE", row.catalogId());
        if (!"CLEAN".equals(row.virusScanStatus())) {
            throw new BusinessException("FILE_VIRUS_SCAN_REQUIRED", "文件尚未通过安全检查，暂不可预览");
        }
        String kind = previewKind(row.originalName(), row.contentType());
        if ("DIRECT".equals(kind)) {
            String url = fileService.getPresignedFileUrl(row.sysFileId()).url();
            return new ProjectFileModels.Preview("READY", url, null, null, null);
        }
        if ("UNSUPPORTED".equals(kind)) {
            markPreview(versionId, "UNSUPPORTED", null, "PROJECT_FILE_PREVIEW_UNSUPPORTED");
            return new ProjectFileModels.Preview("UNSUPPORTED", null,
                    "PROJECT_FILE_PREVIEW_UNSUPPORTED", "该格式不支持在线预览", null);
        }
        if ("READY".equals(row.previewStatus()) && row.previewStoragePath() != null) {
            return new ProjectFileModels.Preview("READY",
                    fileService.getDerivedPreviewPresignedUrl(row.sysFileId(), row.previewStoragePath()),
                    null, null, null);
        }
        if ("FAILED".equals(row.previewStatus())) {
            return new ProjectFileModels.Preview("FAILED", null,
                    Objects.requireNonNullElse(row.previewErrorCode(), "OFFICE_PREVIEW_CONVERSION_FAILED"),
                    "预览生成失败，请稍后重试", 60);
        }
        if ("PENDING".equals(row.previewStatus())) {
            queuePreview(versionId, row.tenantId(), row.sysFileId(), row.originalName());
        }
        return new ProjectFileModels.Preview("PROCESSING", null, null, "预览正在生成", 2);
    }

    /** Called only by the persisted object-task worker after an authorized enqueue. */
    public void processConversionTask(long versionId) {
        PreviewRow row = requirePreviewRowInternal(versionId);
        if (!"OOXML".equals(previewKind(row.originalName(), row.contentType()))) {
            markPreviewInternal(versionId, row.tenantId(), "UNSUPPORTED", null,
                    "PROJECT_FILE_PREVIEW_UNSUPPORTED");
            return;
        }
        if (!markPreviewInternal(versionId, row.tenantId(), "PROCESSING", null, null)) return;
        String storedPath = null;
        try {
            FileService.InternalFileContent source = fileService.readCleanObjectForInternalConversion(
                    row.tenantId(), row.sysFileId());
            byte[] pdf = previewClient.convert(source.content(), source.originalName());
            storedPath = fileService.storeDerivedPreview(row.tenantId(), row.sysFileId(), source.contentIdentity(), pdf);
            if (!markPreviewInternal(versionId, row.tenantId(), "READY", storedPath, null)) {
                fileService.deleteDerivedPreviewLater(row.tenantId(), storedPath);
            }
        } catch (RuntimeException exception) {
            if (storedPath != null) fileService.deleteDerivedPreviewLater(row.tenantId(), storedPath);
            String code = exception instanceof BusinessException businessException
                    ? businessException.getCode() : "OFFICE_PREVIEW_CONVERSION_FAILED";
            markPreviewInternal(versionId, row.tenantId(), "FAILED", null, code);
            throw exception;
        }
    }

    public ProjectFileModels.ImportPreview previewDirectProjectImport() {
        Long tenantId = requireTenantId();
        List<Map<String, Object>> pending = jdbcTemplate.queryForList("""
                SELECT f.id,f.business_type,f.business_id
                FROM sys_file f
                WHERE f.tenant_id=? AND f.deleted_flag=0 AND f.business_type<>'PROJECT_FILE'
                  AND NOT EXISTS (SELECT 1 FROM project_file_version_link v
                                  WHERE v.tenant_id=f.tenant_id AND v.sys_file_id=f.id AND v.deleted_flag=0)
                ORDER BY f.id
                """, tenantId);
        Long imported = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM project_file_catalog
                WHERE tenant_id=? AND source_kind='BUSINESS' AND deleted_flag=0
                """, Long.class, tenantId);
        long resolvable = 0;
        List<ProjectFileModels.ImportException> exceptions = new ArrayList<>();
        for (Map<String, Object> file : pending) {
            HistoricalResolution resolution = resolveHistoricalProject(
                    Objects.toString(file.get("business_type"), null), number(file.get("business_id")), tenantId);
            if (resolution.projectId() != null) {
                resolvable++;
            } else if (exceptions.size() < 100) {
                exceptions.add(new ProjectFileModels.ImportException(
                        stringId(file.get("id")), Objects.toString(file.get("business_type"), null),
                        stringId(file.get("business_id")), resolution.reason()));
            }
        }
        return new ProjectFileModels.ImportPreview(
                pending.size(), Objects.requireNonNullElse(imported, 0L), resolvable,
                pending.size() - resolvable, exceptions,
                "supported project business objects -> READ_ONLY; bid_document_version keeps logical_name/version_no; ordinary sys_file stays independent V1");
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectFileModels.ImportResult importDirectProjectFiles(long afterFileId, int batchSize) {
        int safeBatch = Math.min(MAX_BATCH, Math.max(1, batchSize));
        Long tenantId = requireTenantId();
        preflightHistoricalCapacity(tenantId);
        List<HistoricalCandidate> candidates = pendingHistoricalCandidates(tenantId).stream()
                .sorted(Comparator.comparing(HistoricalCandidate::createdAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparingLong(HistoricalCandidate::markerId)
                        .thenComparing(HistoricalCandidate::kind))
                .limit(safeBatch)
                .toList();
        int imported = 0;
        long last = afterFileId;
        for (HistoricalCandidate candidate : candidates) {
            last = candidate.markerId();
            imported += "BID".equals(candidate.kind())
                    ? importBidDocumentChain(candidate.row(), tenantId)
                    : importHistoricalFile(candidate, tenantId);
        }
        return new ProjectFileModels.ImportResult(imported, last);
    }

    private List<HistoricalCandidate> pendingHistoricalCandidates(long tenantId) {
        List<HistoricalCandidate> candidates = new ArrayList<>();
        List<Map<String, Object>> files = jdbcTemplate.queryForList("""
                SELECT f.id,f.business_type,f.document_type,f.business_id,f.original_name,f.content_type,f.created_by,f.created_at
                FROM sys_file f
                WHERE f.tenant_id=? AND f.business_type<>'PROJECT_FILE' AND f.deleted_flag=0
                  AND NOT EXISTS (SELECT 1 FROM project_file_version_link v
                                  WHERE v.tenant_id=f.tenant_id AND v.sys_file_id=f.id AND v.deleted_flag=0)
                  AND NOT EXISTS (SELECT 1 FROM bid_document_version b
                                  WHERE b.tenant_id=f.tenant_id AND b.sys_file_id=f.id AND b.deleted_flag=0)
                """, tenantId);
        for (Map<String, Object> file : files) {
            long fileId = number(file.get("id"));
            HistoricalResolution resolution = resolveHistoricalProject(
                    Objects.toString(file.get("business_type"), null), number(file.get("business_id")), tenantId);
            if (resolution.projectId() != null) {
                candidates.add(new HistoricalCandidate("FILE", file, fileId,
                        localDateTime(file.get("created_at")), resolution.projectId()));
            }
        }
        List<Map<String, Object>> bidChains = jdbcTemplate.queryForList("""
                SELECT v.bid_cost_id,v.logical_name,b.project_id,MIN(f.created_at) AS created_at,
                       MIN(f.id) AS marker_id
                FROM bid_document_version v
                JOIN bid_cost b ON b.id=v.bid_cost_id AND b.tenant_id=v.tenant_id AND b.deleted_flag=0
                JOIN pm_project p ON p.id=b.project_id AND p.tenant_id=b.tenant_id AND p.deleted_flag=0
                JOIN sys_file f ON f.id=v.sys_file_id AND f.tenant_id=v.tenant_id AND f.deleted_flag=0
                WHERE v.tenant_id=? AND v.deleted_flag=0
                  AND NOT EXISTS (SELECT 1 FROM project_file_version_link l
                                  WHERE l.tenant_id=v.tenant_id AND l.sys_file_id=v.sys_file_id AND l.deleted_flag=0)
                GROUP BY v.bid_cost_id,v.logical_name,b.project_id
                """, tenantId);
        for (Map<String, Object> chain : bidChains) {
            candidates.add(new HistoricalCandidate("BID", chain, number(chain.get("marker_id")),
                    localDateTime(chain.get("created_at")), number(chain.get("project_id"))));
        }
        return candidates;
    }

    private int importHistoricalFile(HistoricalCandidate candidate, long tenantId) {
        Map<String, Object> file = candidate.row();
        long fileId = candidate.markerId();
        String businessType = Objects.toString(file.get("business_type"), null);
        long businessId = number(file.get("business_id"));
        long catalogId = IdWorker.getId();
        try {
            ProjectLock project = lockProject(candidate.projectId());
            LocalDateTime createdAt = candidate.createdAt();
            String fileCode = nextFileCode(project, createdAt == null ? LocalDate.now() : createdAt.toLocalDate());
            Long createdBy = nullableNumber(file.get("created_by"));
            jdbcTemplate.update("""
                    INSERT INTO project_file_catalog(
                        id,tenant_id,project_id,file_code,display_name,category_code,source_kind,
                        source_business_type,source_business_id,maintain_mode,created_by,updated_by,
                        created_at,updated_at,deleted_flag)
                    VALUES(?,?,?,?,?,?,'BUSINESS',?,?, 'READ_ONLY',?,?,
                           COALESCE(?,CURRENT_TIMESTAMP),COALESCE(?,CURRENT_TIMESTAMP),0)
                    """, catalogId, tenantId, candidate.projectId(), fileCode,
                    Objects.toString(file.get("original_name"), "历史文件"),
                    categoryFor(businessType, Objects.toString(file.get("document_type"), null)), businessType, businessId,
                    createdBy, createdBy, createdAt, createdAt);
            insertVersion(catalogId, 1, fileId, "SYS_FILE", fileId,
                    previewStatus(Objects.toString(file.get("original_name"), null),
                            Objects.toString(file.get("content_type"), null)), createdBy);
            return 1;
        } catch (DuplicateKeyException ignored) {
            discardIncompleteImport(catalogId, tenantId);
            return 0;
        }
    }

    private void preflightHistoricalCapacity(long tenantId) {
        Map<ProjectDate, Integer> pending = new HashMap<>();
        List<Map<String, Object>> files = jdbcTemplate.queryForList("""
                SELECT f.id,f.business_type,f.business_id,f.created_at
                FROM sys_file f
                WHERE f.tenant_id=? AND f.business_type<>'PROJECT_FILE' AND f.deleted_flag=0
                  AND NOT EXISTS (SELECT 1 FROM project_file_version_link v
                                  WHERE v.tenant_id=f.tenant_id AND v.sys_file_id=f.id AND v.deleted_flag=0)
                  AND NOT EXISTS (SELECT 1 FROM bid_document_version b
                                  WHERE b.tenant_id=f.tenant_id AND b.sys_file_id=f.id AND b.deleted_flag=0)
                ORDER BY f.created_at,f.id
                """, tenantId);
        for (Map<String, Object> file : files) {
            HistoricalResolution resolution = resolveHistoricalProject(
                    Objects.toString(file.get("business_type"), null), number(file.get("business_id")), tenantId);
            if (resolution.projectId() == null) continue;
            LocalDateTime createdAt = localDateTime(file.get("created_at"));
            pending.merge(new ProjectDate(resolution.projectId(),
                    createdAt == null ? LocalDate.now() : createdAt.toLocalDate()), 1, Integer::sum);
        }
        List<Map<String, Object>> bidChains = jdbcTemplate.queryForList("""
                SELECT b.project_id,MIN(f.created_at) AS created_at
                FROM bid_document_version v
                JOIN bid_cost b ON b.id=v.bid_cost_id AND b.tenant_id=v.tenant_id AND b.deleted_flag=0
                JOIN sys_file f ON f.id=v.sys_file_id AND f.tenant_id=v.tenant_id AND f.deleted_flag=0
                WHERE v.tenant_id=? AND v.deleted_flag=0
                  AND NOT EXISTS (SELECT 1 FROM project_file_version_link l
                                  WHERE l.tenant_id=v.tenant_id AND l.sys_file_id=v.sys_file_id AND l.deleted_flag=0)
                  AND b.project_id IS NOT NULL
                GROUP BY v.bid_cost_id,v.logical_name,b.project_id
                """, tenantId);
        for (Map<String, Object> chain : bidChains) {
            LocalDateTime createdAt = localDateTime(chain.get("created_at"));
            pending.merge(new ProjectDate(number(chain.get("project_id")),
                    createdAt == null ? LocalDate.now() : createdAt.toLocalDate()), 1, Integer::sum);
        }
        for (Map.Entry<ProjectDate, Integer> entry : pending.entrySet()) {
            ProjectDate key = entry.getKey();
            ProjectLock project = lockProject(key.projectId());
            String prefix = "FILE-" + requiredText(project.projectCode(), 50,
                    "PROJECT_FILE_CODE_INVALID", "项目编码为空，无法生成文件编号")
                    + '-' + DATE_CODE.format(key.date()) + '-';
            Long existing = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM project_file_catalog
                    WHERE tenant_id=? AND project_id=? AND file_code LIKE ?
                    """, Long.class, tenantId, key.projectId(), prefix + "%");
            if (Objects.requireNonNullElse(existing, 0L) + entry.getValue() > 999) {
                throw new BusinessException("PROJECT_FILE_CODE_EXHAUSTED",
                        "项目日期历史文件超过999条，当前批次未写入");
            }
        }
    }

    private int importBidDocumentChain(Map<String, Object> chain, long tenantId) {
        long bidCostId = number(chain.get("bid_cost_id"));
        long projectId = number(chain.get("project_id"));
        String logicalName = Objects.toString(chain.get("logical_name"), "投标文件");
        List<Map<String, Object>> versions = jdbcTemplate.queryForList("""
                SELECT v.id,v.version_no,v.sys_file_id,f.original_name,f.content_type,
                       f.created_by,f.created_at
                FROM bid_document_version v
                JOIN sys_file f ON f.id=v.sys_file_id AND f.tenant_id=v.tenant_id AND f.deleted_flag=0
                WHERE v.tenant_id=? AND v.bid_cost_id=? AND v.logical_name=? AND v.deleted_flag=0
                ORDER BY v.version_no
                """, tenantId, bidCostId, logicalName);
        if (versions.isEmpty()) return 0;
        List<Long> existingCatalogs = jdbcTemplate.queryForList("""
                SELECT id FROM project_file_catalog
                WHERE tenant_id=? AND source_kind='BUSINESS' AND source_business_type='BID_COST'
                  AND source_business_id=? AND display_name=? AND deleted_flag=0
                ORDER BY id LIMIT 1
                """, Long.class, tenantId, bidCostId, logicalName);
        boolean newCatalog = existingCatalogs.isEmpty();
        long catalogId = newCatalog ? IdWorker.getId() : existingCatalogs.getFirst();
        try {
            if (newCatalog) {
                ProjectLock project = lockProject(projectId);
                LocalDateTime chainCreatedAt = localDateTime(chain.get("created_at"));
                String fileCode = nextFileCode(project,
                        chainCreatedAt == null ? LocalDate.now() : chainCreatedAt.toLocalDate());
                Long createdBy = nullableNumber(versions.getFirst().get("created_by"));
                jdbcTemplate.update("""
                        INSERT INTO project_file_catalog(
                            id,tenant_id,project_id,file_code,display_name,category_code,source_kind,
                            source_business_type,source_business_id,maintain_mode,created_by,updated_by,
                            created_at,updated_at,deleted_flag)
                        VALUES(?,?,?,?,?,'BID','BUSINESS','BID_COST',?,'READ_ONLY',?,?,
                               COALESCE(?,CURRENT_TIMESTAMP),CURRENT_TIMESTAMP,0)
                        """, catalogId, tenantId, projectId, fileCode, logicalName, bidCostId,
                        createdBy, createdBy, chainCreatedAt);
            }
            int inserted = 0;
            for (Map<String, Object> version : versions) {
                if (alreadyLinked(tenantId, number(version.get("sys_file_id")))) continue;
                insertVersion(catalogId, ((Number) version.get("version_no")).intValue(),
                        number(version.get("sys_file_id")), "BID_DOCUMENT_VERSION",
                        number(version.get("id")), previewStatus(
                                Objects.toString(version.get("original_name"), null),
                                Objects.toString(version.get("content_type"), null)),
                        nullableNumber(version.get("created_by")));
                inserted++;
            }
            return inserted == 0 ? 0 : 1;
        } catch (DuplicateKeyException duplicate) {
            if (!newCatalog) throw duplicate;
            discardIncompleteImport(catalogId, tenantId);
            return 0;
        }
    }

    private boolean alreadyLinked(long tenantId, long sysFileId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM project_file_version_link
                WHERE tenant_id=? AND sys_file_id=? AND deleted_flag=0
                """, Integer.class, tenantId, sysFileId);
        return count != null && count > 0;
    }

    private void discardIncompleteImport(long catalogId, long tenantId) {
        jdbcTemplate.update("""
                DELETE FROM project_file_version_link WHERE tenant_id=? AND catalog_id=?
                """, tenantId, catalogId);
        jdbcTemplate.update("""
                DELETE FROM project_file_catalog WHERE tenant_id=? AND id=?
                """, tenantId, catalogId);
    }

    private HistoricalResolution resolveHistoricalProject(String businessType, long businessId, long tenantId) {
        if (businessType == null) return new HistoricalResolution(null, "BUSINESS_TYPE_MISSING");
        String type = businessType.trim().toUpperCase(Locale.ROOT);
        if (Set.of("PARTNER", "MATERIAL", "PROJECT_FILE", "COMMUNICATION_MESSAGE").contains(type)) {
            return new HistoricalResolution(null, "NON_PROJECT_OR_EXCLUDED");
        }
        String directTable = DIRECT_PROJECT_TABLES.get(type);
        String sql = directTable == null ? switch (type) {
            case "PROJECT" -> "SELECT id AS project_id FROM pm_project WHERE id=? AND tenant_id=? AND deleted_flag=0";
            case "INVOICE" -> """
                    SELECT COALESCE(r.project_id,ra.project_id,ia.project_id) AS project_id
                    FROM pay_invoice i
                    LEFT JOIN pay_record r ON r.id=i.pay_record_id AND r.tenant_id=i.tenant_id AND r.deleted_flag=0
                    LEFT JOIN pay_application ra ON ra.id=r.pay_application_id AND ra.tenant_id=i.tenant_id AND ra.deleted_flag=0
                    LEFT JOIN pay_application ia ON ia.id=i.pay_application_id AND ia.tenant_id=i.tenant_id AND ia.deleted_flag=0
                    WHERE i.id=? AND i.tenant_id=? AND i.deleted_flag=0
                    """;
            case "QS_ISSUE" -> "SELECT r.project_id FROM qs_issue i JOIN qs_inspection_record r ON r.id=i.inspection_id AND r.tenant_id=i.tenant_id AND r.deleted_flag=0 WHERE i.id=? AND i.tenant_id=? AND i.deleted_flag=0";
            case "SUPPLIER_QUOTE" -> "SELECT e.project_id FROM sp_supplier_quote q JOIN sp_sourcing_event e ON e.id=q.sourcing_event_id AND e.tenant_id=q.tenant_id AND e.deleted_flag=0 WHERE q.id=? AND q.tenant_id=? AND q.deleted_flag=0";
            case "TECH_RFI_RESPONSE" -> "SELECT r.project_id FROM tech_rfi_response p JOIN tech_rfi r ON r.id=p.rfi_id AND r.tenant_id=p.tenant_id AND r.deleted_flag=0 WHERE p.id=? AND p.tenant_id=? AND p.deleted_flag=0";
            default -> null;
        } : projectSql(directTable);
        if (sql == null) return new HistoricalResolution(null, "BUSINESS_TYPE_UNSUPPORTED");
        List<Long> ids = jdbcTemplate.query(sql, (rs, rowNum) -> {
            long value = rs.getLong("project_id");
            return rs.wasNull() ? null : value;
        }, businessId, tenantId);
        if (ids.size() != 1 || ids.getFirst() == null) {
            return new HistoricalResolution(null, "PROJECT_UNRESOLVED");
        }
        Integer projectExists = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pm_project WHERE id=? AND tenant_id=? AND deleted_flag=0
                """, Integer.class, ids.getFirst(), tenantId);
        return projectExists != null && projectExists == 1
                ? new HistoricalResolution(ids.getFirst(), null)
                : new HistoricalResolution(null, "PROJECT_MISSING_OR_TENANT_MISMATCH");
    }

    private static String projectSql(String table) {
        return "SELECT project_id FROM " + table + " WHERE id=? AND tenant_id=? AND deleted_flag=0";
    }

    public ProjectFileModels.Reconciliation reconcile() {
        long tenantId = requireTenantId();
        long sourceMissing = 0;
        long wrongProject = 0;
        List<Map<String, Object>> businessCatalogs = jdbcTemplate.queryForList("""
                SELECT project_id,source_business_type,source_business_id
                FROM project_file_catalog
                WHERE tenant_id=? AND source_kind='BUSINESS' AND deleted_flag=0
                """, tenantId);
        for (Map<String, Object> catalog : businessCatalogs) {
            HistoricalResolution resolution = resolveHistoricalProject(
                    Objects.toString(catalog.get("source_business_type"), null),
                    number(catalog.get("source_business_id")), tenantId);
            if (resolution.projectId() == null) sourceMissing++;
            else if (resolution.projectId() != number(catalog.get("project_id"))) wrongProject++;
        }
        long unindexed = previewDirectProjectImport().resolvableCount();
        return new ProjectFileModels.Reconciliation(
                unindexed,
                scalar("""
                        SELECT COUNT(*) FROM project_file_catalog c
                        WHERE c.tenant_id=? AND c.deleted_flag=0
                          AND NOT EXISTS (SELECT 1 FROM project_file_version_link v
                                          WHERE v.tenant_id=c.tenant_id AND v.catalog_id=c.id AND v.deleted_flag=0)
                        """, tenantId),
                scalar("""
                        SELECT COUNT(*) FROM (
                          SELECT sys_file_id FROM project_file_version_link
                          WHERE tenant_id=? AND deleted_flag=0
                          GROUP BY sys_file_id HAVING COUNT(*)>1
                        ) duplicate_refs
                        """, tenantId),
                scalar("""
                        SELECT COUNT(*) FROM (
                          SELECT catalog_id FROM project_file_version_link
                          WHERE tenant_id=? AND deleted_flag=0
                          GROUP BY catalog_id
                          HAVING MAX(version_no)-MIN(version_no)+1<>COUNT(*) OR MIN(version_no)<>1
                        ) version_gaps
                        """, tenantId),
                sourceMissing,
                wrongProject,
                scalar("""
                        SELECT COUNT(*) FROM project_file_version_link v
                        LEFT JOIN project_file_catalog c ON c.id=v.catalog_id
                        LEFT JOIN sys_file f ON f.id=v.sys_file_id
                        WHERE v.tenant_id=? AND v.deleted_flag=0
                          AND (c.id IS NULL OR c.tenant_id<>v.tenant_id
                               OR f.id IS NULL OR f.tenant_id<>v.tenant_id)
                        """, tenantId),
                scalar("""
                        SELECT COUNT(*) FROM project_file_version_link
                        WHERE tenant_id=? AND deleted_flag=0 AND preview_status='READY'
                          AND preview_storage_path IS NULL
                        """, tenantId));
    }

    private long scalar(String sql, Object... args) {
        return Objects.requireNonNullElse(jdbcTemplate.queryForObject(sql, Long.class, args), 0L);
    }

    private List<ProjectFileModels.Record> assemble(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return List.of();
        List<Long> ids = rows.stream().map(row -> number(row.get("id"))).toList();
        String placeholders = ids.stream().map(ignored -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(requireTenantId());
        args.addAll(ids);
        List<Map<String, Object>> versionRows = jdbcTemplate.queryForList("""
                SELECT v.id,v.catalog_id,v.version_no,v.sys_file_id,v.preview_status,
                       f.virus_scan_status,f.created_by,f.created_at,u.real_name
                FROM project_file_version_link v
                JOIN sys_file f ON f.id=v.sys_file_id AND f.tenant_id=v.tenant_id AND f.deleted_flag=0
                LEFT JOIN sys_user u ON u.id=f.created_by AND u.tenant_id=f.tenant_id AND u.deleted_flag=0
                WHERE v.tenant_id=? AND v.deleted_flag=0 AND v.catalog_id IN (%s)
                ORDER BY v.catalog_id,v.version_no DESC
                """.formatted(placeholders), args.toArray());
        Map<Long, List<ProjectFileModels.Version>> versions = new HashMap<>();
        for (Map<String, Object> version : versionRows) {
            Long createdBy = nullableNumber(version.get("created_by"));
            versions.computeIfAbsent(number(version.get("catalog_id")), ignored -> new ArrayList<>())
                    .add(new ProjectFileModels.Version(
                            stringId(version.get("id")), ((Number) version.get("version_no")).intValue(),
                            stringId(version.get("sys_file_id")), Objects.toString(version.get("real_name"), null),
                            Objects.toString(version.get("real_name"), null),
                            createdBy == null ? null : String.valueOf(createdBy),
                            Objects.toString(version.get("created_at"), null),
                            Objects.toString(version.get("virus_scan_status"), "NOT_SCANNED"),
                            Objects.toString(version.get("preview_status"), "PENDING")));
        }
        return rows.stream().map(row -> {
            long id = number(row.get("id"));
            boolean business = "BUSINESS".equals(row.get("source_kind"));
            return new ProjectFileModels.Record(
                    String.valueOf(id), stringId(row.get("project_id")), Objects.toString(row.get("project_name"), null),
                    Objects.toString(row.get("file_code"), null), Objects.toString(row.get("display_name"), null),
                    Objects.toString(row.get("category_code"), null), Objects.toString(row.get("category_name"), null),
                    Objects.toString(row.get("source_kind"), null), Objects.toString(row.get("maintain_mode"), null),
                    business ? "由原业务模块维护" : null,
                    business && "PROJECT".equals(row.get("source_business_type"))
                            ? "/project/list?projectId=" + row.get("project_id") : null,
                    versions.getOrDefault(id, List.of()));
        }).toList();
    }

    private ProjectFileModels.Record recordById(long catalogId) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap("""
                    SELECT c.id,c.project_id,p.project_name,c.file_code,c.display_name,c.category_code,
                           c.source_kind,c.maintain_mode,c.source_business_type,c.source_business_id,
                           COALESCE((SELECT d.dict_label FROM sys_dict_type t
                               JOIN sys_dict_data d ON d.dict_type_id=t.id
                               WHERE t.dict_code='file_category' AND d.dict_value=c.category_code
                                 AND t.tenant_id IN (0,c.tenant_id)
                               ORDER BY t.tenant_id DESC LIMIT 1),c.category_code) AS category_name
                    FROM project_file_catalog c
                    JOIN pm_project p ON p.id=c.project_id AND p.tenant_id=c.tenant_id AND p.deleted_flag=0
                    WHERE c.id=? AND c.tenant_id=? AND c.deleted_flag=0
                    """, catalogId, requireTenantId());
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new BusinessException("PROJECT_FILE_NOT_FOUND", "项目文件不存在");
        }
        projectAccessChecker.checkAccess(number(row.get("project_id")), "读取项目文件");
        return assemble(List.of(row)).getFirst();
    }

    private List<Long> authorizedCatalogIds(QueryParts query) {
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList("""
                SELECT c.id,c.source_kind,c.source_business_type,c.source_business_id
                FROM project_file_catalog c WHERE %s
                ORDER BY c.updated_at DESC,c.id DESC
                """.formatted(query.where()), query.args().toArray());
        List<Long> ids = new ArrayList<>(candidates.size());
        for (Map<String, Object> candidate : candidates) {
            long id = number(candidate.get("id"));
            if (!"BUSINESS".equals(Objects.toString(candidate.get("source_kind"), null))) {
                ids.add(id);
                continue;
            }
            try {
                businessObjectAuthorizer.checkReadAccess("PROJECT_FILE", id);
                ids.add(id);
            } catch (BusinessException deniedOrMissing) {
                // Source permission and source-object existence are part of list visibility.
            }
        }
        return ids;
    }

    private static List<Object> prepend(Object first, List<Object> rest) {
        List<Object> values = new ArrayList<>(rest.size() + 1);
        values.add(first);
        values.addAll(rest);
        return values;
    }

    private QueryParts queryParts(List<Long> projectIds, String keyword, String categoryCode) {
        String placeholders = projectIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
        StringBuilder where = new StringBuilder("c.tenant_id=? AND c.deleted_flag=0 AND c.project_id IN (")
                .append(placeholders).append(')');
        List<Object> args = new ArrayList<>();
        args.add(requireTenantId());
        args.addAll(projectIds);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            if (value.length() > 100) throw new BusinessException("PROJECT_FILE_QUERY_INVALID", "关键词过长");
            where.append(" AND (c.file_code LIKE ? OR c.display_name LIKE ?)");
            args.add('%' + value + '%');
            args.add('%' + value + '%');
        }
        if (categoryCode != null && !categoryCode.isBlank()) {
            where.append(" AND c.category_code=?");
            args.add(categoryCode.trim().toUpperCase(Locale.ROOT));
        }
        return new QueryParts(where.toString(), args);
    }

    private ProjectLock lockProject(long projectId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id,project_code FROM pm_project
                    WHERE id=? AND tenant_id=? AND deleted_flag=0 FOR UPDATE
                    """, (rs, rowNum) -> new ProjectLock(rs.getLong("id"), rs.getString("project_code")),
                    projectId, requireTenantId());
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
    }

    private CatalogLock lockCatalog(long catalogId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id,project_id,source_kind,maintain_mode FROM project_file_catalog
                    WHERE id=? AND tenant_id=? AND deleted_flag=0 FOR UPDATE
                    """, (rs, rowNum) -> new CatalogLock(rs.getLong("id"), rs.getLong("project_id"),
                            rs.getString("source_kind"), rs.getString("maintain_mode")),
                    catalogId, requireTenantId());
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new BusinessException("PROJECT_FILE_NOT_FOUND", "项目文件不存在");
        }
    }

    private String nextFileCode(ProjectLock project, LocalDate date) {
        String projectCode = requiredText(project.projectCode(), 50,
                "PROJECT_FILE_CODE_INVALID", "项目编码为空，无法生成文件编号");
        String prefix = "FILE-" + projectCode + '-' + DATE_CODE.format(date) + '-';
        String last = jdbcTemplate.queryForObject("""
                SELECT MAX(file_code) FROM project_file_catalog
                WHERE tenant_id=? AND project_id=? AND file_code LIKE ?
                """, String.class, requireTenantId(), project.id(), prefix + "%");
        int next = 1;
        if (last != null && last.length() == prefix.length() + 3) {
            try {
                next = Integer.parseInt(last.substring(prefix.length())) + 1;
            } catch (NumberFormatException exception) {
                throw new BusinessException("PROJECT_FILE_CODE_INVALID", "既有文件编号格式异常");
            }
        }
        if (next > 999) throw new BusinessException("PROJECT_FILE_CODE_EXHAUSTED", "当日项目文件编号已用尽");
        return prefix + "%03d".formatted(next);
    }

    private long insertVersion(long catalogId, int versionNo, long sysFileId,
                               String sourceVersionType, Long sourceVersionId,
                               String previewStatus, Long createdBy) {
        long versionId = IdWorker.getId();
        jdbcTemplate.update("""
                INSERT INTO project_file_version_link(
                    id,tenant_id,catalog_id,version_no,sys_file_id,source_version_type,source_version_id,
                    preview_status,created_by,updated_by,created_at,updated_at,deleted_flag)
                VALUES(?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """, versionId, requireTenantId(), catalogId, versionNo, sysFileId,
                sourceVersionType, sourceVersionId, previewStatus, createdBy, createdBy);
        return versionId;
    }

    private long latestVersionId(long catalogId) {
        return jdbcTemplate.queryForObject("""
                SELECT id FROM project_file_version_link
                WHERE tenant_id=? AND catalog_id=? AND deleted_flag=0
                ORDER BY version_no DESC LIMIT 1
                """, Long.class, requireTenantId(), catalogId);
    }

    private void requireEnabledCategory(String categoryCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_dict_type t JOIN sys_dict_data d ON d.dict_type_id=t.id
                WHERE t.dict_code='file_category' AND t.status='ENABLE' AND d.status='ENABLE'
                  AND t.tenant_id IN (0,?) AND d.dict_value=?
                """, Integer.class, requireTenantId(), categoryCode);
        if (count == null || count == 0) {
            throw new BusinessException("PROJECT_FILE_CATEGORY_INVALID", "文件分类无效或已停用");
        }
    }

    private PreviewRow requirePreviewRow(long versionId) {
        PreviewRow row = requirePreviewRowInternal(versionId);
        if (!Objects.equals(row.tenantId(), requireTenantId())) {
            throw new BusinessException("PROJECT_FILE_NOT_FOUND", "项目文件不存在");
        }
        return row;
    }

    private PreviewRow requirePreviewRowInternal(long versionId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT v.id,v.tenant_id,v.catalog_id,c.project_id,v.sys_file_id,v.preview_status,
                           v.preview_storage_path,v.preview_error_code,
                           f.original_name,f.content_type,f.virus_scan_status
                    FROM project_file_version_link v
                    JOIN project_file_catalog c ON c.id=v.catalog_id AND c.tenant_id=v.tenant_id AND c.deleted_flag=0
                    JOIN sys_file f ON f.id=v.sys_file_id AND f.tenant_id=v.tenant_id AND f.deleted_flag=0
                    WHERE v.id=? AND v.deleted_flag=0
                    """, (rs, rowNum) -> new PreviewRow(
                            rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("catalog_id"), rs.getLong("project_id"),
                            rs.getLong("sys_file_id"), rs.getString("original_name"), rs.getString("content_type"),
                            rs.getString("virus_scan_status"), rs.getString("preview_status"),
                            rs.getString("preview_storage_path"), rs.getString("preview_error_code")), versionId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new BusinessException("PROJECT_FILE_NOT_FOUND", "项目文件不存在");
        }
    }

    private void queueOfficePreviewIfNeeded(long versionId, String originalName, String contentType) {
        if (!"OOXML".equals(previewKind(originalName, contentType))) return;
        PreviewRow row = requirePreviewRow(versionId);
        queuePreview(versionId, row.tenantId(), row.sysFileId(), row.originalName());
    }

    private void queuePreview(long versionId, long tenantId, long sysFileId, String originalName) {
        objectTaskService.enqueuePreviewConvert(tenantId, sysFileId, versionId, originalName);
    }

    private void enqueuePreviewDeleteAfterCommit(long tenantId, String path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fileService.deleteDerivedPreviewLater(tenantId, path);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileService.deleteDerivedPreviewLater(tenantId, path);
            }
        });
    }

    static String categoryFor(String businessType, String documentType) {
        String normalizedDocumentType = documentType == null ? "" : documentType.trim().toUpperCase(Locale.ROOT);
        String normalizedBusinessType = businessType == null ? "" : businessType.trim().toUpperCase(Locale.ROOT);
        return DOCUMENT_CATEGORIES.getOrDefault(normalizedDocumentType,
                BUSINESS_CATEGORIES.getOrDefault(normalizedBusinessType, "OTHER"));
    }

    private String previewStatus(String originalName, String contentType) {
        return switch (previewKind(originalName, contentType)) {
            case "DIRECT" -> "READY";
            case "OOXML" -> "PENDING";
            default -> "UNSUPPORTED";
        };
    }

    static String previewKind(String originalName, String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String name = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        if ("application/pdf".equals(type) || type.startsWith("image/")) return "DIRECT";
        if (name.endsWith(".docx") || name.endsWith(".xlsx") || name.endsWith(".pptx")) return "OOXML";
        return "UNSUPPORTED";
    }

    private void markPreview(long versionId, String status, String path, String errorCode) {
        markPreviewInternal(versionId, requireTenantId(), status, path, errorCode);
    }

    private boolean markPreviewInternal(long versionId, long tenantId, String status, String path, String errorCode) {
        return jdbcTemplate.update("""
                UPDATE project_file_version_link
                SET preview_status=?,preview_storage_path=?,preview_error_code=?,
                    preview_updated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND tenant_id=? AND deleted_flag=0
                """, status, path, errorCode, versionId, tenantId) == 1;
    }

    private String requiredText(String value, int maxLength, String code, String message) {
        String safe = value == null ? "" : value.trim();
        if (safe.isEmpty() || safe.length() > maxLength) throw new BusinessException(code, message);
        return safe;
    }

    private Long requireTenantId() {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) throw new BusinessException("AUTH_CONTEXT_MISSING", "缺少租户上下文");
        return tenantId;
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private static Long nullableNumber(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static String stringId(Object value) {
        return value == null ? null : String.valueOf(((Number) value).longValue());
    }

    private static LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return null;
    }

    private record QueryParts(String where, List<Object> args) {}
    private record HistoricalResolution(Long projectId, String reason) {}
    private record HistoricalCandidate(String kind, Map<String, Object> row, long markerId,
                                       LocalDateTime createdAt, long projectId) {}
    private record ProjectLock(long id, String projectCode) {}
    private record ProjectDate(long projectId, LocalDate date) {}
    private record CatalogLock(long id, long projectId, String sourceKind, String maintainMode) {}
    private record PreviewRow(long id, long tenantId, long catalogId, long projectId, long sysFileId,
                              String originalName, String contentType, String virusScanStatus,
                              String previewStatus, String previewStoragePath, String previewErrorCode) {}
}
