package com.cgcpms.accounting.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.accounting.service.AccountingEntryService;
import com.cgcpms.accounting.service.EntryGenerator;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/accounting-entry")
@RequiredArgsConstructor
public class AccountingEntryController {

    private final AccountingEntryService entryService;
    private final EntryGenerator generator;

    @GetMapping
    @PreAuthorize("hasAuthority('accounting:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PageResult<AccountingEntry>> getPage(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String entryType,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String entryStatus) {
        IPage<AccountingEntry> page = entryService.getPage(pageNo, pageSize,
                entryType, sourceType, startDate, endDate, entryStatus);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('accounting:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> getById(@PathVariable Long id) {
        AccountingEntry entry = entryService.getById(id);
        List<AccountingEntryLine> lines = entryService.getLines(id);
        Map<Long, String> subjectNames = entryService.getLineSubjectNames(lines);
        return ApiResponse.success(Map.of("entry", entry, "lines", lines, "subjectNames", subjectNames));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('accounting:add') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Long> generate(
            @RequestParam String sourceType,
            @RequestParam Long sourceId,
            @RequestParam String entryType) {
        AccountingEntry entry = generator.generateEntry(sourceType, sourceId, entryType);
        return ApiResponse.success(entry != null ? entry.getId() : null);
    }

    @PutMapping("/{id}/post")
    @PreAuthorize("hasAuthority('accounting:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> post(@PathVariable Long id) {
        entryService.post(id);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('accounting:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> reverse(@PathVariable Long id) {
        entryService.reverse(id);
        return ApiResponse.success();
    }
}
