package com.cgcpms.site.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.site.entity.SiteDailyLog;
import com.cgcpms.site.mapper.SiteDailyLogMapper;
import com.cgcpms.site.vo.SiteDailyLogVO;
import com.cgcpms.site.vo.SiteDailyDeliveryVO;
import com.cgcpms.site.vo.SiteDailyPlannedTaskVO;
import com.cgcpms.subcontract.entity.SubTask;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SiteDailyLogService {
    private static final String DRAFT = "DRAFT";
    private static final String SUBMITTED = "SUBMITTED";

    private final SiteDailyLogMapper mapper;
    private final PmProjectMapper projectMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final MatReceiptMapper receiptMapper;
    private final MatReceiptItemMapper receiptItemMapper;
    private final MdMaterialMapper materialMapper;
    private final MdPartnerMapper partnerMapper;
    private final SubTaskMapper subTaskMapper;

    public IPage<SiteDailyLogVO> getPage(long pageNo, long pageSize, Long projectId,
                                         LocalDate startDate, LocalDate endDate, String status) {
        LambdaQueryWrapper<SiteDailyLog> query = new LambdaQueryWrapper<SiteDailyLog>()
                .eq(SiteDailyLog::getTenantId, UserContext.getCurrentTenantId());
        if (projectId != null) {
            projectAccessChecker.checkAccess(projectId, "查询现场日报");
            query.eq(SiteDailyLog::getProjectId, projectId);
        } else {
            List<Long> visibleIds = projectAccessChecker.filterAccessible(projectMapper.selectList(
                            new LambdaQueryWrapper<PmProject>().eq(PmProject::getTenantId, UserContext.getCurrentTenantId())))
                    .stream().map(PmProject::getId).toList();
            if (visibleIds.isEmpty()) query.apply("1 = 0");
            else query.in(SiteDailyLog::getProjectId, visibleIds);
        }
        if (startDate != null) query.ge(SiteDailyLog::getReportDate, startDate);
        if (endDate != null) query.le(SiteDailyLog::getReportDate, endDate);
        if (StringUtils.hasText(status)) {
            requireStatus(status);
            query.eq(SiteDailyLog::getStatus, status);
        }
        query.orderByDesc(SiteDailyLog::getReportDate).orderByDesc(SiteDailyLog::getCreatedAt);
        Page<SiteDailyLog> page = mapper.selectPage(new Page<>(pageNo, pageSize), query);
        Set<Long> projectIds = page.getRecords().stream().map(SiteDailyLog::getProjectId).collect(Collectors.toSet());
        Map<Long, String> names = projectIds.isEmpty() ? Map.of() : projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        return page.convert(log -> toVO(log, names.get(log.getProjectId())));
    }

    public SiteDailyLogVO getById(Long id) {
        SiteDailyLog log = requireLog(id);
        projectAccessChecker.checkAccess(log.getProjectId(), "访问现场日报");
        PmProject project = projectMapper.selectById(log.getProjectId());
        SiteDailyLogVO detail = toVO(log, project == null ? null : project.getProjectName());
        detail.setDeliveries(loadDeliveries(log));
        detail.setPlannedTasks(loadPlannedTasks(log));
        return detail;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SiteDailyLog log) {
        projectAccessChecker.checkAccess(log.getProjectId(), "创建现场日报");
        log.setTenantId(UserContext.getCurrentTenantId());
        log.setStatus(DRAFT);
        log.setSubmittedBy(null);
        log.setSubmittedAt(null);
        requireUniqueDate(log.getProjectId(), log.getReportDate(), null);
        try {
            mapper.insert(log);
        } catch (DuplicateKeyException e) {
            throw duplicateDate();
        }
        return log.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SiteDailyLog command) {
        SiteDailyLog existing = requireLog(command.getId());
        projectAccessChecker.checkAccess(existing.getProjectId(), "修改现场日报");
        projectAccessChecker.checkAccess(command.getProjectId(), "修改现场日报");
        requireDraft(existing);
        requireUniqueDate(command.getProjectId(), command.getReportDate(), existing.getId());
        try {
            int updated = mapper.update(null, new LambdaUpdateWrapper<SiteDailyLog>()
                    .eq(SiteDailyLog::getId, existing.getId())
                    .eq(SiteDailyLog::getTenantId, existing.getTenantId())
                    .eq(SiteDailyLog::getStatus, DRAFT)
                    .set(SiteDailyLog::getProjectId, command.getProjectId())
                    .set(SiteDailyLog::getReportDate, command.getReportDate())
                    .set(SiteDailyLog::getConstructionContent, command.getConstructionContent())
                    .set(SiteDailyLog::getIssuesDelays, command.getIssuesDelays())
                    .set(SiteDailyLog::getNextDayPlan, command.getNextDayPlan())
                    .set(SiteDailyLog::getWeatherSummary, command.getWeatherSummary())
                    .set(SiteDailyLog::getOnSiteHeadcount, command.getOnSiteHeadcount()));
            if (updated != 1) throw submittedImmutable();
        } catch (DuplicateKeyException e) {
            throw duplicateDate();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        SiteDailyLog log = requireLog(id);
        projectAccessChecker.checkAccess(log.getProjectId(), "提交现场日报");
        int updated = mapper.update(null, new LambdaUpdateWrapper<SiteDailyLog>()
                .eq(SiteDailyLog::getId, id)
                .eq(SiteDailyLog::getTenantId, log.getTenantId())
                .eq(SiteDailyLog::getStatus, DRAFT)
                .set(SiteDailyLog::getStatus, SUBMITTED)
                .set(SiteDailyLog::getSubmittedBy, UserContext.getCurrentUserId())
                .set(SiteDailyLog::getSubmittedAt, LocalDateTime.now()));
        if (updated != 1) throw submittedImmutable();
    }

    private SiteDailyLog requireLog(Long id) {
        SiteDailyLog log = mapper.selectById(id);
        if (log == null || !UserContext.getCurrentTenantId().equals(log.getTenantId()))
            throw new BusinessException("SITE_DAILY_LOG_NOT_FOUND", "现场日报不存在");
        return log;
    }

    private void requireUniqueDate(Long projectId, LocalDate reportDate, Long excludeId) {
        LambdaQueryWrapper<SiteDailyLog> query = new LambdaQueryWrapper<SiteDailyLog>()
                .eq(SiteDailyLog::getTenantId, UserContext.getCurrentTenantId())
                .eq(SiteDailyLog::getProjectId, projectId)
                .eq(SiteDailyLog::getReportDate, reportDate);
        if (excludeId != null) query.ne(SiteDailyLog::getId, excludeId);
        if (mapper.selectCount(query) > 0) throw duplicateDate();
    }

    private BusinessException duplicateDate() {
        return new BusinessException("SITE_DAILY_LOG_DUPLICATE_DATE", "同一项目同一天只能有一份现场日报");
    }

    private void requireDraft(SiteDailyLog log) {
        if (!DRAFT.equals(log.getStatus()))
            throw submittedImmutable();
    }

    private BusinessException submittedImmutable() {
        return new BusinessException("SITE_DAILY_LOG_SUBMITTED_IMMUTABLE", "已提交的现场日报不可修改或重复提交");
    }

    private void requireStatus(String status) {
        if (!Set.of(DRAFT, SUBMITTED).contains(status))
            throw new BusinessException("SITE_DAILY_LOG_STATUS_INVALID", "现场日报状态不合法");
    }

    private SiteDailyLogVO toVO(SiteDailyLog log, String projectName) {
        SiteDailyLogVO vo = new SiteDailyLogVO();
        vo.setId(log.getId().toString());
        vo.setProjectId(log.getProjectId().toString());
        vo.setProjectName(projectName);
        vo.setReportDate(log.getReportDate().format(DateTimeUtils.DATE_FMT));
        vo.setConstructionContent(log.getConstructionContent());
        vo.setIssuesDelays(log.getIssuesDelays());
        vo.setNextDayPlan(log.getNextDayPlan());
        vo.setWeatherSummary(log.getWeatherSummary());
        vo.setOnSiteHeadcount(log.getOnSiteHeadcount());
        vo.setStatus(log.getStatus());
        vo.setSubmittedBy(log.getSubmittedBy() == null ? null : log.getSubmittedBy().toString());
        vo.setSubmittedAt(log.getSubmittedAt() == null ? null : log.getSubmittedAt().format(DateTimeUtils.DTF));
        vo.setCreatedBy(log.getCreatedBy() == null ? null : log.getCreatedBy().toString());
        vo.setCreatedAt(log.getCreatedAt() == null ? null : log.getCreatedAt().format(DateTimeUtils.DTF));
        vo.setUpdatedAt(log.getUpdatedAt() == null ? null : log.getUpdatedAt().format(DateTimeUtils.DTF));
        return vo;
    }

    private List<SiteDailyDeliveryVO> loadDeliveries(SiteDailyLog log) {
        Long tenantId = UserContext.getCurrentTenantId();
        List<MatReceipt> receipts = receiptMapper.selectList(new LambdaQueryWrapper<MatReceipt>()
                .eq(MatReceipt::getTenantId, tenantId)
                .eq(MatReceipt::getProjectId, log.getProjectId())
                .eq(MatReceipt::getReceiptDate, log.getReportDate())
                .eq(MatReceipt::getApprovalStatus, "APPROVED")
                .orderByAsc(MatReceipt::getReceiptCode));
        if (receipts.isEmpty()) return List.of();

        Set<Long> receiptIds = receipts.stream().map(MatReceipt::getId).collect(Collectors.toSet());
        List<MatReceiptItem> items = receiptItemMapper.selectList(new LambdaQueryWrapper<MatReceiptItem>()
                .eq(MatReceiptItem::getTenantId, tenantId)
                .in(MatReceiptItem::getReceiptId, receiptIds)
                .orderByAsc(MatReceiptItem::getCreatedAt));
        if (items.isEmpty()) return List.of();

        Set<Long> materialIds = items.stream().map(MatReceiptItem::getMaterialId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> materialNames = materialIds.isEmpty() ? Map.of() : materialMapper.selectList(
                        new LambdaQueryWrapper<MdMaterial>().eq(MdMaterial::getTenantId, tenantId)
                                .in(MdMaterial::getId, materialIds))
                .stream().collect(Collectors.toMap(MdMaterial::getId, MdMaterial::getMaterialName, (a, b) -> a));
        Set<Long> partnerIds = receipts.stream().map(MatReceipt::getPartnerId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> partnerNames = partnerIds.isEmpty() ? Map.of() : partnerMapper.selectList(
                        new LambdaQueryWrapper<MdPartner>().eq(MdPartner::getTenantId, tenantId)
                                .in(MdPartner::getId, partnerIds))
                .stream().collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));
        Map<Long, MatReceipt> receiptById = new HashMap<>();
        receipts.forEach(receipt -> receiptById.put(receipt.getId(), receipt));

        return items.stream().map(item -> {
            MatReceipt receipt = receiptById.get(item.getReceiptId());
            SiteDailyDeliveryVO delivery = new SiteDailyDeliveryVO();
            delivery.setReceiptItemId(item.getId().toString());
            delivery.setReceiptId(receipt.getId().toString());
            delivery.setReceiptCode(receipt.getReceiptCode());
            delivery.setPartnerName(partnerNames.get(receipt.getPartnerId()));
            delivery.setMaterialId(item.getMaterialId() == null ? null : item.getMaterialId().toString());
            delivery.setMaterialName(materialNames.get(item.getMaterialId()));
            delivery.setActualQuantity(item.getActualQuantity() == null ? null : item.getActualQuantity().toPlainString());
            delivery.setQualifiedQuantity(item.getQualifiedQuantity() == null ? null : item.getQualifiedQuantity().toPlainString());
            return delivery;
        }).toList();
    }

    private List<SiteDailyPlannedTaskVO> loadPlannedTasks(SiteDailyLog log) {
        LocalDate reportDate = log.getReportDate();
        return subTaskMapper.selectList(new LambdaQueryWrapper<SubTask>()
                        .eq(SubTask::getTenantId, UserContext.getCurrentTenantId())
                        .eq(SubTask::getProjectId, log.getProjectId())
                        .isNotNull(SubTask::getPlannedStartDate)
                        .isNotNull(SubTask::getPlannedEndDate)
                        .le(SubTask::getPlannedStartDate, reportDate)
                        .ge(SubTask::getPlannedEndDate, reportDate)
                        .orderByAsc(SubTask::getPlannedStartDate)
                        .orderByAsc(SubTask::getTaskCode))
                .stream().map(task -> {
                    SiteDailyPlannedTaskVO planned = new SiteDailyPlannedTaskVO();
                    planned.setId(task.getId().toString());
                    planned.setTaskCode(task.getTaskCode());
                    planned.setTaskName(task.getTaskName());
                    planned.setWorkArea(task.getWorkArea());
                    planned.setPlannedStartDate(task.getPlannedStartDate().format(DateTimeUtils.DATE_FMT));
                    planned.setPlannedEndDate(task.getPlannedEndDate().format(DateTimeUtils.DATE_FMT));
                    planned.setStatus(task.getStatus());
                    planned.setProgressPercent(task.getProgressPercent() == null ? null : task.getProgressPercent().toPlainString());
                    return planned;
                }).toList();
    }
}
