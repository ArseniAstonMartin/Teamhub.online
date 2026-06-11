import { describe, it, expect } from 'vitest';
import { formatCustomerSuggestion } from './CustomerPicker';
import type { CustomerResponse } from '@/entities/customer/model/types';

function makeCustomer(overrides: Partial<CustomerResponse> = {}): CustomerResponse {
  return {
    id: 'cust-1',
    tenantId: 'tenant-1',
    name: 'John Darmian',
    companyName: 'Google Co.',
    phone: '+18082548899',
    email: 'johndarmian@gmail.com',
    addressLine: '1 Main St',
    city: 'Honolulu',
    state: 'HI',
    zip: '96813',
    lat: null,
    lon: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    archivedAt: null,
    ...overrides,
  };
}

describe('formatCustomerSuggestion', () => {
  it('formats full customer line per PRD §8.1', () => {
    expect(formatCustomerSuggestion(makeCustomer())).toBe(
      'John Darmian - johndarmian@gmail.com - Google Co. - +18082548899',
    );
  });

  it('omits optional email and company when absent', () => {
    expect(
      formatCustomerSuggestion(
        makeCustomer({ email: null, companyName: null }),
      ),
    ).toBe('John Darmian - +18082548899');
  });
});
