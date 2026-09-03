import type { IssueMetricPoint, IssueTrendRange } from './types';

export function validateTrendRange({ from, to }: IssueTrendRange): string | null {
  const start = parseDate(from);
  const end = parseDate(to);
  if (start === null || end === null) return '시작일과 종료일을 입력해 주세요.';
  if (start > end) return '종료일은 시작일보다 빠를 수 없습니다.';
  if ((end - start) / 86_400_000 >= 366) return '조회 기간은 최대 366일입니다.';
  return null;
}

function parseDate(value: string): number | null {
  const timestamp = Date.parse(`${value}T00:00:00Z`);
  if (!Number.isFinite(timestamp)) return null;
  return new Date(timestamp).toISOString().slice(0, 10) === value ? timestamp : null;
}

export function resolutionComparison(points: IssueMetricPoint[], resolvedDate: string | null) {
  if (resolvedDate === null) return null;
  // A daily snapshot has no capture time, so the resolution day is ambiguous.
  const before = points.findLast((point) => point.snapshotDate < resolvedDate);
  const after = points.findLast((point) => point.snapshotDate > resolvedDate);
  return before === undefined || after === undefined ? null : { before, after };
}

export function negativeRateLabel(point: IssueMetricPoint): string {
  return point.analyzedFeedbackCount === 0 ? '미분석' : `${point.negativeFeedbackRate.toFixed(1)}%`;
}

export function signedValue(value: number, unit: string, decimals = 0): string {
  const rounded = Number(value.toFixed(decimals));
  return `${rounded > 0 ? '+' : ''}${rounded.toFixed(decimals)}${unit}`;
}
