import type { PageResponse } from '../datasets/types';
import { apiRequest } from '../lib/api-client';
import type { IssueDetail, IssueFeedback, IssueQuery, IssueSummary } from './types';

export function issuesRequest(query: IssueQuery): Promise<PageResponse<IssueSummary>> {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
  });
  if (query.status !== undefined) params.set('status', query.status);
  if (query.priority !== undefined) params.set('priority', query.priority);
  if (query.category !== undefined) params.set('category', query.category);
  if (query.assigneeId !== undefined) params.set('assigneeId', String(query.assigneeId));
  if (query.keyword !== undefined) params.set('keyword', query.keyword);
  if (query.from !== undefined) params.set('from', query.from);
  if (query.to !== undefined) params.set('to', query.to);
  return apiRequest(`/api/v1/issues?${params.toString()}`);
}

export function issueDetailRequest(issueId: number): Promise<IssueDetail> {
  return apiRequest(`/api/v1/issues/${issueId}`);
}

export function issueFeedbacksRequest(
  issueId: number,
  page: number,
  size: number,
  representativeOnly = false,
): Promise<PageResponse<IssueFeedback>> {
  const params = new URLSearchParams({
    representativeOnly: String(representativeOnly),
    page: String(page),
    size: String(size),
  });
  return apiRequest(`/api/v1/issues/${issueId}/feedbacks?${params.toString()}`);
}
