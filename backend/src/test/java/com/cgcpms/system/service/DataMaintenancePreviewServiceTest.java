package com.cgcpms.system.service;

import com.cgcpms.system.dto.DataMaintenancePreview;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("Data maintenance preview classification")
class DataMaintenancePreviewServiceTest {

    @Test
    @DisplayName("unknown base table and missing manifest table block eligibility while views are ignored")
    void shouldFailClosedAndIgnoreViews() {
        DataMaintenanceTablePolicy policy = new DataMaintenanceTablePolicy(List.of(
                new DataMaintenanceTablePolicy.Group("control", DataMaintenanceTablePolicy.Disposition.RETAIN,
                        List.of("sys_user", "sys_role")),
                new DataMaintenanceTablePolicy.Group("business", DataMaintenanceTablePolicy.Disposition.CLEAR,
                        List.of("pm_project", "sys_file"))
        ), "fingerprint");
        DataMaintenancePreviewService service = new DataMaintenancePreviewService(mock(JdbcTemplate.class), policy);
        Map<String, Long> rows = Map.of("sys_user", 2L, "pm_project", 3L, "sys_file", 4L);

        DataMaintenancePreview preview = service.classify("cgc_pms", List.of(
                new DataMaintenancePreviewService.DatabaseObject("sys_user", "BASE TABLE"),
                new DataMaintenancePreviewService.DatabaseObject("pm_project", "BASE TABLE"),
                new DataMaintenancePreviewService.DatabaseObject("sys_file", "BASE TABLE"),
                new DataMaintenancePreviewService.DatabaseObject("unexpected_table", "BASE TABLE"),
                new DataMaintenancePreviewService.DatabaseObject("unlisted_view", "VIEW")
        ), table -> rows.get(table));

        assertFalse(preview.eligible());
        assertTrue(preview.blockers().contains("UNKNOWN_BASE_TABLE:unexpected_table"));
        assertTrue(preview.blockers().contains("MISSING_BASE_TABLE:sys_role"));
        assertEquals(List.of("unlisted_view"), preview.ignoredViews());
        assertEquals(2, preview.clearTableCount());
        assertEquals(7, preview.clearRowCount());
        assertEquals(4, preview.sysFileCount());
        assertEquals(new DataMaintenancePreview.RetainedGroupCount("control", 1, 2), preview.retainedGroups().getFirst());
    }
}
