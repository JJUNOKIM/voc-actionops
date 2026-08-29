import type { IssuePriority, IssueStatus } from './types';

const issueStatusLabels: Record<IssueStatus, string> = {
  NEW: '신규',
  TRIAGED: '분류 완료',
  ASSIGNED: '담당 지정',
  IN_PROGRESS: '처리 중',
  RESOLVED: '해결',
  MONITORING: '모니터링',
  CLOSED: '종료',
};

export function formatDashboardPercent(value: number): string {
  return `${value.toFixed(1)}%`;
}

export function formatResolutionHours(value: number | null): string {
  return value === null ? '-' : `${value.toFixed(1)}시간`;
}

export function formatPriorityScore(value: number | null): string {
  return value === null ? '-' : value.toFixed(1);
}

export function issueStatusLabel(status: IssueStatus): string {
  return issueStatusLabels[status];
}

export function priorityTone(priority: IssuePriority): string {
  return priority.toLowerCase();
}
