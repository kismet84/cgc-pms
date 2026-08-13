export type DecimalString = string;
export type ContractType = "MAIN" | "SUB" | "PURCHASE" | "LEASE" | "SERVICE";
export type ContractStatus = "DRAFT" | "PERFORMING" | "SETTLED" | "TERMINATED";
export type ApprovalStatus =
  "DRAFT" | "APPROVING" | "APPROVED" | "REJECTED" | "WITHDRAWN";
export type BidStatus =
  | "PREPARING"
  | "SUBMITTED"
  | "EVALUATING"
  | "WON"
  | "LOST"
  | "CLOSED"
  | "WITHDRAWN"
  | "TERMINATED";
