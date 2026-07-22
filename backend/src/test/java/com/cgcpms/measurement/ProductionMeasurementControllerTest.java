package com.cgcpms.measurement;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.measurement.controller.ProductionMeasurementController;
import com.cgcpms.measurement.dto.MeasurementModels.OwnerReviewRequest;
import com.cgcpms.measurement.dto.MeasurementModels.OwnerSubmissionRequest;
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

@SpringBootTest(properties="spring.main.allow-circular-references=true")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ProductionMeasurementControllerTest {
    private static final long USER=99194001L,OTHER=99194002L,PROJECT=99194003L,OUTSIDE=99194004L;
    private static final long PARTNER=99194005L,CONTRACT=99194006L,OUT_CONTRACT=99194007L;
    private static final long PERIOD=99194008L,OUT_PERIOD=99194009L,MEASUREMENT=99194010L,OUT_MEASUREMENT=99194011L;
    private static final long SUBMISSION=99194012L,SETTLEMENT=99194013L,IN_SUBMISSION=99194014L,TRACE_SUBMISSION=99194015L,IN_SETTLEMENT=99194016L;
    @Autowired MockMvc mockMvc;@Autowired JwtUtils jwtUtils;@Autowired JdbcTemplate jdbc;

    @BeforeEach void setup(){
        cleanup();
        project(PROJECT,"MEASURE-HTTP-IN",USER);project(OUTSIDE,"MEASURE-HTTP-OUT",OTHER);
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_at,updated_at,deleted_flag) VALUES(?,0,'MEASURE-HTTP-P','业主','CUSTOMER','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",PARTNER);
        contract(CONTRACT,PROJECT,"MEASURE-HTTP-C");contract(OUT_CONTRACT,OUTSIDE,"MEASURE-HTTP-OC");
        period(PERIOD,PROJECT,CONTRACT,"HTTP-P","2026-07-01","2026-07-20");period(OUT_PERIOD,OUTSIDE,OUT_CONTRACT,"HTTP-OP","2026-07-01","2026-07-20");
        measurement(MEASUREMENT,PROJECT,CONTRACT,PERIOD,"HTTP-M","2026-07-15");measurement(OUT_MEASUREMENT,OUTSIDE,OUT_CONTRACT,OUT_PERIOD,"HTTP-OM","2026-07-15");
        submission(SUBMISSION,OUTSIDE,OUT_CONTRACT,OUT_MEASUREMENT,"HTTP-SUB","SETTLEMENT_CREATED",OTHER);
        submission(IN_SUBMISSION,PROJECT,CONTRACT,MEASUREMENT,"HTTP-IN-SUB","SUBMITTED",USER);
        submission(TRACE_SUBMISSION,PROJECT,CONTRACT,MEASUREMENT,"HTTP-TRACE-SUB","SETTLEMENT_CREATED",USER);
        settlement(SETTLEMENT,OUTSIDE,OUT_CONTRACT,OUT_MEASUREMENT,SUBMISSION,"HTTP-SET",OTHER);
        settlement(IN_SETTLEMENT,PROJECT,CONTRACT,MEASUREMENT,TRACE_SUBMISSION,"HTTP-IN-SET",USER);
    }
    @AfterEach void teardown(){cleanup();}

    @Test void stateTransitionsRequireClientVersionAndActionPermission()throws Exception{
        assertWrite("closePeriod","measurement:maintain",Long.class,Integer.class);assertWrite("submit","measurement:submit",Long.class,Integer.class);
        assertWrite("submitOwner","measurement:owner:submit",Long.class,Integer.class,OwnerSubmissionRequest.class);assertWrite("review","measurement:owner:review",Long.class,Integer.class,OwnerReviewRequest.class);
    }

    @Test void ordinaryMeasurementRoleReadsAndReadOnlyCannotWrite()throws Exception{
        Cookie reader=cookie(USER,"MEASUREMENT_USER","measurement:query");
        mockMvc.perform(get("/production-measurements").param("projectId",String.valueOf(PROJECT)).cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(post("/production-measurements/periods").cookie(reader).contentType(MediaType.APPLICATION_JSON).content(periodBody("HTTP-NEW"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/production-measurements/periods").cookie(cookie(USER,"MEASUREMENT_USER","measurement:maintain")).contentType(MediaType.APPLICATION_JSON).content(periodBody("HTTP-NEW"))).andExpect(status().isOk());
    }

    @Test void reportParamsBindAndFilterPeriodAndMeasureDates()throws Exception{
        Cookie reader=cookie(USER,"MEASUREMENT_USER","measurement:query");
        mockMvc.perform(get("/production-measurements/periods").param("projectId",String.valueOf(PROJECT)).param("startDate","2026-07-20").param("endDate","2026-07-20").cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/production-measurements/periods").param("projectId",String.valueOf(PROJECT)).param("startDate","2026-07-21").param("endDate","2026-07-31").cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/production-measurements").param("projectId",String.valueOf(PROJECT)).param("startDate","2026-07-15").param("endDate","2026-07-15").cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/production-measurements").param("projectId",String.valueOf(PROJECT)).param("startDate","2026-07-16").param("endDate","2026-07-31").cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test void projectScopeProtectsListDetailWriteAndTrace()throws Exception{
        Cookie scoped=cookie(USER,"MEASUREMENT_USER","measurement:query","measurement:submit","measurement:maintain","measurement:owner:submit","measurement:owner:review");
        mockMvc.perform(get("/production-measurements").cookie(scoped)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/production-measurements").param("projectId",String.valueOf(OUTSIDE)).cookie(scoped)).andExpect(status().isForbidden());
        mockMvc.perform(get("/production-measurements/"+OUT_MEASUREMENT).cookie(scoped)).andExpect(status().isForbidden());
        mockMvc.perform(post("/production-measurements/"+OUT_MEASUREMENT+"/submit").param("version","0").cookie(scoped)).andExpect(status().isForbidden());
        mockMvc.perform(get("/production-measurements/trace/settlements/"+SETTLEMENT).cookie(scoped)).andExpect(status().isForbidden());
    }

    @Test void everyEndpointTraversesSpringSecurityWithItsExactAuthority()throws Exception{
        Cookie query=cookie(USER,"MEASUREMENT_USER","measurement:query");
        expectAllowed(get("/production-measurements/periods").param("projectId",String.valueOf(PROJECT)),query);
        expectAllowed(get("/production-measurements/sources").param("projectId",String.valueOf(PROJECT)).param("contractId",String.valueOf(CONTRACT)),query);
        expectAllowed(get("/production-measurements").param("projectId",String.valueOf(PROJECT)),query);
        expectAllowed(get("/production-measurements/"+MEASUREMENT),query);
        expectAllowed(get("/production-measurements/owner-submissions/list").param("projectId",String.valueOf(PROJECT)),query);
        expectAllowed(get("/production-measurements/owner-submissions/"+IN_SUBMISSION),query);
        expectAllowed(get("/production-measurements/trace/settlements/"+IN_SETTLEMENT),query);

        Cookie maintain=cookie(USER,"MEASUREMENT_USER","measurement:maintain");
        expectAllowed(post("/production-measurements/periods").contentType(MediaType.APPLICATION_JSON).content(periodBody(PROJECT,CONTRACT,"HTTP-AUTH-P")),maintain);
        expectAllowed(post("/production-measurements/periods/"+PERIOD+"/close").param("version","0"),maintain);
        expectAllowed(post("/production-measurements").contentType(MediaType.APPLICATION_JSON).content(measurementBody(PROJECT,CONTRACT,PERIOD)),maintain);
        expectAllowed(post("/production-measurements/"+MEASUREMENT+"/submit").param("version","0"),cookie(USER,"MEASUREMENT_USER","measurement:submit"));
        expectAllowed(post("/production-measurements/"+MEASUREMENT+"/owner-submissions").param("version","0").contentType(MediaType.APPLICATION_JSON).content(ownerSubmissionBody()),cookie(USER,"MEASUREMENT_USER","measurement:owner:submit"));
        expectAllowed(post("/production-measurements/owner-submissions/"+IN_SUBMISSION+"/review").param("version","0").contentType(MediaType.APPLICATION_JSON).content(returnReviewBody()),cookie(USER,"MEASUREMENT_USER","measurement:owner:review"));
    }

    @Test void queryOnlyAuthorityRejectsEveryWriteBeforeDatabaseMutation()throws Exception{
        Cookie queryOnly=cookie(USER,"MEASUREMENT_READER","measurement:query");
        int periods=jdbc.queryForObject("SELECT COUNT(*) FROM measurement_period WHERE project_id=? AND deleted_flag=0",Integer.class,PROJECT);
        int measurements=jdbc.queryForObject("SELECT COUNT(*) FROM production_measurement WHERE project_id=? AND deleted_flag=0",Integer.class,PROJECT);
        expectForbidden(post("/production-measurements/periods").contentType(MediaType.APPLICATION_JSON).content(periodBody(PROJECT,CONTRACT,"HTTP-DENIED-P")),queryOnly);
        expectForbidden(post("/production-measurements/periods/"+PERIOD+"/close").param("version","0"),queryOnly);
        expectForbidden(post("/production-measurements").contentType(MediaType.APPLICATION_JSON).content(measurementBody(PROJECT,CONTRACT,PERIOD)),queryOnly);
        expectForbidden(post("/production-measurements/"+MEASUREMENT+"/submit").param("version","0"),queryOnly);
        expectForbidden(post("/production-measurements/"+MEASUREMENT+"/owner-submissions").param("version","0").contentType(MediaType.APPLICATION_JSON).content(ownerSubmissionBody()),queryOnly);
        expectForbidden(post("/production-measurements/owner-submissions/"+IN_SUBMISSION+"/review").param("version","0").contentType(MediaType.APPLICATION_JSON).content(returnReviewBody()),queryOnly);
        assertEquals(periods,jdbc.queryForObject("SELECT COUNT(*) FROM measurement_period WHERE project_id=? AND deleted_flag=0",Integer.class,PROJECT));
        assertEquals(measurements,jdbc.queryForObject("SELECT COUNT(*) FROM production_measurement WHERE project_id=? AND deleted_flag=0",Integer.class,PROJECT));
        assertEquals("DRAFT",jdbc.queryForObject("SELECT status FROM production_measurement WHERE id=?",String.class,MEASUREMENT));
        assertEquals("SUBMITTED",jdbc.queryForObject("SELECT status FROM owner_measurement_submission WHERE id=?",String.class,IN_SUBMISSION));
    }

    @Test void projectOutsideEveryEndpointFailsClosedWithZeroMutation()throws Exception{
        Cookie all=cookie(USER,"MEASUREMENT_USER","measurement:query","measurement:maintain","measurement:submit","measurement:owner:submit","measurement:owner:review");
        int periods=jdbc.queryForObject("SELECT COUNT(*) FROM measurement_period WHERE project_id=? AND deleted_flag=0",Integer.class,OUTSIDE);
        int measurements=jdbc.queryForObject("SELECT COUNT(*) FROM production_measurement WHERE project_id=? AND deleted_flag=0",Integer.class,OUTSIDE);
        int submissions=jdbc.queryForObject("SELECT COUNT(*) FROM owner_measurement_submission WHERE project_id=? AND deleted_flag=0",Integer.class,OUTSIDE);
        expectForbidden(get("/production-measurements/periods").param("projectId",String.valueOf(OUTSIDE)),all);
        expectForbidden(post("/production-measurements/periods").contentType(MediaType.APPLICATION_JSON).content(periodBody(OUTSIDE,OUT_CONTRACT,"HTTP-OUT-NEW-P")),all);
        expectForbidden(post("/production-measurements/periods/"+OUT_PERIOD+"/close").param("version","0"),all);
        expectForbidden(get("/production-measurements/sources").param("projectId",String.valueOf(OUTSIDE)).param("contractId",String.valueOf(OUT_CONTRACT)),all);
        expectForbidden(get("/production-measurements").param("projectId",String.valueOf(OUTSIDE)),all);
        expectForbidden(get("/production-measurements/"+OUT_MEASUREMENT),all);
        expectForbidden(post("/production-measurements").contentType(MediaType.APPLICATION_JSON).content(measurementBody(OUTSIDE,OUT_CONTRACT,OUT_PERIOD)),all);
        expectForbidden(post("/production-measurements/"+OUT_MEASUREMENT+"/submit").param("version","0"),all);
        expectForbidden(post("/production-measurements/"+OUT_MEASUREMENT+"/owner-submissions").param("version","0").contentType(MediaType.APPLICATION_JSON).content(ownerSubmissionBody()),all);
        expectForbidden(get("/production-measurements/owner-submissions/list").param("projectId",String.valueOf(OUTSIDE)),all);
        expectForbidden(get("/production-measurements/owner-submissions/"+SUBMISSION),all);
        expectForbidden(post("/production-measurements/owner-submissions/"+SUBMISSION+"/review").param("version","1").contentType(MediaType.APPLICATION_JSON).content(returnReviewBody()),all);
        expectForbidden(get("/production-measurements/trace/settlements/"+SETTLEMENT),all);
        assertEquals(periods,jdbc.queryForObject("SELECT COUNT(*) FROM measurement_period WHERE project_id=? AND deleted_flag=0",Integer.class,OUTSIDE));
        assertEquals(measurements,jdbc.queryForObject("SELECT COUNT(*) FROM production_measurement WHERE project_id=? AND deleted_flag=0",Integer.class,OUTSIDE));
        assertEquals(submissions,jdbc.queryForObject("SELECT COUNT(*) FROM owner_measurement_submission WHERE project_id=? AND deleted_flag=0",Integer.class,OUTSIDE));
        assertEquals("DRAFT",jdbc.queryForObject("SELECT status FROM production_measurement WHERE id=?",String.class,OUT_MEASUREMENT));
        assertEquals("SETTLEMENT_CREATED",jdbc.queryForObject("SELECT status FROM owner_measurement_submission WHERE id=?",String.class,SUBMISSION));
    }

    private void assertWrite(String name,String authority,Class<?>...types)throws Exception{Method method=ProductionMeasurementController.class.getMethod(name,types);assertNotNull(method.getParameters()[1].getAnnotation(RequestParam.class));PreAuthorize security=method.getAnnotation(PreAuthorize.class);assertNotNull(security);assertTrue(security.value().contains(authority));}
    private Cookie cookie(long user,String role,String...authorities){return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,jwtUtils.generateToken(user,"measurement-http-"+user,0L,List.of(role),List.of(authorities)));}
    private void expectAllowed(MockHttpServletRequestBuilder request,Cookie cookie)throws Exception{mockMvc.perform(request.cookie(cookie)).andExpect(result->assertNotEquals(403,result.getResponse().getStatus()));}
    private void expectForbidden(MockHttpServletRequestBuilder request,Cookie cookie)throws Exception{mockMvc.perform(request.cookie(cookie)).andExpect(status().isForbidden());}
    private String periodBody(String code){return periodBody(PROJECT,CONTRACT,code);}
    private String periodBody(long project,long contract,String code){return "{\"projectId\":"+project+",\"contractId\":"+contract+",\"periodCode\":\""+code+"\",\"periodName\":\"新增期间\",\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-20\",\"cutoffDate\":\"2026-08-25\"}";}
    private String measurementBody(long project,long contract,long period){return "{\"projectId\":"+project+",\"contractId\":"+contract+",\"periodId\":"+period+",\"measureDate\":\"2026-07-16\",\"attachmentCount\":1,\"lines\":[{\"contractItemId\":999999,\"currentQuantity\":1,\"evidenceCount\":1}]}";}
    private String ownerSubmissionBody(){return "{\"externalDocumentNo\":\"HTTP-OWNER\",\"attachmentCount\":1}";}
    private String returnReviewBody(){return "{\"decision\":\"RETURNED\",\"reviewerName\":\"HTTP业主\",\"reviewComment\":\"退回\"}";}
    private void project(long id,String code,long owner){jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,'计量HTTP项目','ACTIVE',?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0)",id,code,owner,owner);}
    private void contract(long id,long project,String code){jdbc.update("INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,contract_status,approval_status,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,?,'计量HTTP合同','MAIN',?,?,1000,1000,0,'PERFORMING','APPROVED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",id,project,code,PARTNER,PARTNER);}
    private void period(long id,long project,long contract,String code,String start,String end){jdbc.update("INSERT INTO measurement_period(id,tenant_id,project_id,contract_id,period_code,period_name,start_date,end_date,cutoff_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,'HTTP期间',?,?,?,'OPEN',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0)",id,project,contract,code,start,end,end,USER,USER);}
    private void measurement(long id,long project,long contract,long period,String code,String date){jdbc.update("INSERT INTO production_measurement(id,tenant_id,project_id,contract_id,period_id,measure_code,measure_date,current_reported_amount,cumulative_reported_amount,status,approval_status,attachment_count,formula_version,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,?,?,100,100,'DRAFT','DRAFT',0,'PRODUCTION_MEASUREMENT_V1',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0)",id,project,contract,period,code,date,USER,USER);}
    private void submission(long id,long project,long contract,long measurement,String code,String status,long user){int revision=id==TRACE_SUBMISSION?2:1;jdbc.update("INSERT INTO owner_measurement_submission(id,tenant_id,project_id,contract_id,measurement_id,submission_code,revision_no,submitted_at,submitted_amount,confirmed_amount,deducted_amount,status,attachment_count,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,?,?,CURRENT_TIMESTAMP,100,100,0,?,1,0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0)",id,project,contract,measurement,code,revision,status,user,user);}
    private void settlement(long id,long project,long contract,long measurement,long submission,String code,long user){jdbc.update("INSERT INTO owner_settlement(id,tenant_id,project_id,contract_id,settlement_code,settlement_period,settlement_date,gross_amount,tax_amount,retention_amount,net_receivable_amount,due_date,customer_id,status,attachment_count,version,created_by,created_at,updated_by,updated_at,deleted_flag,production_measurement_id,owner_submission_id,reported_amount,deducted_amount) VALUES(?,0,?,?,?,'2026-07','2026-07-20',100,0,0,100,'2026-08-20',?,'DRAFT',1,0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?,?,100,0)",id,project,contract,code,PARTNER,user,user,measurement,submission);}
    private void cleanup(){jdbc.update("DELETE FROM account_receivable WHERE project_id IN (?,?)",PROJECT,OUTSIDE);jdbc.update("DELETE FROM owner_settlement WHERE project_id IN (?,?)",PROJECT,OUTSIDE);jdbc.update("DELETE FROM owner_measurement_review_line WHERE submission_id IN (SELECT id FROM owner_measurement_submission WHERE project_id IN (?,?))",PROJECT,OUTSIDE);jdbc.update("DELETE FROM owner_measurement_submission WHERE project_id IN (?,?)",PROJECT,OUTSIDE);jdbc.update("DELETE FROM production_measurement_line WHERE measurement_id IN (SELECT id FROM production_measurement WHERE project_id IN (?,?))",PROJECT,OUTSIDE);jdbc.update("DELETE FROM production_measurement WHERE project_id IN (?,?)",PROJECT,OUTSIDE);jdbc.update("DELETE FROM measurement_period WHERE project_id IN (?,?)",PROJECT,OUTSIDE);jdbc.update("DELETE FROM ct_contract_item WHERE contract_id IN (?,?)",CONTRACT,OUT_CONTRACT);jdbc.update("DELETE FROM ct_contract WHERE id IN (?,?)",CONTRACT,OUT_CONTRACT);jdbc.update("DELETE FROM md_partner WHERE id=?",PARTNER);jdbc.update("DELETE FROM pm_project WHERE id IN (?,?)",PROJECT,OUTSIDE);}
}
