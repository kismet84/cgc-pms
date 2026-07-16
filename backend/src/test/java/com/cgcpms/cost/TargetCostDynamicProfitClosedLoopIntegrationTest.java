package com.cgcpms.cost;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.dto.CostControlModels.CorrectiveActionRequest;
import com.cgcpms.cost.dto.CostControlModels.CorrectiveCloseRequest;
import com.cgcpms.cost.dto.CostControlModels.ForecastItemRequest;
import com.cgcpms.cost.dto.CostControlModels.ForecastRequest;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.entity.CostTargetItem;
import com.cgcpms.cost.service.CostControlService;
import com.cgcpms.cost.service.CostTargetService;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class TargetCostDynamicProfitClosedLoopIntegrationTest {
    private static final long PROJECT = 99187001L;
    private static final long SUBJECT_A = 99187002L;
    private static final long SUBJECT_B = 99187003L;
    private static final long PARTNER = 99187004L;
    private static final long MAIN_CONTRACT = 99187005L;

    @Autowired CostTargetService targetService;
    @Autowired CostControlService controlService;
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
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,contract_amount,target_cost,status,approval_status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'COST-CTRL-IT','动态利润闭环测试项目',12000,0,'ACTIVE','APPROVED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", PROJECT);
        jdbc.update("INSERT INTO cost_subject(id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,created_at,updated_at,deleted_flag) VALUES(?,0,0,'CTRL-A','主体工程','DETAIL','COST',1,1,'ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", SUBJECT_A);
        jdbc.update("INSERT INTO cost_subject(id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,created_at,updated_at,deleted_flag) VALUES(?,0,0,'CTRL-B','措施工程','DETAIL','COST',1,2,'ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", SUBJECT_B);
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_at,updated_at,deleted_flag) VALUES(?,0,'CTRL-P','业主单位','CUSTOMER','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", PARTNER);
        jdbc.update("INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,contract_status,approval_status,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,'CTRL-MAIN','业主总包合同','MAIN',?,?,12000,12000,0,'PERFORMING','APPROVED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", MAIN_CONTRACT, PROJECT, PARTNER, PARTNER);
    }

    @AfterEach
    void teardown() {
        cleanup();
        UserContext.clear();
    }

    @Test
    void closesTenderTargetResponsibilityForecastCorrectionAndProfitChain() {
        long targetId = approveTarget();
        assertEquals(new BigDecimal("8000.00"), jdbc.queryForObject("SELECT target_cost FROM pm_project WHERE id=?", BigDecimal.class, PROJECT));

        insertCost(99187101L, SUBJECT_A, "CT_CONTRACT", "CONTRACT_LOCKED", "7000.00");
        jdbc.update("INSERT INTO cost_item(id,tenant_id,project_id,contract_id,cost_subject_id,cost_type,amount,tax_amount,amount_without_tax,source_type,source_id,source_item_id,cost_date,cost_status,generated_flag,created_by,created_at,updated_at,deleted_flag) VALUES(99187104,0,?,?,?,'CONTRACT_LOCKED',12000,0,12000,'CT_CONTRACT',?,?,?,'CONFIRMED',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",
                PROJECT, MAIN_CONTRACT, SUBJECT_A, MAIN_CONTRACT, 99187104L, LocalDate.now());
        insertCost(99187102L, SUBJECT_A, "MAT_RECEIPT", "MATERIAL_COST", "4500.00");
        insertCost(99187103L, SUBJECT_B, "SUB_MEASURE", "SUBCONTRACT_COST", "1000.00");

        ForecastRequest forecastRequest = new ForecastRequest(PROJECT, "FC-IT-001", "首期完工预测", LocalDate.now(), List.of(
                new ForecastItemRequest(SUBJECT_A, new BigDecimal("2500.00"), "主体剩余"),
                new ForecastItemRequest(SUBJECT_B, new BigDecimal("1000.00"), "措施剩余")), "月度滚动预测");
        long forecastId = id(controlService.createForecast(forecastRequest));
        Map<String, Object> confirmed = controlService.confirmForecast(forecastId);

        assertEquals(targetId, number(confirmed.get("cost_target_id")));
        assertEquals("ACTION_REQUIRED", confirmed.get("status"));
        assertMoney("10000.00", confirmed.get("bid_cost_amount"));
        assertMoney("8000.00", confirmed.get("target_cost_amount"));
        assertMoney("8000.00", confirmed.get("responsibility_amount"));
        assertMoney("7000.00", confirmed.get("committed_cost_amount"));
        assertMoney("5500.00", confirmed.get("actual_cost_amount"));
        assertMoney("9000.00", confirmed.get("forecast_at_completion_amount"));
        assertMoney("1000.00", confirmed.get("cost_variance_amount"));
        assertMoney("3000.00", confirmed.get("forecast_profit_amount"));
        assertEquals(new BigDecimal("0.250000"), new BigDecimal(String.valueOf(confirmed.get("profit_margin"))));
        assertEquals(forecastId, jdbc.queryForObject("SELECT cost_forecast_id FROM cost_summary WHERE project_id=? LIMIT 1", Long.class, PROJECT));

        BusinessException duplicateConfirm = assertThrows(BusinessException.class, () -> controlService.confirmForecast(forecastId));
        assertEquals("COST_FORECAST_ALREADY_CONFIRMED", duplicateConfirm.getCode());
        BusinessException skippedAction = assertThrows(BusinessException.class, () -> controlService.createForecast(
                new ForecastRequest(PROJECT, "FC-IT-002", "跳过纠偏", LocalDate.now(), forecastRequest.items(), null)));
        assertEquals("COST_FORECAST_PREVIOUS_ACTION_REQUIRED", skippedAction.getCode());

        long actionId = id(controlService.createCorrectiveAction(new CorrectiveActionRequest(
                forecastId, "CA-IT-001", "压降措施成本", "措施投入超出责任预算", "优化租赁周期并复核现场签证",
                new BigDecimal("1000.00"), 1L, LocalDate.now().plusDays(10), "正偏差纠偏")));
        BusinessException draftActionStillOpen = assertThrows(BusinessException.class, () -> controlService.createForecast(
                new ForecastRequest(PROJECT, "FC-IT-003", "纠偏草稿未闭合", LocalDate.now(), forecastRequest.items(), null)));
        assertEquals("COST_FORECAST_PREVIOUS_ACTION_REQUIRED", draftActionStillOpen.getCode());
        controlService.submitCorrectiveAction(actionId);
        approveAll("COST_CORRECTIVE_ACTION", actionId);
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM cost_corrective_action WHERE id=?", String.class, actionId));
        controlService.closeCorrectiveAction(actionId, new CorrectiveCloseRequest(new BigDecimal("800.00"), "措施已执行，剩余偏差纳入下期预测"));
        assertEquals("CLOSED", jdbc.queryForObject("SELECT status FROM cost_corrective_action WHERE id=?", String.class, actionId));
        assertEquals("CONTROLLED", jdbc.queryForObject("SELECT status FROM cost_forecast WHERE id=?", String.class, forecastId));

        Map<String, Object> trace = controlService.trace(forecastId);
        assertNotNull(trace.get("project"));
        assertEquals(targetId, id(cast(trace.get("target"))));
        assertFalse(((List<?>) trace.get("targetItems")).isEmpty());
        assertFalse(((List<?>) trace.get("forecastItems")).isEmpty());
        assertFalse(((List<?>) trace.get("correctiveActions")).isEmpty());
        assertFalse(((List<?>) trace.get("approvalInstances")).isEmpty());
        assertFalse(((List<?>) trace.get("costSources")).isEmpty());
    }

    @Test
    void rejectsIncompleteResponsibilityInactiveProjectAndSavingOverflow() {
        CostTarget invalid = target("TC-INVALID");
        invalid.setTotalResponsibilityAmount(new BigDecimal("7000.00"));
        BusinessException mismatch = assertThrows(BusinessException.class, () -> targetService.create(invalid));
        assertEquals("COST_TARGET_RESPONSIBILITY_MISMATCH", mismatch.getCode());

        approveTarget();
        ForecastRequest request = new ForecastRequest(PROJECT, "FC-EDGE", "边界预测", LocalDate.now(), List.of(
                new ForecastItemRequest(SUBJECT_A, new BigDecimal("7000"), null),
                new ForecastItemRequest(SUBJECT_B, new BigDecimal("2000"), null)), null);
        long forecastId = id(controlService.createForecast(request));
        controlService.confirmForecast(forecastId);
        BusinessException overflow = assertThrows(BusinessException.class, () -> controlService.createCorrectiveAction(
                new CorrectiveActionRequest(forecastId, "CA-OVER", "超额措施", "偏差", "措施", new BigDecimal("1001"), 1L, LocalDate.now().plusDays(1), null)));
        assertEquals("COST_CORRECTIVE_SAVING_EXCEEDS_VARIANCE", overflow.getCode());

        jdbc.update("UPDATE pm_project SET status='SUSPENDED' WHERE id=?", PROJECT);
        BusinessException suspended = assertThrows(BusinessException.class, () -> controlService.createCorrectiveAction(
                new CorrectiveActionRequest(forecastId, "CA-SUSPEND", "暂停项目措施", "偏差", "措施", new BigDecimal("500"), 1L, LocalDate.now().plusDays(1), null)));
        assertEquals("PROJECT_NOT_ACTIVE", suspended.getCode());
    }

    private long approveTarget() {
        CostTarget target = target("TC-IT-001");
        long targetId = targetService.create(target);
        CostTargetItem a = item(SUBJECT_A, "6000", "7500", "6000", "项目成本组");
        CostTargetItem b = item(SUBJECT_B, "2000", "2500", "2000", "项目工程组");
        targetService.batchSaveItems(targetId, List.of(a, b));
        targetService.submitForApproval(targetId);
        approveAll("COST_TARGET", targetId);
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM cost_target WHERE id=?", String.class, targetId));
        return targetId;
    }

    private CostTarget target(String version) {
        CostTarget target = new CostTarget();
        target.setProjectId(PROJECT);
        target.setVersionNo(version);
        target.setVersionName("目标成本与责任预算");
        target.setTotalBidCostAmount(new BigDecimal("10000.00"));
        target.setTotalTargetAmount(new BigDecimal("8000.00"));
        target.setTotalResponsibilityAmount(new BigDecimal("8000.00"));
        return target;
    }

    private CostTargetItem item(long subject, String target, String bid, String responsibility, String unit) {
        CostTargetItem item = new CostTargetItem();
        item.setCostSubjectId(subject);
        item.setTargetAmount(new BigDecimal(target));
        item.setBidCostAmount(new BigDecimal(bid));
        item.setResponsibilityAmount(new BigDecimal(responsibility));
        item.setResponsibleUserId(1L);
        item.setResponsibilityUnit(unit);
        return item;
    }

    private void insertCost(long id, long subject, String sourceType, String costType, String amount) {
        jdbc.update("INSERT INTO cost_item(id,tenant_id,project_id,cost_subject_id,cost_type,amount,tax_amount,amount_without_tax,source_type,source_id,source_item_id,cost_date,cost_status,generated_flag,created_by,created_at,updated_at,deleted_flag) VALUES(?,0,?,?,?, ?,0,?, ?,?,?,?,'CONFIRMED',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",
                id, PROJECT, subject, costType, new BigDecimal(amount), new BigDecimal(amount), sourceType, id, id, LocalDate.now());
    }

    private void approveAll(String businessType, long businessId) {
        WfInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBusinessType, businessType).eq(WfInstance::getBusinessId, businessId));
        assertNotNull(instance);
        for (int i = 0; i < 10; i++) {
            List<WfTask> pending = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                    .eq(WfTask::getInstanceId, instance.getId()).eq(WfTask::getTaskStatus, "PENDING"));
            if (pending.isEmpty()) break;
            for (WfTask task : pending) workflowEngine.approve(task.getId(), 1L, "admin", "同意", "cost-control-it-" + UUID.randomUUID());
        }
        assertEquals("APPROVED", instanceMapper.selectById(instance.getId()).getInstanceStatus());
    }

    private void cleanup() {
        jdbc.update("UPDATE cost_target SET approval_instance_id=NULL WHERE project_id=?", PROJECT);
        jdbc.update("UPDATE cost_corrective_action SET approval_instance_id=NULL WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM cost_summary WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM cost_corrective_action WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM cost_forecast_item WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM cost_forecast WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM cost_target_item WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM cost_target WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM cost_item WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM wf_record WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('COST_TARGET','COST_CORRECTIVE_ACTION'))", PROJECT);
        jdbc.update("DELETE FROM wf_task WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('COST_TARGET','COST_CORRECTIVE_ACTION'))", PROJECT);
        jdbc.update("DELETE FROM wf_node_instance WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('COST_TARGET','COST_CORRECTIVE_ACTION'))", PROJECT);
        jdbc.update("DELETE FROM wf_cc WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type IN('COST_TARGET','COST_CORRECTIVE_ACTION'))", PROJECT);
        jdbc.update("DELETE FROM wf_instance WHERE project_id=? AND business_type IN('COST_TARGET','COST_CORRECTIVE_ACTION')", PROJECT);
        jdbc.update("DELETE FROM ct_contract WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM md_partner WHERE id=?", PARTNER);
        jdbc.update("DELETE FROM cost_subject WHERE id IN(?,?)", SUBJECT_A, SUBJECT_B);
        jdbc.update("DELETE FROM pm_project WHERE id=?", PROJECT);
    }

    private static long id(Map<String, Object> row) { return number(row.get("id")); }
    private static long number(Object value) { return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value)); }
    @SuppressWarnings("unchecked") private static Map<String, Object> cast(Object value) { return (Map<String, Object>) value; }
    private static void assertMoney(String expected, Object actual) { assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(String.valueOf(actual)))); }
}
