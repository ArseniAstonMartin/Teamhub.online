export type StaffRole = 'TEAMHUB_STAFF';

export interface StaffUser {
  id: string;
  role: StaffRole;
}

export interface StaffLoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface SchemaPresetItem {
  presetKey: string;
  schemaName: string;
}

export interface ProvisionTenantRequest {
  name: string;
  ownerEmail: string;
  presetKey?: string;
}

export interface ProvisionTenantResponse {
  tenantId: string;
  inviteId: string;
  ownerEmail: string;
}

export type TenantPlan = 'FREE' | 'STARTER' | 'TEAM' | 'ENTERPRISE';

export interface InternalTenantSummary {
  id: string;
  name: string;
  plan: TenantPlan;
  createdAt: string;
  userCount: number;
}

export interface InternalTenantDetail {
  id: string;
  name: string;
  plan: TenantPlan;
  createdAt: string;
  userCount: number;
  jobCount: number;
  currentPeriodEnd: string | null;
  activeSchemaName: string | null;
}

/** Spring Page wrapper for internal tenant list. */
export interface InternalTenantsPageResponse {
  content: InternalTenantSummary[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
