export interface ProjectListItem {
  id: string;
  projectCode: string;
  projectName: string;
  status: string;
}

export interface ProjectContextOption {
  id: string;
  projectCode: string;
  projectName: string;
  status: string;
  projectManagerId?: string | null;
}

export interface ProjectRecord extends ProjectListItem {
  tenantId: string;
  orgId: string;
  projectType: string;
  projectAddress: string;
  ownerUnit: string;
  supervisorUnit: string;
  designUnit: string;
  contractAmount: string;
  targetCost: string;
  plannedStartDate: string;
  plannedEndDate: string;
  actualStartDate?: string | null;
  actualEndDate?: string | null;
  projectManagerId: string;
  approvalStatus: string;
  ownerContractId?: string | null;
  sourceBidCostId?: string | null;
  initiationBasis?: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  remark?: string | null;
}

export interface ProjectMember {
  id: string;
  tenantId: string;
  projectId: string;
  userId: string;
  username?: string | null;
  realName?: string | null;
  roleCode: string;
  roleName?: string | null;
  positionName?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  status: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  remark?: string | null;
}

export interface ProjectMemberBrief {
  userId: string;
  userName: string;
  roleCode: string;
}

export interface ProjectMemberRoleOption {
  roleCode: string;
  roleName: string;
}

export interface ProjectMemberUserOption {
  userId: string;
  username: string;
  realName?: string | null;
  roleCodes: string[];
}

export interface ProjectMemberOptions {
  roles: ProjectMemberRoleOption[];
  users: ProjectMemberUserOption[];
  usersTruncated: boolean;
}

export interface ProjectMemberOptionQuery {
  keyword?: string;
  includeUserId?: string;
}

export interface ProjectOverview {
  projectId: string;
  contractCount: string;
  totalContractAmount: string;
  dynamicCost: string;
  paidAmount: string;
  warningCount: string;
  memberCount: string;
  members: ProjectMemberBrief[];
}

export interface ProjectQuery {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  projectCode?: string;
  projectName?: string;
  projectType?: string;
  status?: string;
}

export interface ProjectMemberQuery {
  pageNo?: number;
  pageSize?: number;
  roleCode?: string;
  status?: string;
}

export interface ProjectUpsertCommand {
  projectName: string;
  projectType: string;
  projectAddress?: string;
  ownerUnit?: string;
  supervisorUnit?: string;
  designUnit?: string;
  plannedStartDate?: string;
  plannedEndDate?: string;
  remark?: string;
}

export interface ProjectActivationReadiness {
  projectId: string;
  initiationBasis?: string | null;
  ownerContractId?: string | null;
  ownerContractCode?: string | null;
  ownerContractAmount?: string | null;
  costTargetId?: string | null;
  budgetId?: string | null;
  scheduleId?: string | null;
  commencementId?: string | null;
  commencementStatus?: string | null;
  ready: boolean;
  blockers: string[];
}

export interface ProjectCommencementRecord {
  id: string;
  tenantId: string;
  projectId: string;
  plannedStartDate: string;
  actualStartDate?: string | null;
  basisType: string;
  approvalStatus: string;
  approvalInstanceId?: string | null;
  version: number;
  remark?: string | null;
}

export interface ProjectCommencementCommand {
  version?: number;
  plannedStartDate: string;
  basisType: string;
  remark?: string;
}

export interface ProjectStatusCommand {
  targetStatus: string;
  reason: string;
}

export interface ProjectMemberCommand {
  userId: string;
  roleCode: string;
  positionName?: string;
  startDate?: string;
  endDate?: string;
  status?: string;
  remark?: string;
}

export interface DictionaryItem {
  id: string;
  dictLabel: string;
  dictValue: string;
  orderNum: number;
  status: string;
}

export interface ProjectUserOption {
  id: string;
  username: string;
  realName?: string | null;
  status: string;
}

export const PROJECT_API = {
  list: "/projects",
  contextOptions: "/project-context/options",
  detail: (projectId: string) => `/projects/${encodeURIComponent(projectId)}`,
  overview: (projectId: string) =>
    `/projects/${encodeURIComponent(projectId)}/overview`,
  members: (projectId: string) =>
    `/projects/${encodeURIComponent(projectId)}/members`,
  memberOptions: (projectId: string) =>
    `/projects/${encodeURIComponent(projectId)}/members/options`,
  member: (projectId: string, memberId: string) =>
    `/projects/${encodeURIComponent(projectId)}/members/${encodeURIComponent(memberId)}`,
  archive: (projectId: string) =>
    `/projects/${encodeURIComponent(projectId)}/archive`,
  submit: (projectId: string) =>
    `/projects/${encodeURIComponent(projectId)}/submit`,
  status: (projectId: string) =>
    `/projects/${encodeURIComponent(projectId)}/status`,
  activationReadiness: (projectId: string) =>
    `/projects/${encodeURIComponent(projectId)}/activation-readiness`,
  commencement: (projectId: string) =>
    `/projects/${encodeURIComponent(projectId)}/commencement`,
  commencementSubmit: (projectId: string) =>
    `/projects/${encodeURIComponent(projectId)}/commencement/submit`,
  dictionary: (code: string) =>
    `/system/dict/data/by-code/${encodeURIComponent(code)}`,
  users: "/system/users",
} as const;
