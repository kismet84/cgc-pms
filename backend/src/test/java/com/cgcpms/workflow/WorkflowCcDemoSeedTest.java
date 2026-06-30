package com.cgcpms.workflow;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.workflow.service.WorkflowQueryService;
import com.cgcpms.workflow.vo.WfCcVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
class WorkflowCcDemoSeedTest {

    private static final long TENANT_ID = 0L;
    private static final long ADMIN_USER_ID = 1L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkflowQueryService workflowQueryService;

    @Test
    @DisplayName("V109 admin 抄送列表返回最小 demo 样本")
    void v109CcSeedMakesAdminCcListNonEmpty() {
        long seededCount = count("""
                SELECT COUNT(*)
                FROM wf_cc
                WHERE tenant_id = 0
                  AND cc_user_id = 1
                  AND id IN (979000000000000901, 979000000000000902)
                """);
        assertEquals(2L, seededCount);

        IPage<WfCcVO> page = workflowQueryService.getMyCc(ADMIN_USER_ID, TENANT_ID, 1, 20);
        assertTrue(page.getTotal() >= 2, "admin 抄送列表至少应包含 V109 补的 2 条样本");

        Set<String> ids = page.getRecords().stream()
                .map(WfCcVO::getId)
                .collect(Collectors.toSet());
        assertTrue(ids.contains("979000000000000901"));
        assertTrue(ids.contains("979000000000000902"));

        Set<String> statuses = page.getRecords().stream()
                .filter(vo -> ids.contains(vo.getId()))
                .map(WfCcVO::getInstanceStatus)
                .collect(Collectors.toSet());
        assertTrue(statuses.contains("RUNNING"));
        assertTrue(statuses.contains("APPROVED"));
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
