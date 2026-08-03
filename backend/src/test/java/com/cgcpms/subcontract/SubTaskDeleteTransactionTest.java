package com.cgcpms.subcontract;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.subcontract.entity.SubTask;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import com.cgcpms.subcontract.service.SubTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("WBS软删除墓碑事务故障注入回归")
class SubTaskDeleteTransactionTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ID = 1L;
    private static final long PROJECT_ID = 10001L;
    private static final long TASK_ID = 947001L;
    private static final long REUSED_TASK_ID = 947002L;
    private static final long CONTEXT_TASK_ID = 947003L;
    private static final long CONTEXT_SUCCESSOR_ID = 947004L;
    private static final long MEASURE_ID = 947101L;
    private static final long SCHEDULE_ID = 947201L;
    private static final long WBS_ID = 947202L;
    private static final String ORIGINAL_CODE = "WBS-TXN-ROLLBACK-947001";

    @Autowired
    private SubTaskService subTaskService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;
    @MockitoSpyBean
    private SubTaskMapper subTaskMapper;

    @BeforeEach
    void setUp() {
        reset(subTaskMapper);
        cleanupFixture();
        TestUserContext.setAdmin(TENANT_ID, USER_ID);
        insertWbsFixture();
        insertTask(TASK_ID, ORIGINAL_CODE, "墓碑事务故障注入任务");
    }

    @AfterEach
    void tearDown() {
        reset(subTaskMapper);
        cleanupFixture();
        TestUserContext.clear();
    }

    @Test
    @DisplayName("逻辑删除失败时回滚已经执行的墓碑编号更新")
    void deleteFailureRollsBackTombstoneUpdate() {
        SubTaskMapper realMapper = sqlSessionTemplate.getMapper(SubTaskMapper.class);
        AtomicBoolean tombstoneUpdateApplied = new AtomicBoolean();

        doAnswer(invocation -> {
            int updated = realMapper.updateById(invocation.getArgument(0, SubTask.class));
            assertEquals(1, updated, "墓碑编号更新应先真实写入当前事务");
            tombstoneUpdateApplied.set(true);
            return updated;
        }).when(subTaskMapper).updateById(any(SubTask.class));
        doAnswer(invocation -> {
            assertTrue(tombstoneUpdateApplied.get(), "异常必须发生在墓碑更新成功之后");
            throw new IllegalStateException("TEST_DELETE_BY_ID_FAILURE");
        }).when(subTaskMapper).deleteById(TASK_ID);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> subTaskService.delete(TASK_ID));
        assertEquals("TEST_DELETE_BY_ID_FAILURE", failure.getMessage());
        reset(subTaskMapper);

        var persisted = jdbcTemplate.queryForMap(
                "SELECT task_code, deleted_flag FROM sub_task WHERE id = ?", TASK_ID);
        assertEquals(ORIGINAL_CODE, persisted.get("TASK_CODE"));
        assertEquals(0, ((Number) persisted.get("DELETED_FLAG")).intValue(),
                "服务事务退出后墓碑编号与逻辑删除标志必须一起回滚");
    }

    @Test
    @DisplayName("正常删除使用唯一墓碑并释放原业务编号")
    void successfulDeleteUsesUniqueTombstoneAndReleasesOriginalCode() {
        subTaskService.delete(TASK_ID);

        var deleted = jdbcTemplate.queryForMap(
                "SELECT task_code, deleted_flag FROM sub_task WHERE id = ?", TASK_ID);
        assertEquals("DELETED-" + TASK_ID, deleted.get("TASK_CODE"));
        assertEquals(1, ((Number) deleted.get("DELETED_FLAG")).intValue());

        insertTask(REUSED_TASK_ID, ORIGINAL_CODE, "复用原编号的新任务");
        Integer activeWithOriginalCode = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sub_task
                WHERE tenant_id = ? AND task_code = ? AND deleted_flag = 0
                """, Integer.class, TENANT_ID, ORIGINAL_CODE);
        assertEquals(1, activeWithOriginalCode);
    }

    @Test
    @DisplayName("被计量引用的任务禁止删除且双方数据不变")
    void referencedTaskCannotBeDeleted() {
        jdbcTemplate.update("""
                INSERT INTO sub_measure
                    (id, tenant_id, project_id, sub_task_id, measure_code, approval_status, status,
                     cost_generated_flag, created_at, updated_at, created_by, updated_by, deleted_flag)
                VALUES (?, ?, ?, ?, ?, 'DRAFT', 'DRAFT', 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, 0)
                """, MEASURE_ID, TENANT_ID, PROJECT_ID, TASK_ID,
                "SM-TASK-REF-" + MEASURE_ID, USER_ID, USER_ID);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> subTaskService.delete(TASK_ID));
        assertEquals("SUB_TASK_MEASURE_IN_USE", failure.getCode());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sub_task WHERE id = ? AND deleted_flag = 0",
                Integer.class, TASK_ID));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sub_measure WHERE id = ? AND deleted_flag = 0",
                Integer.class, MEASURE_ID));
    }

    @Test
    @DisplayName("前置任务必须与后续任务属于同一合同和分包商")
    void predecessorMustUseSameContractAndPartner() {
        insertContextTask(CONTEXT_TASK_ID, null, 30001L, 20002L);
        SubTask dependent = new SubTask();
        dependent.setProjectId(PROJECT_ID);
        dependent.setWbsTaskId(WBS_ID);
        dependent.setPredecessorTaskId(CONTEXT_TASK_ID);
        dependent.setTaskName("跨合同依赖");
        dependent.setProgressPercent(BigDecimal.ZERO);
        dependent.setStatus("NOT_STARTED");

        BusinessException failure = assertThrows(BusinessException.class,
                () -> subTaskService.create(dependent));

        assertEquals("SUB_TASK_DEPENDENCY_INVALID", failure.getCode());
    }

    @Test
    @DisplayName("被后续任务引用时不得变更为不同合同或分包商上下文")
    void predecessorContextCannotDivergeFromSuccessor() {
        insertContextTask(CONTEXT_SUCCESSOR_ID, TASK_ID, 30001L, 20002L);
        SubTask update = new SubTask();
        update.setId(TASK_ID);
        update.setProjectId(PROJECT_ID);
        update.setWbsTaskId(WBS_ID);
        update.setTaskName("保持空合同上下文");
        update.setProgressPercent(BigDecimal.ZERO);
        update.setStatus("NOT_STARTED");

        BusinessException failure = assertThrows(BusinessException.class,
                () -> subTaskService.update(update));

        assertEquals("SUB_TASK_DEPENDENCY_INVALID", failure.getCode());
    }

    private void insertTask(long id, String taskCode, String taskName) {
        jdbcTemplate.update("""
                INSERT INTO sub_task
                    (id, tenant_id, project_id, task_code, task_name, progress_percent, status,
                     created_at, updated_at, created_by, updated_by, deleted_flag)
                VALUES (?, ?, ?, ?, ?, 0, 'NOT_STARTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, 0)
                """, id, TENANT_ID, PROJECT_ID, taskCode, taskName, USER_ID, USER_ID);
    }

    @Test
    @DisplayName("新施工任务必须关联生效WBS")
    void createRequiresActiveWbs() {
        SubTask task = new SubTask();
        task.setProjectId(PROJECT_ID);
        task.setTaskName("缺少WBS");
        task.setProgressPercent(BigDecimal.ZERO);
        task.setStatus("NOT_STARTED");

        BusinessException failure = assertThrows(BusinessException.class, () -> subTaskService.create(task));

        assertEquals("PROJECT_WBS_REQUIRED", failure.getCode());
    }

    private void insertWbsFixture() {
        jdbcTemplate.update("""
                INSERT INTO project_schedule_plan
                    (id, tenant_id, project_id, plan_code, plan_name, plan_type, version_no,
                     planned_start_date, planned_end_date, status, version, created_by, created_at,
                     updated_by, updated_at, deleted_flag)
                VALUES (?, ?, ?, 'SUB-TASK-TEST-SP', '分包任务测试基线', 'BASELINE', 947,
                        '2026-01-01', '2026-12-31', 'ACTIVE', 0, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, 0)
                """, SCHEDULE_ID, TENANT_ID, PROJECT_ID, USER_ID, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO project_wbs_task
                    (id, tenant_id, project_id, schedule_plan_id, task_code, task_name,
                     planned_start_date, planned_end_date, weight_percent, actual_quantity,
                     actual_progress, status, sort_order, version, created_by, created_at,
                     updated_by, updated_at, deleted_flag)
                VALUES (?, ?, ?, ?, 'SUB-TASK-TEST-WBS', '分包任务测试WBS',
                        '2026-01-01', '2026-12-31', 100, 0, 0, 'NOT_STARTED', 1, 0, ?,
                        CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, 0)
                """, WBS_ID, TENANT_ID, PROJECT_ID, SCHEDULE_ID, USER_ID, USER_ID);
    }

    private void insertContextTask(long id, Long predecessorId, Long contractId, Long partnerId) {
        jdbcTemplate.update("""
                INSERT INTO sub_task
                    (id, tenant_id, project_id, contract_id, partner_id, predecessor_task_id,
                     task_code, task_name, progress_percent, status,
                     created_at, updated_at, created_by, updated_by, deleted_flag)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 'NOT_STARTED',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, 0)
                """, id, TENANT_ID, PROJECT_ID, contractId, partnerId, predecessorId,
                "WBS-CONTEXT-" + id, "上下文依赖任务-" + id, USER_ID, USER_ID);
    }

    private void cleanupFixture() {
        jdbcTemplate.update("DELETE FROM sub_measure WHERE id = ?", MEASURE_ID);
        jdbcTemplate.update("DELETE FROM sub_task WHERE id IN (?, ?)",
                CONTEXT_SUCCESSOR_ID, CONTEXT_TASK_ID);
        jdbcTemplate.update("DELETE FROM sub_task WHERE id IN (?, ?)", TASK_ID, REUSED_TASK_ID);
        jdbcTemplate.update("DELETE FROM project_wbs_task WHERE id = ?", WBS_ID);
        jdbcTemplate.update("DELETE FROM project_schedule_plan WHERE id = ?", SCHEDULE_ID);
    }
}
