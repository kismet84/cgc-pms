package com.cgcpms.file.auth;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.expense.entity.ExpenseApplication;
import com.cgcpms.expense.mapper.ExpenseApplicationMapper;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.site.entity.SiteDailyLog;
import com.cgcpms.site.mapper.SiteDailyLogMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文件关联业务对象授权校验器。
 * <p>
 * 每个 businessType 必须注册对应的校验逻辑，
 * 在上传、下载、列表、删除文件前验证当前用户对关联业务对象的访问权限。
 * 未知 businessType 将直接拒绝。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessObjectAuthorizer {

    private final ProjectAccessChecker projectAccessChecker;
    private final CtContractMapper contractMapper;
    private final PayInvoiceMapper invoiceMapper;
    private final MatReceiptMapper receiptMapper;
    private final PayApplicationMapper paymentMapper;
    private final PayRecordMapper payRecordMapper;
    private final SubMeasureMapper subcontractMapper;
    private final StlSettlementMapper settlementMapper;
    private final VarOrderMapper variationMapper;
    private final BidCostMapper bidCostMapper;
    private final MdPartnerMapper partnerMapper;
    private final MdMaterialMapper materialMapper;
    private final CashJournalEntryMapper cashJournalEntryMapper;
    private final SiteDailyLogMapper siteDailyLogMapper;
    private final ExpenseApplicationMapper expenseApplicationMapper;
    private final PmProjectMapper projectMapper;
    private final JdbcTemplate jdbcTemplate;

    private static final Set<String> KNOWN_BUSINESS_TYPES = Set.of(
            "PROJECT", "PROJECT_FILE", "PROJECT_COMMENCEMENT", "COMMUNICATION_MESSAGE", "CONTRACT", "INVOICE", "RECEIPT",
            "PURCHASE_REQUEST", "PURCHASE_ORDER", "MATERIAL_RECEIPT",
            "PAYMENT", "SUBCONTRACT", "SETTLEMENT", "VARIATION",
            "BID_COST", "PARTNER", "MATERIAL", "CASH_JOURNAL", "SITE_DAILY_LOG", "EXPENSE",
            "CONTRACT_REVENUE", "OWNER_SETTLEMENT", "SALES_INVOICE", "COLLECTION_RECORD",
            "PRODUCTION_MEASUREMENT", "OWNER_MEASUREMENT_SUBMISSION",
            "QS_INSPECTION", "QS_ISSUE", "QS_RECTIFICATION",
            "SUPPLIER_SOURCING", "SUPPLIER_QUOTE",
            "TECH_SCHEME", "TECH_DRAWING_VERSION", "TECH_DRAWING_REVIEW", "TECH_RFI",
            "TECH_RFI_RESPONSE", "TECH_DISCLOSURE", "TECH_ARCHIVE",
            "CLOSEOUT_SECTION_ACCEPTANCE", "CLOSEOUT_FINAL_ACCEPTANCE", "CLOSEOUT_DEFECT",
            "CLOSEOUT_WARRANTY", "CLOSEOUT_ARCHIVE_TRANSFER"
    );

    /**
     * 验证当前用户对指定业务对象拥有读权限。
     */
    public void checkReadAccess(String businessType, Long businessId) {
        checkAccess(businessType, businessId, "读取", false,
                "file:query", "cashbook:journal:query");
    }

    /** Permission-only half of project-file source visibility; object existence stays in SQL. */
    public boolean canReadProjectFileSource(String businessType) {
        String upperType = businessType == null ? "" : businessType.toUpperCase();
        if (!KNOWN_BUSINESS_TYPES.contains(upperType)) return false;
        try {
            requireSourceReadAuthority(upperType);
            requireAccessAuthority(upperType, false, "file:query", "cashbook:journal:query", null);
            return true;
        } catch (BusinessException denied) {
            if ("FILE_ACCESS_DENIED".equals(denied.getCode())) return false;
            throw denied;
        }
    }

    /** Business-query half of the generated-document permission intersection. */
    public void checkGeneratedDocumentAccess(String businessType, Long businessId) {
        String upper = businessType == null ? "" : businessType.toUpperCase();
        String authority = switch (upper) {
            case "PAYMENT" -> "payment:app:query";
            case "SETTLEMENT" -> "settlement:query";
            case "PURCHASE_REQUEST" -> "purchase:request:list";
            case "PURCHASE_ORDER" -> "purchase:order:query";
            case "MATERIAL_RECEIPT" -> "receipt:query";
            default -> throw new BusinessException("DOCUMENT_BUSINESS_TYPE_INVALID",
                    "不支持该业务单据类型");
        };
        checkAccess(upper, businessId, "读取生成文档", false, authority, authority);
    }

    /** Provider handles tenant/project/object lookup; this preserves the domain query permission intersection. */
    public void checkDocumentQueryAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            throw new BusinessException("DOCUMENT_PROVIDER_AUTHORITY_INVALID", "业务单据 Provider 未配置查询权限");
        }
        requireAuthority(authority);
    }

    /**
     * 验证当前用户对指定业务对象拥有附件上传权限。
     */
    public void checkUploadAccess(String businessType, Long businessId) {
        checkUploadAccess(businessType, businessId, null);
    }

    public void checkUploadAccess(String businessType, Long businessId, String documentType) {
        checkAccess(businessType, businessId, "写入", true,
                "file:upload", "cashbook:journal:maintain", documentType);
    }

    /**
     * 验证当前用户对指定业务对象拥有附件删除权限。
     */
    public void checkDeleteAccess(String businessType, Long businessId) {
        checkDeleteAccess(businessType, businessId, null);
    }

    public void checkDeleteAccess(String businessType, Long businessId, String documentType) {
        checkAccess(businessType, businessId, "删除", true,
                "file:delete", "cashbook:journal:maintain", documentType);
    }

    /** 变更签证附件按业务阶段不可逆约束，防止事后替换现场证据或伪造业主核定。 */
    public void checkVariationDocumentStage(String businessType, Long businessId, String documentType) {
        if ("PRODUCTION_MEASUREMENT".equalsIgnoreCase(businessType)) {
            String type = documentType == null ? "" : documentType.toUpperCase();
            if ("MEASUREMENT_GENERAL".equals(type) || type.startsWith("ML_")) {
                requireAuthority("measurement:submit");
                return;
            }
            if ("OWNER_SUBMISSION".equals(type)) {
                requireAuthority("measurement:owner:submit");
                return;
            }
            throw new BusinessException("MEASUREMENT_DOCUMENT_STAGE_INVALID", "不支持的产值计量附件类型");
        }
        if ("OWNER_MEASUREMENT_SUBMISSION".equalsIgnoreCase(businessType)) {
            if (!"OWNER_CONFIRMATION".equalsIgnoreCase(documentType)) {
                throw new BusinessException("MEASUREMENT_DOCUMENT_STAGE_INVALID", "不支持的业主核定附件类型");
            }
            requireAuthority("measurement:owner:review");
            return;
        }
        if (businessType != null && businessType.toUpperCase().startsWith("QS_")) {
            checkQualityDocumentStage(businessType.toUpperCase(), businessId, documentType);
            return;
        }
        if (businessType != null && businessType.toUpperCase().startsWith("SUPPLIER_")) {
            checkSupplierDocumentStage(businessType.toUpperCase(), businessId, documentType);
            return;
        }
        if (businessType != null && businessType.toUpperCase().startsWith("TECH_")) {
            checkTechnicalDocumentStage(businessType.toUpperCase(), businessId, documentType);
            return;
        }
        if (businessType != null && businessType.toUpperCase().startsWith("CLOSEOUT_")) {
            checkCloseoutDocumentStage(businessType.toUpperCase(), businessId, documentType);
            return;
        }
        if (!"VARIATION".equalsIgnoreCase(businessType)) return;
        VarOrder variation = variationMapper.selectByIdForUpdate(
                businessId, UserContext.getCurrentTenantId());
        if (variation == null)
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "变更单不存在: " + businessId);
        String type = documentType == null ? "" : documentType.toUpperCase();
        String authority = switch (type) {
            case "SITE_EVIDENCE", "COST_ESTIMATE" -> "variation:order:edit";
            case "OWNER_SUBMISSION" -> "variation:owner:submit";
            case "OWNER_CONFIRMATION" -> "variation:owner:review";
            default -> null;
        };
        if (authority == null)
            throw new BusinessException("VARIATION_DOCUMENT_STAGE_INVALID", "不支持的变更附件类型");
        requireAuthority(authority);
        boolean allowed = switch (type) {
            case "SITE_EVIDENCE", "COST_ESTIMATE" -> Set.of("DRAFT", "REJECTED").contains(variation.getApprovalStatus());
            case "OWNER_SUBMISSION" -> "APPROVED".equals(variation.getApprovalStatus())
                    && Set.of("INTERNAL_APPROVED", "OWNER_RETURNED").contains(variation.getOwnerStatus());
            case "OWNER_CONFIRMATION" -> "OWNER_SUBMITTED".equals(variation.getOwnerStatus());
            default -> false;
        };
        if (!allowed)
            throw new BusinessException("VARIATION_DOCUMENT_STAGE_INVALID", "当前业务阶段不允许变更该类附件");
    }

    private void checkAccess(String businessType, Long businessId, String action, boolean write,
                             String genericAuthority, String cashJournalAuthority) {
        checkAccess(businessType, businessId, action, write, genericAuthority, cashJournalAuthority, null);
    }

    private void checkAccess(String businessType, Long businessId, String action, boolean write,
                             String genericAuthority, String cashJournalAuthority, String documentType) {
        if (businessType == null || !KNOWN_BUSINESS_TYPES.contains(businessType.toUpperCase())) {
            throw new BusinessException("FILE_BIZ_TYPE_UNKNOWN",
                    "不支持的业务类型: " + businessType);
        }

        String upperType = businessType.toUpperCase();
        requireAccessAuthority(upperType, write, genericAuthority, cashJournalAuthority, documentType);
        checkBusinessObject(upperType, businessId, action, write, documentType, businessType);
    }

    private void requireAccessAuthority(String upperType, boolean write, String genericAuthority,
                                        String cashJournalAuthority, String documentType) {
        String requiredAuthority = switch (upperType) {
            case "CASH_JOURNAL" -> cashJournalAuthority;
            case "BID_COST" -> write ? "bid:file:manage" : "bid:query";
            case "SITE_DAILY_LOG" -> write ? "site:daily:edit" : "site:daily:query";
            case "PROJECT_COMMENCEMENT" -> write ? "project:commencement:edit" : "project:commencement:query";
            case "PROJECT_FILE" -> write ? "project:file:manage" : "project:file:query";
            case "COMMUNICATION_MESSAGE" -> write ? "communication:send" : "communication:view";
            case "SUBCONTRACT" -> write ? "subcontract:measure:edit" : "subcontract:measure:query";
            case "SETTLEMENT" -> write ? "settlement:edit" : "settlement:query";
            case "EXPENSE" -> write ? "expense:edit" : "expense:query";
            case "PAYMENT" -> write ? "payment:app:edit" : "payment:app:query";
            case "INVOICE" -> write ? "invoice:edit" : "invoice:query";
            case "PURCHASE_REQUEST" -> write ? "purchase:request:edit" : "purchase:request:list";
            case "PURCHASE_ORDER" -> write ? "purchase:order:edit" : "purchase:order:query";
            case "MATERIAL_RECEIPT" -> write ? "receipt:edit" : "receipt:query";
            case "CONTRACT_REVENUE", "OWNER_SETTLEMENT", "SALES_INVOICE", "COLLECTION_RECORD" ->
                    write ? "revenue:operations:maintain" : "revenue:operations:query";
            case "PRODUCTION_MEASUREMENT" -> write ? measurementFileAuthority(documentType) : "measurement:query";
            case "OWNER_MEASUREMENT_SUBMISSION" -> write ? "measurement:owner:review" : "measurement:query";
            default -> genericAuthority;
        };
        if ("VARIATION".equals(upperType)) {
            if (!write) requireAnyAuthority(Set.of("variation:order:query", "variation:trace"));
        } else if ("BID_COST".equals(upperType)) requireBidFileAuthority(requiredAuthority);
        else if (upperType.startsWith("QS_")) requireQualityAuthority(upperType, write);
        else if (upperType.startsWith("SUPPLIER_")) requireSupplierAuthority(upperType, write);
        else if (upperType.startsWith("TECH_")) requireTechnicalAuthority(upperType, write);
        else if (upperType.startsWith("CLOSEOUT_")) requireCloseoutAuthority(upperType, write);
        else requireAuthority(requiredAuthority);
    }

    private void checkBusinessObject(String upperType, Long businessId, String action, boolean write,
                                     String documentType, String businessType) {
        switch (upperType) {
            case "PROJECT":
                if (write) {
                    PmProject project = projectMapper.selectByIdForUpdate(
                            businessId, UserContext.getCurrentTenantId());
                    if (project == null) {
                        throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "项目不存在: " + businessId);
                    }
                }
                projectAccessChecker.checkAccess(businessId, action + "项目文件");
                break;
            case "PROJECT_FILE": {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        SELECT project_id,source_kind,source_business_type,source_business_id,maintain_mode
                        FROM project_file_catalog
                        WHERE id=? AND tenant_id=? AND deleted_flag=0
                        """ + (write ? " FOR UPDATE" : ""),
                        businessId, UserContext.getCurrentTenantId());
                if (rows.size() != 1) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "项目文件不存在: " + businessId);
                }
                Map<String, Object> row = rows.getFirst();
                if (write && !("MANAGED".equals(value(row.get("source_kind")))
                        && "MANAGED".equals(value(row.get("maintain_mode"))))) {
                    throw new BusinessException("PROJECT_FILE_READ_ONLY", "业务来源文件只能在原业务模块维护");
                }
                projectAccessChecker.checkAccess(((Number) row.get("project_id")).longValue(), action + "项目文件");
                if (!write && "BUSINESS".equals(value(row.get("source_kind")))) {
                    String sourceType = value(row.get("source_business_type"));
                    Object sourceId = row.get("source_business_id");
                    if (sourceType == null || "PROJECT_FILE".equals(sourceType) || !(sourceId instanceof Number number)) {
                        throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "项目文件来源不存在");
                    }
                    requireSourceReadAuthority(sourceType);
                    checkAccess(sourceType, number.longValue(), action, false,
                            "file:query", "cashbook:journal:query");
                }
                break;
            }
            case "COMMUNICATION_MESSAGE": {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        SELECT msg.sender_id,msg.status AS message_status,msg.seq,
                               conversation.status AS conversation_status,
                               member.status AS member_status,member.join_seq,member.leave_seq,
                               (SELECT COUNT(*) FROM sys_file file
                                WHERE file.tenant_id=msg.tenant_id
                                  AND file.business_type='COMMUNICATION_MESSAGE'
                                  AND file.business_id=msg.id AND file.deleted_flag=0) AS attachment_count
                        FROM communication_message msg
                        JOIN communication_conversation conversation
                          ON conversation.tenant_id=msg.tenant_id AND conversation.id=msg.conversation_id
                        LEFT JOIN communication_member member
                          ON member.tenant_id=msg.tenant_id AND member.conversation_id=msg.conversation_id
                         AND member.user_id=? AND member.deleted_flag=0
                        WHERE msg.id=? AND msg.tenant_id=? AND msg.deleted_flag=0
                          AND conversation.deleted_flag=0
                        """ + (write ? " FOR UPDATE" : ""),
                        UserContext.getCurrentUserId(), businessId, UserContext.getCurrentTenantId());
                if (rows.size() != 1) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "通讯消息不存在");
                }
                Map<String, Object> row = rows.getFirst();
                boolean activeMember = "ACTIVE".equals(value(row.get("member_status")));
                String messageStatus = value(row.get("message_status"));
                boolean ownDraft = "DRAFT".equals(messageStatus)
                        && java.util.Objects.equals(((Number) row.get("sender_id")).longValue(),
                        UserContext.getCurrentUserId());
                long joinSeq = row.get("join_seq") instanceof Number number ? number.longValue() : Long.MAX_VALUE;
                long messageSeq = row.get("seq") instanceof Number number ? number.longValue() : -1L;
                if (!activeMember || !(ownDraft || ("SENT".equals(messageStatus) && messageSeq > joinSeq))) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "通讯消息不存在");
                }
                if (write && (!ownDraft || !"ACTIVE".equals(value(row.get("conversation_status"))))) {
                    throw new BusinessException("COMMUNICATION_MESSAGE_IMMUTABLE", "已发送消息附件不可变更");
                }
                if (write && ((Number) row.get("attachment_count")).intValue() >= 5) {
                    throw new BusinessException("COMMUNICATION_ATTACHMENT_LIMIT", "消息附件不能超过5个");
                }
                if (write && !"CHAT_ATTACHMENT".equalsIgnoreCase(documentType)) {
                    throw new BusinessException("COMMUNICATION_DOCUMENT_TYPE_INVALID", "通讯附件类型无效");
                }
                break;
            }
            case "PROJECT_COMMENCEMENT": {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        SELECT c.project_id,c.approval_status AS commencement_status,
                               p.status AS project_status,p.approval_status AS project_approval_status,
                               p.initiation_basis
                        FROM project_commencement c
                        JOIN pm_project p ON p.id=c.project_id AND p.tenant_id=c.tenant_id
                        WHERE c.id=? AND c.tenant_id=? AND c.deleted_flag=0 AND p.deleted_flag=0
                        """ + (write ? " FOR UPDATE" : ""),
                        businessId, UserContext.getCurrentTenantId());
                if (rows.size() != 1) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "开工准入单不存在: " + businessId);
                }
                Map<String, Object> row = rows.getFirst();
                projectAccessChecker.checkAccess(((Number) row.get("project_id")).longValue(),
                        action + "开工准入文件");
                if (write && !isEditableDocumentStatus(value(row.get("commencement_status")))) {
                    throw new BusinessException("PROJECT_COMMENCEMENT_DOCUMENT_IMMUTABLE",
                            "开工准入提交后附件不可变更");
                }
                if (write && !("PREPARING".equals(value(row.get("project_status")))
                        && "APPROVED".equals(value(row.get("project_approval_status")))
                        && Set.of("BID_AWARD", "DIRECT_APPROVAL")
                                .contains(value(row.get("initiation_basis"))))) {
                    throw new BusinessException("PROJECT_COMMENCEMENT_PROJECT_NOT_READY",
                            "当前项目状态不允许变更开工依据附件");
                }
                break;
            }
            case "CONTRACT": {
                CtContract contract = write
                        ? contractMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                        : contractMapper.selectById(businessId);
                if (contract == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "合同不存在: " + businessId);
                }
                if (!contract.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该合同文件");
                }
                checkProjectAccess(contract.getProjectId(), action + "合同文件");
                if (write && !isEditableDocumentStatus(contract.getApprovalStatus())) {
                    throw new BusinessException("CONTRACT_DOCUMENT_IMMUTABLE", "合同提交后附件不可变更");
                }
                break;
            }
            case "INVOICE": {
                if (write) lockInvoiceForFileMutation(businessId);
                PayInvoice invoice = invoiceMapper.selectById(businessId);
                if (invoice == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "发票不存在: " + businessId);
                }
                if (!invoice.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该发票文件");
                }
                if (write && !"PENDING".equals(invoice.getVerifyStatus())) {
                    throw new BusinessException("INVOICE_DOCUMENT_IMMUTABLE",
                            "已核验或异常发票的附件不可变更");
                }
                if (write) requireInvoiceDocumentType(documentType);
                checkProjectAccess(resolveInvoiceProjectId(invoice), action + "发票文件");
                break;
            }
            case "RECEIPT", "MATERIAL_RECEIPT": {
                MatReceipt receipt = write
                        ? receiptMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                        : receiptMapper.selectById(businessId);
                if (receipt == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "收货单不存在: " + businessId);
                }
                if (!receipt.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该收货单文件");
                }
                checkProjectAccess(receipt.getProjectId(), action + "收货单文件");
                if (write && !Set.of("DRAFT", "REJECTED").contains(receipt.getApprovalStatus())) {
                    throw new BusinessException("PROCUREMENT_DOCUMENT_IMMUTABLE",
                            "材料验收提交后附件不可变更");
                }
                break;
            }
            case "PURCHASE_REQUEST", "PURCHASE_ORDER": {
                ProcurementFileObject object = findProcurementFileObject(upperType, businessId, write);
                if (object == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "采购业务对象不存在: " + businessId);
                }
                if (write && !"DRAFT".equals(object.approvalStatus())) {
                    throw new BusinessException("PROCUREMENT_DOCUMENT_IMMUTABLE",
                            "采购单据提交后附件不可变更");
                }
                checkProjectAccess(object.projectId(), action + "采购单据文件");
                break;
            }
            case "PAYMENT": {
                PayApplication payment = write
                        ? paymentMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                        : paymentMapper.selectById(businessId);
                if (payment == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "付款申请不存在: " + businessId);
                }
                if (!payment.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该付款申请文件");
                }
                if (write && !Set.of("DRAFT", "REJECTED", "WITHDRAWN")
                        .contains(payment.getApprovalStatus())) {
                    throw new BusinessException("PAYMENT_DOCUMENT_IMMUTABLE",
                            "付款申请提交后附件不可变更");
                }
                checkProjectAccess(payment.getProjectId(), action + "付款申请文件");
                break;
            }
            case "EXPENSE": {
                ExpenseApplication expense = write
                        ? expenseApplicationMapper.selectByIdForUpdate(
                                businessId, UserContext.getCurrentTenantId())
                        : expenseApplicationMapper.selectById(businessId);
                if (expense == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "费用申请不存在: " + businessId);
                }
                if (!expense.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该费用申请文件");
                }
                checkProjectAccess(expense.getProjectId(), action + "费用申请文件");
                if (write && !isEditableDocumentStatus(expense.getApprovalStatus())) {
                    throw new BusinessException("EXPENSE_DOCUMENT_IMMUTABLE", "费用申请提交后附件不可变更");
                }
                break;
            }
            case "CONTRACT_REVENUE", "OWNER_SETTLEMENT", "SALES_INVOICE", "COLLECTION_RECORD",
                    "PRODUCTION_MEASUREMENT", "OWNER_MEASUREMENT_SUBMISSION": {
                RevenueFileObject object = findRevenueFileObject(upperType, businessId, write);
                if (object == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "收入回款业务对象不存在: " + businessId);
                }
                if (!object.tenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该收入回款业务文件");
                }
                if (write && "SALES_INVOICE".equals(upperType)) {
                    requireInvoiceDocumentType(documentType);
                }
                if (write && isRevenueFileImmutable(upperType, object.status(),
                        object.verificationStatus(), documentType)) {
                    throw new BusinessException("REVENUE_DOCUMENT_IMMUTABLE", "当前状态的收入回款业务附件不可变更");
                }
                checkProjectAccess(object.projectId(), action + "收入回款业务文件");
                break;
            }
            case "SUBCONTRACT": {
                SubMeasure subcontract = write
                        ? subcontractMapper.selectByIdForUpdate(
                                businessId, UserContext.getCurrentTenantId())
                        : subcontractMapper.selectById(businessId);
                if (subcontract == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "分包计量不存在: " + businessId);
                }
                if (!subcontract.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该分包计量文件");
                }
                checkProjectAccess(subcontract.getProjectId(), action + "分包计量文件");
                if (write && !isEditableDocumentStatus(subcontract.getApprovalStatus())) {
                    throw new BusinessException("SUB_MEASURE_DOCUMENT_IMMUTABLE", "审批中或已审批计量的附件不可变更");
                }
                break;
            }
            case "SETTLEMENT": {
                StlSettlement settlement = write
                        ? settlementMapper.selectByIdForUpdate(
                                businessId, UserContext.getCurrentTenantId())
                        : settlementMapper.selectById(businessId);
                if (settlement == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "结算单不存在: " + businessId);
                }
                if (!settlement.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该结算单文件");
                }
                if (write && !isEditableDocumentStatus(settlement.getApprovalStatus())) {
                    throw new BusinessException("SETTLEMENT_DOCUMENT_IMMUTABLE", "审批中或已定案结算的附件不可变更");
                }
                checkProjectAccess(settlement.getProjectId(), action + "结算单文件");
                break;
            }
            case "VARIATION": {
                VarOrder variation = write
                        ? variationMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                        : variationMapper.selectById(businessId);
                if (variation == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "变更单不存在: " + businessId);
                }
                if (!variation.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该变更单文件");
                }
                checkProjectAccess(variation.getProjectId(), action + "变更单文件");
                break;
            }
            case "BID_COST": {
                BidCost bidCost = bidCostMapper.selectById(businessId);
                if (bidCost == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "投标记录不存在: " + businessId);
                }
                if (!bidCost.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该投标文件");
                }
                if (bidCost.getProjectId() != null) {
                    checkProjectAccess(bidCost.getProjectId(), action + "投标文件");
                }
                break;
            }
            case "PARTNER": {
                MdPartner partner = write
                        ? partnerMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                        : partnerMapper.selectById(businessId);
                if (partner == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "合作方不存在: " + businessId);
                }
                if (!partner.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该合作方文件");
                }
                break;
            }
            case "MATERIAL": {
                MdMaterial material = materialMapper.selectById(businessId);
                if (material == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "物料不存在: " + businessId);
                }
                if (!material.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该物料文件");
                }
                break;
            }
            case "CASH_JOURNAL": {
                CashJournalEntry entry = write
                        ? cashJournalEntryMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                        : cashJournalEntryMapper.selectById(businessId);
                if (entry == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "资金流水不存在: " + businessId);
                }
                if (!entry.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该资金流水文件");
                }
                if (write && !Set.of(CashbookConstants.Status.DRAFT, CashbookConstants.Status.PENDING_ARCHIVE)
                        .contains(entry.getStatus())) {
                    throw new BusinessException("CASH_JOURNAL_ARCHIVED_IMMUTABLE", "归档或红冲流水的附件不可变更");
                }
                if (entry.getProjectId() != null) {
                    projectAccessChecker.checkAccess(entry.getProjectId(), action + "资金流水文件");
                }
                break;
            }
            case "SITE_DAILY_LOG": {
                SiteDailyLog dailyLog = write
                        ? siteDailyLogMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                        : siteDailyLogMapper.selectById(businessId);
                if (dailyLog == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "现场日报不存在: " + businessId);
                }
                if (!dailyLog.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该现场日报文件");
                }
                if (write && "SUBMITTED".equals(dailyLog.getStatus())) {
                    throw new BusinessException("SITE_DAILY_LOG_SUBMITTED_IMMUTABLE", "已提交日报的附件不可变更");
                }
                projectAccessChecker.checkAccess(dailyLog.getProjectId(), action + "现场日报文件");
                break;
            }
            case "QS_INSPECTION", "QS_ISSUE", "QS_RECTIFICATION": {
                QualityFileObject object = findQualityFileObject(upperType, businessId, false);
                if (object == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "质量安全业务对象不存在: " + businessId);
                }
                if (!object.tenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该质量安全业务文件");
                }
                checkProjectAccess(object.projectId(), action + "质量安全业务文件");
                break;
            }
            case "SUPPLIER_SOURCING", "SUPPLIER_QUOTE": {
                SupplierFileObject object = findSupplierFileObject(upperType, businessId, false);
                if (object == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "供应商招采业务对象不存在: " + businessId);
                }
                if (!object.tenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该供应商招采文件");
                }
                checkProjectAccess(object.projectId(), action + "供应商招采文件");
                break;
            }
            case "TECH_SCHEME", "TECH_DRAWING_VERSION", "TECH_DRAWING_REVIEW", "TECH_RFI",
                    "TECH_RFI_RESPONSE", "TECH_DISCLOSURE", "TECH_ARCHIVE": {
                TechnicalFileObject object = findTechnicalFileObject(upperType, businessId, false);
                if (object == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "技术管理业务对象不存在: " + businessId);
                }
                if (!object.tenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该技术管理文件");
                }
                checkProjectAccess(object.projectId(), action + "技术管理文件");
                break;
            }
            case "CLOSEOUT_SECTION_ACCEPTANCE", "CLOSEOUT_FINAL_ACCEPTANCE", "CLOSEOUT_DEFECT",
                    "CLOSEOUT_WARRANTY", "CLOSEOUT_ARCHIVE_TRANSFER": {
                CloseoutFileObject object = findCloseoutFileObject(upperType, businessId, false);
                if (object == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "项目收尾业务对象不存在: " + businessId);
                }
                if (!object.tenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该项目收尾文件");
                }
                checkProjectAccess(object.projectId(), action + "项目收尾文件");
                break;
            }
            default:
                throw new BusinessException("FILE_BIZ_TYPE_UNKNOWN",
                        "不支持的业务类型: " + businessType);
        }
    }

    private String measurementFileAuthority(String documentType) {
        String type = documentType == null ? "" : documentType.toUpperCase();
        if ("OWNER_SUBMISSION".equals(type)) return "measurement:owner:submit";
        if ("MEASUREMENT_GENERAL".equals(type) || type.startsWith("ML_")) return "measurement:submit";
        throw new BusinessException("MEASUREMENT_DOCUMENT_STAGE_INVALID", "不支持的产值计量附件类型");
    }

    private boolean isEditableDocumentStatus(String approvalStatus) {
        return "DRAFT".equals(approvalStatus) || "REJECTED".equals(approvalStatus);
    }

    private void checkProjectAccess(Long projectId, String action) {
        if (projectId == null) {
            throw new BusinessException("FILE_ACCESS_DENIED", "业务对象缺少项目关系，拒绝访问文件");
        }
        projectAccessChecker.checkAccess(projectId, action);
    }

    private void lockInvoiceForFileMutation(Long businessId) {
        try {
            jdbcTemplate.queryForObject("""
                    SELECT id
                    FROM pay_invoice
                    WHERE id=? AND tenant_id=? AND deleted_flag=0
                    FOR UPDATE
                    """, Long.class, businessId, UserContext.getCurrentTenantId());
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "发票不存在: " + businessId);
        }
    }

    private void requireInvoiceDocumentType(String documentType) {
        String type = documentType == null ? "" : documentType.trim().toUpperCase();
        if (!Set.of("ELECTRONIC_INVOICE", "SCANNED_INVOICE").contains(type)) {
            throw new BusinessException("INVOICE_DOCUMENT_TYPE_INVALID",
                    "发票附件仅支持电子发票或扫描件");
        }
    }

    private RevenueFileObject findRevenueFileObject(String businessType, Long businessId, boolean write) {
        String table = switch (businessType) {
            case "CONTRACT_REVENUE" -> "contract_revenue";
            case "OWNER_SETTLEMENT" -> "owner_settlement";
            case "SALES_INVOICE" -> "sales_invoice";
            case "COLLECTION_RECORD" -> "collection_record";
            case "PRODUCTION_MEASUREMENT" -> "production_measurement";
            case "OWNER_MEASUREMENT_SUBMISSION" -> "owner_measurement_submission";
            default -> throw new IllegalArgumentException("Unsupported revenue file type");
        };
        String statusColumn = "CONTRACT_REVENUE".equals(businessType) ? "approval_status" : "status";
        String verificationColumn = "SALES_INVOICE".equals(businessType) ? ",verification_status" : "";
        boolean lockForEvidenceMutation = write
                && Set.of("CONTRACT_REVENUE", "OWNER_SETTLEMENT", "SALES_INVOICE", "COLLECTION_RECORD",
                        "PRODUCTION_MEASUREMENT", "OWNER_MEASUREMENT_SUBMISSION")
                .contains(businessType);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT tenant_id,project_id," + statusColumn + verificationColumn + " FROM " + table
                        + " WHERE id=? AND tenant_id=? AND deleted_flag=0"
                        + (lockForEvidenceMutation ? " FOR UPDATE" : ""),
                businessId, UserContext.getCurrentTenantId());
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        return new RevenueFileObject(
                ((Number) row.get("tenant_id")).longValue(),
                ((Number) row.get("project_id")).longValue(),
                value(row.get(statusColumn)),
                value(row.get("verification_status")));
    }

    private boolean isRevenueFileImmutable(String businessType, String status,
                                           String verificationStatus, String documentType) {
        String type = documentType == null ? "" : documentType.toUpperCase();
        if ("PRODUCTION_MEASUREMENT".equals(businessType) && "OWNER_SUBMISSION".equals(type)) {
            return !Set.of("INTERNAL_APPROVED", "OWNER_RETURNED").contains(status);
        }
        if ("OWNER_MEASUREMENT_SUBMISSION".equals(businessType) && "OWNER_CONFIRMATION".equals(type)) {
            return !"SUBMITTED".equals(status);
        }
        return switch (businessType) {
            case "CONTRACT_REVENUE", "OWNER_SETTLEMENT", "PRODUCTION_MEASUREMENT" -> !Set.of("DRAFT", "REJECTED").contains(status);
            case "OWNER_MEASUREMENT_SUBMISSION" -> !"SUBMITTED".equals(status);
            case "SALES_INVOICE", "COLLECTION_RECORD" -> !"PENDING_EVIDENCE".equals(status);
            default -> true;
        };
    }

    private String value(Object value) {
        return value == null ? null : value.toString();
    }

    private record RevenueFileObject(Long tenantId, Long projectId, String status,
                                     String verificationStatus) {}

    private ProcurementFileObject findProcurementFileObject(
            String businessType, Long businessId, boolean write) {
        String table = "PURCHASE_REQUEST".equals(businessType)
                ? "mat_purchase_request" : "mat_purchase_order";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT project_id,approval_status FROM " + table
                        + " WHERE id=? AND tenant_id=? AND deleted_flag=0"
                        + (write ? " FOR UPDATE" : ""),
                businessId, UserContext.getCurrentTenantId());
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        return new ProcurementFileObject(
                ((Number) row.get("project_id")).longValue(),
                String.valueOf(row.get("approval_status")));
    }

    private record ProcurementFileObject(Long projectId, String approvalStatus) {}

    private void checkQualityDocumentStage(String businessType, Long businessId, String documentType) {
        QualityFileObject object = findQualityFileObject(businessType, businessId, true);
        if (object == null || !object.tenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "质量安全业务对象不存在: " + businessId);
        String type = documentType == null ? "" : documentType.trim().toUpperCase();
        requireQualityDocumentAuthority(businessType, type);
        if ("QS_RECTIFICATION".equals(businessType)
                && "REINSPECTION_EVIDENCE".equals(type)
                && object.responsibleUserId().equals(UserContext.getCurrentUserId())) {
            throw new BusinessException("QS_REINSPECTION_SEGREGATION_REQUIRED",
                    "整改责任人不能复验本人提交的整改");
        }
        boolean allowed = switch (businessType) {
            case "QS_INSPECTION" -> "DRAFT".equals(object.status()) && "INSPECTION_EVIDENCE".equals(type);
            case "QS_ISSUE" -> "DRAFT".equals(object.status()) && "ISSUE_EVIDENCE".equals(type);
            case "QS_RECTIFICATION" -> ("DRAFT".equals(object.status()) && "RECTIFICATION_EVIDENCE".equals(type))
                    || ("SUBMITTED".equals(object.status()) && "REINSPECTION_EVIDENCE".equals(type));
            default -> false;
        };
        if (!allowed) throw new BusinessException("QS_DOCUMENT_STAGE_INVALID", "当前业务阶段不允许变更该类质量安全证据");
    }

    private void requireQualityDocumentAuthority(String businessType, String documentType) {
        String authority = switch (businessType) {
            case "QS_INSPECTION", "QS_ISSUE" -> "quality:safety:inspection:maintain";
            case "QS_RECTIFICATION" -> switch (documentType) {
                case "RECTIFICATION_EVIDENCE" -> "quality:safety:rectify";
                case "REINSPECTION_EVIDENCE" -> "quality:safety:reinspect";
                default -> null;
            };
            default -> null;
        };
        if (authority != null) requireAuthority(authority);
    }

    private QualityFileObject findQualityFileObject(String businessType, Long businessId, boolean write) {
        String sql = switch (businessType) {
            case "QS_INSPECTION" -> "SELECT tenant_id,project_id,status,NULL AS responsible_user_id FROM qs_inspection_record WHERE id=? AND deleted_flag=0";
            case "QS_ISSUE" -> "SELECT i.tenant_id,i.project_id,r.status,NULL AS responsible_user_id FROM qs_issue i JOIN qs_inspection_record r ON r.id=i.inspection_id WHERE i.id=? AND i.deleted_flag=0 AND r.deleted_flag=0";
            case "QS_RECTIFICATION" -> "SELECT tenant_id,project_id,status,responsible_user_id FROM qs_rectification WHERE id=? AND deleted_flag=0";
            default -> throw new IllegalArgumentException("Unsupported quality file type");
        };
        try {
            return jdbcTemplate.queryForObject(sql + (write ? " FOR UPDATE" : ""),
                    (rs, rowNum) -> new QualityFileObject(
                            rs.getLong("tenant_id"),
                            rs.getLong("project_id"),
                            rs.getString("status"),
                            rs.getObject("responsible_user_id", Long.class)),
                    businessId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private record QualityFileObject(Long tenantId, Long projectId, String status,
                                     Long responsibleUserId) {}

    private void checkSupplierDocumentStage(String businessType, Long businessId, String documentType) {
        SupplierFileObject object = findSupplierFileObject(businessType, businessId, true);
        if (object == null || !object.tenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "供应商招采业务对象不存在: " + businessId);
        String type = documentType == null ? "" : documentType.trim().toUpperCase();
        boolean allowed = switch (businessType) {
            case "SUPPLIER_SOURCING" -> "DRAFT".equals(object.status()) && "SOURCING_REQUIREMENT".equals(type);
            case "SUPPLIER_QUOTE" -> "DRAFT".equals(object.status()) && "QUOTE_ATTACHMENT".equals(type);
            default -> false;
        };
        if (!allowed) throw new BusinessException("SP_DOCUMENT_STAGE_INVALID", "当前业务阶段不允许变更该类招采附件");
    }

    private SupplierFileObject findSupplierFileObject(String businessType, Long businessId, boolean write) {
        String sql = switch (businessType) {
            case "SUPPLIER_SOURCING" -> "SELECT tenant_id,project_id,status FROM sp_sourcing_event WHERE id=? AND deleted_flag=0";
            case "SUPPLIER_QUOTE" -> "SELECT q.tenant_id,e.project_id,q.status FROM sp_supplier_quote q JOIN sp_sourcing_event e ON e.id=q.sourcing_event_id WHERE q.id=? AND q.deleted_flag=0 AND e.deleted_flag=0";
            default -> throw new IllegalArgumentException("Unsupported supplier file type");
        };
        try {
            return jdbcTemplate.queryForObject(sql + (write ? " FOR UPDATE" : ""),
                    (rs, rowNum) -> new SupplierFileObject(rs.getLong("tenant_id"), rs.getLong("project_id"), rs.getString("status")),
                    businessId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private record SupplierFileObject(Long tenantId, Long projectId, String status) {}

    private void checkTechnicalDocumentStage(String businessType, Long businessId, String documentType) {
        TechnicalFileObject object = findTechnicalFileObject(businessType, businessId, true);
        if (object == null || !object.tenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "技术管理业务对象不存在: " + businessId);
        String type = documentType == null ? "" : documentType.trim().toUpperCase();
        boolean allowed = switch (businessType) {
            case "TECH_SCHEME" -> Set.of("DRAFT", "REJECTED").contains(object.status()) && "SCHEME_FILE".equals(type);
            case "TECH_DRAWING_VERSION" -> "RECEIVED".equals(object.status()) && "DRAWING_FILE".equals(type);
            case "TECH_DRAWING_REVIEW" -> "DRAFT".equals(object.status()) && "REVIEW_MINUTES".equals(type);
            case "TECH_RFI" -> "DRAFT".equals(object.status()) && "RFI_EVIDENCE".equals(type);
            case "TECH_RFI_RESPONSE" -> "SUBMITTED".equals(object.status()) && "DESIGN_RESPONSE".equals(type);
            case "TECH_DISCLOSURE" -> "DRAFT".equals(object.status()) && "DISCLOSURE_RECORD".equals(type);
            case "TECH_ARCHIVE" -> "DRAFT".equals(object.status()) && "ACCEPTANCE_ARCHIVE".equals(type);
            default -> false;
        };
        if (!allowed) throw new BusinessException("TECH_DOCUMENT_STAGE_INVALID", "当前业务阶段不允许变更该类技术文件");
    }

    private TechnicalFileObject findTechnicalFileObject(String businessType, Long businessId, boolean write) {
        String sql = switch (businessType) {
            case "TECH_SCHEME" -> "SELECT tenant_id,project_id,status FROM technical_scheme WHERE id=? AND deleted_flag=0";
            case "TECH_DRAWING_VERSION" -> "SELECT tenant_id,project_id,status FROM tech_drawing_version WHERE id=? AND deleted_flag=0";
            case "TECH_DRAWING_REVIEW" -> "SELECT tenant_id,project_id,status FROM tech_drawing_review WHERE id=? AND deleted_flag=0";
            case "TECH_RFI" -> "SELECT tenant_id,project_id,status FROM tech_rfi WHERE id=? AND deleted_flag=0";
            case "TECH_RFI_RESPONSE" -> "SELECT p.tenant_id,r.project_id,p.status FROM tech_rfi_response p JOIN tech_rfi r ON r.id=p.rfi_id WHERE p.id=? AND r.deleted_flag=0";
            case "TECH_DISCLOSURE" -> "SELECT tenant_id,project_id,status FROM tech_disclosure WHERE id=? AND deleted_flag=0";
            case "TECH_ARCHIVE" -> "SELECT tenant_id,project_id,status FROM tech_acceptance_archive WHERE id=? AND deleted_flag=0";
            default -> throw new IllegalArgumentException("Unsupported technical file type");
        };
        try {
            return jdbcTemplate.queryForObject(sql + (write ? " FOR UPDATE" : ""),
                    (rs, rowNum) -> new TechnicalFileObject(rs.getLong("tenant_id"), rs.getLong("project_id"), rs.getString("status")),
                    businessId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private record TechnicalFileObject(Long tenantId, Long projectId, String status) {}

    private void checkCloseoutDocumentStage(String businessType, Long businessId, String documentType) {
        CloseoutFileObject object = findCloseoutFileObject(businessType, businessId, true);
        if (object == null || !object.tenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "项目收尾业务对象不存在: " + businessId);
        String type = documentType == null ? "" : documentType.trim().toUpperCase();
        boolean allowed = switch (businessType) {
            case "CLOSEOUT_SECTION_ACCEPTANCE" -> "DRAFT".equals(object.status()) && "SECTION_ACCEPTANCE_RECORD".equals(type);
            case "CLOSEOUT_FINAL_ACCEPTANCE" -> Set.of("DRAFT", "REJECTED").contains(object.status()) && "FINAL_ACCEPTANCE_CERTIFICATE".equals(type);
            case "CLOSEOUT_DEFECT" -> "OPEN".equals(object.status()) && "DEFECT_RECTIFICATION_EVIDENCE".equals(type);
            case "CLOSEOUT_WARRANTY" -> Set.of("ACTIVE", "DEFECT_LIABILITY").contains(object.status()) && "WARRANTY_RELEASE_VOUCHER".equals(type);
            case "CLOSEOUT_ARCHIVE_TRANSFER" -> "DRAFT".equals(object.status()) && "ARCHIVE_TRANSFER_LIST".equals(type);
            default -> false;
        };
        if (!allowed) throw new BusinessException("CLOSEOUT_DOCUMENT_STAGE_INVALID", "当前收尾阶段不允许变更该类证据");
    }

    private CloseoutFileObject findCloseoutFileObject(String businessType, Long businessId, boolean write) {
        String table = switch (businessType) {
            case "CLOSEOUT_SECTION_ACCEPTANCE" -> "closeout_section_acceptance";
            case "CLOSEOUT_FINAL_ACCEPTANCE" -> "closeout_final_acceptance";
            case "CLOSEOUT_DEFECT" -> "closeout_defect";
            case "CLOSEOUT_WARRANTY" -> "closeout_warranty";
            case "CLOSEOUT_ARCHIVE_TRANSFER" -> "closeout_archive_transfer";
            default -> throw new IllegalArgumentException("Unsupported closeout file type");
        };
        try {
            return jdbcTemplate.queryForObject("SELECT tenant_id,project_id,status FROM " + table
                            + " WHERE id=? AND deleted_flag=0" + (write ? " FOR UPDATE" : ""),
                    (rs, rowNum) -> new CloseoutFileObject(rs.getLong("tenant_id"), rs.getLong("project_id"), rs.getString("status")),
                    businessId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private record CloseoutFileObject(Long tenantId, Long projectId, String status) {}

    private void requireQualityAuthority(String businessType, boolean write) {
        if (!write) {
            requireAuthority("quality:safety:query");
            return;
        }
        Set<String> allowed = switch (businessType) {
            case "QS_INSPECTION", "QS_ISSUE" -> Set.of("quality:safety:inspection:maintain");
            case "QS_RECTIFICATION" -> Set.of("quality:safety:rectify", "quality:safety:reinspect");
            default -> Set.of();
        };
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean permitted = authentication != null && authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> allowed.contains(authority)
                        || "ROLE_ADMIN".equals(authority)
                        || "ROLE_SUPER_ADMIN".equals(authority));
        if (!permitted) throw new BusinessException("FILE_ACCESS_DENIED", "无权执行该质量安全文件操作");
    }

    private void requireSupplierAuthority(String businessType, boolean write) {
        Set<String> allowed = !write ? Set.of("supplier:sourcing:query")
                : "SUPPLIER_QUOTE".equals(businessType)
                ? Set.of("supplier:sourcing:quote") : Set.of("supplier:sourcing:maintain");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean permitted = authentication != null && authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> allowed.contains(authority)
                        || "ROLE_ADMIN".equals(authority)
                        || "ROLE_SUPER_ADMIN".equals(authority));
        if (!permitted) throw new BusinessException("FILE_ACCESS_DENIED", "无权执行该供应商招采文件操作");
    }

    private void requireTechnicalAuthority(String businessType, boolean write) {
        Set<String> allowed = !write ? Set.of("technical:query") : switch (businessType) {
            case "TECH_SCHEME" -> Set.of("technical:scheme:maintain", "technical:scheme:submit");
            case "TECH_DRAWING_VERSION" -> Set.of("technical:drawing:receive");
            case "TECH_DRAWING_REVIEW" -> Set.of("technical:drawing:review");
            case "TECH_RFI" -> Set.of("technical:rfi:raise");
            case "TECH_RFI_RESPONSE" -> Set.of("technical:rfi:respond", "technical:rfi:accept");
            case "TECH_DISCLOSURE" -> Set.of("technical:disclosure:maintain");
            case "TECH_ARCHIVE" -> Set.of("technical:archive:confirm");
            default -> Set.of();
        };
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean permitted = authentication != null && authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> allowed.contains(authority)
                        || "ROLE_ADMIN".equals(authority)
                        || "ROLE_SUPER_ADMIN".equals(authority));
        if (!permitted) throw new BusinessException("FILE_ACCESS_DENIED", "无权执行该技术管理文件操作");
    }

    private void requireCloseoutAuthority(String businessType, boolean write) {
        Set<String> allowed = !write ? Set.of("closeout:query") : switch (businessType) {
            case "CLOSEOUT_SECTION_ACCEPTANCE" -> Set.of("closeout:section:maintain");
            case "CLOSEOUT_FINAL_ACCEPTANCE" -> Set.of("closeout:acceptance:submit");
            case "CLOSEOUT_DEFECT" -> Set.of("closeout:defect:maintain", "closeout:defect:verify");
            case "CLOSEOUT_WARRANTY" -> Set.of("closeout:warranty:maintain");
            case "CLOSEOUT_ARCHIVE_TRANSFER" -> Set.of("closeout:archive:maintain");
            default -> Set.of();
        };
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean permitted = authentication != null && authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> allowed.contains(authority)
                        || "ROLE_ADMIN".equals(authority)
                        || "ROLE_SUPER_ADMIN".equals(authority));
        if (!permitted) throw new BusinessException("FILE_ACCESS_DENIED", "无权执行该项目收尾文件操作");
    }

    private void requireAuthority(String requiredAuthority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = authentication != null && authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> requiredAuthority.equals(authority)
                        || "ROLE_ADMIN".equals(authority)
                        || "ROLE_SUPER_ADMIN".equals(authority));
        if (!allowed) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权执行该文件操作");
        }
    }

    private void requireSourceReadAuthority(String businessType) {
        String authority = switch (businessType) {
            case "PROJECT" -> "project:query";
            case "PROJECT_COMMENCEMENT" -> "project:commencement:query";
            case "CONTRACT" -> "contract:query";
            case "INVOICE" -> "invoice:query";
            case "RECEIPT", "MATERIAL_RECEIPT" -> "receipt:query";
            case "PURCHASE_REQUEST" -> "purchase:request:list";
            case "PURCHASE_ORDER" -> "purchase:order:query";
            case "PAYMENT" -> "payment:app:query";
            case "SUBCONTRACT" -> "subcontract:measure:query";
            case "SETTLEMENT" -> "settlement:query";
            case "BID_COST" -> "bid:query";
            case "SITE_DAILY_LOG" -> "site:daily:query";
            case "EXPENSE" -> "expense:query";
            case "CASH_JOURNAL" -> "cashbook:journal:query";
            case "CONTRACT_REVENUE", "OWNER_SETTLEMENT", "SALES_INVOICE", "COLLECTION_RECORD" ->
                    "revenue:operations:query";
            case "PRODUCTION_MEASUREMENT", "OWNER_MEASUREMENT_SUBMISSION" -> "measurement:query";
            default -> null;
        };
        if (authority != null) requireAuthority(authority);
    }

    private void requireBidFileAuthority(String requiredAuthority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = authentication != null && authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> requiredAuthority.equals(authority)
                        || "ROLE_SUPER_ADMIN".equals(authority));
        if (!allowed) throw new BusinessException("FILE_ACCESS_DENIED", "无权执行投标文件操作");
    }

    private void requireAnyAuthority(Set<String> requiredAuthorities) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = authentication != null && authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> requiredAuthorities.contains(authority)
                        || "ROLE_ADMIN".equals(authority)
                        || "ROLE_SUPER_ADMIN".equals(authority));
        if (!allowed) throw new BusinessException("FILE_ACCESS_DENIED", "无权执行该文件操作");
    }

    private Long resolveInvoiceProjectId(PayInvoice invoice) {
        if (invoice.getPayRecordId() != null) {
            PayRecord record = payRecordMapper.selectById(invoice.getPayRecordId());
            if (record != null && record.getTenantId().equals(UserContext.getCurrentTenantId())) {
                if (record.getProjectId() != null) {
                    return record.getProjectId();
                }
                if (record.getPayApplicationId() != null) {
                    PayApplication app = paymentMapper.selectById(record.getPayApplicationId());
                    if (app != null && app.getTenantId().equals(UserContext.getCurrentTenantId())) {
                        return app.getProjectId();
                    }
                }
            }
        }
        if (invoice.getPayApplicationId() != null) {
            PayApplication app = paymentMapper.selectById(invoice.getPayApplicationId());
            if (app != null && app.getTenantId().equals(UserContext.getCurrentTenantId())) {
                return app.getProjectId();
            }
        }
        return null;
    }
}
