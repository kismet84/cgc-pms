package com.cgcpms.subcontract;

import com.cgcpms.common.TestUserContext;
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

    private void insertTask(long id, String taskCode, String taskName) {
        jdbcTemplate.update("""
                INSERT INTO sub_task
                    (id, tenant_id, project_id, task_code, task_name, progress_percent, status,
                     created_at, updated_at, created_by, updated_by, deleted_flag)
                VALUES (?, ?, ?, ?, ?, 0, 'NOT_STARTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, 0)
                """, id, TENANT_ID, PROJECT_ID, taskCode, taskName, USER_ID, USER_ID);
    }

    private void cleanupFixture() {
        jdbcTemplate.update("DELETE FROM sub_task WHERE id IN (?, ?)", TASK_ID, REUSED_TASK_ID);
    }
}
