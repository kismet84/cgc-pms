package com.cgcpms.measurement;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.service.FileService;
import com.cgcpms.measurement.dto.MeasurementModels.OwnerReviewLineRequest;
import com.cgcpms.measurement.dto.MeasurementModels.OwnerReviewRequest;
import com.cgcpms.measurement.dto.MeasurementModels.OwnerSubmissionRequest;
import com.cgcpms.measurement.service.ProductionMeasurementService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import io.minio.MinioClient;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true","minio.enabled=true"})
@ActiveProfiles("local")
class ProductionMeasurementConcurrencyTest {
    private static final long PROJECT=99192001L, PARTNER=99192002L, CONTRACT=99192003L, PERIOD=99192004L;
    private static final long ITEM=99192005L,MEASUREMENT=99192006L,LINE=99192007L,SUBMISSION=99192008L;
    private static final long GENERAL_FILE=99192011L,LINE_FILE=99192012L,OWNER_FILE=99192013L,CONFIRM_FILE=99192014L;
    @Autowired ProductionMeasurementService service;
    @Autowired FileService fileService;
    @MockBean MinioClient minioClient;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @BeforeEach void setup() {
        context(); cleanup();
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,created_at,updated_at,deleted_flag) SELECT 1,0,'measurement-cas-admin','x','计量并发管理员','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0 WHERE NOT EXISTS(SELECT 1 FROM sys_user WHERE id=1)");
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'MEASURE-CAS','计量CAS项目','ACTIVE',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)",PROJECT);
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_at,updated_at,deleted_flag) VALUES(?,0,'MEASURE-CAS-P','业主','CUSTOMER','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",PARTNER);
        jdbc.update("INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,contract_status,approval_status,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,'MEASURE-CAS-C','业主合同','MAIN',?,?,1000,1000,0,'PERFORMING','APPROVED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",CONTRACT,PROJECT,PARTNER,PARTNER);
        jdbc.update("INSERT INTO ct_contract_item(id,tenant_id,contract_id,item_code,item_name,unit,quantity,unit_price,amount,tax_rate,tax_amount,amount_without_tax,sort_order,created_at,updated_at,deleted_flag) VALUES(?,0,?,'CAS-I','并发计量项','m3',100,10,1000,0,0,1000,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",ITEM,CONTRACT);
        jdbc.update("INSERT INTO measurement_period(id,tenant_id,project_id,contract_id,period_code,period_name,start_date,end_date,cutoff_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,'CAS-P','并发周期',CURRENT_DATE,CURRENT_DATE,CURRENT_DATE,'OPEN',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)",PERIOD,PROJECT,CONTRACT);
        jdbc.update("INSERT INTO production_measurement(id,tenant_id,project_id,contract_id,period_id,measure_code,measure_date,current_reported_amount,cumulative_reported_amount,status,approval_status,attachment_count,formula_version,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,'CAS-M',CURRENT_DATE,100,100,'DRAFT','DRAFT',0,'PRODUCTION_MEASUREMENT_V1',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)",MEASUREMENT,PROJECT,CONTRACT,PERIOD);
        jdbc.update("INSERT INTO production_measurement_line(id,tenant_id,measurement_id,source_type,contract_item_id,contract_change_id,item_code,item_name,unit,contract_quantity,prior_approved_quantity,current_reported_quantity,cumulative_reported_quantity,unit_price,current_reported_amount,cumulative_reported_amount,evidence_count,sort_order,created_by,created_at) VALUES(?,0,?,'CONTRACT_ITEM',?,NULL,'CAS-I','并发计量项','m3',100,0,10,10,10,100,100,0,1,1,CURRENT_TIMESTAMP)",LINE,MEASUREMENT,ITEM);
        addFile(GENERAL_FILE,"PRODUCTION_MEASUREMENT",MEASUREMENT,"MEASUREMENT_GENERAL");
        addFile(LINE_FILE,"PRODUCTION_MEASUREMENT",MEASUREMENT,"ML_"+LINE);
        addFile(OWNER_FILE,"PRODUCTION_MEASUREMENT",MEASUREMENT,"OWNER_SUBMISSION");
        addFile(CONFIRM_FILE,"OWNER_MEASUREMENT_SUBMISSION",SUBMISSION,"OWNER_CONFIRMATION");
    }
    @AfterEach void teardown(){ cleanup(); UserContext.clear(); }
    @Test void concurrentCloseHasOneSuccessAndOneStableConflict() throws Exception {
        jdbc.update("DELETE FROM production_measurement_line WHERE measurement_id=?",MEASUREMENT);
        jdbc.update("DELETE FROM production_measurement WHERE id=?",MEASUREMENT);
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1); ExecutorService pool=Executors.newFixedThreadPool(2);
        AtomicInteger successes=new AtomicInteger(); List<String> codes=Collections.synchronizedList(new ArrayList<>());
        for(int i=0;i<2;i++) pool.submit(()->{ try{context();ready.countDown();start.await();service.closePeriod(PERIOD,0);successes.incrementAndGet();}
            catch(BusinessException e){codes.add(e.getCode());}catch(Exception e){codes.add(e.getClass().getSimpleName());}finally{UserContext.clear();}});
        assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();pool.shutdown();assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));
        assertEquals(1,successes.get());assertEquals(List.of("MEASUREMENT_PERIOD_CONCURRENT_UPDATE"),codes);
    }

    @Test void concurrentInternalSubmitHasOneSuccessConflictAndWorkflow() throws Exception {
        RaceResult result=race(() -> service.submitMeasurement(MEASUREMENT,0));
        assertRace(result,"PRODUCTION_MEASUREMENT_CONCURRENT_UPDATE");
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM production_measurement WHERE id=?",String.class,MEASUREMENT));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM wf_instance WHERE business_type='PRODUCTION_MEASUREMENT' AND business_id=? AND deleted_flag=0",Integer.class,MEASUREMENT));
    }

    @Test void concurrentOwnerSubmitHasOneSuccessConflictAndOneRevision() throws Exception {
        jdbc.update("UPDATE production_measurement SET status='INTERNAL_APPROVED',approval_status='APPROVED' WHERE id=?",MEASUREMENT);
        RaceResult result=race(() -> service.submitToOwner(MEASUREMENT,0,new OwnerSubmissionRequest("CAS-OWNER",999,null)));
        assertRace(result,"PRODUCTION_MEASUREMENT_CONCURRENT_UPDATE");
        assertEquals("OWNER_SUBMITTED",jdbc.queryForObject("SELECT status FROM production_measurement WHERE id=?",String.class,MEASUREMENT));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM owner_measurement_submission WHERE measurement_id=? AND deleted_flag=0",Integer.class,MEASUREMENT));
    }

    @Test void concurrentOwnerReturnIsIdempotentWithSingleTransition() throws Exception {
        prepareSubmission();
        OwnerReviewRequest request=new OwnerReviewRequest("RETURNED","业主","退回补充",null,null,null,null,null,List.of());
        RaceResult result=race(() -> service.review(SUBMISSION,0,request));
        assertEquals(2,result.successes());assertTrue(result.codes().isEmpty());
        assertEquals("RETURNED",jdbc.queryForObject("SELECT status FROM owner_measurement_submission WHERE id=?",String.class,SUBMISSION));
        assertEquals(1,jdbc.queryForObject("SELECT version FROM owner_measurement_submission WHERE id=?",Integer.class,SUBMISSION));
    }

    @Test void concurrentOwnerConfirmIsIdempotentAndCreatesOneSettlement() throws Exception {
        prepareSubmission();
        OwnerReviewRequest request=confirmedRequest();
        RaceResult result=race(() -> service.review(SUBMISSION,0,request));
        assertEquals(2,result.successes());assertTrue(result.codes().isEmpty());
        assertEquals("SETTLEMENT_CREATED",jdbc.queryForObject("SELECT status FROM owner_measurement_submission WHERE id=?",String.class,SUBMISSION));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM owner_settlement WHERE owner_submission_id=? AND deleted_flag=0",Integer.class,SUBMISSION));
    }

    @Test void measurementDeleteWaitsForLockedTransitionThenFailsImmutable() throws Exception {
        assertDeleteWaitsForTransition(
                "SELECT id FROM production_measurement WHERE id=? FOR UPDATE",
                "UPDATE production_measurement SET status='PENDING',approval_status='PENDING' WHERE id=?",
                MEASUREMENT, GENERAL_FILE);
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM production_measurement WHERE id=?",String.class,MEASUREMENT));
        assertCleanFile(GENERAL_FILE);
    }

    @Test void ownerConfirmationDeleteWaitsForLockedTransitionThenFailsImmutable() throws Exception {
        prepareSubmission();
        assertDeleteWaitsForTransition(
                "SELECT id FROM owner_measurement_submission WHERE id=? FOR UPDATE",
                "UPDATE owner_measurement_submission SET status='SETTLEMENT_CREATED' WHERE id=?",
                SUBMISSION, CONFIRM_FILE);
        assertEquals("SETTLEMENT_CREATED",jdbc.queryForObject("SELECT status FROM owner_measurement_submission WHERE id=?",String.class,SUBMISSION));
        assertCleanFile(CONFIRM_FILE);
    }

    @Test void committedMeasurementEvidenceDeleteMakesSubmitFailWithoutFreezingState() {
        fileService.delete(GENERAL_FILE);
        BusinessException error=assertThrows(BusinessException.class,()->service.submitMeasurement(MEASUREMENT,0));
        assertEquals("PRODUCTION_MEASUREMENT_ATTACHMENT_REQUIRED",error.getCode());
        assertEquals("DRAFT",jdbc.queryForObject("SELECT status FROM production_measurement WHERE id=?",String.class,MEASUREMENT));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM wf_instance WHERE business_type='PRODUCTION_MEASUREMENT' AND business_id=? AND deleted_flag=0",Integer.class,MEASUREMENT));
    }

    @Test void committedConfirmationEvidenceDeleteMakesReviewFailWithoutFreezingState() {
        prepareSubmission();
        fileService.delete(CONFIRM_FILE);
        BusinessException error=assertThrows(BusinessException.class,()->service.review(SUBMISSION,0,confirmedRequest()));
        assertEquals("OWNER_CONFIRMATION_ATTACHMENT_REQUIRED",error.getCode());
        assertEquals("SUBMITTED",jdbc.queryForObject("SELECT status FROM owner_measurement_submission WHERE id=?",String.class,SUBMISSION));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM owner_settlement WHERE owner_submission_id=? AND deleted_flag=0",Integer.class,SUBMISSION));
    }

    private void prepareSubmission(){
        jdbc.update("UPDATE production_measurement SET status='OWNER_SUBMITTED',approval_status='APPROVED' WHERE id=?",MEASUREMENT);
        jdbc.update("INSERT INTO owner_measurement_submission(id,tenant_id,project_id,contract_id,measurement_id,submission_code,revision_no,submitted_at,submitted_amount,confirmed_amount,deducted_amount,status,attachment_count,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,'CAS-SUB',1,CURRENT_TIMESTAMP,100,0,0,'SUBMITTED',1,0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)",SUBMISSION,PROJECT,CONTRACT,MEASUREMENT);
        jdbc.update("INSERT INTO owner_measurement_review_line(id,tenant_id,submission_id,measurement_line_id,submitted_quantity,submitted_amount,created_by,created_at,updated_by,updated_at) VALUES(99192009,0,?,?,10,100,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP)",SUBMISSION,LINE);
    }
    private OwnerReviewRequest confirmedRequest(){return new OwnerReviewRequest("CONFIRMED","业主","确认",LocalDate.now(),LocalDate.now().plusDays(30),BigDecimal.ZERO,BigDecimal.ZERO,999,List.of(new OwnerReviewLineRequest(LINE,new BigDecimal("10"),null)));}
    private void addFile(long id,String type,long businessId,String documentType){jdbc.update("INSERT INTO sys_file(id,tenant_id,business_type,document_type,business_id,file_name,original_name,file_size,content_type,storage_path,bucket_name,virus_scan_status,virus_scanned_at,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,?,'evidence.pdf',100,'application/pdf',?,'test','CLEAN',CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)",id,type,documentType,businessId,id+".pdf",type+"/"+businessId+"/"+id);}
    private RaceResult race(ThrowingWork work)throws Exception{return racePair(work,work);}
    private RaceResult racePair(ThrowingWork first,ThrowingWork second)throws Exception{
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);AtomicInteger successes=new AtomicInteger();List<String> codes=Collections.synchronizedList(new ArrayList<>());
        for(ThrowingWork work:List.of(first,second)) pool.submit(()->{try{context();ready.countDown();start.await();work.run();successes.incrementAndGet();}catch(BusinessException e){codes.add(e.getCode());}catch(Exception e){codes.add(e.getClass().getSimpleName());}finally{UserContext.clear();SecurityContextHolder.clearContext();}});
        assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();pool.shutdown();assertTrue(pool.awaitTermination(40,TimeUnit.SECONDS));return new RaceResult(successes.get(),codes);
    }
    private void assertRace(RaceResult result,String code){assertEquals(1,result.successes(),result.toString());assertEquals(List.of(code),result.codes(),result.toString());}
    private void assertDeleteWaitsForTransition(String lockSql,String updateSql,long businessId,long fileId)throws Exception{
        CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1),deleteStarted=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Future<?> transition=pool.submit(()->new TransactionTemplate(transactionManager).executeWithoutResult(status->{
                jdbc.queryForObject(lockSql,Long.class,businessId);
                jdbc.update(updateSql,businessId);
                locked.countDown();
                await(release);
            }));
            assertTrue(locked.await(10,TimeUnit.SECONDS));
            Future<String> deletion=pool.submit(()->{
                context();deleteStarted.countDown();
                try{fileService.delete(fileId);return "SUCCESS";}
                catch(BusinessException error){return error.getCode();}
                finally{UserContext.clear();SecurityContextHolder.clearContext();}
            });
            assertTrue(deleteStarted.await(10,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,()->deletion.get(300,TimeUnit.MILLISECONDS),"删除必须等待业务行锁释放");
            release.countDown();
            transition.get(10,TimeUnit.SECONDS);
            assertEquals("REVENUE_DOCUMENT_IMMUTABLE",deletion.get(10,TimeUnit.SECONDS));
        }finally{
            release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));
        }
    }
    private void assertCleanFile(long fileId){
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_file WHERE id=? AND virus_scan_status='CLEAN' AND deleted_flag=0",Integer.class,fileId));
    }
    private static void await(CountDownLatch latch){
        try{if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("LOCK_RELEASE_TIMEOUT");}
        catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
    }
    @FunctionalInterface private interface ThrowingWork{void run()throws Exception;}
    private record RaceResult(int successes,List<String> codes){}
    private void context(){UserContext.set(Jwts.claims().subject("admin").add("userId",1L).add("username","admin").add("tenantId",0L).add("roleCodes",List.of("ADMIN")).build());SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","",List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));}
    private void cleanup(){
        jdbc.update("DELETE FROM account_receivable WHERE project_id=?",PROJECT);jdbc.update("DELETE FROM owner_settlement WHERE project_id=?",PROJECT);
        jdbc.update("DELETE FROM owner_measurement_review_line WHERE submission_id IN (SELECT id FROM owner_measurement_submission WHERE measurement_id=?)",MEASUREMENT);
        jdbc.update("DELETE FROM owner_measurement_submission WHERE measurement_id=?",MEASUREMENT);
        jdbc.update("DELETE FROM sys_file WHERE business_id IN (?,?,?)",MEASUREMENT,SUBMISSION,GENERAL_FILE);jdbc.update("DELETE FROM production_measurement_line WHERE measurement_id=?",MEASUREMENT);jdbc.update("DELETE FROM production_measurement WHERE id=?",MEASUREMENT);
        jdbc.update("DELETE FROM wf_record WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=?)",PROJECT);jdbc.update("DELETE FROM wf_task WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=?)",PROJECT);jdbc.update("DELETE FROM wf_node_instance WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=?)",PROJECT);jdbc.update("DELETE FROM wf_cc WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=?)",PROJECT);jdbc.update("DELETE FROM wf_instance WHERE project_id=?",PROJECT);
        jdbc.update("DELETE FROM measurement_period WHERE id=?",PERIOD);jdbc.update("DELETE FROM ct_contract_item WHERE id=?",ITEM);jdbc.update("DELETE FROM ct_contract WHERE id=?",CONTRACT);jdbc.update("DELETE FROM md_partner WHERE id=?",PARTNER);jdbc.update("DELETE FROM pm_project WHERE id=?",PROJECT);
    }
}
