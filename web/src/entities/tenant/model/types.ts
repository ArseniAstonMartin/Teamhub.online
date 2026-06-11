export interface TenantSettingsResponse {
  closeJobsEnabled: boolean;
  timezone: string;
}

export interface UpdateTenantSettingsRequest {
  closeJobsEnabled?: boolean;
  timezone?: string;
}

export interface TenantResponse {
  id: string;
  name: string;
  plan: string;
  timezone: string;
}

export interface UpdateTenantRequest {
  name: string;
}

export const US_TIMEZONES = [
  { value: 'America/New_York', label: 'Eastern Time (ET)' },
  { value: 'America/Chicago', label: 'Central Time (CT)' },
  { value: 'America/Denver', label: 'Mountain Time (MT)' },
  { value: 'America/Los_Angeles', label: 'Pacific Time (PT)' },
  { value: 'America/Phoenix', label: 'Arizona (no DST)' },
  { value: 'America/Anchorage', label: 'Alaska Time (AKT)' },
  { value: 'Pacific/Honolulu', label: 'Hawaii Time (HST)' },
] as const;

export const DEFAULT_TENANT_TIMEZONE = 'America/New_York';
