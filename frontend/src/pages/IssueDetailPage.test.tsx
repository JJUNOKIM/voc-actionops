import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { IssueDetailPage } from './IssueDetailPage';
import type { PageResponse } from '../datasets/types';
import type { IssueDetail, IssueFeedback } from '../issues/types';
import { ApiError } from '../lib/api-client';
import type { UserProfile } from '../types/api';

const detailMocks = vi.hoisted(() => ({
  issueDetailRequest: vi.fn(),
  issueFeedbacksRequest: vi.fn(),
  changeActionStatusRequest: vi.fn(),
  organizationUsersRequest: vi.fn(),
  useAuth: vi.fn(),
  issueTrendRequest: vi.fn(),
}));

vi.mock('../issues/api', () => detailMocks);
vi.mock('../users/api', () => ({ organizationUsersRequest: detailMocks.organizationUsersRequest }));
vi.mock('../auth/useAuth', () => ({ useAuth: detailMocks.useAuth }));
vi.mock('../dashboard/api', () => ({ issueTrendRequest: detailMocks.issueTrendRequest }));

const admin: UserProfile = {
  id: 1,
  organizationId: 11,
  organizationName: 'VOC ActionOps Demo',
  email: 'admin@voc-actionops.local',
  name: 'Demo Admin',
  role: 'ADMIN',
};

const detail: IssueDetail = {
  id: 7,
  title: '쿠폰 적용 후 결제 실패',
  description: '쿠폰이 적용된 주문에서 결제 승인 단계가 완료되지 않는다.',
  category: 'PAYMENT',
  priority: 'P1',
  priorityScore: 71.5,
  status: 'IN_PROGRESS',
  assigneeId: 3,
  assigneeName: '김개발',
  feedbackCount: 42,
  negativeCount: 34,
  firstSeenAt: '2026-08-18T13:30:00',
  lastSeenAt: '2026-08-20T13:30:00',
  resolvedAt: null,
  createdAt: '2026-08-18T14:00:00',
  updatedAt: '2026-08-21T09:10:00',
  actions: [
    {
      id: 11,
      issueId: 7,
      title: '결제 승인 로그 확인',
      description: '쿠폰 적용 주문의 승인 응답을 비교한다.',
      status: 'IN_PROGRESS',
      assigneeId: 3,
      assigneeName: '김개발',
      dueDate: '2026-08-30',
      createdAt: '2026-08-21T10:00:00',
      updatedAt: '2026-08-22T10:00:00',
      completedAt: null,
    },
  ],
};

const feedbackPage: PageResponse<IssueFeedback> = {
  content: [
    {
      id: 51,
      feedbackId: 31,
      datasetId: 17,
      externalId: 'review-031',
      sourceType: 'APP_REVIEW',
      content: '쿠폰을 적용하면 결제가 완료되지 않습니다.',
      rating: 1,
      similarityScore: 0.92,
      representative: true,
      linkedBy: 'AI',
      feedbackCreatedAt: '2026-08-20T13:30:00',
      linkedAt: '2026-08-21T09:10:00',
    },
  ],
  page: 0,
  size: 10,
  totalElements: 11,
  totalPages: 2,
};

