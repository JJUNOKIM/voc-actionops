import { apiRequest } from '../lib/api-client';
import type {
  DatasetQuery,
  DatasetSummary,
  DatasetUploadInput,
  DatasetUploadResult,
  PageResponse,
} from './types';

export function datasetsRequest(query: DatasetQuery): Promise<PageResponse<DatasetSummary>> {
  const params = new URLSearchParams({
    page: query.page.toString(),
    size: query.size.toString(),
  });
  if (query.sourceType !== undefined) {
    params.set('sourceType', query.sourceType);
  }
  if (query.status !== undefined) {
    params.set('status', query.status);
  }
  return apiRequest(`/api/v1/datasets?${params.toString()}`);
}

export function uploadDatasetRequest(input: DatasetUploadInput): Promise<DatasetUploadResult> {
  const body = new FormData();
  body.set('name', input.name);
  body.set('sourceType', input.sourceType);
  body.set('file', input.file);
  body.set(
    'columnMapping',
    new Blob([JSON.stringify(input.columnMapping)], { type: 'application/json' }),
    'column-mapping.json',
  );

  return apiRequest('/api/v1/datasets', { method: 'POST', body });
}
