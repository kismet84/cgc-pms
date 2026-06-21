package com.cgcpms.system.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 角色分配请求 DTO。
 * 解耦控制器与持久化实体，防止通过请求体注入不可变字段。
 */
@Data
public class AssignRolesRequest {

    @NotNull
    private Long userId;

    @NotEmpty
    private List<Long> roleIds;
}
