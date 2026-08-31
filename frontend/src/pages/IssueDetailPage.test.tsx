import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { IssueDetailPage } from './IssueDetailPage';
import type { PageResponse } from '../datasets/types';
import type { IssueDetail, IssueFeedback } from '../issues/types';
import { ApiError } from '../lib/api-client';

const detailMocks = vi.hoisted(() => ({
  issueDetailRequest: vi.fn(),
  issueFeedbacksRequest: vi.fn(),
}));

vi.mock('../issues/api', () => detailMocks);

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
    detailMocks.issueDetailRequest.mockResolvedValue(detail);
    detailMocks.issueFeedbacksRequest.mockResolvedValue(feedbackPage);
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
