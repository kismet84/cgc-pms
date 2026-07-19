export interface ApiResponse<T = unknown> {
  code: string;
  message: string;
  traceId?: string | null;
  data: T;
}

export interface PageResult<T = unknown> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export const API_SUCCESS_CODE = "0" as const;

export const CSRF_CONTRACT = {
  cookieName: "XSRF-TOKEN",
  headerName: "X-XSRF-TOKEN",
  safeMethods: ["GET", "HEAD", "OPTIONS"] as const,
} as const;

export const AUTH_API = {
  login: "/auth/login",
  userInfo: "/auth/userinfo",
  logout: "/auth/logout",
  refresh: "/auth/refresh",
} as const;
