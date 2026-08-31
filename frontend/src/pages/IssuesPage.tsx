import {
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  CircleDotDashed,
  RefreshCw,
  Search,
  X,
} from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';

import { formatDate, formatNumber } from '../datasets/format';
import { issuesRequest } from '../issues/api';
import {
  formatNegativeRate,
  formatPriorityScore,
  issueStatusLabel,
  issueStatusTone,
  priorityTone,
} from '../issues/format';
import { issuePriorityOptions, issueStatusOptions } from '../issues/labels';
import type { IssuePriority, IssueStatus, IssueSummary } from '../issues/types';
import type { PageResponse } from '../datasets/types';
import { ApiError } from '../lib/api-client';

const PAGE_SIZE = 20;

export function IssuesPage() {
  const [priority, setPriority] = useState<IssuePriority | ''>('');
  const [status, setStatus] = useState<IssueStatus | ''>('');
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<IssueSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [reloadSequence, setReloadSequence] = useState(0);
  const requestSequence = useRef(0);

  useEffect(() => {
    const sequence = requestSequence.current + 1;
    requestSequence.current = sequence;

    void issuesRequest({
      page,
      size: PAGE_SIZE,
      priority: priority === '' ? undefined : priority,
      status: status === '' ? undefined : status,
      keyword: keyword === '' ? undefined : keyword,
    })
      .then((response) => {
        if (requestSequence.current === sequence) {
          setResult(response);
          setErrorMessage(null);
        }
      })
      .catch((error: unknown) => {
        if (requestSequence.current === sequence) {
          setErrorMessage(
            error instanceof ApiError ? error.message : '이슈 목록을 불러올 수 없습니다.',
          );
        }
      })
      .finally(() => {
        if (requestSequence.current === sequence) {
          setLoading(false);
        }
      });
  }, [keyword, page, priority, reloadSequence, status]);

  function changePriority(value: string) {
    setLoading(true);
    setErrorMessage(null);
    setPriority(value as IssuePriority | '');
    setPage(0);
  }

  function changeStatus(value: string) {
    setLoading(true);
    setErrorMessage(null);
    setStatus(value as IssueStatus | '');
    setPage(0);
  }

  function searchIssues(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextKeyword = keywordInput.trim();
    if (nextKeyword === keyword && page === 0) return;
    setLoading(true);
    setErrorMessage(null);
    setKeyword(nextKeyword);
    setPage(0);
  }

  function clearFilters() {
    setLoading(true);
    setErrorMessage(null);
    setPriority('');
    setStatus('');
    setKeywordInput('');
    setKeyword('');
    setPage(0);
  }

  function changePage(nextPage: number) {
    setLoading(true);
    setErrorMessage(null);
    setPage(nextPage);
  }

  function refreshIssues() {
    setLoading(true);
    setErrorMessage(null);
    setReloadSequence((current) => current + 1);
  }

  const issues = result?.content ?? [];
  const filtersActive = priority !== '' || status !== '' || keyword !== '';

  return (
    <div className="page-container issues-page">
      <header className="page-header issues-page-header">
        <div>
          <h1>이슈</h1>
          <p className="page-description">고객 피드백에서 발견된 문제와 대응 상태를 확인합니다.</p>
        </div>
      </header>

      <section className="issue-workspace" aria-labelledby="issue-list-title">
        <div className="issue-toolbar">
          <div className="issue-toolbar-copy">
            <h2 id="issue-list-title">이슈 목록</h2>
            <span>{result === null ? '조회 중' : `총 ${formatNumber(result.totalElements)}건`}</span>
          </div>
          <form className="issue-filters" aria-label="이슈 필터" onSubmit={searchIssues}>
            <label className="issue-search-field">
              <span className="sr-only">이슈 제목 검색</span>
              <Search size={15} aria-hidden="true" />
              <input
                type="search"
                value={keywordInput}
                onChange={(event) => setKeywordInput(event.target.value)}
                placeholder="제목 검색"
              />
            </label>
            <button className="icon-button issue-search-button" type="submit" aria-label="검색" title="검색">
              <Search size={18} />
            </button>
            <label>
              <span className="sr-only">우선순위</span>
              <select value={priority} onChange={(event) => changePriority(event.target.value)}>
                <option value="">모든 우선순위</option>
                {issuePriorityOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span className="sr-only">이슈 상태</span>
              <select value={status} onChange={(event) => changeStatus(event.target.value)}>
                <option value="">모든 상태</option>
                {issueStatusOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <button
              className="icon-button issue-clear-button"
              type="button"
              onClick={clearFilters}
              disabled={!filtersActive}
              aria-label="필터 초기화"
              title="필터 초기화"
            >
              <X size={18} />
            </button>
            <button
              className="icon-button toolbar-refresh"
              type="button"
              onClick={refreshIssues}
              disabled={loading}
              aria-label="새로고침"
              title="새로고침"
            >
              <RefreshCw className={loading ? 'spin' : undefined} size={18} />
            </button>
          </form>
        </div>

        {errorMessage !== null && (
          <div className="dataset-error" role="alert">
            <AlertCircle size={20} aria-hidden="true" />
            <div>
              <strong>이슈를 불러오지 못했습니다.</strong>
              <span>{errorMessage}</span>
            </div>
            <button className="secondary-button" type="button" onClick={refreshIssues}>
              다시 시도
            </button>
          </div>
        )}

        {errorMessage === null && loading && result === null && <IssueTableLoading />}

        {errorMessage === null && !loading && issues.length === 0 && (
          <div className="dataset-empty">
            <CircleDotDashed size={29} aria-hidden="true" />
            <h3>조건에 맞는 이슈가 없습니다.</h3>
            <p>검색어나 필터를 변경해 다시 확인해 주세요.</p>
          </div>
        )}

        {errorMessage === null && issues.length > 0 && (
          <IssueTable issues={issues} refreshing={loading} />
        )}

        {result !== null && result.totalPages > 0 && (
          <footer className="dataset-pagination">
            <span>
              {result.page + 1} / {result.totalPages} 페이지
            </span>
            <div>
              <button
                className="icon-button"
                type="button"
                onClick={() => changePage(Math.max(0, result.page - 1))}
                disabled={loading || result.page === 0}
                aria-label="이전 페이지"
                title="이전 페이지"
              >
                <ChevronLeft size={19} />
              </button>
              <button
                className="icon-button"
                type="button"
                onClick={() => changePage(result.page + 1)}
                disabled={loading || result.page + 1 >= result.totalPages}
                aria-label="다음 페이지"
                title="다음 페이지"
              >
                <ChevronRight size={19} />
              </button>
            </div>
          </footer>
        )}
      </section>
    </div>
  );
}

function IssueTable({ issues, refreshing }: { issues: IssueSummary[]; refreshing: boolean }) {
  return (
    <div className={`issue-table-wrap${refreshing ? ' is-refreshing' : ''}`}>
      <table className="issue-table">
        <caption className="sr-only">조직 이슈 목록</caption>
        <thead>
          <tr>
            <th scope="col">우선순위</th>
            <th scope="col">이슈</th>
            <th scope="col">상태</th>
            <th scope="col">피드백</th>
            <th scope="col">부정 비율</th>
            <th scope="col">담당자</th>
            <th scope="col">최근 감지</th>
          </tr>
        </thead>
        <tbody>
          {issues.map((issue) => (
            <tr key={issue.id}>
              <td data-label="우선순위">
                <div className="issue-priority-cell">
                  <span className={`priority-badge priority-badge--${priorityTone(issue.priority)}`}>
                    {issue.priority}
                  </span>
                  <strong>{formatPriorityScore(issue.priorityScore)}</strong>
                </div>
              </td>
              <td data-label="이슈">
                <div className="issue-title-cell">
                  <Link to={`/issues/${issue.id}`}>{issue.title}</Link>
                  <span>{issue.category}</span>
                </div>
              </td>
              <td data-label="상태">
                <span className={`issue-status issue-status--${issueStatusTone(issue.status)}`}>
                  {issueStatusLabel(issue.status)}
                </span>
              </td>
              <td data-label="피드백">{formatNumber(issue.feedbackCount)}</td>
              <td data-label="부정 비율">
                <div className="issue-negative-cell">
                  <strong>{formatNegativeRate(issue.negativeCount, issue.feedbackCount)}</strong>
                  <span>{formatNumber(issue.negativeCount)}건</span>
                </div>
              </td>
              <td data-label="담당자">{issue.assigneeName ?? '미지정'}</td>
              <td data-label="최근 감지" className="issue-date-cell">
                {issue.lastSeenAt === null ? '-' : formatDate(issue.lastSeenAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function IssueTableLoading() {
  return (
    <div className="issue-loading" aria-label="이슈 목록 로딩 중">
      {Array.from({ length: 5 }, (_, index) => (
        <div key={index}>
          <span />
          <span />
          <span />
        </div>
      ))}
    </div>
  );
}
