package com.cgcpms.partner.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.service.MdPartnerService;
import com.cgcpms.partner.vo.MdPartnerVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/partners")
@RequiredArgsConstructor
public class MdPartnerController {

    private final MdPartnerService mdPartnerService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('partner:query')")
    public ApiResponse<PageResult<MdPartnerVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String partnerCode,
            @RequestParam(required = false) String partnerName,
            @RequestParam(required = false) String partnerType,
            @RequestParam(required = false) String status) {
        IPage<MdPartnerVO> page = mdPartnerService.getPage(pageNo, pageSize, partnerCode, partnerName, partnerType, status);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('partner:query')")
    public ApiResponse<MdPartnerVO> getById(@PathVariable Long id) {
        return ApiResponse.success(mdPartnerService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('partner:add')")
    public ApiResponse<Long> create(@Valid @RequestBody MdPartner partner) {
        return ApiResponse.success(mdPartnerService.create(partner));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('partner:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody MdPartner partner) {
        partner.setId(id);
        mdPartnerService.update(partner);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('partner:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        mdPartnerService.delete(id);
        return ApiResponse.success();
    }
}
