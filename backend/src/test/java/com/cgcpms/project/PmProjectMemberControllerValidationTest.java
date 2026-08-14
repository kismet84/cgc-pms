package com.cgcpms.project;

import com.cgcpms.project.controller.PmProjectMemberController;
import com.cgcpms.project.dto.CreateProjectMemberRequest;
import com.cgcpms.project.dto.UpdateProjectMemberRequest;
import com.cgcpms.project.service.PmProjectMemberService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PmProjectMemberControllerValidationTest {

    @Test
    void optionsUsesProjectMemberPermissionsWithoutSystemUserQuery() throws Exception {
        PreAuthorize authorization = PmProjectMemberController.class
                .getMethod("options", Long.class, String.class, Long.class)
                .getAnnotation(PreAuthorize.class);

        assertTrue(authorization.value().contains("project:member:add"));
        assertTrue(authorization.value().contains("project:member:edit"));
        assertFalse(authorization.value().contains("system:user:query"));
    }

    @Test
    void createAcceptsOnlyClientWritableFields() throws Exception {
        PmProjectMemberService service = mock(PmProjectMemberService.class);
        when(service.create(org.mockito.ArgumentMatchers.eq(21L),
                org.mockito.ArgumentMatchers.any(CreateProjectMemberRequest.class)))
                .thenReturn(41L);
        MockMvc mvc = mvc(service);

        mvc.perform(post("/projects/21/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "900", "tenantId": "999", "projectId": "888",
                                  "userId": "31", "roleCode": "EMPLOYEE", "positionName": "员工",
                                  "createdBy": "77", "createdTime": "1999-01-01 00:00:00",
                                  "createdAt": "1999-01-01T00:00:00", "updatedBy": "78",
                                  "updatedTime": "1999-01-01 00:00:00",
                                  "updatedAt": "1999-01-01T00:00:00", "deletedFlag": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(41));

        ArgumentCaptor<CreateProjectMemberRequest> request =
                ArgumentCaptor.forClass(CreateProjectMemberRequest.class);
        verify(service).create(org.mockito.ArgumentMatchers.eq(21L), request.capture());
        assertEquals(31L, request.getValue().userId());
        assertEquals("EMPLOYEE", request.getValue().roleCode());
        assertEquals("员工", request.getValue().positionName());
    }

    @Test
    void updateBindsOnlyClientWritableFields() throws Exception {
        PmProjectMemberService service = mock(PmProjectMemberService.class);

        mvc(service).perform(put("/projects/21/members/41")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "900", "tenantId": "999", "projectId": "888",
                                  "userId": "31", "roleCode": "PROJECT_ACCOUNTANT",
                                  "status": "INACTIVE", "createdBy": "77",
                                  "createdAt": "1999-01-01T00:00:00",
                                  "updatedBy": "78", "updatedAt": "1999-01-01T00:00:00",
                                  "deletedFlag": 1
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateProjectMemberRequest> request =
                ArgumentCaptor.forClass(UpdateProjectMemberRequest.class);
        verify(service).update(org.mockito.ArgumentMatchers.eq(21L),
                org.mockito.ArgumentMatchers.eq(41L), request.capture());
        assertEquals(31L, request.getValue().userId());
        assertEquals("PROJECT_ACCOUNTANT", request.getValue().roleCode());
        assertEquals("INACTIVE", request.getValue().status());
    }

    @Test
    void createStillRejectsMissingUserId() throws Exception {
        PmProjectMemberService service = mock(PmProjectMemberService.class);

        mvc(service).perform(post("/projects/21/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"EMPLOYEE\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private MockMvc mvc(PmProjectMemberService service) {
        return MockMvcBuilders.standaloneSetup(new PmProjectMemberController(service))
                .setValidator(new SpringValidatorAdapter(Validation.buildDefaultValidatorFactory().getValidator()))
                .build();
    }
}
