import { AlertCircle, BarChart3, RefreshCw } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { useAuth } from '../auth/useAuth';
import { dashboardOverviewRequest } from '../dashboard/api';
import {
  formatDashboardPercent,
  formatPriorityScore,
  formatResolutionHours,
  issueStatusLabel,
  priorityTone,
} from '../dashboard/format';
import type {
  CategoryBreakdownItem,
  DashboardOverview,
  DashboardSummary,
  TopIssue,
} from '../dashboard/types';
import { formatNumber } from '../datasets/format';
import { ApiError } from '../lib/api-client';

export function OverviewPage() {
  const { user } = useAuth();
  const [result, setResult] = useState<DashboardOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [reloadSequence, setReloadSequence] = useState(0);
  const requestSequence = useRef(0);

  useEffect(() => {
    const sequence = requestSequence.current + 1;
    requestSequence.current = sequence;

    void dashboardOverviewRequest()
      .then((response) => {
        if (requestSequence.current === sequence) {
          setResult(response);
          setErrorMessage(null);
        }
      })
      .catch((error: unknown) => {
        if (requestSequence.current === sequence) {
          setErrorMessage(
            error instanceof ApiError ? error.message : '운영 현황을 불러올 수 없습니다.',
          );
        }
      })
      .finally(() => {
        if (requestSequence.current === sequence) {
          setLoading(false);
        }
      });
  }, [reloadSequence]);

  if (user === null) return null;

  function refreshDashboard() {
    setLoading(true);
    setErrorMessage(null);
    setReloadSequence((current) => current + 1);
  }

  return (
    <div className="page-container dashboard-page">
      <header className="page-header dashboard-page-header">
        <div>
          <h1>운영 개요</h1>
          <p className="page-description">{user.organizationName}</p>
        </div>
        <div className="dashboard-header-actions">
          <span>전체 기간</span>
          <button
            className="icon-button"
            type="button"
            onClick={refreshDashboard}
            disabled={loading}
            aria-label="운영 현황 새로고침"
            title="운영 현황 새로고침"
          >
            <RefreshCw className={loading ? 'spin' : undefined} size={18} />
          </button>
        </div>
      </header>

      {errorMessage !== null && (
        <div className="dashboard-error" role="alert">
          <AlertCircle size={19} aria-hidden="true" />
          <div>
            <strong>운영 현황을 불러오지 못했습니다.</strong>
            <span>{errorMessage}</span>
          </div>
          <button className="secondary-button" type="button" onClick={refreshDashboard}>
            다시 시도
          </button>
        </div>
      )}

      {result === null && loading && <DashboardLoading />}

      {result !== null && (
        <div className={loading ? 'dashboard-content is-refreshing' : 'dashboard-content'}>
          <SummaryMetrics summary={result.summary} />
          <div className="dashboard-grid">
            <TopIssuesSection issues={result.topIssues} />
            <CategoryBreakdown categories={result.categories} />
          </div>
        </div>
      )}
    </div>
  );
}

function SummaryMetrics({ summary }: { summary: DashboardSummary }) {
  const metrics = [
    { label: '전체 피드백', value: formatNumber(summary.totalFeedbackCount), note: '누적 접수' },
    {
      label: '부정 피드백',
      value: formatDashboardPercent(summary.negativeFeedbackRate),
      note: '분석 완료 기준',
    },
    { label: '신규 이슈', value: formatNumber(summary.newIssueCount), note: '전체 기간 생성' },
    {
      label: '긴급 이슈',
      value: `${formatNumber(summary.p0IssueCount)} / ${formatNumber(summary.p1IssueCount)}`,
      note: 'P0 / P1',
    },
    {
      label: '미해결 이슈',
      value: formatNumber(summary.unresolvedIssueCount),
      note: '현재 활성 상태',
    },
    {
      label: '평균 처리 시간',
      value: formatResolutionHours(summary.averageResolutionHours),
      note: '해결 이슈 기준',
    },
  ];

  return (
    <section className="dashboard-summary" aria-labelledby="summary-heading">
      <h2 id="summary-heading" className="sr-only">
        VOC 운영 요약
      </h2>
      <dl>
        {metrics.map((metric) => (
          <div key={metric.label}>
            <dt>{metric.label}</dt>
            <dd>{metric.value}</dd>
            <span>{metric.note}</span>
          </div>
        ))}
      </dl>
    </section>
  );
}

