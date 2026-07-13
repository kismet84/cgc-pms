package com.cgcpms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.project.vo.ProjectOverviewVO;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Project overview aggregation service.
 * <p>
 * All sub-queries are batch (selectList with tenantId + projectId filter),
 * never per-row queries. User names are batch-loaded via selectByIds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectOverviewService {

    private final PmProjectMapper projectMapper;
    private final CtContractMapper ctContractMapper;
    private final CostSummaryMapper costSummaryMapper;
    private final PayRecordMapper payRecordMapper;
    private final AlertLogMapper alertLogMapper;
    private final PmProjectMemberMapper memberMapper;
    private final SysUserMapper sysUserMapper;
    private final com.cgcpms.project.auth.ProjectAccessChecker projectAccessChecker;

    /**
     * Get project overview for the current tenant.
     */
    public ProjectOverviewVO getOverview(Long projectId) {
        // 数据范围校验（非管理员用户）
        projectAccessChecker.checkAccess(projectId, "查看总览");
        return getOverview(UserContext.getCurrentTenantId(), projectId);
    }

    /**
     * Get project overview for a specific tenant.
     * <p>
     * Each data source is queried exactly once (batch), preventing N+1.
     */
    public ProjectOverviewVO getOverview(Long tenantId, Long projectId) {
        PmProject project = requireProjectInTenant(tenantId, projectId);

        ProjectOverviewVO vo = new ProjectOverviewVO();
        vo.setProjectId(projectId.toString());

        // ── 1. Contract aggregation (single batch query) ──
        List<CtContract> contracts = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .eq(CtContract::getProjectId, projectId));
        long contractCount = contracts.size();
        BigDecimal totalContractAmount = contracts.stream()
                .map(c -> c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        vo.setContractCount(String.valueOf(contractCount));
        vo.setTotalContractAmount(totalContractAmount.toPlainString());

        // ── 2. Dynamic cost from cost_summary (single batch query) ──
        // Use the latest summary_date for this project
        Page<CostSummary> page = new Page<>(0, 1);
        Page<CostSummary> result = costSummaryMapper.selectPage(page,
                new LambdaQueryWrapper<CostSummary>()
                        .eq(CostSummary::getTenantId, tenantId)
                        .eq(CostSummary::getProjectId, projectId)
                        .orderByDesc(CostSummary::getSummaryDate));
        CostSummary latest = result.getRecords().isEmpty() ? null : result.getRecords().get(0);

        BigDecimal dynamicCost = BigDecimal.ZERO;
        if (latest != null) {
            List<CostSummary> summaries = costSummaryMapper.selectList(
                    new LambdaQueryWrapper<CostSummary>()
                            .eq(CostSummary::getTenantId, tenantId)
                            .eq(CostSummary::getProjectId, projectId)
                            .eq(CostSummary::getSummaryDate, latest.getSummaryDate()));
            dynamicCost = summaries.stream()
                    .map(s -> s.getDynamicCost() != null ? s.getDynamicCost() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        vo.setDynamicCost(dynamicCost.toPlainString());

        // ── 3. Paid amount from pay_record (single batch query) ──
        List<PayRecord> payRecords = payRecordMapper.selectList(
                new LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getTenantId, tenantId)
                        .eq(PayRecord::getProjectId, projectId)
                        .eq(PayRecord::getPayStatus, "SUCCESS"));
        BigDecimal paidAmount = payRecords.stream()
                .map(p -> p.getPayAmount() != null ? p.getPayAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setPaidAmount(paidAmount.toPlainString());

        // ── 4. Warning count this month (single batch query) ──
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.atEndOfMonth().atTime(23, 59, 59);
        List<AlertLog> alerts = alertLogMapper.selectList(
                new LambdaQueryWrapper<AlertLog>()
                        .eq(AlertLog::getTenantId, tenantId)
                        .eq(AlertLog::getProjectId, projectId)
                        .ge(AlertLog::getTriggeredAt, monthStart)
                        .le(AlertLog::getTriggeredAt, monthEnd));
        vo.setWarningCount(String.valueOf(alerts.size()));

        // ── 5. Member aggregation (single batch query) ──
        List<PmProjectMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<PmProjectMember>()
                        .eq(PmProjectMember::getTenantId, tenantId)
                        .eq(PmProjectMember::getProjectId, projectId));
        vo.setMemberCount(String.valueOf(members.size()));

        // ── 6. Batch-load user names (N+1 prevention) ──
        List<ProjectOverviewVO.MemberBriefVO> memberBriefs;
        if (!CollectionUtils.isEmpty(members)) {
            Set<Long> userIds = members.stream()
                    .map(PmProjectMember::getUserId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<Long, SysUser> userMap = Collections.emptyMap();
            if (!userIds.isEmpty()) {
                List<SysUser> users = sysUserMapper.selectByIds(userIds);
                userMap = users.stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
            }

            final Map<Long, SysUser> finalUserMap = userMap;
            memberBriefs = members.stream().map(m -> {
                ProjectOverviewVO.MemberBriefVO brief = new ProjectOverviewVO.MemberBriefVO();
                brief.setUserId(m.getUserId() != null ? m.getUserId().toString() : null);
                SysUser user = finalUserMap.get(m.getUserId());
                brief.setUserName(user != null ? user.getRealName() : "");
                brief.setRoleCode(m.getRoleCode());
                return brief;
            }).collect(Collectors.toList());
        } else {
            memberBriefs = Collections.emptyList();
        }
        vo.setMembers(memberBriefs);

        return vo;
    }

    /**
     * Verify project exists and belongs to the given tenant.
     */
    private PmProject requireProjectInTenant(Long tenantId, Long projectId) {
        if (tenantId == null || projectId == null) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        PmProject project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        return project;
    }
}
