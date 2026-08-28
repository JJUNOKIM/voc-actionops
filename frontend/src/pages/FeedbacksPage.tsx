import {
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  ListFilter,
  MessagesSquare,
  RefreshCw,
  X,
} from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { formatDate, formatNumber } from '../datasets/format';
import { sourceTypeLabel, sourceTypeOptions } from '../datasets/labels';
import type { PageResponse, SourceType } from '../datasets/types';
import { feedbacksRequest } from '../feedbacks/api';
import { feedbackDisplayId, formatScore } from '../feedbacks/format';
import {
  feedbackAnalysisStatusLabel,
  feedbackAnalysisStatusTone,
  sentimentLabel,
  sentimentTone,
} from '../feedbacks/labels';
import type { FeedbackAnalysisSummary, FeedbackListItem } from '../feedbacks/types';
import { ApiError } from '../lib/api-client';

const PAGE_SIZE = 20;

export function FeedbacksPage() {
  const [searchParams] = useSearchParams();
  const datasetId = parseDatasetFilter(searchParams.get('datasetId'));
  const [sourceType, setSourceType] = useState<SourceType | ''>('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<FeedbackListItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [reloadSequence, setReloadSequence] = useState(0);
  const requestSequence = useRef(0);

  useEffect(() => {
    const sequence = requestSequence.current + 1;
    requestSequence.current = sequence;

    void feedbacksRequest({
      page,
      size: PAGE_SIZE,
      datasetId: datasetId ?? undefined,
      sourceType: sourceType === '' ? undefined : sourceType,
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
            error instanceof ApiError ? error.message : '피드백 목록을 불러올 수 없습니다.',
          );
        }
      })
      .finally(() => {
        if (requestSequence.current === sequence) {
          setLoading(false);
        }
      });
  }, [datasetId, page, reloadSequence, sourceType]);

  function changeSourceType(value: string) {
    setLoading(true);
    setErrorMessage(null);
    setSourceType(value as SourceType | '');
    setPage(0);
  }

  function changePage(nextPage: number) {
    setLoading(true);
    setErrorMessage(null);
    setPage(nextPage);
  }

  function refreshFeedbacks() {
    setLoading(true);
    setErrorMessage(null);
    setReloadSequence((current) => current + 1);
  }

  const feedbacks = result?.content ?? [];
  const scopedDatasetName = datasetId === null ? null : feedbacks[0]?.datasetName;

  return (
    <div className="page-container feedbacks-page">
      <header className="page-header datasets-page-header">
        <div>
          <p className="section-label">VOC WORKSPACE</p>
          <h1>피드백</h1>
          <p className="page-description">고객 원문과 AI 분석 결과를 함께 검토합니다.</p>
        </div>
      </header>

      {datasetId !== null && (
        <div className="feedback-scope" role="status">
          <ListFilter size={18} aria-hidden="true" />
          <div>
            <span>데이터셋 범위</span>
            <strong>{scopedDatasetName ?? `데이터셋 #${datasetId}`}</strong>
          </div>
          <Link className="icon-button" to="/feedbacks" aria-label="데이터셋 필터 해제" title="데이터셋 필터 해제">
            <X size={18} />
          </Link>
        </div>
      )}

      <section className="dataset-workspace feedback-workspace" aria-labelledby="feedback-list-title">
        <div className="dataset-toolbar">
          <div className="dataset-toolbar-copy">
            <h2 id="feedback-list-title">VOC 목록</h2>
            <span>{result === null ? '조회 중' : `총 ${formatNumber(result.totalElements)}건`}</span>
          </div>
          <div className="dataset-filters feedback-filters" aria-label="피드백 필터">
            <label>
              <span className="sr-only">데이터 출처</span>
              <select value={sourceType} onChange={(event) => changeSourceType(event.target.value)}>
                <option value="">모든 출처</option>
                {sourceTypeOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <button
              className="icon-button toolbar-refresh"
              type="button"
              onClick={refreshFeedbacks}
              disabled={loading}
              aria-label="새로고침"
              title="새로고침"
            >
              <RefreshCw className={loading ? 'spin' : undefined} size={18} />
            </button>
          </div>
        </div>

        {errorMessage !== null && (
          <div className="dataset-error" role="alert">
            <AlertCircle size={20} aria-hidden="true" />
            <div>
              <strong>피드백을 불러오지 못했습니다.</strong>
              <span>{errorMessage}</span>
            </div>
            <button className="secondary-button" type="button" onClick={refreshFeedbacks}>
              다시 시도
            </button>
          </div>
        )}

        {errorMessage === null && loading && result === null && <FeedbackTableLoading />}

        {errorMessage === null && !loading && feedbacks.length === 0 && (
          <div className="dataset-empty">
            <MessagesSquare size={29} aria-hidden="true" />
            <h3>조건에 맞는 피드백이 없습니다.</h3>
            <p>데이터셋 또는 출처 필터를 변경해 다시 확인해 주세요.</p>
          </div>
        )}

        {errorMessage === null && feedbacks.length > 0 && (
          <FeedbackTable feedbacks={feedbacks} refreshing={loading} />
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

function FeedbackTable({
  feedbacks,
  refreshing,
}: {
  feedbacks: FeedbackListItem[];
  refreshing: boolean;
}) {
  return (
    <div className={`feedback-table-wrap${refreshing ? ' is-refreshing' : ''}`}>
      <table className="feedback-table">
        <caption className="sr-only">조직 피드백 목록</caption>
        <thead>
          <tr>
            <th scope="col">고객 피드백</th>
            <th scope="col">데이터셋</th>
            <th scope="col">AI 분류</th>
            <th scope="col">긴급도</th>
            <th scope="col">신뢰도</th>
            <th scope="col">작성 시각</th>
          </tr>
        </thead>
        <tbody>
          {feedbacks.map((feedback) => (
            <tr key={feedback.id}>
              <td data-label="고객 피드백">
                <div className="feedback-content-cell">
                  <Link to={`/feedbacks/${feedback.id}`}>
                    {feedbackDisplayId(feedback.externalId, feedback.id)}
                  </Link>
                  <span>{feedback.content}</span>
                </div>
              </td>
              <td data-label="데이터셋">
                <div className="feedback-dataset-cell">
                  <Link to={`/datasets/${feedback.datasetId}`}>{feedback.datasetName}</Link>
                  <span>{sourceTypeLabel(feedback.sourceType)}</span>
                </div>
              </td>
              <td data-label="AI 분류">
                <FeedbackAnalysisCell analysis={feedback.analysis} />
              </td>
              <td data-label="긴급도">{formatScore(feedback.analysis?.urgencyScore ?? null)}</td>
              <td data-label="신뢰도">{formatScore(feedback.analysis?.confidenceScore ?? null)}</td>
              <td data-label="작성 시각" className="feedback-date-cell">
                {formatDate(feedback.feedbackCreatedAt ?? feedback.ingestedAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function FeedbackAnalysisCell({ analysis }: { analysis: FeedbackAnalysisSummary | null }) {
  if (analysis === null) {
    return <span className="status-badge status-badge--ready">미분석</span>;
  }
  if (analysis.status !== 'SUCCESS' || analysis.sentiment === null) {
    return (
      <span className={`status-badge status-badge--${feedbackAnalysisStatusTone(analysis.status)}`}>
        {feedbackAnalysisStatusLabel(analysis.status)}
      </span>
    );
  }
  return (
    <div className="feedback-analysis-cell">
      <span className={`sentiment-badge sentiment-badge--${sentimentTone(analysis.sentiment)}`}>
        {sentimentLabel(analysis.sentiment)}
      </span>
      <span>{analysis.category ?? '미분류'}</span>
    </div>
  );
}

function FeedbackTableLoading() {
  return (
    <div className="feedback-loading" aria-label="피드백 목록 로딩 중">
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

function parseDatasetFilter(value: string | null): number | null {
  if (value === null) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}
