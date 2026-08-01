package com.cgcpms.system.dict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SysDictDataRequest {
    @NotNull
    private Long dictTypeId;
    @NotBlank
    private String dictLabel;
    @NotBlank
    private String dictValue;
    private String cssClass;
    private String listClass;
    private Integer orderNum;
    private String status;
}
