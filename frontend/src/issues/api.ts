import type { PageResponse } from '../datasets/types';
import { apiRequest } from '../lib/api-client';
import type {
  ActionStatus,
  ConfirmIssueDraftRequest,
  CreateIssueActionRequest,
  FeedbackIssue,
  IssueAction,
  IssueCandidate,
  IssueDetail,
  IssueDraft,
  IssueFeedback,
  IssueQuery,
  IssueStatus,
  IssueSummary,
} from './types';

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

export function feedbackIssuesRequest(feedbackId: number): Promise<FeedbackIssue[]> {
  return apiRequest(`/api/v1/feedbacks/${feedbackId}/issues`);
}

export function issueCandidatesRequest(
  feedbackId: number,
  limit = 3,
): Promise<IssueCandidate[]> {
  return apiRequest(`/api/v1/feedbacks/${feedbackId}/issue-candidates?limit=${limit}`);
}

export function confirmIssueCandidateRequest(
  feedbackId: number,
  issueId: number,
  representative: boolean,
): Promise<IssueFeedback> {
  return apiRequest(`/api/v1/feedbacks/${feedbackId}/issue-candidates/${issueId}/confirm`, {
    method: 'POST',
    body: JSON.stringify({ representative }),
  });
}

export function linkFeedbackToIssueRequest(
  feedbackId: number,
  issueId: number,
  representative: boolean,
): Promise<IssueFeedback> {
  return apiRequest(`/api/v1/feedbacks/${feedbackId}/issue-links`, {
    method: 'POST',
    body: JSON.stringify({ issueId, representative }),
  });
}

export function issueDraftRequest(feedbackId: number): Promise<IssueDraft> {
  return apiRequest(`/api/v1/feedbacks/${feedbackId}/issue-draft`);
}

export function changeFeedbackRepresentativeRequest(
  feedbackId: number,
  issueId: number,
  representative: boolean,
): Promise<IssueFeedback> {
  return apiRequest(`/api/v1/feedbacks/${feedbackId}/issue-links/${issueId}`, {
    method: 'PATCH',
    body: JSON.stringify({ representative }),
  });
}

export function unlinkFeedbackRequest(feedbackId: number, issueId: number): Promise<void> {
  return apiRequest(`/api/v1/feedbacks/${feedbackId}/issue-links/${issueId}`, {
    method: 'DELETE',
  });
}

export function confirmIssueDraftRequest(
  feedbackId: number,
  request: ConfirmIssueDraftRequest,
): Promise<IssueDetail> {
  return apiRequest(`/api/v1/feedbacks/${feedbackId}/issue-draft/confirm`, {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function assignIssueRequest(issueId: number, assigneeId: number): Promise<IssueDetail> {
  return apiRequest(`/api/v1/issues/${issueId}/assignee`, {
    method: 'PATCH',
    body: JSON.stringify({ assigneeId }),
  });
}

export function changeIssueStatusRequest(
  issueId: number,
  status: IssueStatus,
): Promise<IssueDetail> {
  return apiRequest(`/api/v1/issues/${issueId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  });
}

export function createIssueActionRequest(
  issueId: number,
  request: CreateIssueActionRequest,
): Promise<IssueAction> {
  return apiRequest(`/api/v1/issues/${issueId}/actions`, {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function changeActionStatusRequest(
  actionId: number,
  status: ActionStatus,
): Promise<IssueAction> {
  return apiRequest(`/api/v1/actions/${actionId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  });
}
