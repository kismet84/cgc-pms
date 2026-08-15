package com.cgcpms.bid;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.bid.dto.BidCostOption;
import com.cgcpms.bid.service.BidCostService;
import com.cgcpms.cashbook.dto.CashJournalQuery;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
abstract class BidProjectScopeDataTestSupport {
    private static final long TENANT = 0L;
    private static final long OTHER_TENANT = 1001L;
    private static final long USER = 7_202_001L;
    private static final long ALLOWED_PROJECT = 7_202_011L;
    private static final long DENIED_PROJECT = 7_202_012L;
    private static final long UNBOUND_BID = 7_202_021L;
    private static final long ALLOWED_BID = 7_202_022L;
    private static final long DENIED_BID = 7_202_023L;
    private static final long OTHER_TENANT_BID = 7_202_024L;

    @Autowired private BidCostService bidCostService;
    @Autowired private CashJournalService cashJournalService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("""
                INSERT INTO pm_project
                    (id, tenant_id, project_code, project_name, project_type, contract_amount,
                     approval_status, status, created_by, deleted_flag)
                VALUES (?, ?, ?, ?, 'CONSTRUCTION', 0, 'APPROVED', 'ACTIVE', ?, 0)
                """, ALLOWED_PROJECT, TENANT, "M72-ALLOWED", "72授权项目", 1L);
        jdbc.update("""
                INSERT INTO pm_project
                    (id, tenant_id, project_code, project_name, project_type, contract_amount,
                     approval_status, status, created_by, deleted_flag)
                VALUES (?, ?, ?, ?, 'CONSTRUCTION', 0, 'APPROVED', 'ACTIVE', ?, 0)
                """, DENIED_PROJECT, TENANT, "M72-DENIED", "72无权项目", 1L);
        jdbc.update("""
                INSERT INTO pm_project_member
                    (id, tenant_id, project_id, user_id, role_code, status, deleted_flag)
                VALUES (?, ?, ?, ?, 'PROJECT_MANAGER', 'ACTIVE', 0)
                """, 7_202_031L, TENANT, ALLOWED_PROJECT, USER);
        insertBid(UNBOUND_BID, TENANT, null, "M72-BID-UNBOUND", "72未绑定投标");
        insertBid(ALLOWED_BID, TENANT, ALLOWED_PROJECT, "M72-BID-ALLOWED", "72授权投标");
        insertBid(DENIED_BID, TENANT, DENIED_PROJECT, "M72-BID-DENIED", "72无权投标");
        insertBid(OTHER_TENANT_BID, OTHER_TENANT, null, "M72-BID-OTHER", "72跨租户投标");
        authenticate();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        UserContext.clear();
    }

    @Test
    void optionsReturnOnlyUnboundAndAccessibleProjectBids() {
        Set<Long> ids = bidCostService.listCostOptions().stream()
                .map(BidCostOption::id)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.of(UNBOUND_BID, ALLOWED_BID), ids);
        assertFalse(ids.contains(DENIED_BID));
        assertFalse(ids.contains(OTHER_TENANT_BID));

        TestUserContext.setUser(TENANT, USER + 1, "m72-empty", List.of());
        assertEquals(Set.of(UNBOUND_BID), bidCostService.listCostOptions().stream()
                .map(BidCostOption::id).collect(java.util.stream.Collectors.toSet()));

        TestUserContext.setUser(TENANT, USER, "m72-super-admin", List.of("SUPER_ADMIN"));
        assertEquals(Set.of(UNBOUND_BID, ALLOWED_BID, DENIED_BID), bidCostService.listCostOptions().stream()
                .map(BidCostOption::id).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void unauthorizedBidSummaryRejectsBeforeReturningFinancialFacts() {
        var query = new CashJournalQuery();
        query.setBidCostId(DENIED_BID);
        long before = countBidJournalRows(DENIED_BID);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> cashJournalService.summary(query));

        assertEquals("PROJECT_ACCESS_DENIED", failure.getCode());
        assertEquals(before, countBidJournalRows(DENIED_BID));
        assertTrue(bidCostService.getById(ALLOWED_BID).getProjectId().equals(ALLOWED_PROJECT));
    }

    private void authenticate() {
        TestUserContext.setUser(TENANT, USER, "m72-scope", List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "m72-scope", "n/a", List.of(new SimpleGrantedAuthority("bid:cost:query"))));
    }

    private void insertBid(long id, long tenantId, Long projectId, String code, String name) {
        jdbc.update("""
                INSERT INTO bid_cost
                    (id, tenant_id, project_id, bid_code, bid_project_name, bid_status, created_by, deleted_flag)
                VALUES (?, ?, ?, ?, ?, 'PREPARING', ?, 0)
                """, id, tenantId, projectId, code, name, USER);
    }

    private long countBidJournalRows(long bidId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cash_journal_entry WHERE bid_cost_id = ?", Long.class, bidId);
    }

}

@SpringBootTest
@ActiveProfiles("local")
class BidProjectScopeH2Test extends BidProjectScopeDataTestSupport {
}

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "jdbc:mysql:.*")
class BidProjectScopeMySqlTest extends BidProjectScopeDataTestSupport {
    @DynamicPropertySource
    static void mysqlMigrationLocations(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.locations",
                () -> "classpath:db/migration,classpath:db/migration-legacy");
    }
}
