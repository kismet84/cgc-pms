package com.cgcpms.file.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.file.service.FileService;
import com.cgcpms.file.vo.SysFileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = true)
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('file:upload')")
    public ApiResponse<SysFileVO> upload(
            @RequestParam MultipartFile file,
            @RequestParam String businessType,
            @RequestParam Long businessId) {
        return ApiResponse.success(fileService.upload(file, businessType, businessId));
    }

    @GetMapping("/{id}/url")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('file:query')")
    public ApiResponse<String> getUrl(@PathVariable Long id) {
        return ApiResponse.success(fileService.getPresignedUrl(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('file:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return ApiResponse.success();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('file:query')")
    public ApiResponse<List<SysFileVO>> listByBusiness(
            @RequestParam String businessType,
            @RequestParam Long businessId) {
        return ApiResponse.success(fileService.listByBusiness(businessType, businessId));
    }
}
