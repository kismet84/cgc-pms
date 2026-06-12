package com.cgcpms.org.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OrgDepartmentTreeNodeVO {

    private String id;
    private String companyId;
    private String parentId;
    private String deptCode;
    private String deptName;
    private Integer orderNum;
    private String status;
    private List<OrgDepartmentTreeNodeVO> children = new ArrayList<>();
}
