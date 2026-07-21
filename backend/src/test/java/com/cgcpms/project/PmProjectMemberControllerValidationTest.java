package com.cgcpms.project;

import com.cgcpms.project.controller.PmProjectMemberController;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.service.PmProjectMemberService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PmProjectMemberControllerValidationTest {

    @Test
    void createAcceptsOnlyClientWritableFields() throws Exception {
        PmProjectMemberService service = mock(PmProjectMemberService.class);
        when(service.create(org.mockito.ArgumentMatchers.eq(21L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(41L);
        MockMvc mvc = mvc(service);

        mvc.perform(post("/projects/21/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"31\",\"roleCode\":\"OTH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(41));

        ArgumentCaptor<PmProjectMember> member = ArgumentCaptor.forClass(PmProjectMember.class);
        verify(service).create(org.mockito.ArgumentMatchers.eq(21L), member.capture());
        assertNull(member.getValue().getTenantId());
        assertNull(member.getValue().getProjectId());
    }

    @Test
    void createStillRejectsMissingUserId() throws Exception {
        PmProjectMemberService service = mock(PmProjectMemberService.class);

        mvc(service).perform(post("/projects/21/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"OTH\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private MockMvc mvc(PmProjectMemberService service) {
        return MockMvcBuilders.standaloneSetup(new PmProjectMemberController(service))
                .setValidator(new SpringValidatorAdapter(Validation.buildDefaultValidatorFactory().getValidator()))
                .build();
    }
}
