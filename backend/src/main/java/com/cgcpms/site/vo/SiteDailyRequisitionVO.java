package com.cgcpms.site.vo;

import lombok.Data;

@Data
public class SiteDailyRequisitionVO {
    private String requisitionId;
    private String requisitionCode;
    private String requisitionItemId;
    private String materialId;
    private String materialName;
    private String materialUnit;
    private String quantity;
    private String useLocation;
}
