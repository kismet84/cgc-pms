package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.vo.CostLedgerVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
class CostLedgerServiceTest {

    private static final long PROJECT_ID = 10001L;

    @Autowired
    private CostLedgerService costLedgerService;

    @Autowired
    private CostItemMapper costItemMapper;

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    @DisplayName("getPage handles cost items with null optional relation ids")
    void getPageHandlesNullOptionalRelationIds() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);

        CostItem item = new CostItem();
        item.setTenantId(TestUserContext.TENANT_0);
        item.setProjectId(PROJECT_ID);
        item.setContractId(null);
        item.setPartnerId(null);
        item.setCostSubjectId(null);
        item.setCostType("MATERIAL");
        item.setAmount(new BigDecimal("123.45"));
        item.setTaxAmount(BigDecimal.ZERO);
        item.setAmountWithoutTax(new BigDecimal("123.45"));
        item.setSourceType("TEST_LEDGER_NULL_RELATION");
        item.setSourceId(IdWorker.getId());
        item.setSourceItemId(0L);
        item.setCostDate(LocalDate.now());
        item.setCostStatus("CONFIRMED");
        item.setGeneratedFlag(1);
        item.setCreatedBy(TestUserContext.USER_ADMIN);
        item.setUpdatedBy(TestUserContext.USER_ADMIN);
        costItemMapper.insert(item);

        IPage<CostLedgerVO> page = Assertions.assertDoesNotThrow(
                () -> costLedgerService.getPage(1, 20, null, null, null, null,
                        null, "TEST_LEDGER_NULL_RELATION", null, null, null, null));

        Assertions.assertEquals(1, page.getRecords().size());
        CostLedgerVO vo = page.getRecords().get(0);
        Assertions.assertNull(vo.getContractId());
        Assertions.assertNull(vo.getContractName());
        Assertions.assertNull(vo.getPartnerId());
        Assertions.assertNull(vo.getPartnerName());
        Assertions.assertNull(vo.getCostSubjectId());
        Assertions.assertNull(vo.getCostSubjectName());
    }
}
