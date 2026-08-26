import { beforeEach, describe, expect, it, vi } from 'vitest';

import { datasetsRequest, uploadDatasetRequest } from './api';

const apiRequestMock = vi.hoisted(() => vi.fn());

vi.mock('../lib/api-client', () => ({ apiRequest: apiRequestMock }));

describe('dataset API', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
  });

  it('serializes list filters and pagination', async () => {
    apiRequestMock.mockResolvedValue({ content: [] });

    await datasetsRequest({
      sourceType: 'APP_REVIEW',
      status: 'VALIDATED',
      page: 2,
      size: 20,
    });

    expect(apiRequestMock).toHaveBeenCalledWith(
      '/api/v1/datasets?page=2&size=20&sourceType=APP_REVIEW&status=VALIDATED',
    );
  });

  it('builds the multipart upload request with a JSON mapping part', async () => {
    apiRequestMock.mockResolvedValue({ datasetId: 7 });
    const file = new File(['content\n좋아요'], 'reviews.csv', { type: 'text/csv' });

    await uploadDatasetRequest({
      name: '8월 앱 리뷰',
      sourceType: 'APP_REVIEW',
      file,
      columnMapping: { content: 'content' },
    });

    const [, init] = apiRequestMock.mock.calls[0] as [string, RequestInit];
    const body = init.body as FormData;
    expect(apiRequestMock.mock.calls[0]?.[0]).toBe('/api/v1/datasets');
    expect(init.method).toBe('POST');
    expect(body.get('name')).toBe('8월 앱 리뷰');
    expect(body.get('sourceType')).toBe('APP_REVIEW');
    expect(body.get('file')).toBe(file);
    expect(body.get('columnMapping')).toBeInstanceOf(Blob);
    expect(await (body.get('columnMapping') as Blob).text()).toBe('{"content":"content"}');
  });
});
