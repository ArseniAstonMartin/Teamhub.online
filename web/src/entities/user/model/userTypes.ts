export type UserRole = 'ADMIN' | 'DISPATCHER' | 'TECHNICIAN';

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