describe('IssueDetailPage', () => {
  beforeEach(() => {
    detailMocks.issueDetailRequest.mockReset();
    detailMocks.issueFeedbacksRequest.mockReset();
    detailMocks.changeActionStatusRequest.mockReset();
    detailMocks.organizationUsersRequest.mockReset();
    detailMocks.useAuth.mockReset();
    detailMocks.issueDetailRequest.mockResolvedValue(detail);
    detailMocks.issueFeedbacksRequest.mockResolvedValue(feedbackPage);
    detailMocks.organizationUsersRequest.mockResolvedValue([
      { id: 1, email: admin.email, name: admin.name, role: admin.role },
      { id: 3, email: 'developer@example.com', name: '김개발', role: 'DEVELOPER' },
    ]);
    detailMocks.useAuth.mockReturnValue({ user: admin });
    detailMocks.issueTrendRequest.mockReset().mockResolvedValue({
      issueId: 7, from: '2026-08-01', to: '2026-08-30', resolvedAt: null, resolvedDate: null,
      feedbackGrowthRate: null, points: [],
    });
  });

  it('renders issue context, actions, and related feedback links', async () => {
    renderIssueDetailPage('/issues/7');

    expect(await screen.findByRole('heading', { name: '쿠폰 적용 후 결제 실패' })).toBeInTheDocument();
    expect(screen.getByText('쿠폰이 적용된 주문에서 결제 승인 단계가 완료되지 않는다.')).toBeInTheDocument();
    expect(screen.getByText('결제 승인 로그 확인')).toBeInTheDocument();
    expect(screen.getByText('진행 중')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'review-031' })).toHaveAttribute(
      'href',
      '/feedbacks/31',
    );
    expect(screen.getByRole('link', { name: '#17' })).toHaveAttribute('href', '/datasets/17');
    expect(screen.getByText('대표 피드백')).toBeInTheDocument();
    expect(screen.getByText('92.0%')).toBeInTheDocument();
    expect(detailMocks.issueDetailRequest).toHaveBeenCalledWith(7);
    expect(detailMocks.issueFeedbacksRequest).toHaveBeenCalledWith(7, 0, 10, false);
    expect(await screen.findByRole('heading', { name: '이슈 관리' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '해결로 변경' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '조치 등록' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '피드백 추이' })).toBeInTheDocument();
  });

  it('hides mutation controls from a viewer', async () => {
    detailMocks.useAuth.mockReturnValue({ user: { ...admin, role: 'VIEWER' } });

    renderIssueDetailPage('/issues/7');

    expect(await screen.findByRole('heading', { name: '쿠폰 적용 후 결제 실패' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '이슈 관리' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '조치 등록' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '완료' })).not.toBeInTheDocument();
    expect(detailMocks.organizationUsersRequest).not.toHaveBeenCalled();
    expect(detailMocks.issueTrendRequest).toHaveBeenCalledWith(7, undefined);
  });

  it('does not request restricted dashboard metrics for a developer', async () => {
    detailMocks.useAuth.mockReturnValue({ user: { ...admin, id: 3, role: 'DEVELOPER' } });
    renderIssueDetailPage('/issues/7');
    await screen.findByRole('heading', { name: '쿠폰 적용 후 결제 실패' });
    expect(screen.queryByRole('heading', { name: '피드백 추이' })).not.toBeInTheDocument();
    expect(detailMocks.issueTrendRequest).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: '해결로 변경' })).toBeInTheDocument();
  });

  it('moves through related feedback pages', async () => {
    const user = userEvent.setup();
    renderIssueDetailPage('/issues/7');
    await screen.findByRole('link', { name: 'review-031' });

    await user.click(screen.getByRole('button', { name: '다음 피드백 페이지' }));

    await waitFor(() =>
      expect(detailMocks.issueFeedbacksRequest).toHaveBeenLastCalledWith(7, 1, 10, false),
    );
  });

  it('updates an action with the server response', async () => {
    const user = userEvent.setup();
    const completedAction = {
      ...detail.actions[0],
      status: 'DONE' as const,
      completedAt: '2026-08-31T11:00:00',
    };
    detailMocks.changeActionStatusRequest.mockResolvedValue(completedAction);

    renderIssueDetailPage('/issues/7');
    await screen.findByText('결제 승인 로그 확인');

    await user.click(screen.getByRole('button', { name: '완료' }));

    await waitFor(() =>
      expect(detailMocks.changeActionStatusRequest).toHaveBeenCalledWith(11, 'DONE'),
    );
    expect(
      await screen.findByText('조치 상태를 변경했습니다. 현재 상태: 완료'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '완료' })).not.toBeInTheDocument();
  });

  it('retries a failed detail request and rejects an invalid route id', async () => {
    const user = userEvent.setup();
    detailMocks.issueDetailRequest
      .mockRejectedValueOnce(new ApiError(404, 'NOT_FOUND', '요청한 데이터를 찾을 수 없습니다.'))
      .mockResolvedValueOnce(detail);

    const { unmount } = renderIssueDetailPage('/issues/7');
    expect(await screen.findByText('이슈를 불러오지 못했습니다.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(await screen.findByRole('heading', { name: '쿠폰 적용 후 결제 실패' })).toBeInTheDocument();

    unmount();
    detailMocks.issueDetailRequest.mockClear();
    detailMocks.issueFeedbacksRequest.mockClear();
    renderIssueDetailPage('/issues/invalid');
    expect(screen.getByText('잘못된 이슈 주소입니다.')).toBeInTheDocument();
    expect(detailMocks.issueDetailRequest).not.toHaveBeenCalled();
    expect(detailMocks.issueFeedbacksRequest).not.toHaveBeenCalled();
  });
});

function renderIssueDetailPage(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/issues/:issueId" element={<IssueDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}
