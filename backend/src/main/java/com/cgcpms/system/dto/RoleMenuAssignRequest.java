package com.cgcpms.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RoleMenuAssignRequest {
    @NotNull(message = "菜单ID列表不能为空")
    private List<Long> menuIds;
}
