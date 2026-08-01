package com.cgcpms.system.dict.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.system.dict.service.SysDictTypeService;
import com.cgcpms.system.dict.vo.SysDictGroupTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system/dict")
@RequiredArgsConstructor
public class SysDictTreeController {

    private final SysDictTypeService typeService;

    @GetMapping("/tree")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:dict:list')")
    public ApiResponse<List<SysDictGroupTreeVO>> tree(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(typeService.getTree(keyword));
    }
}
