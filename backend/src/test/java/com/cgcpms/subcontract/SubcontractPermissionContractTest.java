package com.cgcpms.subcontract;

import com.cgcpms.subcontract.controller.SubMeasureController;
import com.cgcpms.subcontract.controller.SubTaskController;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubTask;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SubcontractPermissionContractTest {

    @Test
    void taskAndMeasureMutationsKeepIndependentAuthorities() throws Exception {
        assertAuthority(SubTaskController.class, "create", new Class<?>[]{SubTask.class}, "subtask:add");
        assertAuthority(SubTaskController.class, "update",
                new Class<?>[]{Long.class, SubTask.class}, "subtask:edit");
        assertAuthority(SubTaskController.class, "delete", new Class<?>[]{Long.class}, "subtask:delete");
        assertAuthority(SubMeasureController.class, "create",
                new Class<?>[]{SubMeasure.class}, "subcontract:measure:add");
        assertAuthority(SubMeasureController.class, "update",
                new Class<?>[]{Long.class, SubMeasure.class}, "subcontract:measure:edit");
        assertAuthority(SubMeasureController.class, "delete",
                new Class<?>[]{Long.class}, "subcontract:measure:delete");
        assertAuthority(SubMeasureController.class, "batchSaveItems",
                new Class<?>[]{Long.class, List.class}, "subcontract:measure:edit");
        assertAuthority(SubMeasureController.class, "submit",
                new Class<?>[]{Long.class}, "subcontract:measure:submit");
        PreAuthorize formOptions = SubTaskController.class.getMethod("formOptions", Long.class)
                .getAnnotation(PreAuthorize.class);
        assertTrue(formOptions.value().contains("'subtask:add'"));
        assertTrue(formOptions.value().contains("'subtask:edit'"));
    }

    private void assertAuthority(Class<?> type, String method, Class<?>[] parameters, String authority)
            throws Exception {
        PreAuthorize annotation = type.getMethod(method, parameters).getAnnotation(PreAuthorize.class);
        assertTrue(annotation.value().contains("hasAuthority('" + authority + "')"),
                method + " 缺少精确权限 " + authority);
    }
}
