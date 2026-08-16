package com.cgcpms.cost;

import com.cgcpms.cost.controller.CostSubjectController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class CostSubjectControllerAuthorizationTest {

    @Test
    void targetAndBudgetUsersCanReadOptionsButCannotManageSubjects() throws Exception {
        String readGate = CostSubjectController.class.getMethod("getList", String.class)
                .getAnnotation(PreAuthorize.class).value();
        String writeGate = CostSubjectController.class.getMethod(
                        "create", CostSubjectController.CostSubjectCommand.class)
                .getAnnotation(PreAuthorize.class).value();

        assertThat(readGate)
                .contains("cost:target:query")
                .contains("budget:query");
        assertThat(writeGate)
                .doesNotContain("cost:target:query")
                .doesNotContain("budget:query");
    }
}
