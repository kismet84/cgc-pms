package com.cgcpms.contract;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.service.BusinessMatterRegistryService;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
@Transactional
class BusinessMatterRegistryIntegrationTest {
    private static final long TENANT_ID = 967001L;

    @Autowired BusinessMatterRegistryService registryService;
    @Autowired PmProjectMapper projectMapper;
    @Autowired CtContractMapper contractMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private Long projectId;
    private Long contractId;

    @BeforeEach
    void setUp() {
        UserContext.set(Jwts.claims().add("userId", 1L).add("username", "admin")
                .add("tenantId", TENANT_ID).add("roleCodes", List.of("ADMIN")).build());
        PmProject project = new PmProject();
        project.setTenantId(TENANT_ID);
        project.setProjectCode("MATTER-REGISTRY-PROJECT");
        project.setProjectName("跨域事项测试项目");
        project.setProjectType("CONSTRUCTION");
        project.setContractAmount(new BigDecimal("1000.00"));
        project.setTargetCost(new BigDecimal("800.00"));
        project.setStatus("ACTIVE");
        projectMapper.insert(project);
        projectId = project.getId();
        CtContract contract = new CtContract();
        contract.setTenantId(TENANT_ID);
        contract.setProjectId(projectId);
        contract.setContractCode("MATTER-REGISTRY-CONTRACT");
        contract.setContractName("跨域事项测试合同");
        contract.setContractType("SUB");
        contract.setContractAmount(new BigDecimal("1000.00"));
        contract.setCurrentAmount(new BigDecimal("1000.00"));
        contract.setContractStatus("PERFORMING");
        contractMapper.insert(contract);
        contractId = contract.getId();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void sameMatterCannotBeRegisteredAcrossContractChangeAndVariationOrder() {
        registryService.register(BusinessMatterRegistryService.SOURCE_CONTRACT_CHANGE,
                96700101L, projectId, contractId, "matter-001");

        BusinessException error = assertThrows(BusinessException.class,
                () -> registryService.register(BusinessMatterRegistryService.SOURCE_VARIATION_ORDER,
                        96700102L, projectId, contractId, "MATTER-001"));

        assertEquals("BUSINESS_MATTER_DUPLICATE", error.getCode());
        registryService.release(BusinessMatterRegistryService.SOURCE_CONTRACT_CHANGE,
                96700101L, "人工确认正式来源为现场签证");
        assertDoesNotThrow(() -> registryService.register(BusinessMatterRegistryService.SOURCE_VARIATION_ORDER,
                96700102L, projectId, contractId, "MATTER-001"));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM business_matter_registry WHERE tenant_id = ? AND matter_key = 'MATTER-001'",
                Integer.class, TENANT_ID));
    }
}
