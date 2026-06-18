package com.cgcpms.material.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.service.MdMaterialService;
import com.cgcpms.material.vo.MdMaterialVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/materials")
@RequiredArgsConstructor
public class MdMaterialController {

    private final MdMaterialService mdMaterialService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('material:dict:list')")
    public ApiResponse<PageResult<MdMaterialVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status) {
        PageResult<MdMaterialVO> page = mdMaterialService.getPage(pageNo, pageSize, materialCode, materialName, categoryId, status);
        return ApiResponse.success(page);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('material:dict:query')")
    public ApiResponse<MdMaterialVO> getById(@PathVariable Long id) {
        return ApiResponse.success(mdMaterialService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('material:dict:add')")
    public ApiResponse<Long> create(@Valid @RequestBody MdMaterial material) {
        return ApiResponse.success(mdMaterialService.create(material));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('material:dict:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody MdMaterial material) {
        material.setId(id);
        mdMaterialService.update(material);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('material:dict:edit')")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @RequestParam @Pattern(regexp = "^(ENABLE|DISABLE)$") String status) {
        mdMaterialService.updateStatus(id, status);
        return ApiResponse.success();
    }
}
