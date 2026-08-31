import type { SourceType } from '../datasets/types';

export type IssuePriority = 'P0' | 'P1' | 'P2' | 'P3';

export type IssueStatus =
  | 'NEW'
  | 'TRIAGED'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'RESOLVED'
  | 'MONITORING'
  | 'CLOSED';

export type ActionStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'CANCELED';
export type IssueFeedbackLinkSource = 'AI' | 'MANUAL';

export interface IssueSummary {
  id: number;
  title: string;
  category: string;
  priority: IssuePriority;
  priorityScore: number | null;
  status: IssueStatus;
  assigneeId: number | null;
  assigneeName: string | null;
  feedbackCount: number;
  negativeCount: number;
  firstSeenAt: string | null;
  lastSeenAt: string | null;
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface IssueAction {
  id: number;
  issueId: number;
  title: string;
  description: string | null;
  status: ActionStatus;
  assigneeId: number | null;
  assigneeName: string | null;
  dueDate: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

export interface IssueDetail extends IssueSummary {
  description: string;
  actions: IssueAction[];
}

export interface IssueFeedback {
  id: number;
  feedbackId: number;
  datasetId: number;
  externalId: string | null;
  sourceType: SourceType;
  content: string;
  rating: number | null;
  similarityScore: number | null;
  representative: boolean;
  linkedBy: IssueFeedbackLinkSource;
  feedbackCreatedAt: string | null;
  linkedAt: string;
}

export interface IssueQuery {
  page: number;
  size: number;
  status?: IssueStatus;
  priority?: IssuePriority;
  category?: string;
  assigneeId?: number;
  keyword?: string;
  from?: string;
  to?: string;
}
