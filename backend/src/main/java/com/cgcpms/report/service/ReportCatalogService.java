package com.cgcpms.report.service;

import com.cgcpms.report.dto.ReportCatalogItem;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReportCatalogService {

    private static final List<ReportCatalogItem> CATALOG = List.of(
            item("dashboard-management", "管理驾驶舱", "dashboard", "page", "/dashboard",
                    "dashboard:view", "按当前用户驾驶舱口径展示，可带项目或月份筛选", false, "available"),
            item("cost-ledger", "成本台账", "cost", "page", "/cost/ledger",
                    "cost:ledger:query", "支持项目、合同、成本类型、状态、期间等台账筛选", false, "available"),
            item("cost-summary", "项目成本明细核对", "cost", "page", "/cost/summary",
                    "cost:summary:view", "按项目汇总目标成本、动态成本、实际成本和差异", false, "available"),
            item("alert-center", "预警中心", "alert", "page", "/alert",
                    "alert:view", "支持项目、规则类型、严重程度、处理状态筛选", true, "available"),
            item("workflow-todo", "审批待办", "workflow", "page", "/approval/todo",
                    "workflow:task:query", "按当前用户待办任务、业务类型、状态和时间筛选", false, "available"),
            item("workflow-cc", "抄送我的", "workflow", "page", "/approval/cc",
                    "workflow:cc:query", "按当前用户抄送记录、业务类型、状态和时间筛选", false, "available"),
            item("alerts-processing-report", "预警处理统计", "alert", "api", "/alerts/processing-report",
                    "alert:view", "支持项目、规则类型、严重程度、处理状态统计", false, "api_only"),
            item("workflow-efficiency", "审批效率统计", "workflow", "api", "/workflow/statistics/efficiency",
                    "", "按当前登录用户本人审批记录，支持关键字、业务类型、实例状态和时间范围统计", false, "api_only"),
            item("contracts-kpi", "合同 KPI", "contract", "api", "/contracts/kpi",
                    "contract:query", "按合同模块统计金额、状态和关键指标", false, "api_only"),
            item("contract-revenue", "合同收入", "contract", "api", "/contract-revenue",
                    "revenue:query", "按合同收入记录、合同和状态查询", false, "api_only")
    );

    public List<ReportCatalogItem> listVisibleCatalog() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return List.of();
        }
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());
        if (authorities.contains("ROLE_ADMIN") || authorities.contains("ROLE_SUPER_ADMIN")) {
            return CATALOG;
        }
        return CATALOG.stream()
                .filter(item -> !StringUtils.hasText(item.permissionCode())
                        || authorities.contains(item.permissionCode()))
                .toList();
    }

    private static ReportCatalogItem item(String code, String name, String catalog, String sourceType,
                                          String target, String permissionCode, String filterSummary,
                                          boolean exportSupport, String status) {
        return new ReportCatalogItem(code, name, catalog, sourceType, target, permissionCode,
                filterSummary, exportSupport, status);
    }
}
