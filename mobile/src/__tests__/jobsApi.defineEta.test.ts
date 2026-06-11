import { defineEta } from '../api/jobsApi';

const mockPost = jest.fn();

jest.mock('../api/client', () => ({
  apiClient: {
    post: (...args: unknown[]) => mockPost(...args),
  },
}));

describe('defineEta', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('POSTs to /api/v1/jobs/{id}/eta with etaMinutes', async () => {
    const updated = { id: 'job-1', status: 'IN_PROGRESS' };
    mockPost.mockResolvedValueOnce({ data: updated });

    const result = await defineEta('job-1', 45);

    expect(mockPost).toHaveBeenCalledWith('/api/v1/jobs/job-1/eta', { etaMinutes: 45 });
    expect(result).toEqual(updated);
  });
});
