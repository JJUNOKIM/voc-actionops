import { beforeEach, describe, expect, it, vi } from 'vitest';

import { dashboardOverviewRequest } from './api';

const apiRequestMock = vi.hoisted(() => vi.fn());

vi.mock('../lib/api-client', () => ({ apiRequest: apiRequestMock }));

describe('dashboard API', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
  });

  it('requests the summary, category breakdown, and priority issues together', async () => {
    apiRequestMock
      .mockResolvedValueOnce({ totalFeedbackCount: 25 })
      .mockResolvedValueOnce([{ category: 'PAYMENT' }])
      .mockResolvedValueOnce([{ issueId: 7 }]);

    await expect(dashboardOverviewRequest()).resolves.toEqual({
      summary: { totalFeedbackCount: 25 },
      categories: [{ category: 'PAYMENT' }],
      topIssues: [{ issueId: 7 }],
    });
    expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/v1/dashboard/summary');
    expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/v1/dashboard/category-breakdown');
    expect(apiRequestMock).toHaveBeenNthCalledWith(
      3,
      '/api/v1/dashboard/top-issues?limit=6&sortBy=priority_score',
    );
  });
});
