import { describe, expect, it } from 'vitest';

import { negativeRateLabel, resolutionComparison, signedValue, validateTrendRange } from './issue-trend';
import type { IssueMetricPoint } from './types';

const point: IssueMetricPoint = {
  snapshotDate: '2026-09-01',
  feedbackCount: 10,
  analyzedFeedbackCount: 8,
  negativeFeedbackRate: 50,
  averageSentimentScore: -0.1,
  averageUrgencyScore: 0.7,
  priorityScore: 52,
  unresolvedActionCount: 1,
};

describe('issue trend dates', () => {
  it('allows a single day and 366 inclusive days', () => {
    expect(validateTrendRange({ from: '2026-09-03', to: '2026-09-03' })).toBeNull();
    expect(validateTrendRange({ from: '2025-01-01', to: '2026-01-01' })).toBeNull();
  });

  it('rejects missing, impossible, reversed, and overlong dates', () => {
    expect(validateTrendRange({ from: '', to: '2026-09-03' })).not.toBeNull();
    expect(validateTrendRange({ from: '2026-02-30', to: '2026-03-03' })).not.toBeNull();
    expect(validateTrendRange({ from: '2026-09-04', to: '2026-09-03' })).not.toBeNull();
    expect(validateTrendRange({ from: '2025-01-01', to: '2026-01-02' })).not.toBeNull();
  });
});

describe('resolution comparison', () => {
  it('compares the last prior day with the latest later day, excluding resolution day', () => {
    const points = ['2026-08-29', '2026-08-31', '2026-09-01', '2026-09-03']
      .map((snapshotDate) => ({ ...point, snapshotDate }));
    expect(resolutionComparison(points, '2026-09-01')).toEqual({
      before: points[1],
      after: points[3],
    });
    expect(points.map((item) => item.snapshotDate)).toEqual([
      '2026-08-29', '2026-08-31', '2026-09-01', '2026-09-03',
    ]);
  });

  it('does not infer missing records or compare a still-open issue', () => {
    expect(resolutionComparison([], '2026-09-01')).toBeNull();
    expect(resolutionComparison([point], '2026-09-01')).toBeNull();
    expect(resolutionComparison([point], '2026-09-02')).toBeNull();
    expect(resolutionComparison([point], '2026-08-31')).toBeNull();
    expect(resolutionComparison([point], null)).toBeNull();
  });

  it('distinguishes unanalyzed feedback from a real zero percent', () => {
    expect(negativeRateLabel({ ...point, analyzedFeedbackCount: 0, negativeFeedbackRate: 0 })).toBe('미분석');
    expect(negativeRateLabel({ ...point, negativeFeedbackRate: 0 })).toBe('0.0%');
  });

  it('formats signed differences without negative zero', () => {
    expect(signedValue(3, '건')).toBe('+3건');
    expect(signedValue(-12.25, '%p', 1)).toBe('-12.3%p');
    expect(signedValue(-0.001, '%p', 1)).toBe('0.0%p');
  });
});
