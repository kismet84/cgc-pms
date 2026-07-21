export interface ProjectListItem {
  id: string;
  projectCode: string;
  projectName: string;
  status: string;
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
  roleCode: string;
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
  contractAmount?: string;
  targetCost?: string;
  plannedStartDate?: string;
  plannedEndDate?: string;
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
  detail: (projectId: string) => `/projects/${encodeURIComponent(projectId)}`,
  overview: (projectId: string) =>
    `/projects/${encodeURIComponent(projectId)}/overview`,
  members: (projectId: string) =>
    `/projects/${encodeURIComponent(projectId)}/members`,
  member: (projectId: string, memberId: string) =>
    `/projects/${encodeURIComponent(projectId)}/members/${encodeURIComponent(memberId)}`,
  archive: (projectId: string) =>
    `/projects/${encodeURIComponent(projectId)}/archive`,
  submit: (projectId: string) =>
    `/projects/${encodeURIComponent(projectId)}/submit`,
  status: (projectId: string) =>
    `/projects/${encodeURIComponent(projectId)}/status`,
  dictionary: (code: string) =>
    `/system/dict/data/by-code/${encodeURIComponent(code)}`,
  users: "/system/users",
} as const;
