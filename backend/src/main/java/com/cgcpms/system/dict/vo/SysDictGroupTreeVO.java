package com.cgcpms.system.dict.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SysDictGroupTreeVO {
    private String id;
    private String groupCode;
    private String groupName;
    private Integer orderNum;
    private String status;
    private List<TypeNode> types = new ArrayList<>();

    @Data
    public static class TypeNode {
        private String id;
        private String groupId;
        private String groupCode;
        private String groupName;
        private String dictCode;
        private String dictName;
        private String dictClass;
        private String status;
        private List<SysDictDataVO> data = new ArrayList<>();
    }
}
