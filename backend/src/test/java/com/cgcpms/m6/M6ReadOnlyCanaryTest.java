package com.cgcpms.m6;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.accounting.entity.AccountingEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("M6 subcontract settlement and finance read-only canary")
class M6ReadOnlyCanaryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("representative GETs accept exact permissions and do not change business tables")
    void representativeGetsAreAuthorizedAndReadOnly() throws Exception {
        Snapshot before = snapshot();

        assertAllowed("/sub-tasks", "subtask:query");
        assertAllowed("/settlements", "settlement:query");
        assertAllowed("/pay-applications", "payment:app:query");
        assertAllowed("/cash-journal-entries", "cashbook:journal:query");
        assertAllowed("/accounting-entry", "accounting:query");
        assertAllowed("/financial-close/periods", "finance:close:query");

        assertEquals(before, snapshot());
    }

    @Test
    @DisplayName("accounting amounts serialize as decimal strings")
    void accountingAmountsSerializeAsStrings() throws Exception {
        AccountingEntry entry = new AccountingEntry();
        entry.setTotalDebit(new BigDecimal("9007199254740993.01"));
        entry.setTotalCredit(new BigDecimal("0.00"));

        var json = objectMapper.readTree(objectMapper.writeValueAsString(entry));
        assertEquals("9007199254740993.01", json.path("totalDebit").textValue());
        assertEquals("0.00", json.path("totalCredit").textValue());
    }

    @Test
    @DisplayName("representative GETs reject users without exact permissions")
    void representativeGetsFailClosed() throws Exception {
        for (String path : List.of(
                "/sub-tasks",
                "/settlements",
                "/pay-applications",
                "/cash-journal-entries",
                "/accounting-entry",
                "/financial-close/periods")) {
            mockMvc.perform(apiGet(path).cookie(cookie(List.of())))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("subcontract task GET rejects obsolete navigation permission")
    void subcontractTaskRejectsObsoletePermission() throws Exception {
        mockMvc.perform(apiGet("/sub-tasks").cookie(cookie(List.of("subcontract:task:query"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("same-tenant user without project scope cannot list project financial facts")
    void projectScopedListsFailClosed() throws Exception {
        assertEmptyPage("/sub-measures", "subcontract:measure:query");
        assertEmptyPage("/settlements", "settlement:query");
        assertEmptyPage("/expenses", "expense:query");
        assertEmptyPage("/invoices", "invoice:query");
        assertEmptyPage("/accounting-entry", "accounting:query");

        Long projectId = jdbcTemplate.queryForObject(
                "SELECT id FROM pm_project WHERE tenant_id=0 AND deleted_flag=0 ORDER BY id LIMIT 1",
                Long.class);
        mockMvc.perform(apiGet("/cash-journal-entries?projectId=" + projectId)
                        .cookie(cookie(999L, 0L, List.of("cashbook:journal:query"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("settlement compute, baseline and subresources fail closed across projects")
    void settlementSubresourcesFailClosedAcrossProjects() throws Exception {
        Cookie noProjectAccess = cookie(999L, 0L, List.of("settlement:query"));
        Long contractId = jdbcTemplate.queryForObject(
                "SELECT id FROM ct_contract WHERE tenant_id=0 AND project_id IS NOT NULL "
                        + "AND deleted_flag=0 ORDER BY id LIMIT 1",
                Long.class);
        mockMvc.perform(apiGet("/settlements/compute/" + contractId).cookie(noProjectAccess))
                .andExpect(status().isForbidden());
        mockMvc.perform(apiGet("/settlements/amount-baseline").cookie(noProjectAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    private void assertAllowed(String path, String permission) throws Exception {
        mockMvc.perform(apiGet(path).cookie(cookie(List.of(permission))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    private void assertEmptyPage(String path, String permission) throws Exception {
        mockMvc.perform(apiGet(path).cookie(cookie(999L, 0L, List.of(permission))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    private Cookie cookie(List<String> permissions) {
        return cookie(1L, 0L, permissions);
    }

    private Cookie cookie(Long userId, Long tenantId, List<String> permissions) {
        return new Cookie(
                CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(userId, "m6-canary", tenantId, List.of(), permissions));
    }

    private MockHttpServletRequestBuilder apiGet(String path) {
        return get("/api" + path).contextPath("/api");
    }

    private Snapshot snapshot() {
        return new Snapshot(
                fact("sub_task"),
                fact("stl_settlement"),
                fact("pay_application"),
                fact("cash_journal_entry"),
                fact("accounting_entry"),
                fact("finance_period"));
    }

    private TableFact fact(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        Object updatedAt = jdbcTemplate.queryForObject(
                "SELECT MAX(updated_at) FROM " + table, Object.class);
        return new TableFact(count == null ? 0L : count, updatedAt);
    }

    private record Snapshot(
            TableFact subcontractTasks,
            TableFact settlements,
            TableFact paymentApplications,
            TableFact cashJournalEntries,
            TableFact accountingEntries,
            TableFact financePeriods) {
    }

    private record TableFact(long count, Object updatedAt) {
    }
}
