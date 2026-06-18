package com.cgcpms.contract;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.dto.ContractSaveRequest;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.service.CtContractService;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.service.PayRecordService;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.service.PmProjectService;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.service.SysRoleService;
import com.cgcpms.system.service.SysUserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Negative cross-tenant access tests.
 * <p>
 * Verifies that tenant A cannot read/update/delete tenant B resources.
 * Uses H2/local profile with tenant IDs 0L (current) and 1L (cross-tenant).
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("TenantIsolation — cross-tenant access is blocked")
class TenantIsolationTest {

    private static final long TENANT_A = 0L; // default test tenant
    private static final long TENANT_B = 1L; // cross-tenant
    private static final long USER_A = 1L;   // admin in tenant A

    @Autowired private SysUserService sysUserService;
    @Autowired private SysUserMapper sysUserMapper;
    @Autowired private SysRoleService sysRoleService;
    @Autowired private SysRoleMapper sysRoleMapper;
    @Autowired private CtContractService contractService;
    @Autowired private PmProjectService projectService;
    @Autowired private PmProjectMapper projectMapper;
    @Autowired private MdPartnerMapper partnerMapper;
    @Autowired private PayRecordService payRecordService;
    @Autowired private PayRecordMapper payRecordMapper;

    // ── seeded cross-tenant resource IDs ──
    private Long tenantBUserId;
    private Long tenantBRoleId;
    private Long tenantBContractId;
    private Long tenantBProjectId;
    private Long tenantBPayRecordId;

    @BeforeEach
    void seedCrossTenantResources() {
        // ── Seed as tenant B ──
        TestUserContext.setAdmin(TENANT_B, 999L);

        // Project for tenant B (needed for contract reference)
        if (projectMapper.selectById(10002L) == null) {
            PmProject p = new PmProject();
            p.setId(10002L);
            p.setProjectCode("XM-TB-001");
            p.setProjectName("租户B测试项目");
            p.setProjectType("CONSTRUCTION");
            p.setContractAmount(new BigDecimal("1000000.00"));
            p.setTargetCost(new BigDecimal("800000.00"));
            p.setStatus("RUNNING");
            p.setApprovalStatus("APPROVED");
            p.setTenantId(TENANT_B);
            projectMapper.insert(p);
        }

        // Partner for tenant B (needed for contract)
        if (partnerMapper.selectById(20003L) == null) {
            MdPartner pa = new MdPartner();
            pa.setId(20003L);
            pa.setPartnerCode("PT-TB-A");
            pa.setPartnerName("租户B甲方");
            pa.setPartnerType("PARTY_A");
            pa.setBlacklistFlag(0);
            pa.setStatus("ENABLE");
            pa.setTenantId(TENANT_B);
            partnerMapper.insert(pa);
        }
        if (partnerMapper.selectById(20004L) == null) {
            MdPartner pb = new MdPartner();
            pb.setId(20004L);
            pb.setPartnerCode("PT-TB-B");
            pb.setPartnerName("租户B乙方");
            pb.setPartnerType("PARTY_B");
            pb.setBlacklistFlag(0);
            pb.setStatus("ENABLE");
            pb.setTenantId(TENANT_B);
            partnerMapper.insert(pb);
        }

        // User for tenant B
        SysUser tbUser = new SysUser();
        tbUser.setId(50001L);
        tbUser.setUsername("tbuser");
        tbUser.setRealName("Tenant B User");
        tbUser.setPassword("encoded");
        tbUser.setStatus("ENABLE");
        tbUser.setTenantId(TENANT_B);
        // Use insert directly to avoid service-layer cross-tenant checks on seed
        if (sysUserMapper.selectById(50001L) == null) {
            sysUserMapper.insert(tbUser);
        }
        tenantBUserId = 50001L;

        // Role for tenant B
        if (sysRoleMapper.selectById(50002L) == null) {
            SysRole tbRole = new SysRole();
            tbRole.setId(50002L);
            tbRole.setRoleCode("TB_ROLE");
            tbRole.setRoleName("Tenant B Role");
            tbRole.setRoleType("CUSTOM");
            tbRole.setStatus("ENABLE");
            tbRole.setTenantId(TENANT_B);
            sysRoleMapper.insert(tbRole);
        }
        tenantBRoleId = 50002L;

        // Contract for tenant B (via compositeSave)
        CtContractItem item = new CtContractItem();
        item.setItemCode("TB-ITEM-001");
        item.setItemName("租户B清单项");
        item.setItemSpec("标准");
        item.setUnit("m³");
        item.setQuantity(new BigDecimal("100"));
        item.setUnitPrice(new BigDecimal("350"));
        item.setAmount(new BigDecimal("35000"));
        item.setSortOrder(1);
        ContractSaveRequest req = new ContractSaveRequest();
        CtContract c = new CtContract();
        c.setProjectId(10002L);
        c.setContractName("租户B合同");
        c.setContractType("SUB");
        c.setPartyAId(20003L);
        c.setPartyBId(20004L);
        c.setContractAmount(new BigDecimal("500000.00"));
        c.setCurrentAmount(new BigDecimal("500000.00"));
        c.setPaidAmount(BigDecimal.ZERO);
        c.setTaxRate(new BigDecimal("13.00"));
        c.setSignedDate(LocalDate.now());
        c.setPaymentMethod("银行转账");
        c.setSettlementMethod("按进度");
        c.setContractStatus("DRAFT");
        c.setApprovalStatus("DRAFT");
        req.setContract(c);
        req.setItems(List.of(item));
        req.setSubmitForApproval(false);
        tenantBContractId = contractService.compositeSave(req);

        // Pay record for tenant B (use mapper directly — writeback() requires valid pay application)
        PayRecord pr = new PayRecord();
        pr.setPayApplicationId(-1L); // dummy
        pr.setContractId(tenantBContractId);
        pr.setPayAmount(new BigDecimal("10000.00"));
        pr.setPayDate(LocalDate.now());
        pr.setPayMethod("银行转账");
        pr.setPayStatus("SUCCESS");
        pr.setTenantId(TENANT_B);
        payRecordMapper.insert(pr);
        tenantBPayRecordId = pr.getId();

        // Project for tenant B (separate from the seeded one)
        tenantBProjectId = 10002L;

        TestUserContext.clear();
    }

