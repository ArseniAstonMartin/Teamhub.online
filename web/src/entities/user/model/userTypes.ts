export type UserRole = 'ADMIN' | 'DISPATCHER' | 'TECHNICIAN';

/** Authenticated caller profile from GET /api/v1/users/me (TASK-132). */
export interface CurrentUserResponse {
  id: string;
  email: string;
  name: string;
  role: UserRole;
  tenantId: string;
  /** null when the user has never logged in */
  lastLoginAt: string | null;
}

export interface UserResponse {
  id: string;
  email: string;
  role: UserRole;
  /** null = active user; non-null = deactivated */
  deactivatedAt: string | null;
  createdAt: string;
  /** null when the user has never logged in */
  lastLoginAt: string | null;
}

/** Spring Page<UserResponse> wrapper. */
export interface UsersPageResponse {
  content: UserResponse[];
  totalElements: number;
  totalPages: number;
  /** Current page (0-based). */
  number: number;
  size: number;
}

export interface InviteResponse {
  id: string;
  email: string;
  role: string;
  expiresAt: string;
  createdAt: string;
}

export interface InviteRequest {
  email: string;
  role: UserRole;
}

export interface ChangeRoleRequest {
  role: UserRole;
}
