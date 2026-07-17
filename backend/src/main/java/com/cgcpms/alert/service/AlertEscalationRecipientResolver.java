package com.cgcpms.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.cashbook.service.CashJournalAlertRecipientResolver;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AlertEscalationRecipientResolver {
    private final PmProjectMapper projectMapper;
    private final PmProjectMemberMapper projectMemberMapper;
    private final CashJournalAlertRecipientResolver cashJournalRecipientResolver;
    private final SysUserMapper userMapper;

    public Set<Long> resolve(AlertLog alert, int level) {
        Set<Long> recipients = level >= 2
                ? tenantAdministrators(alert.getTenantId())
                : projectManagement(alert);
        if (recipients.isEmpty() && level >= 2) {
            recipients.addAll(projectManagement(alert));
        }
        if (recipients.size() > 1 && alert.getAcknowledgedBy() != null) {
            recipients.remove(alert.getAcknowledgedBy());
        }
        return recipients;
    }

    private Set<Long> projectManagement(AlertLog alert) {
        Set<Long> recipients = new LinkedHashSet<>();
        Long projectId = alert.getProjectId();
        if (projectId == null || projectId == 0L) {
            recipients.addAll(cashJournalRecipientResolver.resolve(alert.getTenantId()));
            return recipients;
        }
        projectMemberMapper.selectList(new LambdaQueryWrapper<PmProjectMember>()
                        .eq(PmProjectMember::getTenantId, alert.getTenantId())
                        .eq(PmProjectMember::getProjectId, projectId)
                        .eq(PmProjectMember::getStatus, "ACTIVE")
                        .in(PmProjectMember::getRoleCode, List.of("PM", "PROJECT_MANAGER"))
                        .orderByAsc(PmProjectMember::getId))
                .stream()
                .map(PmProjectMember::getUserId)
                .filter(Objects::nonNull)
                .forEach(recipients::add);
        PmProject project = projectMapper.selectById(projectId);
        if (project != null && Objects.equals(project.getTenantId(), alert.getTenantId())
                && project.getCreatedBy() != null) {
            recipients.add(project.getCreatedBy());
        }
        return recipients;
    }

    private Set<Long> tenantAdministrators(Long tenantId) {
        return new LinkedHashSet<>(userMapper.selectTenantAdminRecipientIds(tenantId));
    }
}
