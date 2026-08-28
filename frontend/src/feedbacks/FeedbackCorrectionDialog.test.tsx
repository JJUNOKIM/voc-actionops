import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { FeedbackCorrectionDialog } from './FeedbackCorrectionDialog';
import type { FeedbackAnalysisDetail } from './types';

const apiMocks = vi.hoisted(() => ({
  correctFeedbackAnalysisRequest: vi.fn(),
}));

vi.mock('./api', () => ({
  correctFeedbackAnalysisRequest: apiMocks.correctFeedbackAnalysisRequest,
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

describe('FeedbackCorrectionDialog', () => {
  beforeEach(() => {
    apiMocks.correctFeedbackAnalysisRequest.mockReset();
  });

  it('submits a trimmed category correction and reason', async () => {
    const user = userEvent.setup();
    const correctedAnalysis = { ...analysis, category: 'CHECKOUT' };
    const onCorrected = vi.fn();
    apiMocks.correctFeedbackAnalysisRequest.mockResolvedValue(correctedAnalysis);

    render(
      <FeedbackCorrectionDialog
        feedbackId={31}
        analysis={analysis}
        onClose={vi.fn()}
        onCorrected={onCorrected}
      />,
    );

    const valueInput = screen.getByLabelText('수정값');
    await user.clear(valueInput);
    await user.type(valueInput, '  CHECKOUT  ');
    await user.type(screen.getByLabelText(/수정 사유/), ' 결제 전 단계의 오류로 재분류 ');
    await user.click(screen.getByRole('button', { name: '수정 저장' }));

    expect(apiMocks.correctFeedbackAnalysisRequest).toHaveBeenCalledWith(31, {
      fieldName: 'category',
      correctedValue: 'CHECKOUT',
      reason: '결제 전 단계의 오류로 재분류',
    });
    expect(onCorrected).toHaveBeenCalledWith(correctedAnalysis);
  });

  it('rejects an unchanged value before sending a request', async () => {
    const user = userEvent.setup();

    render(
      <FeedbackCorrectionDialog
        feedbackId={31}
        analysis={analysis}
        onClose={vi.fn()}
        onCorrected={vi.fn()}
      />,
    );

    await user.type(screen.getByLabelText(/수정 사유/), '분류 결과 확인');
    await user.click(screen.getByRole('button', { name: '수정 저장' }));

    expect(screen.getByRole('alert')).toHaveTextContent('현재 분석값과 다른 값을 입력해 주세요.');
    expect(apiMocks.correctFeedbackAnalysisRequest).not.toHaveBeenCalled();
  });

  it('validates urgency range and decimal places', async () => {
    const user = userEvent.setup();

    render(
      <FeedbackCorrectionDialog
        feedbackId={31}
        analysis={analysis}
        onClose={vi.fn()}
        onCorrected={vi.fn()}
      />,
    );

    await user.selectOptions(screen.getByLabelText('수정 항목'), 'urgency_score');
    const valueInput = screen.getByLabelText('수정값');
    await user.clear(valueInput);
    await user.type(valueInput, '0.12345');
    await user.type(screen.getByLabelText(/수정 사유/), '긴급도 재검토');
    await user.click(screen.getByRole('button', { name: '수정 저장' }));

    expect(screen.getByRole('alert')).toHaveTextContent('소수점 넷째 자리까지');
    expect(apiMocks.correctFeedbackAnalysisRequest).not.toHaveBeenCalled();

    await user.clear(valueInput);
    await user.type(valueInput, '1.1');
    await user.click(screen.getByRole('button', { name: '수정 저장' }));

    expect(screen.getByRole('alert')).toHaveTextContent('0부터 1 사이');
    expect(apiMocks.correctFeedbackAnalysisRequest).not.toHaveBeenCalled();
  });
});
