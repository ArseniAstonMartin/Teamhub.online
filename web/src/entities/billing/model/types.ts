export type PlanKey = 'FREE' | 'STARTER' | 'TEAM' | 'ENTERPRISE';

export type PriceKey = 'free' | 'starter' | 'team' | 'enterprise';

// TO-DO: replace with real GET /api/v1/billing/status response when endpoint is added
export interface BillingStatus {
  plan: PlanKey;
  jobsThisPeriod: number;
  periodEnd: string; // ISO date string
  paymentFailed: boolean;
  paymentFailedAt: string | null;
  seats: number; // current active users
}

export interface PlanLimits {
  jobsPerMonth: number | null; // null = unlimited
  seats: number | null; // null = unlimited
  schemaEditor: boolean;
  advancedReports: boolean;
}

export const PLAN_LIMITS: Record<PlanKey, PlanLimits> = {
  FREE:       { jobsPerMonth: 25,   seats: 1,  schemaEditor: false, advancedReports: false },
  STARTER:    { jobsPerMonth: 200,  seats: 5,  schemaEditor: true,  advancedReports: false },
  TEAM:       { jobsPerMonth: 1000, seats: 20, schemaEditor: true,  advancedReports: true  },
  ENTERPRISE: { jobsPerMonth: null, seats: null, schemaEditor: true, advancedReports: true },
};

export const PLAN_DISPLAY_NAMES: Record<PlanKey, string> = {
  FREE:       'Free',
  STARTER:    'Starter',
  TEAM:       'Team',
  ENTERPRISE: 'Enterprise',
};

export const PLAN_PRICES: Record<PlanKey, string> = {
  FREE:       '$0/mo',
  STARTER:    '$49/mo',
  TEAM:       '$149/mo',
  ENTERPRISE: 'Custom',
};

export interface CheckoutResponse {
  url: string;
}

export interface PortalResponse {
  url: string;
}
