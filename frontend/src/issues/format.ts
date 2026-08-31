import type { ActionStatus, IssuePriority, IssueStatus } from './types';

const issueStatusLabels: Record<IssueStatus, string> = {
  NEW: '신규',
  TRIAGED: '분류 완료',
  ASSIGNED: '담당 지정',
  IN_PROGRESS: '처리 중',
  RESOLVED: '해결',
  MONITORING: '모니터링',
  CLOSED: '종료',
};

const actionStatusLabels: Record<ActionStatus, string> = {
  TODO: '할 일',
  IN_PROGRESS: '진행 중',
  DONE: '완료',
  CANCELED: '취소',
};

export function issueStatusLabel(status: IssueStatus): string {
  return issueStatusLabels[status];
}

export function actionStatusLabel(status: ActionStatus): string {
  return actionStatusLabels[status];
}

export function formatPriorityScore(value: number | null): string {
  return value === null ? '-' : value.toFixed(1);
}

export function formatNegativeRate(negativeCount: number, feedbackCount: number): string {
  if (feedbackCount === 0) return '-';
  return `${((negativeCount / feedbackCount) * 100).toFixed(1)}%`;
}

export function priorityTone(priority: IssuePriority): string {
  return priority.toLowerCase();
}

export function issueStatusTone(status: IssueStatus): string {
  return status.toLowerCase();
}

export function actionStatusTone(status: ActionStatus): string {
  return status.toLowerCase();
}
