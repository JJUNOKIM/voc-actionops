import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { OverviewPage } from './OverviewPage';
import type { DashboardOverview } from '../dashboard/types';

const dashboardMocks = vi.hoisted(() => ({ dashboardOverviewRequest: vi.fn() }));

vi.mock('../dashboard/api', () => dashboardMocks);
vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({
    user: {
      id: 1,
      organizationId: 11,
      organizationName: 'VOC ActionOps Demo',
      email: 'admin@voc-actionops.local',
      name: 'Demo Admin',
      role: 'ADMIN',
    },
  }),
}));

const dashboard: DashboardOverview = {
  summary: {
    totalFeedbackCount: 128,
    negativeFeedbackRate: 42.5,
    newIssueCount: 7,
    p0IssueCount: 1,
    p1IssueCount: 3,
    unresolvedIssueCount: 9,
    averageResolutionHours: 36.5,
  },
  categories: [
    { category: 'PAYMENT', issueCount: 4, feedbackCount: 80, negativeFeedbackRate: 72.5 },
  ],
  topIssues: [
    {
      issueId: 7,
      title: '쿠폰 적용 후 결제 실패',
      category: 'PAYMENT',
      priority: 'P1',
      priorityScore: 71.5,
      status: 'IN_PROGRESS',
      feedbackCount: 42,
      feedbackGrowthRate: 12.2,
      negativeFeedbackRate: 81,
      unresolvedActionCount: 2,
      assigneeId: 3,
      assigneeName: '김개발',
      lastSeenAt: '2026-08-20T13:30:00',
    },
  ],
};

describe('OverviewPage', () => {
  beforeEach(() => {
    dashboardMocks.dashboardOverviewRequest.mockReset();
    dashboardMocks.dashboardOverviewRequest.mockResolvedValue(dashboard);
  });

  it('renders operational metrics, priority issues, and category breakdown', async () => {
    render(<OverviewPage />);

    expect(await screen.findByText('128')).toBeInTheDocument();
    expect(screen.getByText('42.5%')).toBeInTheDocument();
    expect(screen.getByText('36.5시간')).toBeInTheDocument();
    expect(screen.getByText('쿠폰 적용 후 결제 실패')).toBeInTheDocument();
    expect(screen.getByText('처리 중')).toBeInTheDocument();
    expect(screen.getAllByText('PAYMENT')).toHaveLength(2);
    expect(screen.getByText('부정 72.5%')).toBeInTheDocument();
  });

  it('refreshes the dashboard and recovers from a request error', async () => {
    const user = userEvent.setup();
    dashboardMocks.dashboardOverviewRequest.mockRejectedValueOnce(new Error('network'));

    render(<OverviewPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '운영 현황을 불러오지 못했습니다.',
    );
    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    await waitFor(() => expect(dashboardMocks.dashboardOverviewRequest).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('쿠폰 적용 후 결제 실패')).toBeInTheDocument();
  });
});
