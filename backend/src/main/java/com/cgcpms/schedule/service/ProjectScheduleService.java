package com.cgcpms.schedule.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.schedule.dto.ProjectScheduleModels.*;
import com.cgcpms.site.entity.SiteDailyLog;
import com.cgcpms.site.vo.SiteDailyPlannedTaskVO;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProjectScheduleService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String ACTIVE = "ACTIVE";

    private final JdbcTemplate jdbc;
    private final WorkflowEngine workflowEngine;
    private final ProjectAccessChecker projectAccessChecker;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createSchedule(ScheduleRequest request) {
        projectAccessChecker.checkAccess(request.projectId(), "创建项目计划");
        requireExecutableProject(request.projectId());
        requireDates(request.plannedStartDate(), request.plannedEndDate(), "PROJECT_SCHEDULE_DATE_INVALID", "项目计划起止日期不合法");
        Integer versionNo = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM project_schedule_plan WHERE tenant_id=? AND project_id=? AND deleted_flag=0", Integer.class, tenant(), request.projectId());
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO project_schedule_plan(id,tenant_id,project_id,plan_code,plan_name,plan_type,version_no,
                     planned_start_date,planned_end_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,'BASELINE',?,?,?,'DRAFT',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), request.projectId(), request.planCode().trim(), request.planName().trim(), versionNo,
                    request.plannedStartDate(), request.plannedEndDate(), user(), user(), request.remark());
        } catch (DuplicateKeyException e) {
            throw error("PROJECT_SCHEDULE_DUPLICATE", "同一项目的计划编码或版本不能重复");
        }
        return schedule(id);
    }

    public List<Map<String, Object>> schedules(Long projectId) {
        if (projectId == null) return List.of();
        projectAccessChecker.checkAccess(projectId, "查看项目计划");
        return jdbc.queryForList("""
                SELECT s.id,s.project_id projectId,s.plan_code planCode,s.plan_name planName,s.plan_type planType,
                 s.version_no versionNo,s.parent_plan_id parentPlanId,s.corrective_action_id correctiveActionId,
                 s.planned_start_date plannedStartDate,s.planned_end_date plannedEndDate,s.status,
                 s.approval_instance_id approvalInstanceId,s.activated_at activatedAt,s.remark
                FROM project_schedule_plan s WHERE s.tenant_id=? AND s.project_id=? AND s.deleted_flag=0
                ORDER BY s.version_no DESC
                """, tenant(), projectId);
    }

    public Map<String, Object> schedule(Long id) {
        Map<String, Object> result = new LinkedHashMap<>(requireSchedule(id, false));
        projectAccessChecker.checkAccess(longValue(result.get("project_id")), "查看项目计划");
        result.put("tasks", taskRows(id));
        result.put("periodPlans", periodPlans(id));
        result.put("latestSnapshot", latestSnapshot(id));
        result.put("correctiveActions", correctiveActionsBySchedule(id));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> replaceTasks(Long scheduleId, WbsTaskBatch batch) {
        Map<String, Object> schedule = requireEditableSchedule(scheduleId);
        projectAccessChecker.checkAccess(longValue(schedule.get("project_id")), "维护WBS");
        Integer periods = jdbc.queryForObject("SELECT COUNT(*) FROM project_period_plan WHERE tenant_id=? AND schedule_plan_id=? AND deleted_flag=0", Integer.class, tenant(), scheduleId);
        if (periods != null && periods > 0) throw error("PROJECT_WBS_PERIOD_EXISTS", "已生成月周计划后不能替换WBS");
        validateTaskBatch(batch.tasks(), localDate(schedule.get("planned_start_date")), localDate(schedule.get("planned_end_date")));

        jdbc.update("DELETE FROM project_wbs_task WHERE tenant_id=? AND schedule_plan_id=?", tenant(), scheduleId);
        Map<String, Long> ids = new LinkedHashMap<>();
        for (WbsTaskRequest item : batch.tasks()) ids.put(item.taskCode().trim(), IdWorker.getId());
        int sort = 0;
        for (WbsTaskRequest item : batch.tasks()) {
            Long parentId = text(item.parentTaskCode()) == null ? null : ids.get(item.parentTaskCode().trim());
            Long predecessorId = text(item.predecessorTaskCode()) == null ? null : ids.get(item.predecessorTaskCode().trim());
            jdbc.update("""
                    INSERT INTO project_wbs_task(id,tenant_id,project_id,schedule_plan_id,parent_task_id,predecessor_task_id,
                     task_code,task_name,work_area,responsible_user_id,planned_start_date,planned_end_date,weight_percent,
                     planned_quantity,unit,actual_quantity,actual_progress,status,sort_order,version,created_by,created_at,
                     updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,0,'NOT_STARTED',?,0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, ids.get(item.taskCode().trim()), tenant(), schedule.get("project_id"), scheduleId, parentId, predecessorId,
                    item.taskCode().trim(), item.taskName().trim(), text(item.workArea()), item.responsibleUserId(),
                    item.plannedStartDate(), item.plannedEndDate(), scale(item.weightPercent()), item.plannedQuantity(), text(item.unit()),
                    sort++, user(), user(), item.remark());
        }
        return schedule(scheduleId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitSchedule(Long id) {
        Map<String, Object> row = requireEditableSchedule(id);
        projectAccessChecker.checkAccess(longValue(row.get("project_id")), "提交项目计划");
        List<Map<String, Object>> tasks = taskRows(id);
        if (tasks.isEmpty()) throw error("PROJECT_WBS_REQUIRED", "项目计划至少包含一条WBS任务");
        BigDecimal total = tasks.stream().map(t -> decimal(t.get("weight_percent"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(HUNDRED) != 0) throw error("PROJECT_WBS_WEIGHT_INVALID", "WBS任务权重合计必须等于100%");
        WfInstance instance = workflowInstance(row, WorkflowBusinessTypes.PROJECT_SCHEDULE, id,
                string(row.get("plan_code")), "项目基线/修订计划", longValue(row.get("project_id")));
        jdbc.update("UPDATE project_schedule_plan SET status='PENDING',approval_instance_id=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                instance.getId(), user(), id, tenant());
        return schedule(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void onScheduleApproved(Long id) {
        Map<String, Object> row = requireSchedule(id, true);
        if (ACTIVE.equals(string(row.get("status")))) return;
        if (!"PENDING".equals(string(row.get("status")))) throw error("PROJECT_SCHEDULE_APPROVAL_STATE_INVALID", "项目计划审批状态不正确");
        Long projectId = longValue(row.get("project_id"));
        jdbc.update("UPDATE project_schedule_plan SET status='SUPERSEDED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND project_id=? AND status='ACTIVE' AND id<>?", tenant(), projectId, id);
        int changed = jdbc.update("UPDATE project_schedule_plan SET status='ACTIVE',activated_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='PENDING'", id, tenant());
        if (changed != 1) throw error("PROJECT_SCHEDULE_APPROVAL_STATE_INVALID", "项目计划审批状态不正确");
    }

    @Transactional(rollbackFor = Exception.class)
    public void onScheduleRejected(Long id) {
        jdbc.update("UPDATE project_schedule_plan SET status='REJECTED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='PENDING'", id, tenant());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createPeriodPlan(PeriodPlanRequest request) {
        Map<String, Object> schedule = requireSchedule(request.schedulePlanId(), false);
        Long projectId = longValue(schedule.get("project_id"));
        projectAccessChecker.checkAccess(projectId, "创建月周计划");
        if (!ACTIVE.equals(string(schedule.get("status")))) throw error("PROJECT_PERIOD_ACTIVE_SCHEDULE_REQUIRED", "只能基于已生效项目计划创建月周计划");
        requireDates(request.startDate(), request.endDate(), "PROJECT_PERIOD_DATE_INVALID", "月周计划起止日期不合法");
        if (request.startDate().isBefore(localDate(schedule.get("planned_start_date"))) || request.endDate().isAfter(localDate(schedule.get("planned_end_date"))))
            throw error("PROJECT_PERIOD_OUTSIDE_SCHEDULE", "月周计划日期必须位于项目计划周期内");
        validatePeriodParent(request, schedule);
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO project_period_plan(id,tenant_id,project_id,schedule_plan_id,parent_period_plan_id,period_type,
                     period_code,period_name,start_date,end_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,'DRAFT',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), projectId, request.schedulePlanId(), request.parentPeriodPlanId(), request.periodType(),
                    request.periodCode().trim(), request.periodName().trim(), request.startDate(), request.endDate(), user(), user(), request.remark());
        } catch (DuplicateKeyException e) {
            throw error("PROJECT_PERIOD_DUPLICATE", "同一项目月周计划编码不能重复");
        }
        return periodPlan(id);
    }

    public List<Map<String, Object>> periodPlans(Long scheduleId) {
        return jdbc.queryForList("""
                SELECT p.id,p.project_id projectId,p.schedule_plan_id schedulePlanId,p.parent_period_plan_id parentPeriodPlanId,
                 p.period_type periodType,p.period_code periodCode,p.period_name periodName,p.start_date startDate,p.end_date endDate,
                 p.status,p.approval_instance_id approvalInstanceId,p.remark
                FROM project_period_plan p WHERE p.tenant_id=? AND p.schedule_plan_id=? AND p.deleted_flag=0
                ORDER BY p.start_date,p.period_type,p.id
                """, tenant(), scheduleId);
    }

    public Map<String, Object> periodPlan(Long id) {
        Map<String, Object> row = new LinkedHashMap<>(requirePeriod(id));
        projectAccessChecker.checkAccess(longValue(row.get("project_id")), "查看月周计划");
        row.put("items", jdbc.queryForList("""
                SELECT i.id,i.wbs_task_id wbsTaskId,t.task_code taskCode,t.task_name taskName,
                 i.target_progress targetProgress,i.planned_quantity plannedQuantity,t.actual_progress actualProgress
                FROM project_period_plan_item i JOIN project_wbs_task t ON t.id=i.wbs_task_id
                WHERE i.tenant_id=? AND i.period_plan_id=? ORDER BY t.sort_order,t.id
                """, tenant(), id));
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> replacePeriodItems(Long periodId, PeriodItemBatch batch) {
        Map<String, Object> period = requirePeriod(periodId);
        projectAccessChecker.checkAccess(longValue(period.get("project_id")), "维护月周计划");
        if (!Set.of("DRAFT", "REJECTED").contains(string(period.get("status")))) throw error("PROJECT_PERIOD_IMMUTABLE", "非草稿或驳回状态的月周计划不能修改");
        Set<Long> unique = new HashSet<>();
        for (PeriodItemRequest item : batch.items()) {
            if (!unique.add(item.wbsTaskId())) throw error("PROJECT_PERIOD_ITEM_DUPLICATE", "月周计划任务不能重复");
            Map<String, Object> task = requireTask(item.wbsTaskId());
            if (!Objects.equals(longValue(task.get("schedule_plan_id")), longValue(period.get("schedule_plan_id"))))
                throw error("PROJECT_PERIOD_TASK_MISMATCH", "月周计划任务必须属于同一项目计划");
            if (item.targetProgress().compareTo(decimal(task.get("actual_progress"))) < 0)
                throw error("PROJECT_PERIOD_TARGET_BELOW_ACTUAL", "计划目标进度不能低于当前实际进度");
        }
        jdbc.update("DELETE FROM project_period_plan_item WHERE tenant_id=? AND period_plan_id=?", tenant(), periodId);
        for (PeriodItemRequest item : batch.items()) {
            jdbc.update("INSERT INTO project_period_plan_item(id,tenant_id,period_plan_id,wbs_task_id,target_progress,planned_quantity,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)",
                    IdWorker.getId(), tenant(), periodId, item.wbsTaskId(), scale(item.targetProgress()), item.plannedQuantity(), user(), user());
        }
        return periodPlan(periodId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitPeriodPlan(Long id) {
        Map<String, Object> row = requirePeriod(id);
        projectAccessChecker.checkAccess(longValue(row.get("project_id")), "提交月周计划");
        if (!Set.of("DRAFT", "REJECTED").contains(string(row.get("status")))) throw error("PROJECT_PERIOD_SUBMIT_STATE_INVALID", "月周计划当前状态不能提交");
        Integer items = jdbc.queryForObject("SELECT COUNT(*) FROM project_period_plan_item WHERE tenant_id=? AND period_plan_id=?", Integer.class, tenant(), id);
        if (items == null || items == 0) throw error("PROJECT_PERIOD_ITEM_REQUIRED", "月周计划至少包含一条WBS任务");
        WfInstance instance = workflowInstance(row, WorkflowBusinessTypes.PROJECT_PERIOD_PLAN, id,
                string(row.get("period_code")), "项目月周计划", longValue(row.get("project_id")));
        jdbc.update("UPDATE project_period_plan SET status='PENDING',approval_instance_id=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", instance.getId(), user(), id, tenant());
        return periodPlan(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void onPeriodApproved(Long id) {
        if (jdbc.update("UPDATE project_period_plan SET status='APPROVED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='PENDING'", id, tenant()) != 1)
            throw error("PROJECT_PERIOD_APPROVAL_STATE_INVALID", "月周计划审批状态不正确");
    }

    @Transactional(rollbackFor = Exception.class)
    public void onPeriodRejected(Long id) {
        jdbc.update("UPDATE project_period_plan SET status='REJECTED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='PENDING'", id, tenant());
    }

    public List<Map<String, Object>> dailyProgress(Long dailyLogId) {
        Map<String, Object> log = requireDailyLog(dailyLogId);
        projectAccessChecker.checkAccess(longValue(log.get("project_id")), "查看日报实际进度");
        return jdbc.queryForList("""
                SELECT d.id,d.daily_log_id dailyLogId,d.schedule_plan_id schedulePlanId,d.weekly_plan_id weeklyPlanId,
                 d.wbs_task_id wbsTaskId,t.task_code taskCode,t.task_name taskName,d.previous_progress previousProgress,
                 d.current_progress currentProgress,d.completed_quantity completedQuantity,d.work_description workDescription
                FROM site_daily_progress d JOIN project_wbs_task t ON t.id=d.wbs_task_id
                WHERE d.tenant_id=? AND d.daily_log_id=? ORDER BY t.sort_order,t.id
                """, tenant(), dailyLogId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> replaceDailyProgress(Long dailyLogId, DailyProgressBatch batch) {
        Map<String, Object> log = requireDailyLog(dailyLogId);
        Long projectId = longValue(log.get("project_id"));
        projectAccessChecker.checkAccess(projectId, "填报日报实际进度");
        if (!"DRAFT".equals(string(log.get("status")))) throw error("SITE_DAILY_PROGRESS_IMMUTABLE", "现场日报提交后不能修改实际进度");
        Map<String, Object> schedule = activeSchedule(projectId);
        LocalDate reportDate = localDate(log.get("report_date"));
        Map<String, Object> weekly = approvedWeeklyPlan(longValue(schedule.get("id")), reportDate);
        Set<Long> allowedTasks = new HashSet<>(jdbc.queryForList("SELECT wbs_task_id FROM project_period_plan_item WHERE tenant_id=? AND period_plan_id=?", Long.class, tenant(), weekly.get("id")));
        Set<Long> unique = new HashSet<>();
        for (DailyProgressRequest item : batch.items()) {
            if (!unique.add(item.wbsTaskId())) throw error("SITE_DAILY_PROGRESS_DUPLICATE", "同一日报WBS任务不能重复填报");
            if (!allowedTasks.contains(item.wbsTaskId())) throw error("SITE_DAILY_PROGRESS_OUTSIDE_WEEKLY_PLAN", "日报实际进度只能填报当周已审批计划内任务");
            Map<String, Object> task = requireTask(item.wbsTaskId());
            if (!Objects.equals(longValue(task.get("schedule_plan_id")), longValue(schedule.get("id"))))
                throw error("SITE_DAILY_PROGRESS_TASK_MISMATCH", "日报任务不属于当前生效项目计划");
            if (item.currentProgress().compareTo(decimal(task.get("actual_progress"))) < 0)
                throw error("SITE_DAILY_PROGRESS_ROLLBACK", "实际进度不能小于已确认进度");
            BigDecimal plannedQuantity = decimalNullable(task.get("planned_quantity"));
            if (plannedQuantity != null && item.completedQuantity().compareTo(plannedQuantity) > 0)
                throw error("SITE_DAILY_PROGRESS_QUANTITY_EXCEEDED", "累计完成量不能超过WBS计划工程量");
        }
        jdbc.update("DELETE FROM site_daily_progress WHERE tenant_id=? AND daily_log_id=?", tenant(), dailyLogId);
        for (DailyProgressRequest item : batch.items()) {
            Map<String, Object> task = requireTask(item.wbsTaskId());
            jdbc.update("""
                    INSERT INTO site_daily_progress(id,tenant_id,daily_log_id,project_id,schedule_plan_id,weekly_plan_id,wbs_task_id,
                     previous_progress,current_progress,completed_quantity,work_description,created_by,created_at,updated_by,updated_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)
                    """, IdWorker.getId(), tenant(), dailyLogId, projectId, schedule.get("id"), weekly.get("id"), item.wbsTaskId(),
                    task.get("actual_progress"), scale(item.currentProgress()), item.completedQuantity(), item.workDescription().trim(), user(), user());
        }
        return dailyProgress(dailyLogId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void onDailyLogSubmitted(SiteDailyLog log) {
        Optional<Map<String, Object>> active = findActiveSchedule(log.getProjectId());
        if (active.isEmpty()) return;
        List<Map<String, Object>> progress = jdbc.queryForList("SELECT * FROM site_daily_progress WHERE tenant_id=? AND daily_log_id=? ORDER BY id", tenant(), log.getId());
        if (progress.isEmpty()) throw error("SITE_DAILY_PROGRESS_REQUIRED", "已启用项目计划的项目提交现场日报时必须填报实际进度");
        approvedWeeklyPlan(longValue(active.get().get("id")), log.getReportDate());
        for (Map<String, Object> entry : progress) {
            Map<String, Object> task = requireTask(longValue(entry.get("wbs_task_id")));
            BigDecimal current = decimal(entry.get("current_progress"));
            if (current.compareTo(decimal(task.get("actual_progress"))) < 0)
                throw error("SITE_DAILY_PROGRESS_CONCURRENT_ROLLBACK", "WBS实际进度已被其他日报推进，请刷新后重新填报");
            String status = current.compareTo(HUNDRED) == 0 ? "COMPLETED" : current.signum() > 0 ? "IN_PROGRESS" : "NOT_STARTED";
            int changed = jdbc.update("""
                    UPDATE project_wbs_task SET actual_progress=?,actual_quantity=?,status=?,
                     actual_start_date=CASE WHEN actual_start_date IS NULL AND ?>0 THEN ? ELSE actual_start_date END,
                     actual_end_date=CASE WHEN ?=100 THEN ? ELSE actual_end_date END,
                     version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                    WHERE id=? AND tenant_id=? AND schedule_plan_id=? AND actual_progress<=?
                    """, current, entry.get("completed_quantity"), status, current, log.getReportDate(), current, log.getReportDate(), user(),
                    task.get("id"), tenant(), active.get().get("id"), current);
            if (changed != 1)
                throw error("SITE_DAILY_PROGRESS_CONCURRENT_ROLLBACK", "WBS实际进度已被其他日报推进，请刷新后重新填报");
        }
        createSnapshot(longValue(active.get().get("id")), log.getReportDate(), log.getId());
    }

    public List<SiteDailyPlannedTaskVO> approvedTasksForDailyLog(Long projectId, LocalDate reportDate) {
        Optional<Map<String, Object>> schedule = findActiveSchedule(projectId);
        if (schedule.isEmpty()) return List.of();
        try {
            Map<String, Object> weekly = approvedWeeklyPlan(longValue(schedule.get().get("id")), reportDate);
            return jdbc.queryForList("""
                    SELECT t.* FROM project_period_plan_item i JOIN project_wbs_task t ON t.id=i.wbs_task_id
                    WHERE i.tenant_id=? AND i.period_plan_id=? ORDER BY t.sort_order,t.task_code
                    """, tenant(), weekly.get("id")).stream().map(this::toPlannedTask).toList();
        } catch (BusinessException e) {
            return List.of();
        }
    }

    public boolean hasActiveSchedule(Long projectId) {
        return findActiveSchedule(projectId).isPresent();
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> calculateSnapshot(Long scheduleId, LocalDate date) {
        Map<String, Object> schedule = requireSchedule(scheduleId, false);
        projectAccessChecker.checkAccess(longValue(schedule.get("project_id")), "分析项目进度偏差");
        return createSnapshot(scheduleId, date, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createCorrectiveAction(Long scheduleId, CorrectiveActionRequest request) {
        Map<String, Object> snapshot = requireSnapshot(request.snapshotId());
        if (!scheduleId.equals(longValue(snapshot.get("schedule_plan_id"))))
            throw error("PROJECT_CORRECTIVE_SCHEDULE_MISMATCH", "路径计划与偏差快照所属计划不一致");
        Long projectId = longValue(snapshot.get("project_id"));
        projectAccessChecker.checkAccess(projectId, "创建进度纠偏");
        if (!Set.of("LAGGING", "OVERDUE").contains(string(snapshot.get("status"))))
            throw error("PROJECT_CORRECTIVE_LAG_REQUIRED", "只有延期或逾期快照可以发起纠偏");
        if (request.dueDate().isBefore(LocalDate.now())) throw error("PROJECT_CORRECTIVE_DUE_DATE_INVALID", "纠偏完成期限不能早于当前日期");
        Long alertId = findAlertId(request.snapshotId());
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO project_corrective_action(id,tenant_id,project_id,schedule_plan_id,snapshot_id,alert_id,action_code,
                     reason,action_plan,responsible_user_id,due_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,'DRAFT',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), projectId, snapshot.get("schedule_plan_id"), request.snapshotId(), alertId,
                    request.actionCode().trim(), request.reason().trim(), request.actionPlan().trim(), request.responsibleUserId(),
                    request.dueDate(), user(), user(), request.remark());
        } catch (DuplicateKeyException e) {
            throw error("PROJECT_CORRECTIVE_DUPLICATE", "该偏差快照已有纠偏单或纠偏编码重复");
        }
        return correctiveAction(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitCorrectiveAction(Long id) {
        Map<String, Object> row = requireCorrective(id);
        projectAccessChecker.checkAccess(longValue(row.get("project_id")), "提交进度纠偏");
        if (!Set.of("DRAFT", "REJECTED").contains(string(row.get("status")))) throw error("PROJECT_CORRECTIVE_SUBMIT_STATE_INVALID", "纠偏单当前状态不能提交");
        WfInstance instance = workflowInstance(row, WorkflowBusinessTypes.PROJECT_CORRECTIVE_ACTION, id,
                string(row.get("action_code")), "项目进度纠偏", longValue(row.get("project_id")));
        jdbc.update("UPDATE project_corrective_action SET status='PENDING',approval_instance_id=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", instance.getId(), user(), id, tenant());
        return correctiveAction(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void onCorrectiveApproved(Long id) {
        Map<String, Object> action = requireCorrective(id);
        if ("APPROVED".equals(string(action.get("status")))) return;
        if (!"PENDING".equals(string(action.get("status")))) throw error("PROJECT_CORRECTIVE_APPROVAL_STATE_INVALID", "纠偏审批状态不正确");
        Long revisionId = createRevision(action);
        jdbc.update("UPDATE project_corrective_action SET status='APPROVED',generated_revision_plan_id=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='PENDING'", revisionId, id, tenant());
        if (action.get("alert_id") != null) jdbc.update("UPDATE alert_log SET process_status='PROCESSED',processed_at=CURRENT_TIMESTAMP,status_remark='纠偏审批通过，已生成计划修订草稿',updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", user(), action.get("alert_id"), tenant());
    }

    @Transactional(rollbackFor = Exception.class)
    public void onCorrectiveRejected(Long id) {
        jdbc.update("UPDATE project_corrective_action SET status='REJECTED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='PENDING'", id, tenant());
    }

    public Map<String, Object> correctiveAction(Long id) {
        Map<String, Object> row = new LinkedHashMap<>(requireCorrective(id));
        projectAccessChecker.checkAccess(longValue(row.get("project_id")), "查看进度纠偏");
        return row;
    }

    public Map<String, Object> trace(Long scheduleId) {
        Map<String, Object> schedule = requireSchedule(scheduleId, false);
        projectAccessChecker.checkAccess(longValue(schedule.get("project_id")), "追溯项目计划履约");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schedule", schedule);
        result.put("wbsTasks", taskRows(scheduleId));
        result.put("periodPlans", periodPlans(scheduleId));
        result.put("dailyProgress", jdbc.queryForList("SELECT d.*,l.report_date reportDate,l.status dailyLogStatus FROM site_daily_progress d JOIN site_daily_log l ON l.id=d.daily_log_id WHERE d.tenant_id=? AND d.schedule_plan_id=? ORDER BY l.report_date,d.id", tenant(), scheduleId));
        result.put("snapshots", jdbc.queryForList("SELECT * FROM project_progress_snapshot WHERE tenant_id=? AND schedule_plan_id=? ORDER BY snapshot_date,id", tenant(), scheduleId));
        result.put("alerts", jdbc.queryForList("""
                SELECT a.* FROM alert_log a
                JOIN project_progress_snapshot s ON s.id=a.source_id AND s.tenant_id=a.tenant_id
                WHERE a.tenant_id=? AND a.source_type='PROJECT_PROGRESS_SNAPSHOT'
                  AND s.schedule_plan_id=? AND a.deleted_flag=0
                ORDER BY a.triggered_at,a.id
                """, tenant(), scheduleId));
        result.put("correctiveActions", correctiveActionsBySchedule(scheduleId));
        result.put("revisions", jdbc.queryForList("SELECT * FROM project_schedule_plan WHERE tenant_id=? AND parent_plan_id=? AND deleted_flag=0 ORDER BY version_no", tenant(), scheduleId));
        return result;
    }

    private Map<String, Object> createSnapshot(Long scheduleId, LocalDate date, Long dailyLogId) {
        Map<String, Object> schedule = requireSchedule(scheduleId, false);
        List<Map<String, Object>> tasks = taskRows(scheduleId);
        if (tasks.isEmpty()) throw error("PROJECT_WBS_REQUIRED", "项目计划缺少WBS任务");
        BigDecimal planned = BigDecimal.ZERO;
        BigDecimal actual = BigDecimal.ZERO;
        int lagging = 0;
        for (Map<String, Object> task : tasks) {
            BigDecimal weight = decimal(task.get("weight_percent")).divide(HUNDRED, 8, RoundingMode.HALF_UP);
            BigDecimal taskPlanned = plannedProgress(task, date);
            BigDecimal taskActual = decimal(task.get("actual_progress"));
            planned = planned.add(weight.multiply(taskPlanned));
            actual = actual.add(weight.multiply(taskActual));
            if (taskActual.add(new BigDecimal("0.0001")).compareTo(taskPlanned) < 0) lagging++;
        }
        planned = scale(planned); actual = scale(actual);
        BigDecimal deviation = scale(actual.subtract(planned));
        String status = actual.compareTo(HUNDRED) == 0 ? "COMPLETED"
                : date.isAfter(localDate(schedule.get("planned_end_date"))) && actual.compareTo(HUNDRED) < 0 ? "OVERDUE"
                : deviation.compareTo(BigDecimal.ZERO) < 0 ? "LAGGING" : "ON_TRACK";
        Long snapshotId;
        try {
            snapshotId = jdbc.queryForObject("SELECT id FROM project_progress_snapshot WHERE tenant_id=? AND schedule_plan_id=? AND snapshot_date=?", Long.class, tenant(), scheduleId, date);
            jdbc.update("UPDATE project_progress_snapshot SET source_daily_log_id=COALESCE(?,source_daily_log_id),planned_progress=?,actual_progress=?,deviation_percent=?,lagging_task_count=?,status=? WHERE id=? AND tenant_id=?",
                    dailyLogId, planned, actual, deviation, lagging, status, snapshotId, tenant());
        } catch (EmptyResultDataAccessException e) {
            snapshotId = IdWorker.getId();
            jdbc.update("INSERT INTO project_progress_snapshot(id,tenant_id,project_id,schedule_plan_id,snapshot_date,source_daily_log_id,planned_progress,actual_progress,deviation_percent,lagging_task_count,status,formula_version,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,'SCHEDULE_PROGRESS_V1',?,CURRENT_TIMESTAMP)",
                    snapshotId, tenant(), schedule.get("project_id"), scheduleId, date, dailyLogId, planned, actual, deviation, lagging, status, user());
        }
        if (Set.of("LAGGING", "OVERDUE").contains(status)) createDelayAlert(snapshotId, schedule, date, planned, actual, deviation);
        return requireSnapshot(snapshotId);
    }

    private void createDelayAlert(Long snapshotId, Map<String, Object> schedule, LocalDate date, BigDecimal planned, BigDecimal actual, BigDecimal deviation) {
        Map<String, Object> config;
        try {
            config = jdbc.queryForMap("SELECT * FROM alert_rule_config WHERE tenant_id IN(0,?) AND rule_type='PROJECT_PROGRESS_DELAY' AND enabled=1 AND deleted_flag=0 ORDER BY CASE WHEN tenant_id=? THEN 0 ELSE 1 END LIMIT 1", tenant(), tenant());
        } catch (EmptyResultDataAccessException e) {
            return;
        }
        BigDecimal threshold = decimal(config.get("threshold_ratio")).multiply(HUNDRED);
        if (deviation.abs().compareTo(threshold) < 0) return;
        String dedupKey = "S:PROJECT_PROGRESS_SNAPSHOT:" + snapshotId + ":R:PROJECT_PROGRESS_DELAY";
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM alert_log WHERE tenant_id=? AND dedup_key=? AND process_status IN('OPEN','PROCESSED') AND deleted_flag=0", Integer.class, tenant(), dedupKey);
        if (exists != null && exists > 0) return;
        jdbc.update("""
                INSERT INTO alert_log(id,tenant_id,project_id,contract_id,alert_domain,alert_category,source_type,source_id,dedup_key,
                 rule_type,severity,message,triggered_at,is_read,process_status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                VALUES(?,?,?,NULL,'SCHEDULE','PROGRESS_DELAY','PROJECT_PROGRESS_SNAPSHOT',?,?,'PROJECT_PROGRESS_DELAY',?, ?,CURRENT_TIMESTAMP,0,'OPEN',?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                """, IdWorker.getId(), tenant(), schedule.get("project_id"), snapshotId, dedupKey,
                text(config.get("severity_override")) == null ? "HIGH" : config.get("severity_override"),
                "项目计划进度延期：" + date + " 计划" + planned + "%、实际" + actual + "%、偏差" + deviation + "个百分点",
                user(), user(), "计划版本=" + schedule.get("version_no"));
    }

    private Long createRevision(Map<String, Object> action) {
        Map<String, Object> source = requireSchedule(longValue(action.get("schedule_plan_id")), true);
        Integer versionNo = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM project_schedule_plan WHERE tenant_id=? AND project_id=? AND deleted_flag=0", Integer.class, tenant(), source.get("project_id"));
        Long revisionId = IdWorker.getId();
        String code = string(source.get("plan_code")) + "-R" + versionNo;
        jdbc.update("""
                INSERT INTO project_schedule_plan(id,tenant_id,project_id,plan_code,plan_name,plan_type,version_no,parent_plan_id,corrective_action_id,
                 planned_start_date,planned_end_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                VALUES(?,?,?,?,?,'REVISION',?,?,?,?,?,'DRAFT',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                """, revisionId, tenant(), source.get("project_id"), code, string(source.get("plan_name")) + "（修订" + versionNo + "）",
                versionNo, source.get("id"), action.get("id"), source.get("planned_start_date"), source.get("planned_end_date"), user(), user(), "由纠偏单" + action.get("action_code") + "自动生成");
        List<Map<String, Object>> sourceTasks = taskRows(longValue(source.get("id")));
        Map<Long, Long> ids = new HashMap<>();
        for (Map<String, Object> task : sourceTasks) ids.put(longValue(task.get("id")), IdWorker.getId());
        for (Map<String, Object> task : sourceTasks) {
            jdbc.update("""
                    INSERT INTO project_wbs_task(id,tenant_id,project_id,schedule_plan_id,parent_task_id,predecessor_task_id,task_code,task_name,
                     work_area,responsible_user_id,planned_start_date,planned_end_date,weight_percent,planned_quantity,unit,actual_start_date,
                     actual_end_date,actual_quantity,actual_progress,status,sort_order,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, ids.get(longValue(task.get("id"))), tenant(), source.get("project_id"), revisionId,
                    task.get("parent_task_id") == null ? null : ids.get(longValue(task.get("parent_task_id"))),
                    task.get("predecessor_task_id") == null ? null : ids.get(longValue(task.get("predecessor_task_id"))),
                    task.get("task_code"), task.get("task_name"), task.get("work_area"), task.get("responsible_user_id"),
                    task.get("planned_start_date"), task.get("planned_end_date"), task.get("weight_percent"), task.get("planned_quantity"),
                    task.get("unit"), task.get("actual_start_date"), task.get("actual_end_date"), task.get("actual_quantity"),
                    task.get("actual_progress"), task.get("status"), task.get("sort_order"), user(), user(), task.get("remark"));
        }
        return revisionId;
    }

    private void validateTaskBatch(List<WbsTaskRequest> tasks, LocalDate scheduleStart, LocalDate scheduleEnd) {
        Map<String, WbsTaskRequest> byCode = new LinkedHashMap<>();
        for (WbsTaskRequest task : tasks) {
            String code = task.taskCode().trim();
            if (byCode.put(code, task) != null) throw error("PROJECT_WBS_CODE_DUPLICATE", "WBS任务编码不能重复");
            requireDates(task.plannedStartDate(), task.plannedEndDate(), "PROJECT_WBS_DATE_INVALID", "WBS任务起止日期不合法");
            if (task.plannedStartDate().isBefore(scheduleStart) || task.plannedEndDate().isAfter(scheduleEnd))
                throw error("PROJECT_WBS_OUTSIDE_SCHEDULE", "WBS任务日期必须位于项目计划周期内");
        }
        for (WbsTaskRequest task : tasks) {
            validateReference(task.taskCode(), task.parentTaskCode(), byCode, "父任务");
            validateReference(task.taskCode(), task.predecessorTaskCode(), byCode, "前置任务");
        }
        for (String code : byCode.keySet()) detectCycle(code, byCode, true, new HashSet<>(), new HashSet<>());
        for (String code : byCode.keySet()) detectCycle(code, byCode, false, new HashSet<>(), new HashSet<>());
    }

    private void detectCycle(String code, Map<String, WbsTaskRequest> byCode, boolean parent, Set<String> visiting, Set<String> visited) {
        if (visited.contains(code)) return;
        if (!visiting.add(code)) throw error("PROJECT_WBS_CYCLE", parent ? "WBS父子层级存在循环" : "WBS前置关系存在循环");
        WbsTaskRequest task = byCode.get(code);
        String next = parent ? text(task.parentTaskCode()) : text(task.predecessorTaskCode());
        if (next != null) detectCycle(next, byCode, parent, visiting, visited);
        visiting.remove(code); visited.add(code);
    }

    private void validateReference(String self, String reference, Map<String, WbsTaskRequest> byCode, String label) {
        String ref = text(reference);
        if (ref == null) return;
        if (self.equals(ref)) throw error("PROJECT_WBS_SELF_REFERENCE", "WBS任务不能将自身设为" + label);
        if (!byCode.containsKey(ref)) throw error("PROJECT_WBS_REFERENCE_NOT_FOUND", label + "必须属于当前WBS");
    }

    private void validatePeriodParent(PeriodPlanRequest request, Map<String, Object> schedule) {
        if ("MONTHLY".equals(request.periodType())) {
            if (request.parentPeriodPlanId() != null) throw error("PROJECT_MONTHLY_PARENT_FORBIDDEN", "月计划不能设置上级周期计划");
            return;
        }
        if (request.parentPeriodPlanId() == null) throw error("PROJECT_WEEKLY_MONTH_REQUIRED", "周计划必须关联已审批月计划");
        Map<String, Object> parent = requirePeriod(request.parentPeriodPlanId());
        if (!"MONTHLY".equals(string(parent.get("period_type"))) || !"APPROVED".equals(string(parent.get("status"))))
            throw error("PROJECT_WEEKLY_MONTH_NOT_APPROVED", "周计划必须关联已审批月计划");
        if (!Objects.equals(longValue(parent.get("schedule_plan_id")), longValue(schedule.get("id"))))
            throw error("PROJECT_WEEKLY_MONTH_MISMATCH", "周计划和月计划必须属于同一项目计划");
        if (request.startDate().isBefore(localDate(parent.get("start_date"))) || request.endDate().isAfter(localDate(parent.get("end_date"))))
            throw error("PROJECT_WEEKLY_OUTSIDE_MONTH", "周计划日期必须位于所属月计划周期内");
    }

    private WfInstance workflowInstance(Map<String, Object> row, String businessType, Long businessId, String title, String summary, Long projectId) {
        Long existing = longValueNullable(row.get("approval_instance_id"));
        if ("REJECTED".equals(string(row.get("status"))) && existing != null)
            return workflowEngine.resubmit(existing, user(), UserContext.getCurrentUsername());
        return workflowEngine.submit(user(), UserContext.getCurrentUsername(), tenant(), businessType, businessId,
                title, BigDecimal.ZERO, projectId, null, summary, null, null);
    }

    private Map<String, Object> requireEditableSchedule(Long id) {
        Map<String, Object> row = requireSchedule(id, true);
        if (!Set.of("DRAFT", "REJECTED").contains(string(row.get("status")))) throw error("PROJECT_SCHEDULE_IMMUTABLE", "非草稿或驳回状态的项目计划不能修改");
        return row;
    }

    private Map<String, Object> requireSchedule(Long id, boolean lock) {
        return queryOne("SELECT * FROM project_schedule_plan WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""),
                "PROJECT_SCHEDULE_NOT_FOUND", "项目计划不存在", id, tenant());
    }

    private Map<String, Object> requirePeriod(Long id) {
        return queryOne("SELECT * FROM project_period_plan WHERE id=? AND tenant_id=? AND deleted_flag=0", "PROJECT_PERIOD_NOT_FOUND", "月周计划不存在", id, tenant());
    }

    private Map<String, Object> requireTask(Long id) {
        return queryOne("SELECT * FROM project_wbs_task WHERE id=? AND tenant_id=? AND deleted_flag=0", "PROJECT_WBS_NOT_FOUND", "WBS任务不存在", id, tenant());
    }

    private Map<String, Object> requireDailyLog(Long id) {
        return queryOne("SELECT * FROM site_daily_log WHERE id=? AND tenant_id=? AND deleted_flag=0", "SITE_DAILY_LOG_NOT_FOUND", "现场日报不存在", id, tenant());
    }

    private Map<String, Object> requireSnapshot(Long id) {
        return queryOne("SELECT * FROM project_progress_snapshot WHERE id=? AND tenant_id=?", "PROJECT_PROGRESS_SNAPSHOT_NOT_FOUND", "进度偏差快照不存在", id, tenant());
    }

    private Map<String, Object> requireCorrective(Long id) {
        return queryOne("SELECT * FROM project_corrective_action WHERE id=? AND tenant_id=? AND deleted_flag=0", "PROJECT_CORRECTIVE_NOT_FOUND", "进度纠偏单不存在", id, tenant());
    }

    private Map<String, Object> queryOne(String sql, String code, String message, Object... args) {
        try { return jdbc.queryForMap(sql, args); }
        catch (EmptyResultDataAccessException e) { throw error(code, message); }
    }

    private Optional<Map<String, Object>> findActiveSchedule(Long projectId) {
        try { return Optional.of(jdbc.queryForMap("SELECT * FROM project_schedule_plan WHERE tenant_id=? AND project_id=? AND status='ACTIVE' AND deleted_flag=0 ORDER BY version_no DESC LIMIT 1", tenant(), projectId)); }
        catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    private Map<String, Object> activeSchedule(Long projectId) {
        return findActiveSchedule(projectId).orElseThrow(() -> error("PROJECT_ACTIVE_SCHEDULE_REQUIRED", "项目尚未启用基线计划"));
    }

    private Map<String, Object> approvedWeeklyPlan(Long scheduleId, LocalDate date) {
        return queryOne("SELECT * FROM project_period_plan WHERE tenant_id=? AND schedule_plan_id=? AND period_type='WEEKLY' AND status='APPROVED' AND deleted_flag=0 AND start_date<=? AND end_date>=? ORDER BY start_date DESC LIMIT 1",
                "PROJECT_APPROVED_WEEKLY_PLAN_REQUIRED", "日报日期必须存在已审批周计划", tenant(), scheduleId, date, date);
    }

    private List<Map<String, Object>> taskRows(Long scheduleId) {
        return jdbc.queryForList("""
                SELECT t.*,p.task_code parent_task_code,d.task_code predecessor_task_code
                FROM project_wbs_task t
                LEFT JOIN project_wbs_task p ON p.id=t.parent_task_id AND p.tenant_id=t.tenant_id
                LEFT JOIN project_wbs_task d ON d.id=t.predecessor_task_id AND d.tenant_id=t.tenant_id
                WHERE t.tenant_id=? AND t.schedule_plan_id=? AND t.deleted_flag=0
                ORDER BY t.sort_order,t.id
                """, tenant(), scheduleId);
    }

    private void requireExecutableProject(Long projectId) {
        String status;
        try {
            status = jdbc.queryForObject("SELECT status FROM pm_project WHERE id=? AND tenant_id=? AND deleted_flag=0",
                    String.class, projectId, tenant());
        } catch (EmptyResultDataAccessException e) {
            throw error("PROJECT_NOT_FOUND", "项目不存在");
        }
        if (!"ACTIVE".equals(status))
            throw error("PROJECT_NOT_ACTIVE", "只有进行中的项目可以创建项目计划");
    }

    private Map<String, Object> latestSnapshot(Long scheduleId) {
        try { return jdbc.queryForMap("SELECT * FROM project_progress_snapshot WHERE tenant_id=? AND schedule_plan_id=? ORDER BY snapshot_date DESC,id DESC LIMIT 1", tenant(), scheduleId); }
        catch (EmptyResultDataAccessException e) { return Map.of(); }
    }

    private List<Map<String, Object>> correctiveActionsBySchedule(Long scheduleId) {
        return jdbc.queryForList("SELECT * FROM project_corrective_action WHERE tenant_id=? AND schedule_plan_id=? AND deleted_flag=0 ORDER BY created_at DESC,id DESC", tenant(), scheduleId);
    }

    private Long findAlertId(Long snapshotId) {
        try { return jdbc.queryForObject("SELECT id FROM alert_log WHERE tenant_id=? AND source_type='PROJECT_PROGRESS_SNAPSHOT' AND source_id=? AND deleted_flag=0 ORDER BY triggered_at DESC LIMIT 1", Long.class, tenant(), snapshotId); }
        catch (EmptyResultDataAccessException e) { return null; }
    }

    private BigDecimal plannedProgress(Map<String, Object> task, LocalDate date) {
        LocalDate start = localDate(task.get("planned_start_date"));
        LocalDate end = localDate(task.get("planned_end_date"));
        if (date.isBefore(start)) return BigDecimal.ZERO;
        if (!date.isBefore(end)) return HUNDRED;
        long total = ChronoUnit.DAYS.between(start, end) + 1;
        long elapsed = ChronoUnit.DAYS.between(start, date) + 1;
        return BigDecimal.valueOf(elapsed).multiply(HUNDRED).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    private SiteDailyPlannedTaskVO toPlannedTask(Map<String, Object> task) {
        SiteDailyPlannedTaskVO result = new SiteDailyPlannedTaskVO();
        result.setId(string(task.get("id"))); result.setTaskCode(string(task.get("task_code")));
        result.setTaskName(string(task.get("task_name"))); result.setWorkArea(string(task.get("work_area")));
        result.setPlannedStartDate(string(task.get("planned_start_date"))); result.setPlannedEndDate(string(task.get("planned_end_date")));
        result.setStatus(string(task.get("status"))); result.setProgressPercent(string(task.get("actual_progress")));
        return result;
    }

    private void requireDates(LocalDate start, LocalDate end, String code, String message) {
        if (start == null || end == null || start.isAfter(end)) throw error(code, message);
    }

    private Long tenant() { return UserContext.getCurrentTenantId(); }
    private Long user() { return UserContext.getCurrentUserId(); }
    private BigDecimal scale(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }
    private BigDecimal decimal(Object value) { return value == null ? BigDecimal.ZERO : value instanceof BigDecimal b ? b : new BigDecimal(value.toString()); }
    private BigDecimal decimalNullable(Object value) { return value == null ? null : decimal(value); }
    private Long longValue(Object value) { return value instanceof Number n ? n.longValue() : Long.valueOf(value.toString()); }
    private Long longValueNullable(Object value) { return value == null ? null : longValue(value); }
    private String string(Object value) { return value == null ? null : value.toString(); }
    private String text(Object value) { return value == null || value.toString().isBlank() ? null : value.toString().trim(); }
    private LocalDate localDate(Object value) { return value instanceof LocalDate d ? d : value instanceof Date d ? d.toLocalDate() : LocalDate.parse(value.toString()); }
    private BusinessException error(String code, String message) { return new BusinessException(code, message); }
}
