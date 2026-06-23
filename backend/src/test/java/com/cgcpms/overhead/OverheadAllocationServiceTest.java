package com.cgcpms.overhead;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.overhead.entity.OverheadAllocationRule;
import com.cgcpms.overhead.mapper.OverheadAllocationRuleMapper;
import com.cgcpms.overhead.service.OverheadAllocationService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
class OverheadAllocationServiceTest {

    @Autowired private OverheadAllocationService service;
    @Autowired private OverheadAllocationRuleMapper mapper;

    @BeforeEach void setUp() {
        UserContext.set(Jwts.claims().subject("admin").add("userId", 1L)
                .add("username", "admin").add("tenantId", 0L)
                .add("roleCodes", List.of("ADMIN")).build());
    }
    @AfterEach void tearDown() { UserContext.clear(); }

    @Test @Transactional @DisplayName("create")
    void testCreate() {
        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(1L); r.setAllocationBasis("EQUAL"); r.setAllocationCycle("MONTHLY");
        Long id = service.create(r);
        assertNotNull(id); assertTrue(id > 0);
    }

    @Test @Transactional @DisplayName("getPage")
    void testGetPage() {
        var p = service.getPage(1, 10);
        assertNotNull(p); assertTrue(p.getTotal() >= 0);
    }

    @Test @Transactional @DisplayName("update")
    void testUpdate() {
        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(1L); r.setAllocationBasis("EQUAL"); r.setAllocationCycle("MONTHLY");
        Long id = service.create(r);

        OverheadAllocationRule u = new OverheadAllocationRule();
        u.setId(id); u.setCostSubjectId(1L); u.setAllocationBasis("DIRECT_LABOR");
        u.setAllocationCycle("MONTHLY");
        service.update(u);
    }

    @Test @Transactional @DisplayName("delete")
    void testDelete() {
        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(1L); r.setAllocationBasis("EQUAL"); r.setAllocationCycle("MONTHLY");
        Long id = service.create(r);
        assertDoesNotThrow(() -> service.delete(id));
    }

    @Test @Transactional @DisplayName("delete — non-existent")
    void testDelete_NotFound() {
        assertThrows(BusinessException.class, () -> service.delete(99999999L));
    }

    @Test @Transactional @DisplayName("executeAllocation")
    void testExecuteAllocation() {
        assertDoesNotThrow(() -> service.executeAllocation(0L, LocalDate.of(2026, 5, 1)));
    }
}
