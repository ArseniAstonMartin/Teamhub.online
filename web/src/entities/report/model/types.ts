export type ReportPeriod = 'day' | 'week' | 'month' | 'year';

export interface DayBucket {
  date: string;       // ISO local date "2026-01-15"
  created: number;
  completed: number;
  cancelled: number;
}

export interface JobCountsResponse {
  buckets: DayBucket[];
}

export interface AvgTimesResponse {
  avgTimeToClaimSeconds: number | null;
  avgTimeToCompletionSeconds: number | null;
}

export interface TechnicianRateEntry {
  technicianId: string;
  totalAssigned: number;
  totalCompleted: number;
  completionRate: number; // 0.0–1.0
}
