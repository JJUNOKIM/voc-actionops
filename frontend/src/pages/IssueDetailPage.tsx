import {
  AlertCircle,
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  MessagesSquare,
  RefreshCw,
} from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';

import { formatDate, formatNumber } from '../datasets/format';
import type { PageResponse } from '../datasets/types';
import { sourceTypeLabel } from '../datasets/labels';
import { feedbackDisplayId, formatRating, formatScore } from '../feedbacks/format';
import { issueDetailRequest, issueFeedbacksRequest } from '../issues/api';
import {
  actionStatusLabel,
  actionStatusTone,
  formatNegativeRate,
  formatPriorityScore,
  issueStatusLabel,
  issueStatusTone,
  priorityTone,
} from '../issues/format';
import type { IssueAction, IssueDetail, IssueFeedback } from '../issues/types';
import { ApiError } from '../lib/api-client';

const RELATED_FEEDBACK_PAGE_SIZE = 10;

type DetailState =
  | { issueId: number; status: 'success'; data: IssueDetail }
  | { issueId: number; status: 'error'; message: string }
  | null;

type FeedbackState =
  | {
      issueId: number;
      page: number;
      status: 'success';
      data: PageResponse<IssueFeedback>;
    }
  | { issueId: number; page: number; status: 'error'; message: string }
  | null;

