import type { IssuePriority, IssueStatus } from '../issues/types';

export interface DashboardSummary {
  totalFeedbackCount: number;
  negativeFeedbackRate: number;
  newIssueCount: number;
  p0IssueCount: number;
  p1IssueCount: number;
  unresolvedIssueCount: number;
  averageResolutionHours: number | null;
}

export interface CategoryBreakdownItem {
  category: string;
  issueCount: number;
  feedbackCount: number;
  negativeFeedbackRate: number;
}

export interface TopIssue {
  issueId: number;
  title: string;
  category: string;
  priority: IssuePriority;
  priorityScore: number | null;
  status: IssueStatus;
  feedbackCount: number;
  feedbackGrowthRate: number | null;
  negativeFeedbackRate: number;
  unresolvedActionCount: number;
  assigneeId: number | null;
  assigneeName: string | null;
  lastSeenAt: string | null;
}

export interface DashboardOverview {
  summary: DashboardSummary;
  categories: CategoryBreakdownItem[];
  topIssues: TopIssue[];
}
