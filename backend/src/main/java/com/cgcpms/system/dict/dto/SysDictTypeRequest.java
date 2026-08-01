package com.cgcpms.system.dict.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SysDictTypeRequest {
    private Long groupId;
    @NotBlank
    private String dictCode;
    @NotBlank
    private String dictName;
    private String dictClass;
    private String status;
}
