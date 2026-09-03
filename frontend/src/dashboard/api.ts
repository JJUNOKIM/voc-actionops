import { apiRequest } from '../lib/api-client';
import type {
  CategoryBreakdownItem,
  DashboardOverview,
  DashboardSummary,
  IssueTrend,
  IssueTrendRange,
  TopIssue,
} from './types';

const TOP_ISSUE_LIMIT = 6;

export function issueTrendRequest(issueId: number, range?: IssueTrendRange): Promise<IssueTrend> {
  const parameters = new URLSearchParams({ issueId: String(issueId) });
  if (range !== undefined) {
    parameters.set('from', range.from);
    parameters.set('to', range.to);
  }
  return apiRequest(`/api/v1/dashboard/issue-trends?${parameters.toString()}`);
}

export function dashboardSummaryRequest(): Promise<DashboardSummary> {
  return apiRequest('/api/v1/dashboard/summary');
}

export function categoryBreakdownRequest(): Promise<CategoryBreakdownItem[]> {
  return apiRequest('/api/v1/dashboard/category-breakdown');
}

export function topIssuesRequest(): Promise<TopIssue[]> {
  return apiRequest(
    `/api/v1/dashboard/top-issues?limit=${TOP_ISSUE_LIMIT}&sortBy=priority_score`,
  );
}

export async function dashboardOverviewRequest(): Promise<DashboardOverview> {
  const [summary, categories, topIssues] = await Promise.all([
    dashboardSummaryRequest(),
    categoryBreakdownRequest(),
    topIssuesRequest(),
  ]);

  return { summary, categories, topIssues };
}
