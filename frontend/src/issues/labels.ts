import type { IssuePriority, IssueStatus } from './types';

export const issuePriorityOptions: ReadonlyArray<{ value: IssuePriority; label: string }> = [
  { value: 'P0', label: 'P0 긴급' },
  { value: 'P1', label: 'P1 높음' },
  { value: 'P2', label: 'P2 보통' },
  { value: 'P3', label: 'P3 낮음' },
];

export const issueStatusOptions: ReadonlyArray<{ value: IssueStatus; label: string }> = [
  { value: 'NEW', label: '신규' },
  { value: 'TRIAGED', label: '분류 완료' },
  { value: 'ASSIGNED', label: '담당 지정' },
  { value: 'IN_PROGRESS', label: '처리 중' },
  { value: 'RESOLVED', label: '해결' },
  { value: 'MONITORING', label: '모니터링' },
  { value: 'CLOSED', label: '종료' },
];
