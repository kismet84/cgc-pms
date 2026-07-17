package com.cgcpms.financeops;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.financeops.dto.FinanceOperationsModels.*;
import com.cgcpms.financeops.service.FinanceAnalyticsService;
import com.cgcpms.financeops.service.FinanceIntegrationService;
import com.cgcpms.financeops.service.FinanceOperationsService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class FinanceOperationsIntegrationTest {
    static final long PROJECT=98400101L, CONTRACT=98400102L, SUBJECT1=98400103L, SUBJECT2=98400104L;
    static final long PARTNER=98400105L, BUDGET=98400106L, LINE1=98400107L, LINE2=98400108L;
    static final long ACCOUNT1=98400109L, ACCOUNT2=98400110L;
    static final long REVENUE_CONTRACT=98400111L;
    @Autowired FinanceOperationsService operations;
    @Autowired FinanceAnalyticsService analytics;
    @Autowired FinanceIntegrationService integrations;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        UserContext.set(Jwts.claims().add("userId",1L).add("username","admin").add("tenantId",0L)
                .add("roleCodes", List.of("ADMIN")).build());
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'FINOPS-IT','资金运营集成测试','ACTIVE',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)",PROJECT);
        jdbc.update("INSERT INTO cost_subject(id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,created_at,updated_at,deleted_flag) VALUES(?,0,0,'FINOPS-01','人工费','DETAIL','COST',1,1,'ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",SUBJECT1);
        jdbc.update("INSERT INTO cost_subject(id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,created_at,updated_at,deleted_flag) VALUES(?,0,0,'FINOPS-02','材料费','DETAIL','COST',1,2,'ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",SUBJECT2);
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_at,updated_at,deleted_flag) VALUES(?,0,'FINOPS-P','测试供应商','SUPPLIER','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",PARTNER);
        jdbc.update("INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,contract_status,approval_status,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,'FINOPS-C','测试合同','SUBCONTRACT',?,?,2000,2000,0,'PERFORMING','APPROVED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",CONTRACT,PROJECT,PARTNER,PARTNER);
        jdbc.update("INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,contract_status,approval_status,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,'FINOPS-RC','测试业主合同','MAIN',?,?,2000,2000,0,'PERFORMING','APPROVED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",REVENUE_CONTRACT,PROJECT,PARTNER,PARTNER);
        jdbc.update("INSERT INTO project_budget(id,tenant_id,project_id,version_no,budget_name,total_amount,approval_status,status,active_flag,active_token,effective_at,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,'V1','测试预算',1000,'APPROVED','ACTIVE',1,?,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",BUDGET,PROJECT,PROJECT);
        jdbc.update("INSERT INTO project_budget_line(id,tenant_id,budget_id,project_id,cost_subject_id,budget_amount,reserved_amount,consumed_amount,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,?,?,600,100,100,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",LINE1,BUDGET,PROJECT,SUBJECT1);
        jdbc.update("INSERT INTO project_budget_line(id,tenant_id,budget_id,project_id,cost_subject_id,budget_amount,reserved_amount,consumed_amount,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,?,?,400,0,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",LINE2,BUDGET,PROJECT,SUBJECT2);
        jdbc.update("INSERT INTO fund_account(id,tenant_id,account_code,account_name,account_type,opening_date,opening_balance,enabled_flag,version,created_at,updated_at,deleted_flag) VALUES(?,0,'FINOPS-A1','账户1','BANK',CURRENT_DATE,1000,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",ACCOUNT1);
        jdbc.update("INSERT INTO fund_account(id,tenant_id,account_code,account_name,account_type,opening_date,opening_balance,enabled_flag,version,created_at,updated_at,deleted_flag) VALUES(?,0,'FINOPS-A2','账户2','BANK',CURRENT_DATE,1000,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",ACCOUNT2);
    }

    @Test
    void p1BudgetScheduleAlertAndReconciliationAreExecutable() {
        var adjusted=operations.adjustBudget(new BudgetAdjustmentRequest(LINE1,new BigDecimal("100"),"追加人工费","IT-ADJUST"));
        assertEquals("ADJUST",adjusted.get("operation_type"));
        operations.transferBudget(new BudgetTransferRequest(LINE1,LINE2,new BigDecimal("50"),"调拨材料费","IT-TRANSFER"));
        assertEquals(0,new BigDecimal("650").compareTo(jdbc.queryForObject("SELECT budget_amount FROM project_budget_line WHERE id=?",BigDecimal.class,LINE1)));
        var schedule=operations.createSchedule(new PaymentScheduleRequest(PROJECT,CONTRACT,null,"本月进度款",LocalDate.now(),new BigDecimal("200"),7));
        assertNotNull(schedule.get("id"));
        assertTrue(((Number)operations.generateAlerts().get("open")).longValue()>=1);
        Map<String,Object> alert = operations.alerts("OPEN").getFirst();
        long alertId = ((Number) alert.get("id")).longValue();
        assertEquals(alertId, ((Number) alert.get("alert_log_id")).longValue(),
                "运营队列必须链接驾驶舱权威预警事实");
        assertEquals("OPEN", jdbc.queryForObject(
                "SELECT process_status FROM alert_log WHERE id=? AND tenant_id=0", String.class, alertId));
        operations.handleAlert(alertId, new AlertHandleRequest("RESOLVED", "集成测试闭环"));
        assertEquals("PROCESSED", jdbc.queryForObject(
                "SELECT process_status FROM alert_log WHERE id=? AND tenant_id=0", String.class, alertId),
                "处理运营预警必须同步权威预警状态");
        var run=operations.runReconciliation(LocalDate.now());
        assertTrue(String.valueOf(run.get("status")).startsWith("COMPLETED"));
        assertFalse(operations.budgetVersionComparison(PROJECT).isEmpty());
    }

    @Test
    void p2SnapshotOcrImportAndRoutingAreExecutable() {
        var snapshot=analytics.rebuildSnapshot(PROJECT,LocalDate.now(),"FULL_REBUILD");
        assertEquals(PROJECT,((Number)snapshot.get("project_id")).longValue());
        var preview=analytics.previewImport(new ImportPreviewRequest("BUDGET_LINE",PROJECT,"budget.xlsx","hash-finops",
                List.of(new ImportRow(1,Map.of("budgetId",BUDGET,"costSubjectId",SUBJECT1,"budgetAmount","700.00")))));
        assertEquals(0,((Number)preview.get("invalid_rows")).intValue());
        analytics.applyImport(((Number)preview.get("id")).longValue());
        assertEquals(0,new BigDecimal("700").compareTo(jdbc.queryForObject("SELECT budget_amount FROM project_budget_line WHERE id=?",BigDecimal.class,LINE1)));
        var rule=analytics.createRoutingRule(new RoutingRuleRequest("付款默认路由","PAY_REQUEST",BigDecimal.ZERO,new BigDecimal("1000"),"SUBCONTRACT","LABOR",50010L,10,true));
        assertEquals(50010L,((Number)rule.get("workflow_template_id")).longValue());
        assertEquals(rule.get("id"),analytics.matchRouting(new RoutingMatchRequest("PAY_REQUEST",new BigDecimal("500"),"SUBCONTRACT","LABOR")).get("id"));
    }

    @Test
    void p3OutboxForecastAndFundPoolAreExecutable() {
        var endpoint=integrations.createEndpoint(new IntegrationEndpointRequest("BANK","BANK-IT","测试银行",null,"vault://bank","secret",Map.of()));
        long endpointId=((Number)endpoint.get("id")).longValue();
        var message=integrations.enqueue(new IntegrationMessageRequest(endpointId,"PAYMENT","PAY_RECORD",1L,"OUT-1",Map.of("amount","100")));
        assertEquals("PENDING",message.get("status"));
        assertEquals(1,integrations.leaseOutbound(endpointId,10).size());
        assertEquals("SUCCEEDED",integrations.acknowledgeOutbound(((Number)message.get("id")).longValue(),true,Map.of("ok",true),null).get("status"));
        assertEquals("SUCCEEDED",integrations.acceptCallback("BANK-IT","secret",new IntegrationCallbackRequest("IN-1","RECEIPT","BANK_RECEIPT",null,Map.of("ok",true))).get("status"));
        var receipt=integrations.ingestBankReceipt(new BankReceiptRequest(endpointId,"BANK-IN-1","****0001",LocalDateTime.now(),
                "IN",new BigDecimal("100.00"),"测试供应商","工程回款",PROJECT,REVENUE_CONTRACT,PARTNER,ACCOUNT1,List.of(),Map.of("source","test")));
        assertEquals("MATCHED",receipt.get("match_status"));
        assertNotNull(receipt.get("collection_record_id"));
        var duplicate=integrations.ingestBankReceipt(new BankReceiptRequest(endpointId,"BANK-IN-1","****0001",LocalDateTime.now(),
                "IN",new BigDecimal("100.00"),"测试供应商","重复回调",PROJECT,REVENUE_CONTRACT,PARTNER,ACCOUNT1,List.of(),Map.of("source","retry")));
        assertEquals(receipt.get("collection_record_id"),duplicate.get("collection_record_id"));
        var trace=integrations.traceBankReceipt(((Number)receipt.get("id")).longValue());
        assertNotNull(trace.get("collection"));
        assertNotNull(trace.get("cashJournal"));
        assertFalse(((List<?>)trace.get("accountingEntries")).isEmpty());
        integrations.createForecast(new CashForecastRequest(PROJECT,LocalDate.now(),"BASE",new BigDecimal("100"),new BigDecimal("40"),BigDecimal.ZERO,"MANUAL",null,BigDecimal.ONE));
        assertEquals(0,new BigDecimal("60").compareTo((BigDecimal)integrations.forecastSummary("BASE",LocalDate.now(),LocalDate.now()).get("endingBalance")));
        var pool=integrations.createFundPool(new FundPoolRequest("POOL-IT","测试资金池","CNY","QUOTA"));
        long poolId=((Number)pool.get("id")).longValue();
        long m1=((Number)integrations.addFundPoolMember(new FundPoolMemberRequest(poolId,1L,ACCOUNT1,new BigDecimal("500"))).get("id")).longValue();
        long m2=((Number)integrations.addFundPoolMember(new FundPoolMemberRequest(poolId,2L,ACCOUNT2,new BigDecimal("100"))).get("id")).longValue();
        assertEquals("COMPLETED",integrations.transferFundPool(new FundPoolTransferRequest(poolId,m1,m2,new BigDecimal("50"),"POOL-TXN-1",null,LocalDateTime.now())).get("status"));
        assertEquals(2,((List<?>)integrations.fundPoolView(poolId).get("members")).size());
    }
}
