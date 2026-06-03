export interface TenantSettingsResponse {
  closeJobsEnabled: boolean;
}

export interface UpdateTenantSettingsRequest {
  // Nullable PATCH semantics — only non-null fields are applied server-side
  closeJobsEnabled?: boolean;
}
