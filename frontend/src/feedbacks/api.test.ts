import { beforeEach, describe, expect, it, vi } from 'vitest';

import { feedbackDetailRequest, feedbacksRequest } from './api';

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
});
