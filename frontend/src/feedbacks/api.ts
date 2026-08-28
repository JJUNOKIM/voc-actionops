import type { PageResponse } from '../datasets/types';
import { apiRequest } from '../lib/api-client';
import type {
  FeedbackAnalysisDetail,
  FeedbackCorrection,
  FeedbackCorrectionRequest,
  FeedbackDetail,
  FeedbackListItem,
  FeedbackQuery,
} from './types';

export function feedbacksRequest(query: FeedbackQuery): Promise<PageResponse<FeedbackListItem>> {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
  });
  if (query.datasetId !== undefined) params.set('datasetId', String(query.datasetId));
  if (query.sourceType !== undefined) params.set('sourceType', query.sourceType);
  return apiRequest(`/api/v1/feedbacks?${params.toString()}`);
}

export function feedbackDetailRequest(feedbackId: number): Promise<FeedbackDetail> {
  return apiRequest(`/api/v1/feedbacks/${feedbackId}`);
}

export function correctFeedbackAnalysisRequest(
  feedbackId: number,
  correction: FeedbackCorrectionRequest,
): Promise<FeedbackAnalysisDetail> {
  return apiRequest(`/api/v1/feedbacks/${feedbackId}/analysis`, {
    method: 'PATCH',
    body: JSON.stringify(correction),
  });
}

export function feedbackCorrectionsRequest(
  feedbackId: number,
  page: number,
  size: number,
): Promise<PageResponse<FeedbackCorrection>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return apiRequest(`/api/v1/feedbacks/${feedbackId}/corrections?${params.toString()}`);
}