function TopIssuesSection({ issues }: { issues: TopIssue[] }) {
  return (
    <section className="dashboard-section priority-issues" aria-labelledby="priority-issues-heading">
      <header className="dashboard-section-header">
        <div>
          <h2 id="priority-issues-heading">우선 처리 이슈</h2>
          <span>우선순위 점수 기준</span>
        </div>
        <strong>{formatNumber(issues.length)}건</strong>
      </header>

      {issues.length === 0 ? (
        <DashboardEmpty title="처리할 활성 이슈가 없습니다." />
      ) : (
        <div className="dashboard-table-wrap">
          <table className="dashboard-issues-table">
            <caption className="sr-only">우선 처리 이슈 목록</caption>
            <thead>
              <tr>
                <th scope="col">우선순위</th>
                <th scope="col">이슈</th>
                <th scope="col">상태</th>
                <th scope="col">피드백</th>
                <th scope="col">미완료 액션</th>
                <th scope="col">담당자</th>
              </tr>
            </thead>
            <tbody>
              {issues.map((issue) => (
                <tr key={issue.issueId}>
                  <td data-label="우선순위">
                    <div className="dashboard-priority-cell">
                      <span
                        className={`priority-badge priority-badge--${priorityTone(issue.priority)}`}
                      >
                        {issue.priority}
                      </span>
                      <strong>{formatPriorityScore(issue.priorityScore)}</strong>
                    </div>
                  </td>
                  <td data-label="이슈">
                    <div className="dashboard-issue-copy">
                      <strong>{issue.title}</strong>
                      <span>{issue.category}</span>
                    </div>
                  </td>
                  <td data-label="상태">
                    <span className={`issue-status issue-status--${issue.status.toLowerCase()}`}>
                      {issueStatusLabel(issue.status)}
                    </span>
                  </td>
                  <td data-label="피드백">{formatNumber(issue.feedbackCount)}</td>
                  <td data-label="미완료 액션">{formatNumber(issue.unresolvedActionCount)}</td>
                  <td data-label="담당자">{issue.assigneeName ?? '미지정'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function CategoryBreakdown({ categories }: { categories: CategoryBreakdownItem[] }) {
  const visibleCategories = categories.slice(0, 6);
  const maxFeedbackCount = Math.max(1, ...visibleCategories.map((item) => item.feedbackCount));

  return (
    <section className="dashboard-section category-breakdown" aria-labelledby="category-heading">
      <header className="dashboard-section-header">
        <div>
          <h2 id="category-heading">카테고리 현황</h2>
          <span>피드백 수 기준</span>
        </div>
      </header>

      {visibleCategories.length === 0 ? (
        <DashboardEmpty title="집계된 카테고리가 없습니다." />
      ) : (
        <ol className="category-list">
          {visibleCategories.map((item) => (
            <li key={item.category}>
              <div className="category-row-heading">
                <strong>{item.category}</strong>
                <span>{formatNumber(item.feedbackCount)}건</span>
              </div>
              <div className="category-volume-track" aria-hidden="true">
                <span style={{ width: `${(item.feedbackCount / maxFeedbackCount) * 100}%` }} />
              </div>
              <div className="category-row-meta">
                <span>활성 이슈 {formatNumber(item.issueCount)}</span>
                <span>부정 {formatDashboardPercent(item.negativeFeedbackRate)}</span>
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

function DashboardEmpty({ title }: { title: string }) {
  return (
    <div className="dashboard-empty">
      <BarChart3 size={24} aria-hidden="true" />
      <span>{title}</span>
    </div>
  );
}

function DashboardLoading() {
  return (
    <div className="dashboard-loading" aria-label="운영 현황 불러오는 중">
      <div className="dashboard-loading-metrics">
        {Array.from({ length: 6 }, (_, index) => (
          <span key={index} />
        ))}
      </div>
      <div className="dashboard-loading-panels">
        <span />
        <span />
      </div>
    </div>
  );
}
