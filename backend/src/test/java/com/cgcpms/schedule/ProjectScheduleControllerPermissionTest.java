package com.cgcpms.schedule;

import com.cgcpms.schedule.controller.ProjectScheduleController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectScheduleControllerPermissionTest {
    @Test
    void snapshotWriteRequiresProgressPermission() throws Exception {
        PreAuthorize guard = ProjectScheduleController.class
                .getMethod("snapshot", Long.class, LocalDate.class)
                .getAnnotation(PreAuthorize.class);

        assertTrue(guard.value().contains("schedule:progress"));
        assertFalse(guard.value().contains("schedule:query"));
    }
}
