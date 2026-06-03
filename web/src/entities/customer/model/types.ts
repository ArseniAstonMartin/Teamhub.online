export interface CustomerResponse {
  id: string;
  tenantId: string;
  name: string;
  companyName: string | null;
  /** E.164; TECHNICIAN sees masked form "+1503***4444" */
  phone: string;
  email: string | null;
  addressLine: string;
  city: string;
  state: string;
  zip: string;
  lat: number | null;
  lon: number | null;
  createdAt: string;
  updatedAt: string;
  /** null = active; non-null = archived */
  archivedAt: string | null;
}

export interface CustomersPageResponse {
  content: CustomerResponse[];
  totalElements: number;
  totalPages: number;
  /** Current page (0-based). */
  number: number;
  size: number;
}

export interface CreateCustomerRequest {
  name: string;
  phone: string;
  email?: string;
  companyName?: string;
  addressLine: string;
  city: string;
  state: string;
  zip: string;
  lat: number;
  lon: number;
}

export interface UpdateCustomerRequest {
  name?: string;
  email?: string;
  companyName?: string;
  addressLine?: string;
  city?: string;
  state?: string;
  zip?: string;
  lat?: number;
  lon?: number;
  // NOTE: phone is intentionally absent — phone changes require a new POST (upsert-on-phone)
}

export interface CustomersQueryParams {
  q?: string;
  include_archived?: boolean;
  page?: number;
  size?: number;
}
