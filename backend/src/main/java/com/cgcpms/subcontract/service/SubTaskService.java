package com.cgcpms.subcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.subcontract.entity.SubTask;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import com.cgcpms.subcontract.vo.SubTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import com.cgcpms.common.util.DateTimeUtils;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubTaskService {

    private static final int CODE_GENERATION_MAX_RETRIES = 3;

    private final SubTaskMapper subTaskMapper;
    private final PmProjectMapper pmProjectMapper;
    private final CtContractMapper ctContractMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final ProjectAccessChecker projectAccessChecker;

    public IPage<SubTaskVO> getPage(long pageNo, long pageSize, Long projectId, Long contractId,
                                     Long partnerId, String status, String taskCode, String taskName) {
        LambdaQueryWrapper<SubTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubTask::getTenantId, UserContext.getCurrentTenantId());
        if (projectId != null) {
            projectAccessChecker.checkAccess(projectId, "查询分包任务");
            wrapper.eq(SubTask::getProjectId, projectId);
        } else {
            List<Long> visibleProjectIds = projectAccessChecker.filterAccessible(
                            pmProjectMapper.selectList(new LambdaQueryWrapper<PmProject>()
                                    .eq(PmProject::getTenantId, UserContext.getCurrentTenantId())))
                    .stream().map(PmProject::getId).toList();
            if (visibleProjectIds.isEmpty()) wrapper.apply("1 = 0"); // SQL-SAFETY: fixed-sql-fragment
            else wrapper.in(SubTask::getProjectId, visibleProjectIds);
        }
        if (contractId != null) wrapper.eq(SubTask::getContractId, contractId);
        if (partnerId != null) wrapper.eq(SubTask::getPartnerId, partnerId);
        if (StringUtils.hasText(status)) wrapper.eq(SubTask::getStatus, status);
        if (StringUtils.hasText(taskCode)) wrapper.like(SubTask::getTaskCode, taskCode);
        if (StringUtils.hasText(taskName)) wrapper.like(SubTask::getTaskName, taskName);
        wrapper.orderByDesc(SubTask::getCreatedAt);

        Page<SubTask> page = subTaskMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        // Batch-prefetch related project/contract/partner names to avoid N+1 queries
        List<SubTask> records = page.getRecords();
        Set<Long> projectIds = records.stream()
                .map(SubTask::getProjectId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> contractIds = records.stream()
                .map(SubTask::getContractId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> partnerIds = records.stream()
                .map(SubTask::getPartnerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> predecessorIds = records.stream()
                .map(SubTask::getPredecessorTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : pmProjectMapper.selectByIds(projectIds).stream()
                        .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<Long, String> contractNames = contractIds.isEmpty() ? Map.of()
                : ctContractMapper.selectByIds(contractIds).stream()
                        .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));
        Map<Long, String> partnerNames = partnerIds.isEmpty() ? Map.of()
                : mdPartnerMapper.selectByIds(partnerIds).stream()
                        .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));
        Map<Long, SubTask> predecessors = predecessorIds.isEmpty() ? Map.of()
                : subTaskMapper.selectByIds(predecessorIds).stream()
                        .collect(Collectors.toMap(SubTask::getId, t -> t, (a, b) -> a));

        records.forEach(task -> validateStoredPredecessor(task, predecessors));

        return page.convert(t -> toVO(t, projectNames, contractNames, partnerNames, predecessors));
    }

    public SubTaskVO getById(Long id) {
        SubTask task = subTaskMapper.selectById(id);
        if (task == null || !task.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("SUB_TASK_NOT_FOUND", "分包任务不存在");
        projectAccessChecker.checkAccess(task.getProjectId(), "访问分包任务");
        SubTask predecessor = loadStoredPredecessor(task);
        return toVO(task, predecessor);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SubTask task) {
        // Force tenantId from authenticated context, ignore client-supplied value
        task.setTenantId(UserContext.getCurrentTenantId());
        projectAccessChecker.checkAccess(task.getProjectId(), "创建分包任务");
        // Auto-generate task code: SUB-yyyyMMdd-XXX
        String prefix = "SUB-" + LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";

        // Default status
        if (task.getStatus() == null || task.getStatus().isBlank()) {
            task.setStatus("NOT_STARTED");
        }
        validateScheduleConsistency(task, null);
        validateDependencyConsistency(task, null);

        for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
            task.setTaskCode(nextTaskCode(prefix, attempt));
            try {
                subTaskMapper.insert(task);
                return task.getId();
            } catch (DuplicateKeyException e) {
                log.warn("分包任务编号冲突，重试生成 taskCode={}", task.getTaskCode());
            }
        }
        throw new BusinessException("SUB_TASK_CODE_CONFLICT", "分包任务编号生成冲突，请重试");
    }

    private String nextTaskCode(String prefix, int offset) {
        LambdaQueryWrapper<SubTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubTask::getTenantId, UserContext.getCurrentTenantId())
                .likeRight(SubTask::getTaskCode, prefix)
                .orderByDesc(SubTask::getTaskCode);
        Page<SubTask> page = new Page<>(0, 1);
        Page<SubTask> result = subTaskMapper.selectPage(page, wrapper);
        SubTask last = result.getRecords().isEmpty() ? null : result.getRecords().get(0);

        int seq = 1 + offset;
        if (last != null && last.getTaskCode() != null && last.getTaskCode().length() == prefix.length() + 3) {
            try {
                seq = Integer.parseInt(last.getTaskCode().substring(prefix.length())) + 1 + offset;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse sequence number: {}", last.getTaskCode(), e);
            }
        }
        return prefix + String.format("%03d", seq);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SubTask task) {
        SubTask existing = subTaskMapper.selectById(task.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("SUB_TASK_NOT_FOUND", "分包任务不存在");

        projectAccessChecker.checkAccess(existing.getProjectId(), "修改分包任务");
        Long effectiveProjectId = task.getProjectId() != null ? task.getProjectId() : existing.getProjectId();
        projectAccessChecker.checkAccess(effectiveProjectId, "修改分包任务");
        task.setTenantId(existing.getTenantId());
        validateScheduleConsistency(task, existing);
        validateDependencyConsistency(task, existing);
        subTaskMapper.updateById(task);
        if (task.isPredecessorTaskIdSpecified() && task.getPredecessorTaskId() == null) {
            subTaskMapper.update(null, new LambdaUpdateWrapper<SubTask>()
                    .eq(SubTask::getId, existing.getId())
                    .eq(SubTask::getTenantId, existing.getTenantId())
                    .set(SubTask::getPredecessorTaskId, null));
        }
    }

    private void validateScheduleConsistency(SubTask task, SubTask existing) {
        LocalDate plannedStart = task.getPlannedStartDate() != null ? task.getPlannedStartDate()
                : existing == null ? null : existing.getPlannedStartDate();
        LocalDate plannedEnd = task.getPlannedEndDate() != null ? task.getPlannedEndDate()
                : existing == null ? null : existing.getPlannedEndDate();
        LocalDate actualStart = task.getActualStartDate() != null ? task.getActualStartDate()
                : existing == null ? null : existing.getActualStartDate();
        LocalDate actualEnd = task.getActualEndDate() != null ? task.getActualEndDate()
                : existing == null ? null : existing.getActualEndDate();
        java.math.BigDecimal progress = task.getProgressPercent() != null ? task.getProgressPercent()
                : existing == null ? null : existing.getProgressPercent();
        if (existing != null && task.getStatus() != null && task.getStatus().isBlank())
            throw new BusinessException("SUB_TASK_STATUS_INVALID", "任务状态不合法");
        String status = task.getStatus() != null ? task.getStatus()
                : existing == null ? null : existing.getStatus();

        if (progress != null && (progress.signum() < 0 || progress.compareTo(java.math.BigDecimal.valueOf(100)) > 0))
            throw new BusinessException("SUB_TASK_PROGRESS_INVALID", "任务进度必须在0到100之间");
        if (plannedStart != null && plannedEnd != null && plannedEnd.isBefore(plannedStart))
            throw new BusinessException("SUB_TASK_PLANNED_DATE_INVALID", "计划结束日期不能早于计划开始日期");
        if (actualEnd != null && actualStart == null)
            throw new BusinessException("SUB_TASK_ACTUAL_DATE_INVALID", "实际完成日期要求先填写实际开始日期");
        if (actualStart != null && actualEnd != null && actualEnd.isBefore(actualStart))
            throw new BusinessException("SUB_TASK_ACTUAL_DATE_INVALID", "实际完成日期不能早于实际开始日期");
        if (StringUtils.hasText(status) && !Set.of("NOT_STARTED", "IN_PROGRESS", "COMPLETED", "SUSPENDED").contains(status))
            throw new BusinessException("SUB_TASK_STATUS_INVALID", "任务状态不合法");

        boolean completed = "COMPLETED".equals(status);
        boolean fullProgress = progress != null && progress.compareTo(java.math.BigDecimal.valueOf(100)) == 0;
        if (completed != fullProgress || completed != (actualEnd != null))
            throw new BusinessException("SUB_TASK_COMPLETION_INVALID", "完成状态、100%进度和实际完成日期必须一致");
        if ("NOT_STARTED".equals(status)
                && ((progress != null && progress.signum() != 0) || actualStart != null))
            throw new BusinessException("SUB_TASK_STATUS_PROGRESS_INVALID", "未开始任务不能填写实际进度或实际日期");
    }

    private void validateDependencyConsistency(SubTask task, SubTask existing) {
        Long tenantId = existing == null ? UserContext.getCurrentTenantId() : existing.getTenantId();
        Long currentId = existing == null ? null : existing.getId();
        Long projectId = task.getProjectId() != null ? task.getProjectId()
                : existing == null ? null : existing.getProjectId();
        Long predecessorId = task.isPredecessorTaskIdSpecified() ? task.getPredecessorTaskId()
                : existing == null ? null : existing.getPredecessorTaskId();
        LocalDate plannedStart = task.getPlannedStartDate() != null ? task.getPlannedStartDate()
                : existing == null ? null : existing.getPlannedStartDate();
        LocalDate plannedEnd = task.getPlannedEndDate() != null ? task.getPlannedEndDate()
                : existing == null ? null : existing.getPlannedEndDate();
        String status = task.getStatus() != null ? task.getStatus()
                : existing == null ? null : existing.getStatus();

        if (predecessorId != null) {
            if (Objects.equals(currentId, predecessorId))
                throw new BusinessException("SUB_TASK_DEPENDENCY_CYCLE", "任务不能依赖自身");

            Set<Long> visited = new HashSet<>();
            Long cursor = predecessorId;
            SubTask predecessor = null;
            while (cursor != null) {
                if (Objects.equals(currentId, cursor) || !visited.add(cursor))
                    throw new BusinessException("SUB_TASK_DEPENDENCY_CYCLE", "前置任务不能形成循环依赖");
                SubTask node = subTaskMapper.selectById(cursor);
                if (node == null || !Objects.equals(tenantId, node.getTenantId())
                        || !Objects.equals(projectId, node.getProjectId()))
                    throw new BusinessException("SUB_TASK_DEPENDENCY_INVALID", "前置任务必须属于当前租户和同一项目");
                if (predecessor == null) predecessor = node;
                cursor = node.getPredecessorTaskId();
            }
            if (predecessor.getPlannedEndDate() != null && plannedStart != null
                    && plannedStart.isBefore(predecessor.getPlannedEndDate()))
                throw new BusinessException("SUB_TASK_FS_DATE_INVALID", "后续任务计划开始不能早于前置任务计划结束");
            if (("IN_PROGRESS".equals(status) || "COMPLETED".equals(status))
                    && !"COMPLETED".equals(predecessor.getStatus()))
                throw new BusinessException("SUB_TASK_PREDECESSOR_NOT_COMPLETED", "前置任务未完成，后续任务不能开工或完成");
        }

        if (currentId == null) return;
        List<SubTask> successors = subTaskMapper.selectList(new LambdaQueryWrapper<SubTask>()
                .eq(SubTask::getTenantId, tenantId)
                .eq(SubTask::getPredecessorTaskId, currentId));
        for (SubTask successor : successors) {
            if (!Objects.equals(projectId, successor.getProjectId()))
                throw new BusinessException("SUB_TASK_DEPENDENCY_INVALID", "任务调整不能造成跨项目依赖");
            if (plannedEnd != null && successor.getPlannedStartDate() != null
                    && successor.getPlannedStartDate().isBefore(plannedEnd))
                throw new BusinessException("SUB_TASK_FS_DATE_INVALID", "前置任务计划结束不能晚于后续任务计划开始");
        }
    }

    private void validateStoredPredecessor(SubTask task, Map<Long, SubTask> predecessors) {
        if (task.getPredecessorTaskId() == null) return;
        SubTask predecessor = predecessors.get(task.getPredecessorTaskId());
        if (predecessor == null || !Objects.equals(task.getTenantId(), predecessor.getTenantId())
                || !Objects.equals(task.getProjectId(), predecessor.getProjectId()))
            throw new BusinessException("SUB_TASK_DEPENDENCY_INVALID", "分包任务存在无效前置引用");
    }

    private SubTask loadStoredPredecessor(SubTask task) {
        if (task.getPredecessorTaskId() == null) return null;
        SubTask predecessor = subTaskMapper.selectById(task.getPredecessorTaskId());
        if (predecessor == null || !Objects.equals(task.getTenantId(), predecessor.getTenantId())
                || !Objects.equals(task.getProjectId(), predecessor.getProjectId()))
            throw new BusinessException("SUB_TASK_DEPENDENCY_INVALID", "分包任务存在无效前置引用");
        return predecessor;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SubTask existing = subTaskMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("SUB_TASK_NOT_FOUND", "分包任务不存在");

        projectAccessChecker.checkAccess(existing.getProjectId(), "删除分包任务");
        Long successorCount = subTaskMapper.selectCount(new LambdaQueryWrapper<SubTask>()
                .eq(SubTask::getTenantId, existing.getTenantId())
                .eq(SubTask::getPredecessorTaskId, id));
        if (successorCount > 0)
            throw new BusinessException("SUB_TASK_DEPENDENCY_IN_USE", "前置任务仍被后续任务引用");

        existing.setTaskCode("DELETED-" + id);
        subTaskMapper.updateById(existing);
        subTaskMapper.deleteById(id);
    }

    private SubTaskVO toVO(SubTask t, SubTask predecessor) {
        // Single-record variant: fetch project/contract/partner individually (for getById)
        SubTaskVO vo = buildBaseVO(t);
        if (t.getProjectId() != null) {
            PmProject project = pmProjectMapper.selectById(t.getProjectId());
            if (project != null) vo.setProjectName(project.getProjectName());
        }
        if (t.getContractId() != null) {
            CtContract contract = ctContractMapper.selectById(t.getContractId());
            if (contract != null) vo.setContractName(contract.getContractName());
        }
        if (t.getPartnerId() != null) {
            MdPartner partner = mdPartnerMapper.selectById(t.getPartnerId());
            if (partner != null) vo.setPartnerName(partner.getPartnerName());
        }
        applyPredecessor(vo, predecessor);
        return vo;
    }

    private SubTaskVO toVO(SubTask t, Map<Long, String> projectNames,
                           Map<Long, String> contractNames, Map<Long, String> partnerNames,
                           Map<Long, SubTask> predecessors) {
        // Batch variant: use pre-fetched name maps
        SubTaskVO vo = buildBaseVO(t);
        if (t.getProjectId() != null) vo.setProjectName(projectNames.get(t.getProjectId()));
        if (t.getContractId() != null) vo.setContractName(contractNames.get(t.getContractId()));
        if (t.getPartnerId() != null) vo.setPartnerName(partnerNames.get(t.getPartnerId()));
        if (t.getPredecessorTaskId() != null) {
            applyPredecessor(vo, predecessors.get(t.getPredecessorTaskId()));
        }
        return vo;
    }

    private void applyPredecessor(SubTaskVO vo, SubTask predecessor) {
        if (predecessor == null) return;
        vo.setPredecessorTaskId(predecessor.getId().toString());
        vo.setPredecessorTaskName(predecessor.getTaskName());
        vo.setPredecessorStatus(predecessor.getStatus());
        vo.setPredecessorPlannedEndDate(predecessor.getPlannedEndDate() == null ? null
                : predecessor.getPlannedEndDate().format(DateTimeUtils.DATE_FMT));
        vo.setPredecessorActualEndDate(predecessor.getActualEndDate() == null ? null
                : predecessor.getActualEndDate().format(DateTimeUtils.DATE_FMT));
    }

    private SubTaskVO buildBaseVO(SubTask t) {
        SubTaskVO vo = new SubTaskVO();
        vo.setId(t.getId() != null ? t.getId().toString() : null);
        vo.setTenantId(t.getTenantId() != null ? t.getTenantId().toString() : null);
        vo.setProjectId(t.getProjectId() != null ? t.getProjectId().toString() : null);
        vo.setContractId(t.getContractId() != null ? t.getContractId().toString() : null);
        vo.setPartnerId(t.getPartnerId() != null ? t.getPartnerId().toString() : null);
        vo.setTaskCode(t.getTaskCode());
        vo.setTaskName(t.getTaskName());
        vo.setWorkArea(t.getWorkArea());
        vo.setPlannedStartDate(t.getPlannedStartDate() != null ? t.getPlannedStartDate().format(DateTimeUtils.DATE_FMT) : null);
        vo.setPlannedEndDate(t.getPlannedEndDate() != null ? t.getPlannedEndDate().format(DateTimeUtils.DATE_FMT) : null);
        vo.setActualStartDate(t.getActualStartDate() != null ? t.getActualStartDate().format(DateTimeUtils.DATE_FMT) : null);
        vo.setActualEndDate(t.getActualEndDate() != null ? t.getActualEndDate().format(DateTimeUtils.DATE_FMT) : null);
        vo.setProgressPercent(t.getProgressPercent() != null ? t.getProgressPercent().toPlainString() : null);
        vo.setStatus(t.getStatus());
        vo.setCreatedBy(t.getCreatedBy() != null ? t.getCreatedBy().toString() : null);
        vo.setCreatedAt(t.getCreatedAt() != null ? t.getCreatedAt().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedAt(t.getUpdatedAt() != null ? t.getUpdatedAt().format(DateTimeUtils.DTF) : null);
        vo.setRemark(t.getRemark());
        return vo;
    }
}
