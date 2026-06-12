package com.cgcpms.org.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.org.entity.OrgCompany;
import com.cgcpms.org.service.OrgCompanyService;
import com.cgcpms.org.vo.OrgCompanyVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/org/companies")
@RequiredArgsConstructor
public class OrgCompanyController {

    private final OrgCompanyService orgCompanyService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('org:list')")
    public ApiResponse<PageResult<OrgCompanyVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String companyCode,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String status) {
        IPage<OrgCompanyVO> page = orgCompanyService.getPage(pageNo, pageSize, companyCode, companyName, status);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('org:query')")
    public ApiResponse<OrgCompanyVO> getById(@PathVariable Long id) {
        return ApiResponse.success(orgCompanyService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('org:add')")
    public ApiResponse<Long> create(@Valid @RequestBody OrgCompany company) {
        return ApiResponse.success(orgCompanyService.create(company));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('org:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody OrgCompany company) {
        company.setId(id);
        orgCompanyService.update(company);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('org:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        orgCompanyService.delete(id);
        return ApiResponse.success();
    }
}
