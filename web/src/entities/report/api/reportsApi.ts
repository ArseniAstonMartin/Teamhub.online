import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import apiClient from '@/shared/api/client';
import type { AvgTimesResponse, JobCountsResponse, ReportPeriod, TechnicianRateEntry } from '../model/types';

export const REPORTS_QUERY_KEY = ['reports'] as const;

export interface ReportDateRange {
  from: string;  // ISO date string "YYYY-MM-DD"
  to: string;
  period: ReportPeriod;
}

export function useJobCounts(range: ReportDateRange) {
  return useQuery({
    queryKey: [...REPORTS_QUERY_KEY, 'jobs', range],
    queryFn: async () => {
      const { data } = await apiClient.get<JobCountsResponse>('/api/v1/reports/jobs', {
        params: { period: range.period, from: range.from, to: range.to },
      });
      return data;
    },
  });
}

export function useAvgTimes(range: Pick<ReportDateRange, 'from' | 'to'>) {
  return useQuery({
    queryKey: [...REPORTS_QUERY_KEY, 'avg-times', range],
    queryFn: async () => {
      const { data } = await apiClient.get<AvgTimesResponse>('/api/v1/reports/avg-times', {
        params: { from: range.from, to: range.to },
      });
      return data;
    },
  });
}

/** Returns null on 402 (plan gate) instead of throwing. */
export function usePerTechnician(range: Pick<ReportDateRange, 'from' | 'to'>) {
  return useQuery({
    queryKey: [...REPORTS_QUERY_KEY, 'per-technician', range],
    queryFn: async () => {
      try {
        const { data } = await apiClient.get<TechnicianRateEntry[]>(
          '/api/v1/reports/per-technician',
          { params: { from: range.from, to: range.to } },
        );
        return data;
      } catch (err) {
        if (axios.isAxiosError(err) && err.response?.status === 402) {
          return null;
        }
        throw err;
      }
    },
  });
}
