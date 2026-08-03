package com.cgcpms.project.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/** Central project-stage and active-WBS write guard. */
@Service
@RequiredArgsConstructor
public class ProjectExecutionGuard {
    private final JdbcTemplate jdbc;

    public void requirePlanningProject(Long projectId, String action) {
        requireProjectStage(projectId, Set.of("PREPARING", "ACTIVE"), action);
    }

    public void requireActiveProject(Long projectId, String action) {
        requireProjectStage(projectId, Set.of("ACTIVE"), action);
    }

    public void requireProjectStage(Long projectId, Set<String> allowed, String action) {
        String status;
        try {
            status = jdbc.queryForObject(
                    "SELECT status FROM pm_project WHERE id=? AND tenant_id=? AND deleted_flag=0",
                    String.class, projectId, tenantId());
        } catch (EmptyResultDataAccessException error) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        if (!allowed.contains(status)) {
            throw new BusinessException("PROJECT_STAGE_WRITE_FORBIDDEN",
                    action + "不允许在项目阶段 " + status + " 执行");
        }
    }

    public void requireActiveSchedule(Long projectId, String action) {
        requireActiveProject(projectId, action);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_schedule_plan
                WHERE tenant_id=? AND project_id=? AND status='ACTIVE' AND deleted_flag=0
                """, Integer.class, tenantId(), projectId);
        if (count == null || count != 1) {
            throw new BusinessException("PROJECT_ACTIVE_SCHEDULE_REQUIRED", action + "前必须存在唯一生效WBS基线");
        }
    }

    public void requireActiveWbs(Long projectId, Long wbsTaskId, String action) {
        if (wbsTaskId == null) {
            throw new BusinessException("PROJECT_WBS_REQUIRED", action + "必须关联WBS任务");
        }
        requireActiveProject(projectId, action);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM project_wbs_task task
                JOIN project_schedule_plan schedule
                  ON schedule.tenant_id=task.tenant_id
                 AND schedule.id=task.schedule_plan_id
                 AND schedule.project_id=task.project_id
                 AND schedule.deleted_flag=0
                 AND schedule.status='ACTIVE'
                WHERE task.tenant_id=? AND task.id=? AND task.project_id=? AND task.deleted_flag=0
                """, Integer.class, tenantId(), wbsTaskId, projectId);
        if (count == null || count != 1) {
            throw new BusinessException("PROJECT_WBS_MISMATCH", "WBS任务不属于当前租户、项目或生效基线");
        }
    }

    private Long tenantId() {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) throw new BusinessException("TENANT_CONTEXT_REQUIRED", "缺少租户上下文");
        return tenantId;
    }
}
