import { apiRequest } from '../lib/api-client';
import type {
  AnalysisJobView,
  DatasetDetail,
  DatasetQuery,
  DatasetSummary,
  DatasetUploadInput,
  DatasetUploadResult,
  DatasetValidationError,
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

export function datasetDetailRequest(datasetId: number): Promise<DatasetDetail> {
  return apiRequest(`/api/v1/datasets/${datasetId}`);
}

export function datasetValidationErrorsRequest(
  datasetId: number,
  page: number,
  size: number,
): Promise<PageResponse<DatasetValidationError>> {
  const params = new URLSearchParams({ page: page.toString(), size: size.toString() });
  return apiRequest(`/api/v1/datasets/${datasetId}/validation-errors?${params.toString()}`);
}

export function startDatasetAnalysisRequest(datasetId: number): Promise<AnalysisJobView> {
  return apiRequest(`/api/v1/datasets/${datasetId}/analyze`, { method: 'POST' });
}

export function datasetAnalysisStatusRequest(datasetId: number): Promise<AnalysisJobView> {
  return apiRequest(`/api/v1/datasets/${datasetId}/analysis-status`);
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
