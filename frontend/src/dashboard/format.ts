export { formatPriorityScore, issueStatusLabel, priorityTone } from '../issues/format';

export function formatDashboardPercent(value: number): string {
  return `${value.toFixed(1)}%`;
}

export function formatResolutionHours(value: number | null): string {
  return value === null ? '-' : `${value.toFixed(1)}시간`;
}
