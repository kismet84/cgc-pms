package com.cgcpms.bid;

import com.cgcpms.bid.dto.BidCostCreateRequest;
import com.cgcpms.bid.dto.BidCostUpdateRequest;
import com.cgcpms.bid.dto.BidDocumentVoidRequest;
import com.cgcpms.bid.dto.BidStatusUpdateRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BidRequestNormalizationTest {

    @Test
    void createAndUpdateNormalizeNullableText() {
        var create = new BidCostCreateRequest(
                " 工程 ", " ", null, " 代理 ", null, "公开", " ", null, " https://example.test ",
                null, null, null, null, null, null, null, null, null, " 备注 ");
        assertEquals("工程", create.bidProjectName());
        assertNull(create.bidSectionName());
        assertNull(create.tendereeName());
        assertEquals("代理", create.agencyName());
        assertEquals("https://example.test", create.sourceUrl());
        assertEquals("备注", create.toEntity().getRemark());

        var update = new BidCostUpdateRequest(
                " 工程二 ", null, " ", " 代理二 ", null, "邀请", " ", null, " /source ",
                null, null, null, null, null, null, null, null, null, " 更新 ");
        assertEquals("工程二", update.bidProjectName());
        assertNull(update.bidSectionName());
        assertNull(update.tendereeName());
        assertEquals("代理二", update.agencyName());
        assertEquals("/source", update.toEntity(7L).getSourceUrl());
        assertEquals(7L, update.toEntity(7L).getId());
    }

    @Test
    void statusAndVoidRequestsNormalizeCompatibilityValues() {
        var legacy = new BidStatusUpdateRequest(null, " bidding ", " ");
        assertNull(legacy.targetStatus());
        assertEquals("PREPARING", legacy.expectedStatus());
        assertNull(legacy.reason());

        var current = new BidStatusUpdateRequest(" won ", " submitted ", " 有效通知书 ");
        assertEquals("WON", current.targetStatus());
        assertEquals("SUBMITTED", current.expectedStatus());
        assertEquals("有效通知书", current.reason());
        assertNull(new BidStatusUpdateRequest("LOST", "EVALUATING", null).reason());

        assertNull(new BidDocumentVoidRequest(null).reason());
        assertEquals("重复文件", new BidDocumentVoidRequest(" 重复文件 ").reason());
    }
}
