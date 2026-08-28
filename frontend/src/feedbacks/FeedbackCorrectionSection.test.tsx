import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { FeedbackCorrectionSection } from './FeedbackCorrectionSection';
import type { PageResponse } from '../datasets/types';
import { ApiError } from '../lib/api-client';
import type {
  FeedbackAnalysisDetail,
  FeedbackCorrection,
} from './types';

const apiMocks = vi.hoisted(() => ({
  correctFeedbackAnalysisRequest: vi.fn(),
  feedbackCorrectionsRequest: vi.fn(),
}));

vi.mock('./api', () => ({
  correctFeedbackAnalysisRequest: apiMocks.correctFeedbackAnalysisRequest,
  feedbackCorrectionsRequest: apiMocks.feedbackCorrectionsRequest,
}));

const analysis: FeedbackAnalysisDetail = {
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
};

const emptyHistory: PageResponse<FeedbackCorrection> = {
  content: [],
  page: 0,
  size: 5,
  totalElements: 0,
  totalPages: 0,
};

const correction: FeedbackCorrection = {
  id: 9,
  fieldName: 'category',
  aiValue: 'PAYMENT',
  correctedValue: 'CHECKOUT',
  reason: '결제 전 단계에서 발생한 오류로 재분류',
  correctedBy: 3,
  createdAt: '2026-08-28T15:20:00',
};

describe('FeedbackCorrectionSection', () => {
  beforeEach(() => {
    apiMocks.correctFeedbackAnalysisRequest.mockReset();
    apiMocks.feedbackCorrectionsRequest.mockReset();
  });

  it('updates the analysis and reloads history after a correction', async () => {
    const user = userEvent.setup();
    const onAnalysisCorrected = vi.fn();
    const correctedAnalysis = { ...analysis, category: 'CHECKOUT' };
    apiMocks.feedbackCorrectionsRequest
      .mockResolvedValueOnce(emptyHistory)
      .mockResolvedValueOnce({
        ...emptyHistory,
        content: [correction],
        totalElements: 1,
        totalPages: 1,
      });
    apiMocks.correctFeedbackAnalysisRequest.mockResolvedValue(correctedAnalysis);

    render(
      <FeedbackCorrectionSection
        feedbackId={31}
        analysis={analysis}
        onAnalysisCorrected={onAnalysisCorrected}
      />,
    );

    expect(await screen.findByText('아직 수정 이력이 없습니다.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '분석 결과 수정' }));
    const valueInput = screen.getByLabelText('수정값');
    await user.clear(valueInput);
    await user.type(valueInput, 'CHECKOUT');
    await user.type(screen.getByLabelText(/수정 사유/), correction.reason);
    await user.click(screen.getByRole('button', { name: '수정 저장' }));

    expect(await screen.findByText('분석 결과와 수정 이력을 반영했습니다.')).toBeInTheDocument();
    expect(await screen.findByText(correction.reason)).toBeInTheDocument();
    expect(screen.getByText('PAYMENT')).toBeInTheDocument();
    expect(screen.getByText('CHECKOUT')).toBeInTheDocument();
    expect(onAnalysisCorrected).toHaveBeenCalledWith(correctedAnalysis);
    expect(apiMocks.feedbackCorrectionsRequest).toHaveBeenLastCalledWith(31, 0, 5);
  });

  it('recovers from a history request error', async () => {
    const user = userEvent.setup();
    apiMocks.feedbackCorrectionsRequest
      .mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', '일시적인 오류입니다.'))
      .mockResolvedValueOnce(emptyHistory);

    render(
      <FeedbackCorrectionSection
        feedbackId={31}
        analysis={analysis}
        onAnalysisCorrected={vi.fn()}
      />,
    );

    expect(await screen.findByText('수정 이력을 불러오지 못했습니다.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '수정 이력 다시 불러오기' }));

    expect(await screen.findByText('아직 수정 이력이 없습니다.')).toBeInTheDocument();
    await waitFor(() => expect(apiMocks.feedbackCorrectionsRequest).toHaveBeenCalledTimes(2));
  });
});
