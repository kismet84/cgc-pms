package com.cgcpms.file.auth;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.site.entity.SiteDailyLog;
import com.cgcpms.site.mapper.SiteDailyLogMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class ProjectCollaborationFileAccessPolicy implements FileAccessPolicy {

    @FunctionalInterface
    interface SourceAccessChecker {
        void check(String businessType, Long businessId, String action);
    }

    private final ProjectAccessChecker projectAccessChecker;
    private final PmProjectMapper projectMapper;
    private final SiteDailyLogMapper siteDailyLogMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SourceAccessChecker sourceAccessChecker;

    ProjectCollaborationFileAccessPolicy(ProjectAccessChecker projectAccessChecker,
                                         PmProjectMapper projectMapper,
                                         SiteDailyLogMapper siteDailyLogMapper,
                                         JdbcTemplate jdbcTemplate,
                                         SourceAccessChecker sourceAccessChecker) {
        this.projectAccessChecker = projectAccessChecker;
        this.projectMapper = projectMapper;
        this.siteDailyLogMapper = siteDailyLogMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.sourceAccessChecker = sourceAccessChecker;
    }

    @Override
    public FileAccessPolicyRegistry.Group group() {
        return FileAccessPolicyRegistry.Group.PROJECT_COLLABORATION;
    }

    @Override
    public void checkObject(FileAccessPolicyRegistry.BusinessType businessType,
                            Long businessId,
                            String action,
                            boolean write,
                            String documentType) {
        switch (businessType) {
            case PROJECT -> checkProject(businessId, action, write);
            case PROJECT_FILE -> checkProjectFile(businessId, action, write);
            case PROJECT_COMMENCEMENT -> checkProjectCommencement(businessId, action, write);
            case COMMUNICATION_MESSAGE -> checkCommunicationMessage(businessId, write, documentType);
            case SITE_DAILY_LOG -> checkSiteDailyLog(businessId, action, write);
            default -> throw new IllegalArgumentException("Unsupported project collaboration file type");
        }
    }

    private void checkProject(Long businessId, String action, boolean write) {
        if (write) {
            PmProject project = projectMapper.selectByIdForUpdate(
                    businessId, UserContext.getCurrentTenantId());
            if (project == null) {
                throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "项目不存在: " + businessId);
            }
        }
        projectAccessChecker.checkAccess(businessId, action + "项目文件");
    }

    private void checkProjectFile(Long businessId, String action, boolean write) {
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
        if (write && !("MANAGED".equals(FilePolicySupport.value(row.get("source_kind")))
                && "MANAGED".equals(FilePolicySupport.value(row.get("maintain_mode"))))) {
            throw new BusinessException("PROJECT_FILE_READ_ONLY", "业务来源文件只能在原业务模块维护");
        }
        projectAccessChecker.checkAccess(((Number) row.get("project_id")).longValue(), action + "项目文件");
        if (!write && "BUSINESS".equals(FilePolicySupport.value(row.get("source_kind")))) {
            String sourceType = FilePolicySupport.value(row.get("source_business_type"));
            Object sourceId = row.get("source_business_id");
            if (sourceType == null || "PROJECT_FILE".equals(sourceType) || !(sourceId instanceof Number number)) {
                throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "项目文件来源不存在");
            }
            sourceAccessChecker.check(sourceType, number.longValue(), action);
        }
    }

    private void checkCommunicationMessage(Long businessId, boolean write, String documentType) {
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
        boolean activeMember = "ACTIVE".equals(FilePolicySupport.value(row.get("member_status")));
        String messageStatus = FilePolicySupport.value(row.get("message_status"));
        boolean ownDraft = "DRAFT".equals(messageStatus)
                && Objects.equals(((Number) row.get("sender_id")).longValue(),
                UserContext.getCurrentUserId());
        long joinSeq = row.get("join_seq") instanceof Number number ? number.longValue() : Long.MAX_VALUE;
        long messageSeq = row.get("seq") instanceof Number number ? number.longValue() : -1L;
        if (!activeMember || !(ownDraft || ("SENT".equals(messageStatus) && messageSeq > joinSeq))) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "通讯消息不存在");
        }
        if (write && (!ownDraft
                || !"ACTIVE".equals(FilePolicySupport.value(row.get("conversation_status"))))) {
            throw new BusinessException("COMMUNICATION_MESSAGE_IMMUTABLE", "已发送消息附件不可变更");
        }
        if (write && ((Number) row.get("attachment_count")).intValue() >= 5) {
            throw new BusinessException("COMMUNICATION_ATTACHMENT_LIMIT", "消息附件不能超过5个");
        }
        if (write && !"CHAT_ATTACHMENT".equalsIgnoreCase(documentType)) {
            throw new BusinessException("COMMUNICATION_DOCUMENT_TYPE_INVALID", "通讯附件类型无效");
        }
    }

    private void checkProjectCommencement(Long businessId, String action, boolean write) {
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
        if (write && !FilePolicySupport.isEditableDocumentStatus(
                FilePolicySupport.value(row.get("commencement_status")))) {
            throw new BusinessException("PROJECT_COMMENCEMENT_DOCUMENT_IMMUTABLE",
                    "开工准入提交后附件不可变更");
        }
        if (write && !("PREPARING".equals(FilePolicySupport.value(row.get("project_status")))
                && "APPROVED".equals(FilePolicySupport.value(row.get("project_approval_status")))
                && Set.of("BID_AWARD", "DIRECT_APPROVAL")
                .contains(FilePolicySupport.value(row.get("initiation_basis"))))) {
            throw new BusinessException("PROJECT_COMMENCEMENT_PROJECT_NOT_READY",
                    "当前项目状态不允许变更开工依据附件");
        }
    }

    private void checkSiteDailyLog(Long businessId, String action, boolean write) {
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
    }
}
