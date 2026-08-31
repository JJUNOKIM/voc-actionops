import type { UserProfile } from '../types/api';
import type { ActionStatus, IssueAction, IssueDetail, IssueStatus } from './types';

const issueTransitions: Record<IssueStatus, IssueStatus[]> = {
  NEW: ['TRIAGED'],
  TRIAGED: ['ASSIGNED'],
  ASSIGNED: ['IN_PROGRESS'],
  IN_PROGRESS: ['RESOLVED'],
  RESOLVED: ['MONITORING'],
  MONITORING: ['CLOSED', 'IN_PROGRESS'],
  CLOSED: [],
};

const actionTransitions: Record<ActionStatus, ActionStatus[]> = {
  TODO: ['IN_PROGRESS', 'CANCELED'],
  IN_PROGRESS: ['DONE', 'CANCELED'],
  DONE: [],
  CANCELED: [],
};

export function nextIssueStatuses(status: IssueStatus): IssueStatus[] {
  return issueTransitions[status];
}

export function nextActionStatuses(status: ActionStatus): ActionStatus[] {
  return actionTransitions[status];
}

export function canManageIssue(user: UserProfile): boolean {
  return user.role === 'ADMIN' || user.role === 'PM';
}

export function canChangeIssueStatus(user: UserProfile, issue: IssueDetail): boolean {
  return (
    canManageIssue(user) ||
    (user.role === 'DEVELOPER' && issue.assigneeId !== null && issue.assigneeId === user.id)
  );
}

export function canChangeActionStatus(user: UserProfile, action: IssueAction): boolean {
  return (
    canManageIssue(user) ||
    (user.role === 'DEVELOPER' && action.assigneeId !== null && action.assigneeId === user.id)
  );
}

export function issueTransitionLabel(current: IssueStatus, target: IssueStatus): string {
  if (current === 'MONITORING' && target === 'IN_PROGRESS') return '재처리 시작';
  const labels: Record<IssueStatus, string> = {
    NEW: '신규로 변경',
    TRIAGED: '분류 완료로 변경',
    ASSIGNED: '담당 지정으로 변경',
    IN_PROGRESS: '처리 시작',
    RESOLVED: '해결로 변경',
    MONITORING: '모니터링 시작',
    CLOSED: '이슈 종료',
  };
  return labels[target];
}

export function actionTransitionLabel(target: ActionStatus): string {
  const labels: Record<ActionStatus, string> = {
    TODO: '할 일로 변경',
    IN_PROGRESS: '진행 시작',
    DONE: '완료',
    CANCELED: '취소',
  };
  return labels[target];
}
