package com.cgcpms.file.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.file.service.FileService;
import com.cgcpms.file.vo.SysFileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * Upload a file associated with a business entity.
     */
    @PostMapping("/upload")
    public ApiResponse<SysFileVO> upload(
            @RequestParam MultipartFile file,
            @RequestParam String businessType,
            @RequestParam Long businessId) {
        return ApiResponse.success(fileService.upload(file, businessType, businessId));
    }

    /**
     * Get a presigned download URL for a file.
     */
    @GetMapping("/{id}/url")
    public ApiResponse<String> getUrl(@PathVariable Long id) {
        return ApiResponse.success(fileService.getPresignedUrl(id));
    }

    /**
     * Delete a file (MinIO + DB).
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return ApiResponse.success();
    }

    /**
     * List files by business type and business ID.
     */
    @GetMapping
    public ApiResponse<List<SysFileVO>> listByBusiness(
            @RequestParam String businessType,
            @RequestParam Long businessId) {
        return ApiResponse.success(fileService.listByBusiness(businessType, businessId));
    }
}
