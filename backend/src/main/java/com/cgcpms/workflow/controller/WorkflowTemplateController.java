package com.cgcpms.workflow.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.workflow.dto.WorkflowTemplateNodeReorderRequest;
import com.cgcpms.workflow.dto.WorkflowTemplateNodeRequest;
import com.cgcpms.workflow.dto.WorkflowTemplateUpdateRequest;
import com.cgcpms.workflow.service.WorkflowTemplateService;
import com.cgcpms.workflow.vo.WfTemplateNodeVO;
import com.cgcpms.workflow.vo.WfTemplateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workflow/templates")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class WorkflowTemplateController {

    private final WorkflowTemplateService workflowTemplateService;

    @GetMapping
    public ApiResponse<PageResult<WfTemplateVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(workflowTemplateService.listTemplates(pageNo, pageSize, businessType, enabled, keyword));
    }

    @GetMapping("/{templateId}")
    public ApiResponse<WfTemplateVO> detail(@PathVariable Long templateId) {
        return ApiResponse.success(workflowTemplateService.getTemplateDetail(templateId));
    }

    @PutMapping("/{templateId}")
    public ApiResponse<Void> updateTemplate(@PathVariable Long templateId,
                                            @Valid @RequestBody WorkflowTemplateUpdateRequest request) {
        workflowTemplateService.updateTemplate(templateId, request);
        return ApiResponse.success();
    }

    @PostMapping("/{templateId}/nodes")
    public ApiResponse<WfTemplateNodeVO> createNode(@PathVariable Long templateId,
                                                    @Valid @RequestBody WorkflowTemplateNodeRequest request) {
        return ApiResponse.success(workflowTemplateService.createNode(templateId, request));
    }

    @PutMapping("/{templateId}/nodes/{nodeId}")
    public ApiResponse<Void> updateNode(@PathVariable Long templateId,
                                        @PathVariable Long nodeId,
                                        @Valid @RequestBody WorkflowTemplateNodeRequest request) {
        workflowTemplateService.updateNode(templateId, nodeId, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{templateId}/nodes/{nodeId}")
    public ApiResponse<Void> deleteNode(@PathVariable Long templateId,
                                        @PathVariable Long nodeId) {
        workflowTemplateService.deleteNode(templateId, nodeId);
        return ApiResponse.success();
    }

    @PutMapping("/{templateId}/nodes/reorder")
    public ApiResponse<Void> reorderNodes(@PathVariable Long templateId,
                                          @Valid @RequestBody WorkflowTemplateNodeReorderRequest request) {
        workflowTemplateService.reorderNodes(templateId, request);
        return ApiResponse.success();
    }
}
