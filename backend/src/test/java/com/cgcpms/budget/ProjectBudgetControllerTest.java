package com.cgcpms.budget;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.budget.controller.ProjectBudgetController;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ProjectBudgetControllerTest {
    private static final long USER=99193001L, OTHER=99193002L, PROJECT=99193003L, OUTSIDE=99193004L;
    private static final long BUDGET=99193005L, OUTSIDE_BUDGET=99193006L, SUBJECT=99193007L, LINE=99193008L;
    @Autowired MockMvc mockMvc;
    @Autowired JwtUtils jwtUtils;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach void setup() {
        cleanup();
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,created_at,updated_at,deleted_flag) SELECT 1,0,'budget-http-approver','x','预算审批人','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0 WHERE NOT EXISTS(SELECT 1 FROM sys_user WHERE id=1)");
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'BUDGET-HTTP-IN','预算HTTP项目','ACTIVE',?,'2026-07-01',?,'2026-07-01',0)",PROJECT,USER,USER);
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'BUDGET-HTTP-OUT','预算越权项目','ACTIVE',?,'2026-07-01',?,'2026-07-01',0)",OUTSIDE,OTHER,OTHER);
        jdbc.update("INSERT INTO cost_subject(id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,created_at,updated_at,deleted_flag) VALUES(?,0,0,'BUDGET-HTTP-S','预算HTTP科目','DETAIL','COST',1,1,'ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",SUBJECT);
        insertBudget(BUDGET,PROJECT,"HTTP-V1","2026-07-15 12:00:00");
        insertBudget(OUTSIDE_BUDGET,OUTSIDE,"HTTP-OUT","2026-07-15 12:00:00");
        jdbc.update("INSERT INTO project_budget_line(id,tenant_id,budget_id,project_id,cost_subject_id,budget_amount,reserved_amount,consumed_amount,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,1000,0,0,0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0)",LINE,BUDGET,PROJECT,SUBJECT,USER,USER);
    }

    @AfterEach void teardown(){ cleanup(); }

    @Test void allExistingResourceWritesRequireVersionAndActionPermission() throws Exception {
        assertWrite("update", "budget:edit", Long.class, Integer.class, ProjectBudget.class);
        assertWrite("saveLines", "budget:edit", Long.class, Integer.class, List.class);
        assertWrite("delete", "budget:delete", Long.class, Integer.class);
        assertWrite("submit", "budget:submit", Long.class, Integer.class);
    }

    @Test void ordinaryBudgetRoleAllowedButReadOnlyCannotWrite() throws Exception {
        Cookie editor=cookie(USER,"BUDGET_USER","budget:query","budget:edit");
        mockMvc.perform(get("/project-budgets").param("projectId",String.valueOf(PROJECT)).cookie(editor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(put("/project-budgets/"+BUDGET).param("version","0").cookie(editor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"projectId\":"+PROJECT+",\"versionNo\":\"HTTP-V1\",\"budgetName\":\"已更新\",\"totalAmount\":\"1000.00\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/project-budgets/"+BUDGET).param("version","1")
                        .cookie(cookie(USER,"BUDGET_READER","budget:query"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"projectId\":"+PROJECT+",\"versionNo\":\"HTTP-V1\",\"budgetName\":\"拒绝\",\"totalAmount\":\"1000.00\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void reportDateParamsAreBoundAndFilterServerCreatedAt() throws Exception {
        Cookie reader=cookie(USER,"BUDGET_USER","budget:query");
        mockMvc.perform(get("/project-budgets").param("projectId",String.valueOf(PROJECT))
                        .param("startDate","2026-07-15").param("endDate","2026-07-15").cookie(reader))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/project-budgets").param("projectId",String.valueOf(PROJECT))
                        .param("startDate","2026-07-16").param("endDate","2026-07-31").cookie(reader))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test void projectScopeProtectsListDetailAvailabilityAndWrite() throws Exception {
        Cookie scoped=cookie(USER,"BUDGET_USER","budget:query","budget:edit","budget:delete","budget:submit");
        mockMvc.perform(get("/project-budgets").cookie(scoped)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/project-budgets").param("projectId",String.valueOf(OUTSIDE)).cookie(scoped)).andExpect(status().isForbidden());
        mockMvc.perform(get("/project-budgets/"+OUTSIDE_BUDGET).cookie(scoped)).andExpect(status().isForbidden());
        mockMvc.perform(get("/project-budgets/"+OUTSIDE_BUDGET+"/availability").cookie(scoped)).andExpect(status().isForbidden());
        mockMvc.perform(put("/project-budgets/"+OUTSIDE_BUDGET).param("version","0").cookie(scoped)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"projectId\":"+OUTSIDE+",\"versionNo\":\"HTTP-OUT\",\"budgetName\":\"越权\",\"totalAmount\":\"1000.00\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void everyEndpointTraversesSpringSecurityWithItsExactAuthority() throws Exception {
        Cookie query=cookie(USER,"BUDGET_USER","budget:query");
        expectAllowed(get("/project-budgets").param("projectId",String.valueOf(PROJECT)),query);
        expectAllowed(get("/project-budgets/"+BUDGET),query);
        expectAllowed(get("/project-budgets/"+BUDGET+"/availability"),query);

        String created=mockMvc.perform(post("/project-budgets").cookie(cookie(USER,"BUDGET_USER","budget:add"))
                        .contentType(MediaType.APPLICATION_JSON).content(budgetBody(PROJECT,"HTTP-CREATE","新建预算")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long createdId=Long.parseLong(objectMapper.readTree(created).path("data").asText());
        expectAllowed(put("/project-budgets/"+BUDGET).param("version","0").contentType(MediaType.APPLICATION_JSON)
                .content(budgetBody(PROJECT,"HTTP-V1","更新预算")),cookie(USER,"BUDGET_USER","budget:edit"));
        expectAllowed(post("/project-budgets/"+BUDGET+"/lines").param("version","1").contentType(MediaType.APPLICATION_JSON)
                .content(lineBody()),cookie(USER,"BUDGET_USER","budget:edit"));
        expectAllowed(post("/project-budgets/"+BUDGET+"/submit").param("version","2"),cookie(USER,"BUDGET_USER","budget:submit"));
        expectAllowed(delete("/project-budgets/"+createdId).param("version","0"),cookie(USER,"BUDGET_USER","budget:delete"));
    }

    @Test void queryOnlyAuthorityRejectsEveryWriteBeforeDatabaseMutation() throws Exception {
        Cookie queryOnly=cookie(USER,"BUDGET_READER","budget:query");
        int beforeCount=jdbc.queryForObject("SELECT COUNT(*) FROM project_budget WHERE project_id=? AND deleted_flag=0",Integer.class,PROJECT);
        expectForbidden(post("/project-budgets").contentType(MediaType.APPLICATION_JSON).content(budgetBody(PROJECT,"HTTP-DENIED","拒绝新建")),queryOnly);
        expectForbidden(put("/project-budgets/"+BUDGET).param("version","0").contentType(MediaType.APPLICATION_JSON).content(budgetBody(PROJECT,"HTTP-V1","拒绝更新")),queryOnly);
        expectForbidden(post("/project-budgets/"+BUDGET+"/lines").param("version","0").contentType(MediaType.APPLICATION_JSON).content(lineBody()),queryOnly);
        expectForbidden(delete("/project-budgets/"+BUDGET).param("version","0"),queryOnly);
        expectForbidden(post("/project-budgets/"+BUDGET+"/submit").param("version","0"),queryOnly);
        assertEquals(beforeCount,jdbc.queryForObject("SELECT COUNT(*) FROM project_budget WHERE project_id=? AND deleted_flag=0",Integer.class,PROJECT));
        assertEquals(0,jdbc.queryForObject("SELECT version FROM project_budget WHERE id=?",Integer.class,BUDGET));
        assertEquals("预算HTTP-V1",jdbc.queryForObject("SELECT budget_name FROM project_budget WHERE id=?",String.class,BUDGET));
    }

    @Test void projectOutsideEveryReadAndWriteFailsClosedWithZeroMutation() throws Exception {
        Cookie all=cookie(USER,"BUDGET_USER","budget:query","budget:add","budget:edit","budget:delete","budget:submit");
        int beforeCount=jdbc.queryForObject("SELECT COUNT(*) FROM project_budget WHERE project_id=? AND deleted_flag=0",Integer.class,OUTSIDE);
        expectForbidden(get("/project-budgets").param("projectId",String.valueOf(OUTSIDE)),all);
        expectForbidden(get("/project-budgets/"+OUTSIDE_BUDGET),all);
        expectForbidden(get("/project-budgets/"+OUTSIDE_BUDGET+"/availability"),all);
        expectForbidden(post("/project-budgets").contentType(MediaType.APPLICATION_JSON).content(budgetBody(OUTSIDE,"HTTP-OUT-CREATE","越权新建")),all);
        expectForbidden(put("/project-budgets/"+OUTSIDE_BUDGET).param("version","0").contentType(MediaType.APPLICATION_JSON).content(budgetBody(OUTSIDE,"HTTP-OUT","越权更新")),all);
        expectForbidden(post("/project-budgets/"+OUTSIDE_BUDGET+"/lines").param("version","0").contentType(MediaType.APPLICATION_JSON).content(lineBody()),all);
        expectForbidden(delete("/project-budgets/"+OUTSIDE_BUDGET).param("version","0"),all);
        expectForbidden(post("/project-budgets/"+OUTSIDE_BUDGET+"/submit").param("version","0"),all);
        assertEquals(beforeCount,jdbc.queryForObject("SELECT COUNT(*) FROM project_budget WHERE project_id=? AND deleted_flag=0",Integer.class,OUTSIDE));
        assertEquals(0,jdbc.queryForObject("SELECT version FROM project_budget WHERE id=?",Integer.class,OUTSIDE_BUDGET));
        assertEquals("DRAFT",jdbc.queryForObject("SELECT approval_status FROM project_budget WHERE id=?",String.class,OUTSIDE_BUDGET));
    }

    private void assertWrite(String name,String authority,Class<?>... types)throws Exception{
        Method method=ProjectBudgetController.class.getMethod(name,types);
        assertNotNull(method.getParameters()[1].getAnnotation(RequestParam.class));
        PreAuthorize security=method.getAnnotation(PreAuthorize.class);assertNotNull(security);assertTrue(security.value().contains(authority));
    }
    private Cookie cookie(long user,String role,String... authorities){
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,jwtUtils.generateToken(user,"budget-http-"+user,0L,List.of(role),List.of(authorities)));
    }
    private void expectAllowed(MockHttpServletRequestBuilder request,Cookie cookie)throws Exception{mockMvc.perform(request.cookie(cookie)).andExpect(result->assertNotEquals(403,result.getResponse().getStatus()));}
    private void expectForbidden(MockHttpServletRequestBuilder request,Cookie cookie)throws Exception{mockMvc.perform(request.cookie(cookie)).andExpect(status().isForbidden());}
    private String budgetBody(long project,String version,String name){return "{\"projectId\":"+project+",\"versionNo\":\""+version+"\",\"budgetName\":\""+name+"\",\"totalAmount\":\"1000.00\"}";}
    private String lineBody(){return "[{\"costSubjectId\":"+SUBJECT+",\"budgetAmount\":\"1000.00\"}]";}
    private void insertBudget(long id,long project,String version,String created){
        jdbc.update("INSERT INTO project_budget(id,tenant_id,project_id,version_no,budget_name,total_amount,approval_status,status,active_flag,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,1000,'DRAFT','DRAFT',0,0,?,?,?,CURRENT_TIMESTAMP,0)",id,project,version,"预算"+version,USER,created,USER);
    }
    private void cleanup(){
        jdbc.update("DELETE FROM project_budget_line WHERE project_id IN (?,?)",PROJECT,OUTSIDE);
        jdbc.update("DELETE FROM project_budget WHERE project_id IN (?,?)",PROJECT,OUTSIDE);
        jdbc.update("DELETE FROM wf_record WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id IN (?,?))",PROJECT,OUTSIDE);
        jdbc.update("DELETE FROM wf_task WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id IN (?,?))",PROJECT,OUTSIDE);
        jdbc.update("DELETE FROM wf_node_instance WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id IN (?,?))",PROJECT,OUTSIDE);
        jdbc.update("DELETE FROM wf_cc WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id IN (?,?))",PROJECT,OUTSIDE);
        jdbc.update("DELETE FROM wf_instance WHERE project_id IN (?,?)",PROJECT,OUTSIDE);
        jdbc.update("DELETE FROM cost_subject WHERE id=?",SUBJECT);
        jdbc.update("DELETE FROM pm_project WHERE id IN (?,?)",PROJECT,OUTSIDE);
    }
}
