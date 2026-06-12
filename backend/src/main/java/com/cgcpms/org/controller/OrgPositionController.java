package com.cgcpms.org.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.org.entity.OrgPosition;
import com.cgcpms.org.service.OrgPositionService;
import com.cgcpms.org.vo.OrgPositionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/org/positions")
@RequiredArgsConstructor
public class OrgPositionController {

    private final OrgPositionService orgPositionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('org:list')")
    public ApiResponse<PageResult<OrgPositionVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String positionCode,
            @RequestParam(required = false) String positionName,
            @RequestParam(required = false) String status) {
        IPage<OrgPositionVO> page = orgPositionService.getPage(pageNo, pageSize, positionCode, positionName, status);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('org:query')")
    public ApiResponse<OrgPositionVO> getById(@PathVariable Long id) {
        return ApiResponse.success(orgPositionService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('org:add')")
    public ApiResponse<Long> create(@Valid @RequestBody OrgPosition position) {
        return ApiResponse.success(orgPositionService.create(position));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('org:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody OrgPosition position) {
        position.setId(id);
        orgPositionService.update(position);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('org:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        orgPositionService.delete(id);
        return ApiResponse.success();
    }
}
