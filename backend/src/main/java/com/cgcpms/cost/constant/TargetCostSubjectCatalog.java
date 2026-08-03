package com.cgcpms.cost.constant;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public final class TargetCostSubjectCatalog {

    public static final String PARENT_CODE = "5401.03";
    public static final List<Definition> ITEMS = List.of(
            new Definition("5401.03.01", "人工成本", "LABOR", new BigDecimal("25.0000")),
            new Definition("5401.03.02", "材料及工程设备成本", "MATERIAL", new BigDecimal("40.0000")),
            new Definition("5401.03.03", "施工机械成本", "MACHINERY", new BigDecimal("5.0000")),
            new Definition("5401.03.04", "分包成本", "SUBCONTRACT", new BigDecimal("5.0000")),
            new Definition("5401.03.05", "施工措施成本", "MEASURES", new BigDecimal("5.0000")),
            new Definition("5401.03.06", "项目现场管理成本", "SITE_MANAGEMENT", new BigDecimal("3.0000")),
            new Definition("5401.03.07", "公司管理费分摊", "OVERHEAD", new BigDecimal("5.0000")),
            new Definition("5401.03.08", "其他专项成本", "SPECIAL", new BigDecimal("1.0000")),
            new Definition("5401.03.09", "财务及税费成本", "FINANCE_TAX", new BigDecimal("8.0000")),
            new Definition("5401.03.10", "风险准备成本", "RISK_RESERVE", new BigDecimal("3.0000"))
    );
    public static final Set<String> CODES = Set.copyOf(ITEMS.stream().map(Definition::code).toList());

    private TargetCostSubjectCatalog() {
    }

    public record Definition(String code, String name, String type, BigDecimal ratio) {
    }
}
