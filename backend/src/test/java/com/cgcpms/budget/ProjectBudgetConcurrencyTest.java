package com.cgcpms.budget;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.service.ProjectBudgetService;
import com.cgcpms.common.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class ProjectBudgetConcurrencyTest {
    private static final long PROJECT = 99191001L;
    private static final long BUDGET = 99191002L;
    private static final long SUBJECT = 99191003L;
    @Autowired ProjectBudgetService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void setup() {
        context(); cleanup();
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) SELECT 1,0,'admin','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','系统管理员','ENABLE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0 WHERE NOT EXISTS(SELECT 1 FROM sys_user WHERE id=1)");
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'BUDGET-CAS','预算CAS项目','ACTIVE',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", PROJECT);
        jdbc.update("INSERT INTO pm_project_member(id,tenant_id,project_id,user_id,role_code,position_name,start_date,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(99191005,0,?,1,'PROJECT_MANAGER','项目经理',CURRENT_DATE,'ACTIVE',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)",PROJECT);
        jdbc.update("INSERT INTO cost_subject(id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,created_at,updated_at,deleted_flag) VALUES(?,0,0,'BUDGET-CAS-S','预算CAS科目','DETAIL','COST',1,1,'ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", SUBJECT);
        jdbc.update("INSERT INTO project_budget(id,tenant_id,project_id,version_no,budget_name,total_amount,approval_status,status,active_flag,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,'V1','并发预算',1000,'DRAFT','DRAFT',0,0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", BUDGET, PROJECT);
        jdbc.update("INSERT INTO project_budget_line(id,tenant_id,budget_id,project_id,cost_subject_id,budget_amount,reserved_amount,consumed_amount,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(99191004,0,?,?,?,1000,0,0,0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", BUDGET,PROJECT,SUBJECT);
    }
    @AfterEach void teardown() { cleanup(); UserContext.clear(); }

    @Test void concurrentUpdatesHaveOneSuccessAndOneStableConflict() throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger successes = new AtomicInteger();
        List<String> codes = Collections.synchronizedList(new ArrayList<>());
        for (String name : List.of("版本A", "版本B")) pool.submit(() -> {
            try {
                context(); ready.countDown(); start.await();
                ProjectBudget input = new ProjectBudget(); input.setId(BUDGET); input.setVersionNo("V1");
                input.setBudgetName(name); input.setTotalAmount(new BigDecimal("1000"));
                service.update(input, 0); successes.incrementAndGet();
            } catch (BusinessException e) { codes.add(e.getCode()); }
            catch (Exception e) { codes.add(e.getClass().getSimpleName()); }
            finally { UserContext.clear(); }
        });
        assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown(); pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(1, successes.get());
        assertEquals(List.of("BUDGET_CONCURRENT_UPDATE"), codes);
        assertEquals(1, jdbc.queryForObject("SELECT version FROM project_budget WHERE id=?", Integer.class, BUDGET));
    }

    @Test void concurrentSaveLinesHasOneSuccessAndOneStableConflict() throws Exception {
        RaceResult result=race(() -> {
            ProjectBudgetLine line=new ProjectBudgetLine(); line.setCostSubjectId(SUBJECT); line.setBudgetAmount(new java.math.BigDecimal("1000"));
            service.saveLines(BUDGET,0,List.of(line));
        });
        assertRace(result,"BUDGET_CONCURRENT_UPDATE");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM project_budget_line WHERE budget_id=? AND deleted_flag=0",Integer.class,BUDGET));
        assertEquals(1,jdbc.queryForObject("SELECT version FROM project_budget WHERE id=?",Integer.class,BUDGET));
    }

    @Test void concurrentDeleteHasOneSuccessAndOneStableConflict() throws Exception {
        RaceResult result=race(() -> service.delete(BUDGET,0));
        assertRace(result,"BUDGET_CONCURRENT_UPDATE");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM project_budget WHERE id=? AND deleted_flag=0",Integer.class,BUDGET));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM project_budget_line WHERE budget_id=? AND deleted_flag=0",Integer.class,BUDGET));
    }

    @Test void concurrentSubmitHasOneSuccessOneConflictAndOneWorkflow() throws Exception {
        RaceResult result=race(() -> service.submit(BUDGET,0));
        assertRace(result,"BUDGET_CONCURRENT_UPDATE");
        assertEquals("APPROVING",jdbc.queryForObject("SELECT approval_status FROM project_budget WHERE id=?",String.class,BUDGET));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM wf_instance WHERE tenant_id=0 AND business_type='PROJECT_BUDGET' AND business_id=? AND deleted_flag=0",Integer.class,BUDGET));
    }

    private RaceResult race(ThrowingWork work)throws Exception{
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        AtomicInteger successes=new AtomicInteger();List<String> codes=Collections.synchronizedList(new ArrayList<>());
        for(int i=0;i<2;i++) pool.submit(()->{try{context();ready.countDown();start.await();work.run();successes.incrementAndGet();}
            catch(BusinessException e){codes.add(e.getCode());}catch(Exception e){codes.add(e.getClass().getSimpleName());}finally{UserContext.clear();}});
        assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();pool.shutdown();assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));
        return new RaceResult(successes.get(),codes);
    }
    private void assertRace(RaceResult result,String code){assertEquals(1,result.successes(),result.toString());assertEquals(List.of(code),result.codes(),result.toString());}
    @FunctionalInterface private interface ThrowingWork{void run()throws Exception;}
    private record RaceResult(int successes,List<String> codes){}

    private void context() { UserContext.set(Jwts.claims().subject("admin").add("userId",1L).add("username","admin").add("tenantId",0L).add("roleCodes",List.of("ADMIN")).build()); }
    private void cleanup() {
        jdbc.update("DELETE FROM wf_record WHERE instance_id IN(SELECT id FROM wf_instance WHERE business_type='PROJECT_BUDGET' AND business_id=?)",BUDGET);
        jdbc.update("DELETE FROM wf_task WHERE instance_id IN(SELECT id FROM wf_instance WHERE business_type='PROJECT_BUDGET' AND business_id=?)",BUDGET);
        jdbc.update("DELETE FROM wf_node_instance WHERE instance_id IN(SELECT id FROM wf_instance WHERE business_type='PROJECT_BUDGET' AND business_id=?)",BUDGET);
        jdbc.update("DELETE FROM wf_cc WHERE instance_id IN(SELECT id FROM wf_instance WHERE business_type='PROJECT_BUDGET' AND business_id=?)",BUDGET);
        jdbc.update("DELETE FROM wf_instance WHERE business_type='PROJECT_BUDGET' AND business_id=?",BUDGET);
        jdbc.update("DELETE FROM project_budget_line WHERE budget_id=?", BUDGET); jdbc.update("DELETE FROM project_budget WHERE id=?", BUDGET);
        jdbc.update("DELETE FROM pm_project_member WHERE project_id=?",PROJECT);jdbc.update("DELETE FROM cost_subject WHERE id=?",SUBJECT);jdbc.update("DELETE FROM pm_project WHERE id=?", PROJECT);
    }
}
