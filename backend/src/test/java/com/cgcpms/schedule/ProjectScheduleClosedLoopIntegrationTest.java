package com.cgcpms.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.schedule.dto.ProjectScheduleModels.*;
import com.cgcpms.schedule.service.ProjectScheduleService;
import com.cgcpms.site.service.SiteDailyLogService;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class ProjectScheduleClosedLoopIntegrationTest {
    private static final long PROJECT = 99185001L;
    private static final long DAILY_LOG = 99185002L;

    @Autowired ProjectScheduleService service;
    @Autowired SiteDailyLogService dailyLogService;
    @Autowired WorkflowEngine workflowEngine;
    @Autowired WfInstanceMapper instanceMapper;
    @Autowired WfTaskMapper taskMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        UserContext.set(Jwts.claims().subject("admin").add("userId", 1L).add("username", "admin")
                .add("tenantId", 0L).add("roleCodes", List.of("ADMIN")).build());
        cleanup();
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) SELECT 1,0,'admin','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','系统管理员','ENABLE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0 WHERE NOT EXISTS(SELECT 1 FROM sys_user WHERE id=1)");
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'SCHEDULE-IT-P','计划履约测试项目','ACTIVE',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", PROJECT);
    }

    @AfterEach
    void teardown() {
        cleanup();
        UserContext.clear();
    }

    @Test
    void fullChainFromBaselineToDailyDeviationCorrectiveAndRevision() {
        long baseline = createAndActivateBaseline();
        long task = jdbc.queryForObject("SELECT id FROM project_wbs_task WHERE schedule_plan_id=?", Long.class, baseline);
        long month = createAndApprovePeriod(baseline, null, "MONTHLY", "M-2099-07",
                LocalDate.of(2099, 7, 1), LocalDate.of(2099, 7, 31), task, new BigDecimal("100"));
        createAndApprovePeriod(baseline, month, "WEEKLY", "W-2099-07-03",
                LocalDate.of(2099, 7, 14), LocalDate.of(2099, 7, 20), task, new BigDecimal("70"));

        jdbc.update("INSERT INTO site_daily_log(id,tenant_id,project_id,report_date,construction_content,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,'2099-07-20','完成基础施工','DRAFT',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", DAILY_LOG, PROJECT);
        service.replaceDailyProgress(DAILY_LOG, new DailyProgressBatch(List.of(
                new DailyProgressRequest(task, new BigDecimal("20"), new BigDecimal("20"), "基础工程累计完成20%"))));
        dailyLogService.submit(DAILY_LOG);

        assertEquals("SUBMITTED", jdbc.queryForObject("SELECT status FROM site_daily_log WHERE id=?", String.class, DAILY_LOG));
        assertEquals(new BigDecimal("20.0000"), jdbc.queryForObject("SELECT actual_progress FROM project_wbs_task WHERE id=?", BigDecimal.class, task));
        Map<String,Object> snapshot = jdbc.queryForMap("SELECT * FROM project_progress_snapshot WHERE source_daily_log_id=?", DAILY_LOG);
        assertEquals("LAGGING", snapshot.get("status"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM alert_log WHERE source_type='PROJECT_PROGRESS_SNAPSHOT' AND source_id=? AND rule_type='PROJECT_PROGRESS_DELAY'", Integer.class, snapshot.get("id")));

        CorrectiveActionRequest correctiveRequest = new CorrectiveActionRequest(
                id(snapshot), "COR-2099-001", "实际资源投入不足", "增加作业班组并调整后续任务日期", 1L,
                LocalDate.of(2099, 7, 31), "偏差纠正");
        BusinessException mismatch = assertThrows(BusinessException.class,
                () -> service.createCorrectiveAction(baseline + 1, correctiveRequest));
        assertEquals("PROJECT_CORRECTIVE_SCHEDULE_MISMATCH", mismatch.getCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM project_corrective_action WHERE snapshot_id=?", Integer.class, id(snapshot)));

        Map<String,Object> corrective = service.createCorrectiveAction(baseline, correctiveRequest);
        long correctiveId = id(corrective);
        service.submitCorrectiveAction(correctiveId);
        approveAll("PROJECT_CORRECTIVE_ACTION", correctiveId);
        long revision = jdbc.queryForObject("SELECT generated_revision_plan_id FROM project_corrective_action WHERE id=?", Long.class, correctiveId);
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM project_schedule_plan WHERE id=?", String.class, revision));
        assertEquals(new BigDecimal("20.0000"), jdbc.queryForObject("SELECT actual_progress FROM project_wbs_task WHERE schedule_plan_id=?", BigDecimal.class, revision));

        service.submitSchedule(revision);
        approveAll("PROJECT_SCHEDULE", revision);
        assertEquals("SUPERSEDED", jdbc.queryForObject("SELECT status FROM project_schedule_plan WHERE id=?", String.class, baseline));
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM project_schedule_plan WHERE id=?", String.class, revision));

        Map<String,Object> trace = service.trace(baseline);
        assertFalse(((List<?>) trace.get("dailyProgress")).isEmpty());
        assertFalse(((List<?>) trace.get("snapshots")).isEmpty());
        assertFalse(((List<?>) trace.get("alerts")).isEmpty());
        assertFalse(((List<?>) trace.get("correctiveActions")).isEmpty());
        assertFalse(((List<?>) trace.get("revisions")).isEmpty());
    }

    @Test
    void rejectsInvalidWeightsWeeklyWithoutApprovedMonthAndProgressRollback() {
        long schedule = id(service.createSchedule(new ScheduleRequest(PROJECT, "BASE-INVALID", "错误权重基线",
                LocalDate.of(2099, 7, 1), LocalDate.of(2099, 7, 31), null)));
        service.replaceTasks(schedule, new WbsTaskBatch(List.of(task("T1", new BigDecimal("90")))));
        BusinessException weightError = assertThrows(BusinessException.class, () -> service.submitSchedule(schedule));
        assertEquals("PROJECT_WBS_WEIGHT_INVALID", weightError.getCode());

        service.replaceTasks(schedule, new WbsTaskBatch(List.of(task("T1", new BigDecimal("100")))));
        service.submitSchedule(schedule);
        approveAll("PROJECT_SCHEDULE", schedule);
        long taskId = jdbc.queryForObject("SELECT id FROM project_wbs_task WHERE schedule_plan_id=?", Long.class, schedule);
        BusinessException monthError = assertThrows(BusinessException.class, () -> service.createPeriodPlan(new PeriodPlanRequest(
                schedule, "WEEKLY", null, "W-NO-MONTH", "无月计划周计划", LocalDate.of(2099, 7, 1), LocalDate.of(2099, 7, 7), null)));
        assertEquals("PROJECT_WEEKLY_MONTH_REQUIRED", monthError.getCode());

        long month = createAndApprovePeriod(schedule, null, "MONTHLY", "M-ROLLBACK", LocalDate.of(2099, 7, 1), LocalDate.of(2099, 7, 31), taskId, new BigDecimal("100"));
        createAndApprovePeriod(schedule, month, "WEEKLY", "W-ROLLBACK", LocalDate.of(2099, 7, 1), LocalDate.of(2099, 7, 7), taskId, new BigDecimal("50"));
        jdbc.update("UPDATE project_wbs_task SET actual_progress=30 WHERE id=?", taskId);
        jdbc.update("INSERT INTO site_daily_log(id,tenant_id,project_id,report_date,construction_content,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,'2099-07-05','测试回退','DRAFT',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", DAILY_LOG, PROJECT);
        BusinessException rollback = assertThrows(BusinessException.class, () -> service.replaceDailyProgress(DAILY_LOG,
                new DailyProgressBatch(List.of(new DailyProgressRequest(taskId, new BigDecimal("20"), new BigDecimal("20"), "错误回退")))));
        assertEquals("SITE_DAILY_PROGRESS_ROLLBACK", rollback.getCode());

        jdbc.update("UPDATE project_wbs_task SET actual_progress=0 WHERE id=?", taskId);
        service.replaceDailyProgress(DAILY_LOG,
                new DailyProgressBatch(List.of(new DailyProgressRequest(taskId, new BigDecimal("20"), new BigDecimal("20"), "并发填报"))));
        jdbc.update("UPDATE project_wbs_task SET actual_progress=30 WHERE id=?", taskId);
        BusinessException concurrent = assertThrows(BusinessException.class, () -> dailyLogService.submit(DAILY_LOG));
        assertEquals("SITE_DAILY_PROGRESS_CONCURRENT_ROLLBACK", concurrent.getCode());
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM site_daily_log WHERE id=?", String.class, DAILY_LOG));
        assertEquals(new BigDecimal("30.0000"), jdbc.queryForObject("SELECT actual_progress FROM project_wbs_task WHERE id=?", BigDecimal.class, taskId));

        jdbc.update("UPDATE pm_project SET status='SUSPENDED' WHERE id=?", PROJECT);
        BusinessException suspended = assertThrows(BusinessException.class, () -> service.createSchedule(new ScheduleRequest(
                PROJECT, "BASE-SUSPENDED", "暂停项目计划", LocalDate.of(2099, 8, 1), LocalDate.of(2099, 8, 31), null)));
        assertEquals("PROJECT_NOT_ACTIVE", suspended.getCode());
    }

    private long createAndActivateBaseline() {
        long id = id(service.createSchedule(new ScheduleRequest(PROJECT, "BASE-2099-01", "项目基线计划",
                LocalDate.of(2099, 7, 1), LocalDate.of(2099, 7, 31), "首版基线")));
        service.replaceTasks(id, new WbsTaskBatch(List.of(task("WBS-001", new BigDecimal("100")))));
        service.submitSchedule(id);
        approveAll("PROJECT_SCHEDULE", id);
        return id;
    }

    private WbsTaskRequest task(String code, BigDecimal weight) {
        return new WbsTaskRequest(code, "基础工程", null, null, "1号楼", 1L,
                LocalDate.of(2099, 7, 1), LocalDate.of(2099, 7, 31), weight,
                new BigDecimal("100"), "%", null);
    }

    private long createAndApprovePeriod(long schedule, Long parent, String type, String code,
                                        LocalDate start, LocalDate end, long task, BigDecimal target) {
        long id = id(service.createPeriodPlan(new PeriodPlanRequest(schedule, type, parent, code, code, start, end, null)));
        service.replacePeriodItems(id, new PeriodItemBatch(List.of(new PeriodItemRequest(task, target, target))));
        service.submitPeriodPlan(id);
        approveAll("PROJECT_PERIOD_PLAN", id);
        return id;
    }

    private void approveAll(String businessType, long businessId) {
        WfInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBusinessType, businessType).eq(WfInstance::getBusinessId, businessId));
        assertNotNull(instance);
        for (int i = 0; i < 10; i++) {
            List<WfTask> pending = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                    .eq(WfTask::getInstanceId, instance.getId()).eq(WfTask::getTaskStatus, "PENDING"));
            if (pending.isEmpty()) break;
            for (WfTask task : pending) workflowEngine.approve(task.getId(), 1L, "admin", "同意", "schedule-it-" + UUID.randomUUID());
        }
        assertEquals("APPROVED", instanceMapper.selectById(instance.getId()).getInstanceStatus());
    }

    private long id(Map<?,?> row) { return ((Number) row.get("id")).longValue(); }

    private void cleanup() {
        jdbc.update("UPDATE project_schedule_plan SET corrective_action_id=NULL,parent_plan_id=NULL WHERE project_id=?", PROJECT);
        jdbc.update("UPDATE project_corrective_action SET generated_revision_plan_id=NULL WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM project_corrective_action WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM alert_log WHERE project_id=? AND rule_type='PROJECT_PROGRESS_DELAY'", PROJECT);
        jdbc.update("DELETE FROM project_progress_snapshot WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM site_daily_progress WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM project_period_plan_item WHERE period_plan_id IN(SELECT id FROM project_period_plan WHERE project_id=?)", PROJECT);
        jdbc.update("UPDATE project_period_plan SET parent_period_plan_id=NULL WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM project_period_plan WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM site_daily_log WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM project_wbs_task WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM project_schedule_plan WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM wf_record WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('PROJECT_SCHEDULE','PROJECT_PERIOD_PLAN','PROJECT_CORRECTIVE_ACTION'))", PROJECT);
        jdbc.update("DELETE FROM wf_task WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('PROJECT_SCHEDULE','PROJECT_PERIOD_PLAN','PROJECT_CORRECTIVE_ACTION'))", PROJECT);
        jdbc.update("DELETE FROM wf_node_instance WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('PROJECT_SCHEDULE','PROJECT_PERIOD_PLAN','PROJECT_CORRECTIVE_ACTION'))", PROJECT);
        jdbc.update("DELETE FROM wf_cc WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('PROJECT_SCHEDULE','PROJECT_PERIOD_PLAN','PROJECT_CORRECTIVE_ACTION'))", PROJECT);
        jdbc.update("DELETE FROM wf_instance WHERE project_id=? AND business_type IN('PROJECT_SCHEDULE','PROJECT_PERIOD_PLAN','PROJECT_CORRECTIVE_ACTION')", PROJECT);
        jdbc.update("DELETE FROM pm_project WHERE id=?", PROJECT);
    }
}
