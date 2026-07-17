package com.cgcpms.materialreturn;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.materialreturn.dto.MaterialReturnRequest;
import com.cgcpms.materialreturn.service.MaterialReturnService;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import com.cgcpms.requisition.service.MatRequisitionService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
class MaterialReturnConcurrencyTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ID = 1L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;
    private static final long WAREHOUSE_ID = 93040001L;
    private static final long MATERIAL_ID = 93040002L;

    @Autowired private MatWarehouseMapper warehouseMapper;
    @Autowired private MdMaterialMapper materialMapper;
    @Autowired private MatStockMapper stockMapper;
    @Autowired private MatStockTxnMapper stockTxnMapper;
    @Autowired private MatRequisitionMapper requisitionMapper;
    @Autowired private MatRequisitionService requisitionService;
    @Autowired private MaterialReturnService returnService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long requisitionId;
    private Long requisitionItemId;
    private Long originalStockTxnId;

    @BeforeEach
    void setUp() {
        setAdminContext();
        cleanFixtures();

        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setId(WAREHOUSE_ID);
        warehouse.setTenantId(TENANT_ID);
        warehouse.setProjectId(PROJECT_ID);
        warehouse.setWarehouseCode("WH-RETURN-RACE");
        warehouse.setWarehouseName("并发退料测试仓");
        warehouse.setStatus("ENABLE");
        warehouseMapper.insert(warehouse);

        MdMaterial material = new MdMaterial();
        material.setId(MATERIAL_ID);
        material.setTenantId(TENANT_ID);
        material.setMaterialCode("MAT-RETURN-RACE");
        material.setMaterialName("并发退料测试物料");
        material.setUnit("件");
        material.setStatus("ENABLE");
        materialMapper.insert(material);

        MatStock stock = new MatStock();
        stock.setTenantId(TENANT_ID);
        stock.setWarehouseId(WAREHOUSE_ID);
        stock.setMaterialId(MATERIAL_ID);
        stock.setAvailableQty(new BigDecimal("10.0000"));
        stock.setInventoryValue(new BigDecimal("50.00"));
        stock.setAverageUnitCost(new BigDecimal("5.000000"));
        stock.setVersion(0);
        stockMapper.insert(stock);

        MatRequisition requisition = new MatRequisition();
        requisition.setProjectId(PROJECT_ID);
        requisition.setContractId(CONTRACT_ID);
        requisition.setWarehouseId(WAREHOUSE_ID);
        requisition.setRequisitionDate(LocalDate.of(2026, 7, 17));
        requisitionId = requisitionService.create(requisition);

        MatRequisitionItem item = new MatRequisitionItem();
        item.setMaterialId(MATERIAL_ID);
        item.setQuantity(new BigDecimal("10.0000"));
        item.setUnitPrice(new BigDecimal("5.000000"));
        item.setAmount(new BigDecimal("50.00"));
        requisitionService.saveItemsBatch(requisitionId, List.of(item));
        requisitionItemId = item.getId();

        MatRequisition persisted = requisitionMapper.selectById(requisitionId);
        persisted.setApprovalStatus("APPROVED");
        persisted.setStockOutFlag(0);
        requisitionMapper.updateById(persisted);
        requisitionService.executeStockOut(requisitionId);

        MatStockTxn original = stockTxnMapper.selectOne(new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getTenantId, TENANT_ID)
                .eq(MatStockTxn::getSourceType, "MAT_REQUISITION")
                .eq(MatStockTxn::getSourceId, requisitionId)
                .eq(MatStockTxn::getSourceLineId, requisitionItemId));
        originalStockTxnId = original.getId();
    }

    @AfterEach
    void tearDown() {
        setAdminContext();
        cleanFixtures();
        UserContext.clear();
    }

    @Test
    @DisplayName("M3: 同一原出库流水并发部分退料时，行锁保证累计数量不超发")
    void serializesConcurrentReturnsAgainstOriginalStockTransaction() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> confirmAfterBarrier(
                    ready, start, "RETURN-RACE-001"));
            Future<String> second = executor.submit(() -> confirmAfterBarrier(
                    ready, start, "RETURN-RACE-002"));

            assertTrue(ready.await(10, TimeUnit.SECONDS), "两个并发请求必须在超时前就绪");
            start.countDown();
            List<String> outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));

            assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count(), outcomes.toString());
            assertEquals(1, outcomes.stream().filter("RETURN_EXCEEDS_ISSUED"::equals).count(), outcomes.toString());
            BigDecimal totalReturned = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(SUM(i.quantity), 0)
                    FROM mat_material_return_item i
                    JOIN mat_material_return r ON r.tenant_id=i.tenant_id AND r.id=i.return_id
                    WHERE i.tenant_id=? AND i.original_stock_txn_id=?
                      AND i.deleted_flag=0 AND r.deleted_flag=0 AND r.status='CONFIRMED'
                    """, BigDecimal.class, TENANT_ID, originalStockTxnId);
            assertEquals(0, new BigDecimal("6.0000").compareTo(totalReturned));
        } finally {
            executor.shutdownNow();
        }
    }

    private String confirmAfterBarrier(CountDownLatch ready, CountDownLatch start, String idempotencyKey)
            throws InterruptedException {
        setAdminContext();
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) return "BARRIER_TIMEOUT";
            returnService.confirm(new MaterialReturnRequest(
                    requisitionItemId, originalStockTxnId, new BigDecimal("6.0000"),
                    LocalDate.of(2026, 7, 18), "并发部分退料", idempotencyKey));
            return "SUCCESS";
        } catch (BusinessException exception) {
            return exception.getCode();
        } finally {
            UserContext.clear();
        }
    }

    private void setAdminContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ID)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN"))
                .build());
    }

    private void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM cost_item WHERE tenant_id=? AND source_type IN ('MATERIAL_RETURN','MATERIAL_RETURN_REVERSAL') AND source_id IN (SELECT id FROM mat_material_return WHERE tenant_id=? AND requisition_id=?)",
                TENANT_ID, TENANT_ID, requisitionId == null ? -1L : requisitionId);
        jdbcTemplate.update("DELETE FROM mat_stock_txn WHERE tenant_id=? AND source_type IN ('MATERIAL_RETURN','MATERIAL_RETURN_REVERSAL') AND source_id IN (SELECT id FROM mat_material_return WHERE tenant_id=? AND requisition_id=?)",
                TENANT_ID, TENANT_ID, requisitionId == null ? -1L : requisitionId);
        jdbcTemplate.update("DELETE FROM mat_material_return_item WHERE tenant_id=? AND return_id IN (SELECT id FROM mat_material_return WHERE tenant_id=? AND requisition_id=?)",
                TENANT_ID, TENANT_ID, requisitionId == null ? -1L : requisitionId);
        jdbcTemplate.update("DELETE FROM mat_material_return WHERE tenant_id=? AND requisition_id=?",
                TENANT_ID, requisitionId == null ? -1L : requisitionId);
        jdbcTemplate.update("DELETE FROM cost_item WHERE tenant_id=? AND source_type='MAT_REQUISITION' AND source_id=?",
                TENANT_ID, requisitionId == null ? -1L : requisitionId);
        jdbcTemplate.update("DELETE FROM mat_stock_txn WHERE tenant_id=? AND source_type='MAT_REQUISITION' AND source_id=?",
                TENANT_ID, requisitionId == null ? -1L : requisitionId);
        jdbcTemplate.update("DELETE FROM mat_stock WHERE tenant_id=? AND warehouse_id=? AND material_id=?",
                TENANT_ID, WAREHOUSE_ID, MATERIAL_ID);
        jdbcTemplate.update("DELETE FROM mat_requisition_item WHERE tenant_id=? AND requisition_id=?",
                TENANT_ID, requisitionId == null ? -1L : requisitionId);
        jdbcTemplate.update("DELETE FROM mat_requisition WHERE tenant_id=? AND id=?",
                TENANT_ID, requisitionId == null ? -1L : requisitionId);
        jdbcTemplate.update("DELETE FROM mat_warehouse WHERE tenant_id=? AND id=?", TENANT_ID, WAREHOUSE_ID);
        jdbcTemplate.update("DELETE FROM md_material WHERE tenant_id=? AND id=?", TENANT_ID, MATERIAL_ID);
    }
}
