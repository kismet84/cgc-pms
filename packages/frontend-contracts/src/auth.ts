export interface LoginParams {
  username: string;
  password: string;
}

export interface UserInfo {
  userId: string;
  username: string;
  realName?: string;
  avatar?: string;
  phone?: string;
  email?: string;
  roles: string[];
  permissions: string[];
  roleName?: string;
}

export interface LoginResult {
  userInfo: UserInfo;
}

export type SessionStatus =
  | "idle"
  | "restoring"
  | "authenticating"
  | "authenticated"
  | "signing-out"
  | "anonymous";

export function isUserInfo(value: unknown): value is UserInfo {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<UserInfo>;
  return (
    typeof candidate.userId === "string" &&
    typeof candidate.username === "string" &&
    Array.isArray(candidate.roles) &&
    candidate.roles.every((role) => typeof role === "string") &&
    Array.isArray(candidate.permissions) &&
    candidate.permissions.every((permission) => typeof permission === "string")
  );
}

export function isLoginResult(value: unknown): value is LoginResult {
  return Boolean(
    value &&
    typeof value === "object" &&
    isUserInfo((value as LoginResult).userInfo),
  );
}
