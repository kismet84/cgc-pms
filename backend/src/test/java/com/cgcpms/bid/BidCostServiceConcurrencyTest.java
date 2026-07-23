package com.cgcpms.bid;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.bid.service.BidCostService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidCostServiceConcurrencyTest {

    @Mock BidCostMapper mapper;
    @Mock CostItemMapper costItemMapper;
    @Mock PmProjectMapper projectMapper;
    @Mock ProjectAccessChecker projectAccessChecker;

    private BidCostService service;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        if (TableInfoHelper.getTableInfo(BidCost.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            assistant.setCurrentNamespace("BidCostServiceConcurrencyTest");
            TableInfoHelper.initTableInfo(assistant, BidCost.class);
        }
        service = new BidCostService(mapper, costItemMapper, projectMapper, projectAccessChecker);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void updateFailsClosedWhenConditionalWriteLosesRace() {
        BidCost current = bidding(1L);
        when(mapper.selectById(1L)).thenReturn(current);
        when(mapper.update(isNull(), any())).thenReturn(0);
        BidCost command = new BidCost();
        command.setId(1L);
        command.setBidProjectName("并发修改");

        assertConcurrent(() -> service.update(command));
    }

    @Test
    void deleteFailsClosedWhenConditionalWriteLosesRace() {
        when(mapper.selectById(2L)).thenReturn(bidding(2L));
        when(mapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);

        assertConcurrent(() -> service.delete(2L));
    }

    @Test
    void markWonFailsClosedWhenConditionalWriteLosesRace() {
        when(mapper.selectById(3L)).thenReturn(bidding(3L));
        PmProject project = new PmProject();
        project.setId(10001L);
        project.setTenantId(TestUserContext.TENANT_0);
        when(projectMapper.selectById(10001L)).thenReturn(project);
        when(mapper.update(isNull(), any())).thenReturn(0);

        assertConcurrent(() -> service.markAsWon(3L, 10001L));
    }

    @Test
    void markLostFailsBeforeCostWriteWhenConditionalWriteLosesRace() {
        when(mapper.selectById(4L)).thenReturn(bidding(4L));
        when(mapper.update(isNull(), any())).thenReturn(0);

        assertConcurrent(() -> service.markAsLost(4L));
        verify(costItemMapper, never()).update(any(), any());
    }

    private BidCost bidding(Long id) {
        BidCost bid = new BidCost();
        bid.setId(id);
        bid.setTenantId(TestUserContext.TENANT_0);
        bid.setBidStatus("BIDDING");
        return bid;
    }

    private void assertConcurrent(Runnable action) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals("BID_CONCURRENT_STATE_CHANGE", error.getCode());
    }
}
