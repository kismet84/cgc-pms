export type ReportCatalogSourceType = "page" | "api";
export type ReportCatalogStatus = "available" | "api_only";

export interface ReportCatalogItem {
  code: string;
  name: string;
  catalog: string;
  sourceType: ReportCatalogSourceType;
  target: string;
  permissionCode: string;
  filterSummary: string;
  exportSupport: boolean;
  status: ReportCatalogStatus;
}

export const REPORT_API = { catalog: "/reports/catalog" } as const;

export function canOpenReportTarget(
  item: ReportCatalogItem,
  knownPageTargets: readonly string[],
): boolean {
  return (
    item.sourceType === "page" &&
    item.status === "available" &&
    item.target.startsWith("/") &&
    !item.target.startsWith("//") &&
    knownPageTargets.includes(item.target)
  );
}
