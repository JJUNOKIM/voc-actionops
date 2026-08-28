import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  correctFeedbackAnalysisRequest,
  feedbackCorrectionsRequest,
  feedbackDetailRequest,
  feedbacksRequest,
} from './api';

const apiRequestMock = vi.hoisted(() => vi.fn());

vi.mock('../lib/api-client', () => ({ apiRequest: apiRequestMock }));

describe('feedback API', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
    apiRequestMock.mockResolvedValue({});
  });

  it('requests a filtered feedback page', async () => {
    await feedbacksRequest({ page: 2, size: 20, datasetId: 17, sourceType: 'APP_REVIEW' });

    expect(apiRequestMock).toHaveBeenCalledWith(
      '/api/v1/feedbacks?page=2&size=20&datasetId=17&sourceType=APP_REVIEW',
    );
  });

  it('requests feedback detail', async () => {
    await feedbackDetailRequest(31);

    expect(apiRequestMock).toHaveBeenCalledWith('/api/v1/feedbacks/31');
  });

  it('submits an analysis correction', async () => {
    await correctFeedbackAnalysisRequest(31, {
      fieldName: 'category',
      correctedValue: 'CHECKOUT',
      reason: '결제 전 단계의 오류로 재분류',
    });

    expect(apiRequestMock).toHaveBeenCalledWith('/api/v1/feedbacks/31/analysis', {
      method: 'PATCH',
      body: JSON.stringify({
        fieldName: 'category',
        correctedValue: 'CHECKOUT',
        reason: '결제 전 단계의 오류로 재분류',
      }),
    });
  });

  it('requests a correction history page', async () => {
    await feedbackCorrectionsRequest(31, 2, 5);

    expect(apiRequestMock).toHaveBeenCalledWith(
      '/api/v1/feedbacks/31/corrections?page=2&size=5',
    );
  });
});
