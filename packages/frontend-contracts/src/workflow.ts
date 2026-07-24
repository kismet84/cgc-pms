import type { PageResult } from "./api";

export type WorkflowTab = "todo" | "done" | "cc" | "mine";
export type WorkflowUiAction =
  "approve" | "reject" | "withdraw" | "resubmit" | "transfer" | "addSign";

export interface WorkflowTask {
  id: string;
  instanceId: string;
  nodeInstanceId: string;
  businessType: string;
  businessId: string;
  businessCode?: string;
  approverId: string;
  approverName: string;
  taskStatus: string;
  roundNo: number;
  taskVersion: number;
  receivedAt: string;
  handledAt?: string;
  actionType?: string;
  comment?: string;
  title: string;
  instanceStatus: string;
}

export interface WorkflowRecord {
  id: string;
  instanceId: string;
  nodeInstanceId?: string;
  taskId?: string;
  roundNo: number;
  nodeCode?: string;
  nodeName?: string;
  actionType: string;
  actionName: string;
  operatorId: string;
  operatorName: string;
  comment?: string;
  recordStatus: string;
  createdAt: string;
  businessType?: string;
  businessId?: string;
  businessCode?: string;
  title?: string;
  instanceStatus?: string;
}

export interface WorkflowCc {
  id: string;
  instanceId: string;
  ccUserId: string;
  ccUserName: string;
  businessType: string;
  businessId?: string;
  businessCode?: string;
  title: string;
  isRead: number;
  createdTime: string;
  instanceStatus?: string;
}

export interface WorkflowMine {
  instanceId: string;
  businessType: string;
  businessId?: string;
  businessCode?: string;
  title: string;
  instanceStatus: string;
  createdAt: string;
  updatedAt?: string;
  currentNodeName?: string;
}

export interface WorkflowNode {
  id: string;
  templateNodeId: string;
  nodeCode: string;
  nodeName: string;
  nodeOrder: number;
  approveMode: string;
  nodeStatus: string;
  roundNo: number;
  startedAt?: string;
  endedAt?: string;
  tasks: WorkflowTask[];
}

export interface WorkflowInstance {
  id: string;
  templateId: string;
  templateName: string;
  businessType: string;
  businessId: string;
  businessCode?: string;
  projectId?: string;
  contractId?: string;
  title: string;
  amount?: string;
  instanceStatus: string;
  currentRound: number;
  resubmitCount: number;
  initiatorId: string;
  initiatorName: string;
  businessSummary?: string;
  startedAt: string;
  endedAt?: string;
  availableActions: WorkflowUiAction[];
  nodes: WorkflowNode[];
  records: WorkflowRecord[];
}

export interface WorkflowQuery {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  businessType?: string;
  instanceStatus?: string;
  startTime?: string;
  endTime?: string;
}

export interface WorkflowActionBody {
  action: "APPROVE" | "REJECT";
  comment?: string;
  idempotencyKey: string;
}

export type WorkflowListResult =
  | PageResult<WorkflowTask>
  | PageResult<WorkflowRecord>
  | PageResult<WorkflowCc>
  | PageResult<WorkflowMine>;

export const WORKFLOW_API = {
  businessTypes: (tab: WorkflowTab) => `/workflow/business-types?tab=${tab}`,
  list: {
    todo: "/workflow/tasks/todo",
    done: "/workflow/tasks/done",
    cc: "/workflow/tasks/cc",
    mine: "/workflow/instances/mine",
  },
  detail: (instanceId: string) => `/workflow/instances/${instanceId}`,
  approve: (taskId: string) => `/workflow/tasks/${taskId}/approve`,
  reject: (taskId: string) => `/workflow/tasks/${taskId}/reject`,
  withdraw: (instanceId: string) =>
    `/workflow/instances/${instanceId}/withdraw`,
  resubmit: (instanceId: string) =>
    `/workflow/instances/${instanceId}/resubmit`,
  transfer: (taskId: string) => `/workflow/tasks/${taskId}/transfer`,
  addSign: (taskId: string) => `/workflow/tasks/${taskId}/add-sign`,
} as const;

export const WORKFLOW_ACTION_PERMISSIONS: Record<WorkflowUiAction, string> = {
  approve: "workflow:approve",
  reject: "workflow:reject",
  withdraw: "workflow:withdraw",
  resubmit: "workflow:resubmit",
  transfer: "workflow:transfer",
  addSign: "workflow:add-sign",
};

export function canPerformWorkflowAction(
  action: WorkflowUiAction,
  availableActions: readonly string[],
  permissions: readonly string[],
): boolean {
  if (!availableActions.includes(action)) return false;
  return (
    permissions.includes("*") ||
    permissions.includes(WORKFLOW_ACTION_PERMISSIONS[action])
  );
}
