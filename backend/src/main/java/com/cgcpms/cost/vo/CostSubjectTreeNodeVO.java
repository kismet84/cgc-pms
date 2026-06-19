package com.cgcpms.cost.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CostSubjectTreeNodeVO {
    private String id;
    private String subjectCode;
    private String subjectName;
    private String subjectType;
    private String accountCategory;
    private Integer level;
    private String status;
    private Integer sortOrder;
    private String parentId;
    private List<CostSubjectTreeNodeVO> children = new ArrayList<>();
}
