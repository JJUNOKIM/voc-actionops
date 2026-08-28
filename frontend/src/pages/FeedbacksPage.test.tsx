import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { FeedbacksPage } from './FeedbacksPage';
import type { PageResponse } from '../datasets/types';
import type { FeedbackListItem } from '../feedbacks/types';

const pageMocks = vi.hoisted(() => ({
  feedbacksRequest: vi.fn(),
}));

vi.mock('../feedbacks/api', () => ({ feedbacksRequest: pageMocks.feedbacksRequest }));

const feedbackPage: PageResponse<FeedbackListItem> = {
  content: [
    {
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
        status: 'SUCCESS',
        sentiment: 'NEGATIVE',
        category: 'PAYMENT',
        urgencyScore: 0.9,
        confidenceScore: 0.88,
      },
    },
  ],
  page: 0,
  size: 20,
  totalElements: 21,
  totalPages: 2,
};

describe('FeedbacksPage', () => {
  beforeEach(() => {
    pageMocks.feedbacksRequest.mockReset();
    pageMocks.feedbacksRequest.mockResolvedValue(feedbackPage);
  });

  it('renders analysis summaries and keeps the dataset scope in API requests', async () => {
    const user = userEvent.setup();
    renderFeedbacksPage('/feedbacks?datasetId=17');

    expect(await screen.findByRole('link', { name: 'review-031' })).toHaveAttribute(
      'href',
      '/feedbacks/31',
    );
    expect(screen.getAllByText('8월 앱 리뷰')).toHaveLength(2);
    expect(screen.getByText('부정')).toBeInTheDocument();
    expect(screen.getByText('PAYMENT')).toBeInTheDocument();
    expect(screen.getByText('90.0%')).toBeInTheDocument();
    expect(pageMocks.feedbacksRequest).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      datasetId: 17,
      sourceType: undefined,
    });

    await user.selectOptions(screen.getByLabelText('데이터 출처'), 'APP_REVIEW');
    await waitFor(() =>
      expect(pageMocks.feedbacksRequest).toHaveBeenLastCalledWith({
        page: 0,
        size: 20,
        datasetId: 17,
        sourceType: 'APP_REVIEW',
      }),
    );

    await user.click(screen.getByRole('button', { name: '다음 페이지' }));
    await waitFor(() =>
      expect(pageMocks.feedbacksRequest).toHaveBeenLastCalledWith({
        page: 1,
        size: 20,
        datasetId: 17,
        sourceType: 'APP_REVIEW',
      }),
    );
  });

  it('shows unanalysed feedback without hiding the source content', async () => {
    pageMocks.feedbacksRequest.mockResolvedValue({
      ...feedbackPage,
      content: [{ ...feedbackPage.content[0], analysis: null }],
      totalElements: 1,
      totalPages: 1,
    });

    renderFeedbacksPage('/feedbacks');

    expect(await screen.findByText('미분석')).toBeInTheDocument();
    expect(screen.getByText('쿠폰을 적용하면 결제가 완료되지 않습니다.')).toBeInTheDocument();
  });
});

function renderFeedbacksPage(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <FeedbacksPage />
    </MemoryRouter>,
  );
}