    @AfterEach
    void clearContext() {
        TestUserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // T-ISOLATION-1: Cross-tenant user read → USER_NOT_FOUND
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T-ISOLATION-1: tenant A cannot read tenant B user")
    void testCrossTenantUserRead() {
        TestUserContext.setAdmin(TENANT_A, USER_A);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> sysUserService.getById(tenantBUserId));
        assertEquals("USER_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // T-ISOLATION-2: Cross-tenant user update → USER_NOT_FOUND
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T-ISOLATION-2: tenant A cannot update tenant B user")
    void testCrossTenantUserUpdate() {
        TestUserContext.setAdmin(TENANT_A, USER_A);
        SysUser user = new SysUser();
        user.setId(tenantBUserId);
        user.setRealName("Hacked Name");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> sysUserService.update(user));
        assertEquals("USER_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // T-ISOLATION-3: Cross-tenant contract read → CONTRACT_NOT_FOUND
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T-ISOLATION-3: tenant A cannot read tenant B contract")
    void testCrossTenantContractRead() {
        TestUserContext.setAdmin(TENANT_A, USER_A);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.getById(tenantBContractId));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // T-ISOLATION-4: Cross-tenant project read → PROJECT_NOT_FOUND
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T-ISOLATION-4: tenant A cannot read tenant B project")
    void testCrossTenantProjectRead() {
        TestUserContext.setAdmin(TENANT_A, USER_A);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.getById(tenantBProjectId));
        assertEquals("PROJECT_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // T-ISOLATION-5: Cross-tenant pay record read → PAY_RECORD_NOT_FOUND
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T-ISOLATION-5: tenant A cannot read tenant B pay record")
    void testCrossTenantPayRecordRead() {
        TestUserContext.setAdmin(TENANT_A, USER_A);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> payRecordService.getById(tenantBPayRecordId));
        assertEquals("PAY_RECORD_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // T-ISOLATION-6: Cross-tenant role read → ROLE_NOT_FOUND
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T-ISOLATION-6: tenant A cannot read tenant B role")
    void testCrossTenantRoleRead() {
        TestUserContext.setAdmin(TENANT_A, USER_A);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> sysRoleService.getById(tenantBRoleId));
        assertEquals("ROLE_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // T-ISOLATION-7: Cross-tenant contract update → CONTRACT_NOT_FOUND
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T-ISOLATION-7: tenant A cannot update tenant B contract")
    void testCrossTenantContractUpdate() {
        TestUserContext.setAdmin(TENANT_A, USER_A);
        CtContract c = new CtContract();
        c.setId(tenantBContractId);
        c.setContractName("Hacked Contract");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.update(c));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // T-ISOLATION-8: Cross-tenant role binding → ROLE_NOT_FOUND
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T-ISOLATION-8: assignRoles rejects cross-tenant user+role combination")
    void testCrossTenantRoleBinding() {
        // Use tenant A context; try to assign tenant B role to an existing tenant A user
        TestUserContext.setAdmin(TENANT_A, USER_A);
        // tenant A admin user ID is 1 (USER_A), tenant B role is tenantBRoleId
        BusinessException ex = assertThrows(BusinessException.class,
                () -> sysUserService.assignRoles(USER_A, List.of(tenantBRoleId)));
        assertEquals("ROLE_NOT_FOUND", ex.getCode());
    }
}
