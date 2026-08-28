import type { PageResponse } from '../datasets/types';
import { apiRequest } from '../lib/api-client';
import type { FeedbackDetail, FeedbackListItem, FeedbackQuery } from './types';

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
