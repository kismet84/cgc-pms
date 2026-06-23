package com.cgcpms.subcontract;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubMeasureItem;
import com.cgcpms.subcontract.entity.SubTask;
import com.cgcpms.subcontract.mapper.SubMeasureItemMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import com.cgcpms.subcontract.service.SubMeasureService;
import com.cgcpms.subcontract.vo.SubMeasureItemVO;
import com.cgcpms.subcontract.vo.SubMeasureVO;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("SubMeasureService — CRUD + guards + net calc")
class SubMeasureServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;
    private static final long PARTNER_ID = 20002L;

    @Autowired private SubMeasureService service;
    @Autowired private SubMeasureMapper measureMapper;
    @Autowired private SubMeasureItemMapper itemMapper;
    @Autowired private SubTaskMapper subTaskMapper;

    @BeforeEach void setupContext() {
        UserContext.set(Jwts.claims().add("userId", USER_ADMIN).add("username", "admin")
                .add("tenantId", TENANT_ID).add("roleCodes", List.of("ADMIN")).build());
    }
    @AfterEach void clearContext() { UserContext.clear(); }

    SubMeasure buildMeasure() {
        SubMeasure m = new SubMeasure();
        m.setProjectId(PROJECT_ID); m.setContractId(CONTRACT_ID); m.setPartnerId(PARTNER_ID);
        m.setMeasureCode("SM-" + System.nanoTime()); m.setMeasurePeriod("2026-06");
        m.setMeasureDate(LocalDate.now()); m.setReportedAmount(new BigDecimal("50000.00"));
        m.setApprovedAmount(new BigDecimal("48000.00")); m.setDeductionAmount(new BigDecimal("2000.00"));
        m.setNetAmount(new BigDecimal("46000.00"));
        return m;
    }

    @Test @Transactional @DisplayName("create → returns ID with auto code")
    void testCreate() {
        SubMeasure m = buildMeasure();
        m.setMeasureCode(null);
        Long id = service.create(m);
        assertNotNull(id);
        SubMeasureVO vo = service.getById(id);
        assertNotNull(vo.getMeasureCode(), "应自动生成编码");
        assertEquals("DRAFT", vo.getApprovalStatus());
    }

    @Test @Transactional @DisplayName("create → with valid subTaskId succeeds")
    void testCreate_WithValidSubTaskId() {
        // Setup: create a subtask in the same project/contract/partner context
        SubTask task = new SubTask();
        task.setTenantId(TENANT_ID); task.setProjectId(PROJECT_ID);
        task.setContractId(CONTRACT_ID); task.setPartnerId(PARTNER_ID);
        task.setTaskCode("TEST-CODE-001"); task.setTaskName("测试任务-subTaskId绑定"); task.setStatus("IN_PROGRESS");
        subTaskMapper.insert(task);

        SubMeasure m = buildMeasure();
        m.setSubTaskId(task.getId());
        Long id = service.create(m);
        assertNotNull(id);
        SubMeasureVO vo = service.getById(id);
        assertEquals(task.getId().toString(), vo.getSubTaskId());
        assertEquals(task.getTaskCode(), vo.getSubTaskCode());
        assertEquals(task.getTaskName(), vo.getSubTaskName());
    }

    @Test @Transactional @DisplayName("create → with non-existent subTaskId throws")
    void testCreate_WithInvalidSubTaskId() {
        SubMeasure m = buildMeasure();
        m.setSubTaskId(99999999L);
        assertThrows(BusinessException.class, () -> service.create(m));
    }

    @Test @Transactional @DisplayName("create → with cross-project subTaskId throws")
    void testCreate_WithCrossProjectSubTaskId() {
        SubTask task = new SubTask();
        task.setTenantId(TENANT_ID); task.setProjectId(99999L);
        task.setContractId(CONTRACT_ID); task.setPartnerId(PARTNER_ID);
        task.setTaskCode("TEST-CODE-002"); task.setTaskName("跨项目任务"); task.setStatus("IN_PROGRESS");
        subTaskMapper.insert(task);

        SubMeasure m = buildMeasure();
        m.setSubTaskId(task.getId());
        assertThrows(BusinessException.class, () -> service.create(m));
    }

    @Test @Transactional @DisplayName("create → with cross-contract subTaskId throws")
    void testCreate_WithCrossContractSubTaskId() {
        SubTask task = new SubTask();
        task.setTenantId(TENANT_ID); task.setProjectId(PROJECT_ID);
        task.setContractId(99999L); task.setPartnerId(PARTNER_ID);
        task.setTaskCode("TEST-CODE-003"); task.setTaskName("跨合同任务"); task.setStatus("IN_PROGRESS");
        subTaskMapper.insert(task);

        SubMeasure m = buildMeasure();
        m.setSubTaskId(task.getId());
        assertThrows(BusinessException.class, () -> service.create(m));
    }

    @Test @Transactional @DisplayName("getPage → null subTaskId returns null fields (compatible)")
    void testGetPage_NoSubTaskId_Compatible() {
        service.create(buildMeasure());
        var page = service.getPage(1, 20, PROJECT_ID, null, null, null, null);
        assertTrue(page.getTotal() > 0);
        SubMeasureVO vo = page.getRecords().get(0);
        assertNotNull(vo.getMeasureCode());
        // subTaskId not set → subTaskCode/Name should be null, no error
        assertNull(vo.getSubTaskId());
        assertNull(vo.getSubTaskCode());
        assertNull(vo.getSubTaskName());
    }

    @Test @Transactional @DisplayName("submitForApproval → with null subTaskId succeeds")
    void testSubmitForApproval_NoSubTaskId() {
        Long id = service.create(buildMeasure());
        service.submitForApproval(id);
        assertEquals("APPROVING", service.getById(id).getApprovalStatus());
    }

    @Test @Transactional @DisplayName("getById → throws on non-existent")
    void testGetById_NotFound() {
        assertThrows(BusinessException.class, () -> service.getById(99999999L));
    }

    @Test @Transactional @DisplayName("getById → tenant isolation")
    void testGetById_CrossTenant() {
        Long id = service.create(buildMeasure());
        UserContext.clear();
        UserContext.set(Jwts.claims().add("userId", 999L).add("tenantId", 999L).add("roleCodes", List.of("ADMIN")).build());
        assertThrows(BusinessException.class, () -> service.getById(id));
    }

    @Test @Transactional @DisplayName("update → succeeds")
    void testUpdate() {
        Long id = service.create(buildMeasure());
        SubMeasure upd = new SubMeasure();
        upd.setId(id); upd.setProjectId(PROJECT_ID); upd.setContractId(CONTRACT_ID);
        upd.setPartnerId(PARTNER_ID); upd.setMeasureCode("SM-UPD-" + System.nanoTime());
        upd.setMeasurePeriod("2026-07"); upd.setMeasureDate(LocalDate.now());
        service.update(upd);
        assertEquals("2026-07", service.getById(id).getMeasurePeriod());
    }

    @Test @Transactional @DisplayName("update → guard: cannot edit when APPROVED")
    void testUpdate_WhenApproved() {
        Long id = service.create(buildMeasure());
        SubMeasure db = measureMapper.selectById(id);
        db.setApprovalStatus("APPROVED"); measureMapper.updateById(db);
        SubMeasure upd = buildMeasure();
        upd.setId(id);
        assertThrows(BusinessException.class, () -> service.update(upd));
    }

    @Test @Transactional @DisplayName("saveItemsBatch → bulk saves items")
    void testSaveItemsBatch() {
        Long id = service.create(buildMeasure());
        SubMeasureItem item = new SubMeasureItem();
        item.setContractItemId(1L); item.setItemName("测试项");
        item.setUnit("m³"); item.setContractQuantity(new BigDecimal("100.00"));
        item.setCurrentQuantity(new BigDecimal("10.00"));
        item.setUnitPrice(new BigDecimal("500.00")); item.setAmount(new BigDecimal("5000.00"));
        service.saveItems(id, List.of(item));
        assertNotNull(service.getById(id).getItems());
    }

    @Test @Transactional @DisplayName("saveItemsBatch → empty list ok")
    void testSaveItemsBatch_Empty() {
        Long id = service.create(buildMeasure());
        service.saveItems(id, List.of());
        assertTrue(service.getById(id).getItems() == null || service.getById(id).getItems().isEmpty());
    }

    @Test @Transactional @DisplayName("submitForApproval → DRAFT→APPROVING")
    void testSubmitForApproval() {
        Long id = service.create(buildMeasure());
        service.submitForApproval(id);
        assertEquals("APPROVING", service.getById(id).getApprovalStatus());
    }

    @Test @Transactional @DisplayName("submitForApproval → duplicate throws")
    void testSubmitForApproval_Duplicate() {
        Long id = service.create(buildMeasure());
        service.submitForApproval(id);
        assertThrows(BusinessException.class, () -> service.submitForApproval(id));
    }
}
