import { beforeEach, describe, expect, it, vi } from 'vitest';

import { dashboardOverviewRequest, issueTrendRequest } from './api';

const apiRequestMock = vi.hoisted(() => vi.fn());

vi.mock('../lib/api-client', () => ({ apiRequest: apiRequestMock }));

describe('dashboard API', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
  });

  it('uses the server default trend period when no range is supplied', async () => {
    apiRequestMock.mockResolvedValue({ issueId: 7, points: [] });
    await issueTrendRequest(7);
    expect(apiRequestMock).toHaveBeenCalledWith('/api/v1/dashboard/issue-trends?issueId=7');
  });

  it('requests an explicit inclusive trend period', async () => {
    apiRequestMock.mockResolvedValue({ issueId: 7, points: [] });
    await issueTrendRequest(7, { from: '2026-08-01', to: '2026-09-03' });
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/api/v1/dashboard/issue-trends?issueId=7&from=2026-08-01&to=2026-09-03',
    );
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
