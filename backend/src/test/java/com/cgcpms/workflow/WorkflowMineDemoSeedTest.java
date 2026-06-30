package com.cgcpms.workflow;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.workflow.service.WorkflowQueryService;
import com.cgcpms.workflow.vo.WfMyInstanceVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
class WorkflowMineDemoSeedTest {

    private static final long TENANT_ID = 0L;
    private static final long ADMIN_USER_ID = 1L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkflowQueryService workflowQueryService;

    @Test
    @DisplayName("V108 admin 我发起样本支持四态筛选与第二页")
    void v108MineSeedSupportsStatusesAndSecondPage() {
        long v108Total = count("""
                SELECT COUNT(*)
                FROM wf_instance
                WHERE tenant_id = 0
                  AND initiator_id = 1
                  AND deleted_flag = 0
                  AND remark = 'V108审批中心我发起增强实例样本'
                """);
        assertEquals(16L, v108Total);

        assertEquals(4L, count("""
                SELECT COUNT(*)
                FROM wf_instance
                WHERE tenant_id = 0
                  AND initiator_id = 1
                  AND deleted_flag = 0
                  AND remark = 'V108审批中心我发起增强实例样本'
                  AND instance_status = 'RUNNING'
                """));
        assertEquals(4L, count("""
                SELECT COUNT(*)
                FROM wf_instance
                WHERE tenant_id = 0
                  AND initiator_id = 1
                  AND deleted_flag = 0
                  AND remark = 'V108审批中心我发起增强实例样本'
                  AND instance_status = 'APPROVED'
                """));
        assertEquals(4L, count("""
                SELECT COUNT(*)
                FROM wf_instance
                WHERE tenant_id = 0
                  AND initiator_id = 1
                  AND deleted_flag = 0
                  AND remark = 'V108审批中心我发起增强实例样本'
                  AND instance_status = 'REJECTED'
                """));
        assertEquals(4L, count("""
                SELECT COUNT(*)
                FROM wf_instance
                WHERE tenant_id = 0
                  AND initiator_id = 1
                  AND deleted_flag = 0
                  AND remark = 'V108审批中心我发起增强实例样本'
                  AND instance_status = 'WITHDRAWN'
                """));

        long totalMine = count("""
                SELECT COUNT(*)
                FROM wf_instance
                WHERE tenant_id = 0
                  AND initiator_id = 1
                  AND deleted_flag = 0
                """);
        assertTrue(totalMine > 20, "admin 我发起总数必须大于默认 pageSize=20");

        IPage<WfMyInstanceVO> page1 = workflowQueryService.getMyStarted(TENANT_ID, ADMIN_USER_ID, 1, 20);
        IPage<WfMyInstanceVO> page2 = workflowQueryService.getMyStarted(TENANT_ID, ADMIN_USER_ID, 2, 20);

        assertEquals(totalMine, page1.getTotal());
        assertEquals(20, page1.getRecords().size());
        assertTrue(page2.getRecords().size() >= 1, "必须形成真实第二页");
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