export function IssueDetailPage() {
  const { issueId: issueIdParam } = useParams();
  const issueId = parseIssueId(issueIdParam);
  const [detailState, setDetailState] = useState<DetailState>(null);
  const [feedbackState, setFeedbackState] = useState<FeedbackState>(null);
  const [feedbackPagination, setFeedbackPagination] = useState({ issueId: null as number | null, page: 0 });
  const [detailReloadSequence, setDetailReloadSequence] = useState(0);
  const [feedbackReloadSequence, setFeedbackReloadSequence] = useState(0);
  const detailRequestSequence = useRef(0);
  const feedbackRequestSequence = useRef(0);
  const feedbackPage = feedbackPagination.issueId === issueId ? feedbackPagination.page : 0;

  useEffect(() => {
    if (issueId === null) return;
    const sequence = detailRequestSequence.current + 1;
    detailRequestSequence.current = sequence;

    void issueDetailRequest(issueId)
      .then((data) => {
        if (detailRequestSequence.current === sequence) {
          setDetailState({ issueId, status: 'success', data });
        }
      })
      .catch((error: unknown) => {
        if (detailRequestSequence.current === sequence) {
          setDetailState({
            issueId,
            status: 'error',
            message: error instanceof ApiError ? error.message : '이슈를 불러올 수 없습니다.',
          });
        }
      });
  }, [detailReloadSequence, issueId]);

  useEffect(() => {
    if (issueId === null) return;
    const sequence = feedbackRequestSequence.current + 1;
    feedbackRequestSequence.current = sequence;

    void issueFeedbacksRequest(
      issueId,
      feedbackPage,
      RELATED_FEEDBACK_PAGE_SIZE,
      false,
    )
      .then((data) => {
        if (feedbackRequestSequence.current === sequence) {
          setFeedbackState({ issueId, page: feedbackPage, status: 'success', data });
        }
      })
      .catch((error: unknown) => {
        if (feedbackRequestSequence.current === sequence) {
          setFeedbackState({
            issueId,
            page: feedbackPage,
            status: 'error',
            message:
              error instanceof ApiError
                ? error.message
                : '연결된 피드백을 불러올 수 없습니다.',
          });
        }
      });
  }, [feedbackPage, feedbackReloadSequence, issueId]);

  function retryDetail() {
    setDetailState(null);
    setDetailReloadSequence((current) => current + 1);
  }

  function refreshFeedbacks() {
    setFeedbackState(null);
    setFeedbackReloadSequence((current) => current + 1);
  }

  function changeFeedbackPage(nextPage: number) {
    setFeedbackState(null);
    setFeedbackPagination({ issueId, page: nextPage });
  }

  if (issueId === null) {
    return (
      <IssueDetailFailure
        title="잘못된 이슈 주소입니다."
        description="이슈 목록에서 다시 선택해 주세요."
      />
    );
  }

  const currentDetailState = detailState?.issueId === issueId ? detailState : null;
  if (currentDetailState === null) return <IssueDetailLoading />;
  if (currentDetailState.status === 'error') {
    return (
      <IssueDetailFailure
        title="이슈를 불러오지 못했습니다."
        description={currentDetailState.message}
        onRetry={retryDetail}
      />
    );
  }

  const issue = currentDetailState.data;
  const unresolvedActionCount = issue.actions.filter(
    (action) => action.status === 'TODO' || action.status === 'IN_PROGRESS',
  ).length;
  const currentFeedbackState =
    feedbackState?.issueId === issueId && feedbackState.page === feedbackPage
      ? feedbackState
      : null;

  return (
    <div className="page-container dataset-detail-page issue-detail-page">
      <Link className="back-link" to="/issues">
        <ArrowLeft size={17} aria-hidden="true" />
        <span>이슈 목록</span>
      </Link>

      <header className="page-header issue-detail-header">
        <div>
          <h1>{issue.title}</h1>
          <p className="page-description">{issue.category}</p>
        </div>
        <div className="issue-detail-badges">
          <span className={`priority-badge priority-badge--${priorityTone(issue.priority)}`}>
            {issue.priority}
          </span>
          <span className={`issue-status issue-status--${issueStatusTone(issue.status)}`}>
            {issueStatusLabel(issue.status)}
          </span>
        </div>
      </header>

      <section className="issue-metric-strip" aria-label="이슈 요약">
        <MetricItem label="우선순위 점수" value={formatPriorityScore(issue.priorityScore)} />
        <MetricItem label="연결 피드백" value={formatNumber(issue.feedbackCount)} unit="건" />
        <MetricItem
          label="부정 피드백"
          value={formatNegativeRate(issue.negativeCount, issue.feedbackCount)}
          note={`${formatNumber(issue.negativeCount)}건`}
        />
        <MetricItem label="미완료 조치" value={formatNumber(unresolvedActionCount)} unit="건" />
      </section>

      <section className="issue-overview-section" aria-labelledby="issue-overview-title">
        <div className="issue-description-block">
          <h2 id="issue-overview-title">이슈 설명</h2>
          <p>{issue.description}</p>
        </div>
        <dl className="issue-metadata-list">
          <MetadataItem label="담당자" value={issue.assigneeName ?? '미지정'} />
          <MetadataItem label="최초 감지" value={formatNullableDate(issue.firstSeenAt)} />
          <MetadataItem label="최근 감지" value={formatNullableDate(issue.lastSeenAt)} />
          <MetadataItem label="최종 수정" value={formatDate(issue.updatedAt)} />
        </dl>
      </section>

      <section className="issue-detail-section" aria-labelledby="issue-actions-title">
        <header className="issue-section-header">
          <div>
            <h2 id="issue-actions-title">조치</h2>
            <span>문제 해결을 위해 등록된 작업</span>
          </div>
          <strong>{formatNumber(issue.actions.length)}건</strong>
        </header>
        {issue.actions.length === 0 ? (
          <IssueSectionEmpty
            icon={<ClipboardCheck size={24} aria-hidden="true" />}
            message="등록된 조치가 없습니다."
          />
        ) : (
          <ActionTable actions={issue.actions} />
        )}
      </section>

      <section className="issue-detail-section" aria-labelledby="issue-feedback-title">
        <header className="issue-section-header">
          <div>
            <h2 id="issue-feedback-title">연결된 피드백</h2>
            <span>이 이슈를 구성하는 고객 의견</span>
          </div>
          <div className="issue-section-actions">
            {currentFeedbackState?.status === 'success' && (
              <strong>{formatNumber(currentFeedbackState.data.totalElements)}건</strong>
            )}
            <button
              className="icon-button"
              type="button"
              onClick={refreshFeedbacks}
              disabled={currentFeedbackState === null}
              aria-label="연결된 피드백 새로고침"
              title="연결된 피드백 새로고침"
            >
              <RefreshCw className={currentFeedbackState === null ? 'spin' : undefined} size={18} />
            </button>
          </div>
        </header>

        {currentFeedbackState === null && <IssueFeedbackLoading />}

        {currentFeedbackState?.status === 'error' && (
          <div className="issue-section-error" role="alert">
            <AlertCircle size={20} aria-hidden="true" />
            <div>
              <strong>연결된 피드백을 불러오지 못했습니다.</strong>
              <span>{currentFeedbackState.message}</span>
            </div>
            <button className="secondary-button" type="button" onClick={refreshFeedbacks}>
              다시 시도
            </button>
          </div>
        )}

        {currentFeedbackState?.status === 'success' &&
          currentFeedbackState.data.content.length === 0 && (
            <IssueSectionEmpty
              icon={<MessagesSquare size={24} aria-hidden="true" />}
              message="연결된 피드백이 없습니다."
            />
          )}

        {currentFeedbackState?.status === 'success' &&
          currentFeedbackState.data.content.length > 0 && (
            <RelatedFeedbackList feedbacks={currentFeedbackState.data.content} />
          )}

        {currentFeedbackState?.status === 'success' &&
          currentFeedbackState.data.totalPages > 1 && (
            <footer className="dataset-pagination">
              <span>
                {currentFeedbackState.data.page + 1} / {currentFeedbackState.data.totalPages} 페이지
              </span>
              <div>
                <button
                  className="icon-button"
                  type="button"
                  onClick={() =>
                    changeFeedbackPage(Math.max(0, currentFeedbackState.data.page - 1))
                  }
                  disabled={currentFeedbackState.data.page === 0}
                  aria-label="이전 피드백 페이지"
                  title="이전 피드백 페이지"
                >
                  <ChevronLeft size={19} />
                </button>
                <button
                  className="icon-button"
                  type="button"
                  onClick={() => changeFeedbackPage(currentFeedbackState.data.page + 1)}
                  disabled={
                    currentFeedbackState.data.page + 1 >= currentFeedbackState.data.totalPages
                  }
                  aria-label="다음 피드백 페이지"
                  title="다음 피드백 페이지"
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

function MetricItem({
  label,
  value,
  unit,
  note,
}: {
  label: string;
  value: string;
  unit?: string;
  note?: string;
}) {
  return (
    <div>
      <span>{label}</span>
      <div>
        <strong>{value}</strong>
        {unit !== undefined && <small>{unit}</small>}
      </div>
      {note !== undefined && <small>{note}</small>}
    </div>
  );
}

function MetadataItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function ActionTable({ actions }: { actions: IssueAction[] }) {
  return (
    <div className="issue-action-table-wrap">
      <table className="issue-action-table">
        <caption className="sr-only">이슈 조치 목록</caption>
        <thead>
          <tr>
            <th scope="col">상태</th>
            <th scope="col">조치</th>
            <th scope="col">담당자</th>
            <th scope="col">마감일</th>
          </tr>
        </thead>
        <tbody>
          {actions.map((action) => (
            <tr key={action.id}>
              <td data-label="상태">
                <span className={`action-status action-status--${actionStatusTone(action.status)}`}>
                  {actionStatusLabel(action.status)}
                </span>
              </td>
              <td data-label="조치">
                <div className="issue-action-copy">
                  <strong>{action.title}</strong>
                  {action.description !== null && <span>{action.description}</span>}
                </div>
              </td>
              <td data-label="담당자">{action.assigneeName ?? '미지정'}</td>
              <td data-label="마감일">{formatDateOnly(action.dueDate)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function RelatedFeedbackList({ feedbacks }: { feedbacks: IssueFeedback[] }) {
  return (
    <ol className="issue-feedback-list">
      {feedbacks.map((feedback) => (
        <li key={feedback.id}>
          <div className="issue-feedback-main">
            <div className="issue-feedback-heading">
              <Link to={`/feedbacks/${feedback.feedbackId}`}>
                {feedbackDisplayId(feedback.externalId, feedback.feedbackId)}
              </Link>
              {feedback.representative && <span>대표 피드백</span>}
            </div>
            <p>{feedback.content}</p>
          </div>
          <dl className="issue-feedback-meta">
            <MetadataItem label="출처" value={sourceTypeLabel(feedback.sourceType)} />
            <MetadataItem label="평점" value={formatRating(feedback.rating)} />
            <MetadataItem
              label="유사도"
              value={feedback.similarityScore === null ? '-' : formatScore(feedback.similarityScore)}
            />
            <MetadataItem label="연결 방식" value={feedback.linkedBy === 'AI' ? 'AI 분석' : '직접 연결'} />
            <MetadataItem label="작성 시각" value={formatNullableDate(feedback.feedbackCreatedAt)} />
            <div>
              <dt>데이터셋</dt>
              <dd>
                <Link to={`/datasets/${feedback.datasetId}`}>#{feedback.datasetId}</Link>
              </dd>
            </div>
          </dl>
        </li>
      ))}
    </ol>
  );
}

function IssueSectionEmpty({ icon, message }: { icon: ReactNode; message: string }) {
  return (
    <div className="issue-section-empty">
      {icon}
      <span>{message}</span>
    </div>
  );
}

function IssueFeedbackLoading() {
  return (
    <div className="issue-feedback-loading" aria-label="연결된 피드백 로딩 중">
      {Array.from({ length: 3 }, (_, index) => (
        <div key={index}>
          <span />
          <span />
        </div>
      ))}
    </div>
  );
}

function IssueDetailLoading() {
  return (
    <div className="page-container dataset-detail-page" aria-label="이슈 상세 로딩 중">
      <div className="detail-loading detail-loading--back" />
      <div className="detail-loading detail-loading--title" />
      <div className="detail-loading detail-loading--counts" />
      <div className="detail-loading detail-loading--section" />
      <div className="detail-loading detail-loading--section" />
    </div>
  );
}

function IssueDetailFailure({
  title,
  description,
  onRetry,
}: {
  title: string;
  description: string;
  onRetry?: () => void;
}) {
  return (
    <div className="page-container dataset-detail-page">
      <Link className="back-link" to="/issues">
        <ArrowLeft size={17} aria-hidden="true" />
        <span>이슈 목록</span>
      </Link>
      <div className="detail-failure">
        <AlertCircle size={28} aria-hidden="true" />
        <h1>{title}</h1>
        <p>{description}</p>
        {onRetry !== undefined && (
          <button className="secondary-button" type="button" onClick={onRetry}>
            다시 시도
          </button>
        )}
      </div>
    </div>
  );
}

function parseIssueId(value: string | undefined): number | null {
  if (value === undefined) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

function formatNullableDate(value: string | null): string {
  return value === null ? '-' : formatDate(value);
}

function formatDateOnly(value: string | null): string {
  if (value === null) return '-';
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date);
}
