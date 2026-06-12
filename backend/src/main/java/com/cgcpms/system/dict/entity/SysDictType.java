package com.cgcpms.system.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典类型实体 — 映射 sys_dict_type 表 (V5 migration)
 */
@Data
@TableName("sys_dict_type")
public class SysDictType implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    @NotBlank
    private String dictCode;

    @NotBlank
    private String dictName;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
