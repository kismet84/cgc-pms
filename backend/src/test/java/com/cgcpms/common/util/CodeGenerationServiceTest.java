package com.cgcpms.common.util;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CodeGenerationServiceTest {

    private static final long TENANT_ID = 7L;
    private static final String PREFIX = "TST-";

    private final CodeGenerationService service = new CodeGenerationService();
    private TestMapper mapper;
    private String fullPrefix;

    @BeforeEach
    void setUp() {
        mapper = mock(TestMapper.class);
        fullPrefix = PREFIX + LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";
    }

    @Test
    void formatsAllSupportedBoundariesThroughTheStringEntryPoint() {
        stubActiveRows();

        assertEquals(fullPrefix + "001", nextActive(0));
        assertEquals(fullPrefix + "009", nextActive(8));
        assertEquals(fullPrefix + "099", nextActive(98));
        assertEquals(fullPrefix + "999", nextActive(998));
    }

    @Test
    void formatsTheSameBoundaryThroughTheLambdaEntryPoint() {
        stubActiveRows();

        assertEquals(fullPrefix + "999",
                service.nextCode(mapper, TestRow::getCode, PREFIX, TENANT_ID, false, 998));
    }

    @Test
    void advancesSoftDeletedHistoryTo999() {
        when(mapper.selectLastCodeByPrefix(anyString(), anyLong())).thenReturn(fullPrefix + "998");

        assertEquals(fullPrefix + "999", nextIncludingDeleted(0));
    }

    @Test
    void failsClosedWhenTheDailySequenceIsExhausted() {
        when(mapper.selectLastCodeByPrefix(anyString(), anyLong())).thenReturn(fullPrefix + "999");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> nextIncludingDeleted(0));

        assertEquals("BUSINESS_CODE_SEQUENCE_EXHAUSTED", exception.getCode());
    }

    @Test
    void failsClosedWhenAnOffsetWouldOverflowTheDailySequence() {
        stubActiveRows(new TestRow(fullPrefix + "998"));

        BusinessException exception = assertThrows(BusinessException.class, () -> nextActive(1));

        assertEquals("BUSINESS_CODE_SEQUENCE_EXHAUSTED", exception.getCode());
    }

    @Test
    void treatsLegacy1000AsExhaustedInsteadOfRestartingAt001() {
        when(mapper.selectLastCodeByPrefix(anyString(), anyLong())).thenReturn(fullPrefix + "1000");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> nextIncludingDeleted(0));

        assertEquals("BUSINESS_CODE_SEQUENCE_EXHAUSTED", exception.getCode());
    }

    @Test
    void rejectsMalformedHistoricalSuffixInsteadOfRestartingAt001() {
        when(mapper.selectLastCodeByPrefix(anyString(), anyLong())).thenReturn(fullPrefix + "ABC");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> nextIncludingDeleted(0));

        assertEquals("BUSINESS_CODE_SEQUENCE_INVALID", exception.getCode());
    }

    @Test
    void rejectsWrongLengthHistoricalSuffixInsteadOfRestartingAt001() {
        when(mapper.selectLastCodeByPrefix(anyString(), anyLong())).thenReturn(fullPrefix + "99");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> nextIncludingDeleted(0));

        assertEquals("BUSINESS_CODE_SEQUENCE_INVALID", exception.getCode());
    }

    @Test
    void rejectsSignedAndNonAsciiHistoricalSuffixes() {
        for (String suffix : List.of("+99", "９９９")) {
            when(mapper.selectLastCodeByPrefix(anyString(), anyLong())).thenReturn(fullPrefix + suffix);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> nextIncludingDeleted(0));

            assertEquals("BUSINESS_CODE_SEQUENCE_INVALID", exception.getCode());
        }
    }

    @Test
    void failsClosedWhenSoftDeletedHistorySourceIsMissing() {
        ActiveOnlyMapper activeOnlyMapper = mock(ActiveOnlyMapper.class);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.nextCode(activeOnlyMapper, TestRow::getCode,
                        PREFIX, TENANT_ID, true));

        assertEquals("BUSINESS_CODE_DELETED_SOURCE_MISSING", exception.getCode());
        verifyNoInteractions(activeOnlyMapper);
    }

    private String nextActive(int offset) {
        return service.nextCode(mapper, "code", TestRow::getCode,
                PREFIX, TENANT_ID, false, offset);
    }

    private String nextIncludingDeleted(int offset) {
        return service.nextCode(mapper, "code", TestRow::getCode,
                PREFIX, TENANT_ID, true, offset);
    }

    @SafeVarargs
    private final void stubActiveRows(TestRow... rows) {
        doAnswer(invocation -> {
            Page<TestRow> page = invocation.getArgument(0);
            page.setRecords(List.of(rows));
            return page;
        }).when(mapper).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    private interface TestMapper extends BaseMapper<TestRow>, DeletedCodeSource {
    }

    private interface ActiveOnlyMapper extends BaseMapper<TestRow> {
    }

    private static final class TestRow {
        private final String code;

        private TestRow(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
