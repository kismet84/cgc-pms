package com.cgcpms.subcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.cgcpms.subcontract.entity.SubTask;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import com.cgcpms.subcontract.vo.SubTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import com.cgcpms.common.util.DateTimeUtils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubTaskService {

    private final SubTaskMapper subTaskMapper;
    private final PmProjectMapper pmProjectMapper;
    private final CtContractMapper ctContractMapper;
    private final MdPartnerMapper mdPartnerMapper;

    public IPage<SubTaskVO> getPage(long pageNo, long pageSize, Long projectId, Long contractId,
                                     Long partnerId, String status, String taskCode, String taskName) {
        LambdaQueryWrapper<SubTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubTask::getTenantId, UserContext.getCurrentTenantId());
        if (projectId != null) wrapper.eq(SubTask::getProjectId, projectId);
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

        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : pmProjectMapper.selectBatchIds(projectIds).stream()
                        .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<Long, String> contractNames = contractIds.isEmpty() ? Map.of()
                : ctContractMapper.selectBatchIds(contractIds).stream()
                        .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));
        Map<Long, String> partnerNames = partnerIds.isEmpty() ? Map.of()
                : mdPartnerMapper.selectBatchIds(partnerIds).stream()
                        .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));

        return page.convert(t -> toVO(t, projectNames, contractNames, partnerNames));
    }

    public SubTaskVO getById(Long id) {
        SubTask task = subTaskMapper.selectById(id);
        if (task == null || !task.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("SUB_TASK_NOT_FOUND", "分包任务不存在");
        return toVO(task);
    }

    @Transactional
    public Long create(SubTask task) {
        // Auto-generate task code: SUB-yyyyMMdd-XXX
        String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        String prefix = "SUB-" + today + "-";

        LambdaQueryWrapper<SubTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(SubTask::getTaskCode, prefix)
                .orderByDesc(SubTask::getTaskCode);
        Page<SubTask> page = new Page<>(0, 1);
        Page<SubTask> result = subTaskMapper.selectPage(page, wrapper);
        SubTask last = result.getRecords().isEmpty() ? null : result.getRecords().get(0);

        int seq = 1;
        if (last != null && last.getTaskCode() != null && last.getTaskCode().length() == prefix.length() + 3) {
            try {
                seq = Integer.parseInt(last.getTaskCode().substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse sequence number: {}", last.getTaskCode(), e);
            }
        }
        task.setTaskCode(prefix + String.format("%03d", seq));

        // Default status
        if (task.getStatus() == null || task.getStatus().isBlank()) {
            task.setStatus("NOT_STARTED");
        }

        subTaskMapper.insert(task);
        return task.getId();
    }

    @Transactional
    public void update(SubTask task) {
        SubTask existing = subTaskMapper.selectById(task.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("SUB_TASK_NOT_FOUND", "分包任务不存在");

        subTaskMapper.updateById(task);
    }

    @Transactional
    public void delete(Long id) {
        SubTask existing = subTaskMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("SUB_TASK_NOT_FOUND", "分包任务不存在");

        subTaskMapper.deleteById(id);
    }

    private SubTaskVO toVO(SubTask t) {
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
        return vo;
    }

    private SubTaskVO toVO(SubTask t, Map<Long, String> projectNames,
                           Map<Long, String> contractNames, Map<Long, String> partnerNames) {
        // Batch variant: use pre-fetched name maps
        SubTaskVO vo = buildBaseVO(t);
        if (t.getProjectId() != null) vo.setProjectName(projectNames.get(t.getProjectId()));
        if (t.getContractId() != null) vo.setContractName(contractNames.get(t.getContractId()));
        if (t.getPartnerId() != null) vo.setPartnerName(partnerNames.get(t.getPartnerId()));
        return vo;
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
