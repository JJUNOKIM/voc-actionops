import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../lib/api-client';
import { IssueTrendSection } from './IssueTrendSection';
import type { IssueMetricPoint, IssueTrend } from './types';

const trendRequest = vi.hoisted(() => vi.fn());
vi.mock('./api', () => ({ issueTrendRequest: trendRequest }));

const point: IssueMetricPoint = {
  snapshotDate: '2026-08-31',
  feedbackCount: 10,
  analyzedFeedbackCount: 10,
  negativeFeedbackRate: 80,
  averageSentimentScore: -0.4,
  averageUrgencyScore: 0.7,
  priorityScore: 52,
  unresolvedActionCount: 2,
};

const trend: IssueTrend = {
  issueId: 7,
  title: '결제 실패',
  category: 'PAYMENT',
  status: 'MONITORING',
  resolvedAt: '2026-09-01T12:00:00',
  resolvedDate: '2026-09-01',
  from: '2026-08-05',
  to: '2026-09-03',
  feedbackGrowthRate: 25,
  points: [
    point,
    { ...point, snapshotDate: '2026-09-01', feedbackCount: 12 },
    { ...point, snapshotDate: '2026-09-03', feedbackCount: 15, negativeFeedbackRate: 60, unresolvedActionCount: 0 },
  ],
};

describe('IssueTrendSection', () => {
  beforeEach(() => {
    trendRequest.mockReset().mockResolvedValue(trend);
  });

  it('shows persisted records, growth, and a resolution comparison', async () => {
    render(<IssueTrendSection issueId={7} resolvedAt={trend.resolvedAt} />);

    const comparison = await screen.findByRole('region', { name: '해결 전후 지표 비교' });
    expect(within(comparison).getByText('+5건')).toBeInTheDocument();
    expect(within(comparison).getByText('-20.0%p')).toBeInTheDocument();
    expect(within(comparison).getByText('-2건')).toBeInTheDocument();
    expect(within(comparison).queryByText('2026-09-01')).not.toBeInTheDocument();
    expect(screen.getByText('+25.0%')).toBeInTheDocument();
    expect(screen.getByLabelText('시작일')).toHaveValue('2026-08-05');
    expect(screen.getByLabelText('종료일')).toHaveValue('2026-09-03');
    const records = screen.getByRole('region', { name: '일별 누적 집계 기록' });
    expect(within(records).getAllByRole('row')).toHaveLength(4);
    expect(within(records).queryByText('2026-09-02')).not.toBeInTheDocument();
    expect(trendRequest).toHaveBeenCalledWith(7, undefined);
  });

  it('does not display zero metrics when no snapshots exist', async () => {
    trendRequest.mockResolvedValue({ ...trend, points: [], feedbackGrowthRate: null });
    render(<IssueTrendSection issueId={7} resolvedAt={trend.resolvedAt} />);
    expect(await screen.findByText('이 기간에 저장된 집계 기록이 없습니다.')).toBeInTheDocument();
    expect(screen.queryByText('0.0%')).not.toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('uses the server snapshot date instead of slicing the resolution timestamp', async () => {
    trendRequest.mockResolvedValue({ ...trend, resolvedAt: '2026-08-31T18:00:00' });
    render(<IssueTrendSection issueId={7} resolvedAt="2026-08-31T18:00:00" />);
    const comparison = await screen.findByRole('region', { name: '해결 전후 지표 비교' });
    expect(screen.getByText('해결일 2026-09-01 · 당일 집계 제외')).toBeInTheDocument();
    expect(within(comparison).getByText('+5건')).toBeInTheDocument();
  });

  it('marks unanalyzed values and insufficient comparisons separately', async () => {
    trendRequest.mockResolvedValue({
      ...trend,
      points: [{ ...point, analyzedFeedbackCount: 0, negativeFeedbackRate: 0 }],
      feedbackGrowthRate: null,
    });
    render(<IssueTrendSection issueId={7} resolvedAt={trend.resolvedAt} />);
    expect(await screen.findByText('비교 불가')).toBeInTheDocument();
    expect(screen.getAllByText('미분석')).toHaveLength(2);
    expect(screen.getByText('조회 기간에 해결일 전후 기록이 모두 있지 않아 비교할 수 없습니다.')).toBeInTheDocument();
    expect(screen.queryByText('0.0%')).not.toBeInTheDocument();
  });

  it('does not calculate a percentage-point change without analyzed feedback on both sides', async () => {
    trendRequest.mockResolvedValue({
      ...trend,
      points: [{ ...point, analyzedFeedbackCount: 0, negativeFeedbackRate: 0 }, trend.points[2]],
    });
    render(<IssueTrendSection issueId={7} resolvedAt={trend.resolvedAt} />);
    const comparison = await screen.findByRole('region', { name: '해결 전후 지표 비교' });
    const row = within(comparison).getByRole('row', { name: /부정 비율/ });
    expect(within(row).getByText('-')).toBeInTheDocument();
    expect(within(row).getByText('미분석')).toBeInTheDocument();
  });

  it('validates and submits a selected period', async () => {
    const user = userEvent.setup();
    render(<IssueTrendSection issueId={7} resolvedAt={trend.resolvedAt} />);
    await screen.findByText('+25.0%');
    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '2026-09-04' } });
    await user.click(screen.getByRole('button', { name: '추이 기간 조회' }));
    expect(screen.getByRole('alert')).toHaveTextContent('종료일은 시작일보다 빠를 수 없습니다.');
    expect(trendRequest).toHaveBeenCalledTimes(1);

    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '2026-08-01' } });
    await user.click(screen.getByRole('button', { name: '추이 기간 조회' }));
    await waitFor(() => expect(trendRequest).toHaveBeenLastCalledWith(7, {
      from: '2026-08-01', to: '2026-09-03',
    }));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('retries a failed request', async () => {
    const user = userEvent.setup();
    trendRequest.mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', '집계를 조회할 수 없습니다.'));
    render(<IssueTrendSection issueId={7} resolvedAt={trend.resolvedAt} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('집계를 조회할 수 없습니다.');
    await user.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(await screen.findByText('+25.0%')).toBeInTheDocument();
  });

  it('ignores a late response for an older date range', async () => {
    const user = userEvent.setup();
    let finishOldRequest!: (value: IssueTrend) => void;
    trendRequest.mockReturnValueOnce(new Promise<IssueTrend>((resolve) => { finishOldRequest = resolve; }));
    render(<IssueTrendSection issueId={7} resolvedAt={trend.resolvedAt} />);
    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '2026-08-05' } });
    fireEvent.change(screen.getByLabelText('종료일'), { target: { value: '2026-09-03' } });
    await user.click(screen.getByRole('button', { name: '추이 기간 조회' }));
    expect(await screen.findByText('+25.0%')).toBeInTheDocument();

    await act(async () => { finishOldRequest({ ...trend, feedbackGrowthRate: 99 }); });
    expect(screen.queryByText('+99.0%')).not.toBeInTheDocument();
    expect(screen.getByText('+25.0%')).toBeInTheDocument();
  });

  it('reloads when the issue is resolved or reopened', async () => {
    const { rerender } = render(<IssueTrendSection issueId={7} resolvedAt={trend.resolvedAt} />);
    await screen.findByText('+25.0%');
    trendRequest.mockResolvedValue({ ...trend, status: 'IN_PROGRESS', resolvedAt: null, resolvedDate: null });
    rerender(<IssueTrendSection issueId={7} resolvedAt={null} />);
    await waitFor(() => expect(trendRequest).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.queryByText('해결 전후 기록')).not.toBeInTheDocument());
  });
});
