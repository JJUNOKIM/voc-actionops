import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { FeedbackDetailPage } from './FeedbackDetailPage';
import type { FeedbackDetail } from '../feedbacks/types';
import { ApiError } from '../lib/api-client';

const detailMocks = vi.hoisted(() => ({
  feedbackDetailRequest: vi.fn(),
  useAuth: vi.fn(),
}));

vi.mock('../feedbacks/api', () => ({ feedbackDetailRequest: detailMocks.feedbackDetailRequest }));
vi.mock('../auth/useAuth', () => ({ useAuth: detailMocks.useAuth }));
vi.mock('../feedbacks/FeedbackCorrectionSection', () => ({
  FeedbackCorrectionSection: () => <div data-testid="correction-section" />,
}));
vi.mock('../issues/FeedbackIssueSection', () => ({
  FeedbackIssueSection: () => <div data-testid="feedback-issue-section" />,
}));

const detail: FeedbackDetail = {
  id: 31,
  datasetId: 17,
  datasetName: '8월 앱 리뷰',
  externalId: 'review-031',
  sourceType: 'APP_REVIEW',
  customerSegment: '신규 고객',
  productName: '모바일 앱',
  rating: 1,
  content: '쿠폰을 적용하면 결제가 완료되지 않습니다.',
  language: 'ko',
  feedbackCreatedAt: '2026-08-20T13:30:00',
  ingestedAt: '2026-08-21T09:10:00',
  analysis: {
    id: 41,
    status: 'SUCCESS',
    sentiment: 'NEGATIVE',
    sentimentScore: -0.85,
    category: 'PAYMENT',
    urgencyScore: 0.9,
    summary: '쿠폰 적용 주문에서 결제를 완료하지 못하고 있습니다.',
    confidenceScore: 0.65,
    modelName: 'feedback-classifier-v1',
    errorMessage: null,
    analyzedAt: '2026-08-21T09:11:00',
  },
};

describe('FeedbackDetailPage', () => {
  beforeEach(() => {
    detailMocks.feedbackDetailRequest.mockReset();
    detailMocks.feedbackDetailRequest.mockResolvedValue(detail);
    detailMocks.useAuth.mockReturnValue({ user: { role: 'ADMIN' } });
  });

  it('renders original content, AI result, and a low-confidence warning', async () => {
    renderFeedbackDetailPage('/feedbacks/31');

    expect(await screen.findByRole('heading', { name: 'review-031' })).toBeInTheDocument();
    expect(screen.getByText('쿠폰을 적용하면 결제가 완료되지 않습니다.')).toBeInTheDocument();
    expect(screen.getByText('쿠폰 적용 주문에서 결제를 완료하지 못하고 있습니다.')).toBeInTheDocument();
    expect(screen.getByText('부정')).toBeInTheDocument();
    expect(screen.getByText('PAYMENT')).toBeInTheDocument();
    expect(screen.getByText('65.0%')).toBeInTheDocument();
    expect(screen.getByText(/신뢰도가 70% 미만입니다/)).toBeInTheDocument();
    expect(screen.getByTestId('correction-section')).toBeInTheDocument();
    expect(screen.getByTestId('feedback-issue-section')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '피드백 목록' })).toHaveAttribute(
      'href',
      '/feedbacks?datasetId=17',
    );
    expect(detailMocks.feedbackDetailRequest).toHaveBeenCalledWith(31);
  });

  it('does not expose the protected correction section to a viewer', async () => {
    detailMocks.useAuth.mockReturnValue({ user: { role: 'VIEWER' } });

    renderFeedbackDetailPage('/feedbacks/31');

    expect(await screen.findByRole('heading', { name: 'review-031' })).toBeInTheDocument();
    expect(screen.queryByTestId('correction-section')).not.toBeInTheDocument();
  });

  it('renders the unanalysed state', async () => {
    detailMocks.feedbackDetailRequest.mockResolvedValue({ ...detail, analysis: null });

    renderFeedbackDetailPage('/feedbacks/31');

    expect(await screen.findByText('아직 분석 결과가 없습니다.')).toBeInTheDocument();
    expect(screen.getByText('미분석')).toBeInTheDocument();
  });

  it('retries after an API error and rejects an invalid route id', async () => {
    const user = userEvent.setup();
    detailMocks.feedbackDetailRequest
      .mockRejectedValueOnce(new ApiError(404, 'NOT_FOUND', '요청한 데이터를 찾을 수 없습니다.'))
      .mockResolvedValueOnce(detail);

    const { unmount } = renderFeedbackDetailPage('/feedbacks/31');
    expect(await screen.findByText('피드백을 불러오지 못했습니다.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(await screen.findByRole('heading', { name: 'review-031' })).toBeInTheDocument();

    unmount();
    detailMocks.feedbackDetailRequest.mockClear();
    renderFeedbackDetailPage('/feedbacks/invalid');
    expect(screen.getByText('잘못된 피드백 주소입니다.')).toBeInTheDocument();
    expect(detailMocks.feedbackDetailRequest).not.toHaveBeenCalled();
  });
});

function renderFeedbackDetailPage(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/feedbacks/:feedbackId" element={<FeedbackDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}
