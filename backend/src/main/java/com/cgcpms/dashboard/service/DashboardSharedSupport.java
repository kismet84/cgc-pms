package com.cgcpms.dashboard.service;

import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.dashboard.vo.DashboardAlertItemVO;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import org.springframework.util.CollectionUtils;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

abstract class DashboardSharedSupport {
    protected final CostSummaryService costSummaryService;
    protected final CostSummaryMapper costSummaryMapper;
    protected final PmProjectMapper projectMapper;
    protected final CtContractMapper ctContractMapper;
    protected final WfTaskMapper wfTaskMapper;
    protected final WfInstanceMapper wfInstanceMapper;
    protected final PayRecordMapper payRecordMapper;
    protected final AlertLogMapper alertLogMapper;

    protected DashboardSharedSupport(
            CostSummaryService costSummaryService,
            CostSummaryMapper costSummaryMapper,
            PmProjectMapper projectMapper,
            CtContractMapper ctContractMapper,
            WfTaskMapper wfTaskMapper,
            WfInstanceMapper wfInstanceMapper,
            PayRecordMapper payRecordMapper,
            AlertLogMapper alertLogMapper) {
        this.costSummaryService = costSummaryService;
        this.costSummaryMapper = costSummaryMapper;
        this.projectMapper = projectMapper;
        this.ctContractMapper = ctContractMapper;
        this.wfTaskMapper = wfTaskMapper;
        this.wfInstanceMapper = wfInstanceMapper;
        this.payRecordMapper = payRecordMapper;
        this.alertLogMapper = alertLogMapper;
    }

    protected YearMonth parseDashboardMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessException(
                    "INVALID_DASHBOARD_MONTH",
                    "报告月份格式无效，应为 yyyy-MM",
                    e);
        }
    }

    protected PmProject requireProject(Long tenantId, Long projectId) {
        if (projectId == null) {
            throw new BusinessException("PROJECT_NOT_FOUND", "请指定项目");
        }
        PmProject project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        return project;
    }

    protected Map<Long, WfInstance> batchLoadInstances(List<WfTask> tasks) {
        if (CollectionUtils.isEmpty(tasks)) {
            return Collections.emptyMap();
        }
        Set<Long> instanceIds = tasks.stream()
                .map(WfTask::getInstanceId)
                .collect(Collectors.toSet());
        List<WfInstance> instances = wfInstanceMapper.selectByIds(instanceIds);
        return instances.stream().collect(Collectors.toMap(WfInstance::getId, i -> i, (a, b) -> a));
    }

    protected DashboardAlertItemVO toAlertItem(AlertLog alert) {
        DashboardAlertItemVO vo = new DashboardAlertItemVO();
        vo.setAlertType(alert.getRuleType());
        vo.setSeverity(alert.getSeverity());
        vo.setMessage(alert.getMessage());
        vo.setProjectId(alert.getProjectId() != null ? String.valueOf(alert.getProjectId()) : null);
        if (alert.getTriggeredAt() != null) {
            vo.setTriggeredAt(DateTimeUtils.DTF.format(alert.getTriggeredAt()));
        }
        return vo;
    }
}
