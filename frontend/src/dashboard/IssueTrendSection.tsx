import { AlertCircle, BarChart3, RefreshCw, Search } from 'lucide-react';
import { useEffect, useState, type FormEvent } from 'react';

import { formatNumber } from '../datasets/format';
import { ApiError } from '../lib/api-client';
import { issueTrendRequest } from './api';
import {
  negativeRateLabel,
  resolutionComparison,
  signedValue,
  validateTrendRange,
} from './issue-trend';
import type { IssueMetricPoint, IssueTrend, IssueTrendRange } from './types';

type TrendState = { key: string; data: IssueTrend } | { key: string; error: string } | null;

interface IssueTrendSectionProps {
  issueId: number;
  resolvedAt: string | null;
}

export function IssueTrendSection({ issueId, resolvedAt }: IssueTrendSectionProps) {
  const [range, setRange] = useState<IssueTrendRange>();
  const [defaultRange, setDefaultRange] = useState<IssueTrendRange>({ from: '', to: '' });
  const [draft, setDraft] = useState<IssueTrendRange | null>(null);
  const [rangeError, setRangeError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const [state, setState] = useState<TrendState>(null);
  const requestKey = `${issueId}:${resolvedAt ?? ''}:${range?.from ?? ''}:${range?.to ?? ''}:${reload}`;
  const currentState = state?.key === requestKey ? state : null;
  const data = currentState !== null && 'data' in currentState ? currentState.data : null;
  const error = currentState !== null && 'error' in currentState ? currentState.error : null;
  const loading = currentState === null;
  const displayedRange = draft ?? range ?? defaultRange;

  useEffect(() => {
    let active = true;
    void issueTrendRequest(issueId, range)
      .then((result) => {
        if (active) {
          setDefaultRange({ from: result.from, to: result.to });
          setState({ key: requestKey, data: result });
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setState({
            key: requestKey,
            error: cause instanceof ApiError ? cause.message : '피드백 추이를 불러올 수 없습니다.',
          });
        }
      });
    return () => {
      active = false;
    };
  }, [issueId, range, requestKey]);

  function applyRange(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const message = validateTrendRange(displayedRange);
    setRangeError(message);
    if (message !== null) return;
    setRange({ ...displayedRange });
    setReload((current) => current + 1);
  }

  return (
    <section className="issue-detail-section issue-trend-section" aria-labelledby="issue-trend-title">
      <header className="issue-section-header">
        <div>
          <h2 id="issue-trend-title">피드백 추이</h2>
          <span>일별 누적 집계 · 한국 시간</span>
        </div>
        <button
          className="icon-button"
          type="button"
          onClick={() => setReload((current) => current + 1)}
          disabled={loading}
          aria-label="피드백 추이 새로고침"
          title="피드백 추이 새로고침"
        >
          <RefreshCw size={18} className={loading ? 'spin' : undefined} />
        </button>
      </header>

      <form className="issue-trend-filters" onSubmit={applyRange}>
        <label>
          <span>시작일</span>
          <input
            type="date"
            value={displayedRange.from}
            required
            aria-describedby={rangeError ? 'trend-range-error' : undefined}
            onChange={(event) => {
              setDraft({ ...displayedRange, from: event.target.value });
              setRangeError(null);
            }}
          />
        </label>
        <label>
          <span>종료일</span>
          <input
            type="date"
            value={displayedRange.to}
            required
            aria-describedby={rangeError ? 'trend-range-error' : undefined}
            onChange={(event) => {
              setDraft({ ...displayedRange, to: event.target.value });
              setRangeError(null);
            }}
          />
        </label>
        <button className="icon-button" type="submit" aria-label="추이 기간 조회" title="기간 조회">
          <Search size={18} />
        </button>
        {rangeError !== null && <p id="trend-range-error" role="alert">{rangeError}</p>}
      </form>

      {loading && (
        <div className="issue-trend-empty" role="status">집계 기록을 불러오는 중입니다.</div>
      )}
      {error !== null && (
        <div className="issue-section-error" role="alert">
          <AlertCircle size={20} aria-hidden="true" />
          <div>
            <strong>피드백 추이를 불러오지 못했습니다.</strong>
            <span>{error}</span>
          </div>
          <button className="secondary-button" type="button" onClick={() => setReload((current) => current + 1)}>
            다시 시도
          </button>
        </div>
      )}
      {data !== null && data.points.length === 0 && (
        <div className="issue-trend-empty">
          <BarChart3 size={22} aria-hidden="true" />
          <p>이 기간에 저장된 집계 기록이 없습니다.</p>
        </div>
      )}
      {data !== null && data.points.length > 0 && <TrendContent trend={data} />}
    </section>
  );
}

function TrendContent({ trend }: { trend: IssueTrend }) {
  const latest = trend.points[trend.points.length - 1];
  const resolvedDate = trend.resolvedDate ?? null;
  const comparison = resolutionComparison(trend.points, resolvedDate);
  const previous = trend.points.at(-2);

  return (
    <>
      <dl className="issue-trend-summary">
        <div>
          <dt>누적 피드백</dt>
          <dd>{formatNumber(latest.feedbackCount)}<small>건</small></dd>
          <span>최근 집계 {latest.snapshotDate}</span>
        </div>
        <div>
          <dt>부정 비율</dt>
          <dd>{negativeRateLabel(latest)}</dd>
          <span>분석 완료 {formatNumber(latest.analyzedFeedbackCount)}건 기준</span>
        </div>
        <div>
          <dt>직전 집계 대비</dt>
          <dd>{trend.feedbackGrowthRate === null ? '비교 불가' : signedValue(trend.feedbackGrowthRate, '%', 1)}</dd>
          <span>{previous ? `${previous.snapshotDate} 대비` : '비교 기록 없음'}</span>
        </div>
      </dl>

      {resolvedDate !== null && (
        <div className="issue-resolution-comparison">
          <h3>해결 전후 기록</h3>
          <p className="issue-trend-caption">해결일 {resolvedDate} · 당일 집계 제외</p>
          {comparison === null ? (
            <p className="issue-trend-unavailable">조회 기간에 해결일 전후 기록이 모두 있지 않아 비교할 수 없습니다.</p>
          ) : (
            <ResolutionComparison before={comparison.before} after={comparison.after} />
          )}
        </div>
      )}

      <div className="issue-trend-records-header">
        <h3>일별 기록</h3>
        <span>{formatNumber(trend.points.length)}일</span>
      </div>
      <DailyRecords points={trend.points} />
    </>
  );
}

function ResolutionComparison({ before, after }: { before: IssueMetricPoint; after: IssueMetricPoint }) {
  const negativeRateChange = before.analyzedFeedbackCount > 0 && after.analyzedFeedbackCount > 0
    ? signedValue(after.negativeFeedbackRate - before.negativeFeedbackRate, '%p', 1)
    : '-';

  return (
    <div className="issue-trend-table-wrap" tabIndex={0} role="region" aria-label="해결 전후 지표 비교">
      <table className="issue-trend-comparison-table">
        <caption className="sr-only">해결일 이전 마지막 집계와 해결일 이후 최신 집계</caption>
        <thead>
          <tr>
            <th scope="col">항목</th>
            <th scope="col">해결 전<small>{before.snapshotDate}</small></th>
            <th scope="col">해결 후<small>{after.snapshotDate}</small></th>
            <th scope="col">변화</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <th scope="row">누적 피드백</th>
            <td>{formatNumber(before.feedbackCount)}건</td>
            <td>{formatNumber(after.feedbackCount)}건</td>
            <td>{signedValue(after.feedbackCount - before.feedbackCount, '건')}</td>
          </tr>
          <tr>
            <th scope="row">부정 비율</th>
            <td>{negativeRateLabel(before)}</td>
            <td>{negativeRateLabel(after)}</td>
            <td>{negativeRateChange}</td>
          </tr>
          <tr>
            <th scope="row">미완료 조치</th>
            <td>{formatNumber(before.unresolvedActionCount)}건</td>
            <td>{formatNumber(after.unresolvedActionCount)}건</td>
            <td>{signedValue(after.unresolvedActionCount - before.unresolvedActionCount, '건')}</td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}

function DailyRecords({ points }: { points: IssueMetricPoint[] }) {
  const maximum = Math.max(1, ...points.map((point) => point.feedbackCount));
  return (
    <div className="issue-trend-table-wrap issue-trend-records" tabIndex={0} role="region" aria-label="일별 누적 집계 기록">
      <table className="issue-trend-records-table">
        <caption className="sr-only">집계일별 피드백 누적값, 분석 완료 건수, 부정 비율과 미완료 조치</caption>
        <thead>
          <tr>
            <th scope="col">집계일</th>
            <th scope="col">누적 피드백</th>
            <th scope="col">분석 완료</th>
            <th scope="col">부정 비율</th>
            <th scope="col">미완료 조치</th>
          </tr>
        </thead>
        <tbody>
          {[...points].reverse().map((point) => (
            <tr key={point.snapshotDate}>
              <th scope="row"><time dateTime={point.snapshotDate}>{point.snapshotDate}</time></th>
              <td>
                <div className="issue-trend-volume">
                  <span aria-hidden="true"><i style={{ width: `${point.feedbackCount / maximum * 100}%` }} /></span>
                  {formatNumber(point.feedbackCount)}
                </div>
              </td>
              <td>{formatNumber(point.analyzedFeedbackCount)}</td>
              <td>{negativeRateLabel(point)}</td>
              <td>{formatNumber(point.unresolvedActionCount)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
