import {
  AlertCircle,
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  MessagesSquare,
  Plus,
  RefreshCw,
} from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';

import { useAuth } from '../auth/useAuth';
import { formatDate, formatNumber } from '../datasets/format';
import type { PageResponse } from '../datasets/types';
import { sourceTypeLabel } from '../datasets/labels';
import { feedbackDisplayId, formatRating, formatScore } from '../feedbacks/format';
import { ActionCreateDialog } from '../issues/ActionCreateDialog';
import {
  changeActionStatusRequest,
  issueDetailRequest,
  issueFeedbacksRequest,
} from '../issues/api';
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
import { IssueManagementPanel } from '../issues/IssueManagementPanel';
import {
  actionTransitionLabel,
  canChangeActionStatus,
  canManageIssue,
  nextActionStatuses,
} from '../issues/workflow';
import { ApiError } from '../lib/api-client';
import type { OrganizationUser, UserProfile } from '../types/api';
import { organizationUsersRequest } from '../users/api';

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
  const { user } = useAuth();
  const issueId = parseIssueId(issueIdParam);
  const [detailState, setDetailState] = useState<DetailState>(null);
  const [feedbackState, setFeedbackState] = useState<FeedbackState>(null);
  const [feedbackPagination, setFeedbackPagination] = useState({ issueId: null as number | null, page: 0 });
  const [detailReloadSequence, setDetailReloadSequence] = useState(0);
  const [feedbackReloadSequence, setFeedbackReloadSequence] = useState(0);
  const [users, setUsers] = useState<OrganizationUser[] | null>(null);
  const [usersLoading, setUsersLoading] = useState(true);
  const [usersError, setUsersError] = useState<string | null>(null);
  const [usersReloadSequence, setUsersReloadSequence] = useState(0);
  const [actionDialogOpen, setActionDialogOpen] = useState(false);
  const [changingActionId, setChangingActionId] = useState<number | null>(null);
  const [actionNotice, setActionNotice] = useState<{
    tone: 'success' | 'error';
    message: string;
  } | null>(null);
  const detailRequestSequence = useRef(0);
  const feedbackRequestSequence = useRef(0);
  const feedbackPage = feedbackPagination.issueId === issueId ? feedbackPagination.page : 0;
  const canLoadUsers = user !== null && canManageIssue(user);

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

  useEffect(() => {
    if (!canLoadUsers) return;
    let active = true;

    void organizationUsersRequest()
      .then((organizationUsers) => {
        if (active) {
          setUsers(organizationUsers);
          setUsersError(null);
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setUsersError(
            error instanceof ApiError ? error.message : '조직 구성원을 불러올 수 없습니다.',
          );
        }
      })
      .finally(() => {
        if (active) setUsersLoading(false);
      });

    return () => {
      active = false;
    };
  }, [canLoadUsers, usersReloadSequence]);

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

  function retryUsers() {
    setUsersLoading(true);
    setUsersError(null);
    setUsersReloadSequence((current) => current + 1);
  }

  function updateIssue(updatedIssue: IssueDetail) {
    setDetailState({ issueId: updatedIssue.id, status: 'success', data: updatedIssue });
  }

  const closeActionDialog = useCallback(() => setActionDialogOpen(false), []);

  function handleActionCreated(action: IssueAction) {
    setDetailState((current) => {
      if (current?.status !== 'success' || current.issueId !== action.issueId) return current;
      return {
        ...current,
        data: { ...current.data, actions: [action, ...current.data.actions] },
      };
    });
    setActionDialogOpen(false);
    setActionNotice({ tone: 'success', message: '조치를 등록했습니다.' });
  }

  async function changeActionStatus(action: IssueAction, status: IssueAction['status']) {
    setChangingActionId(action.id);
    setActionNotice(null);
    try {
      const updatedAction = await changeActionStatusRequest(action.id, status);
      setDetailState((current) => {
        if (current?.status !== 'success' || current.issueId !== updatedAction.issueId) {
          return current;
        }
        return {
          ...current,
          data: {
            ...current.data,
            actions: current.data.actions.map((item) =>
              item.id === updatedAction.id ? updatedAction : item,
            ),
          },
        };
      });
      setActionNotice({
        tone: 'success',
        message: `조치 상태를 변경했습니다. 현재 상태: ${actionStatusLabel(updatedAction.status)}`,
      });
    } catch (error) {
      setActionNotice({
        tone: 'error',
        message: error instanceof ApiError ? error.message : '조치 상태를 변경할 수 없습니다.',
      });
    } finally {
      setChangingActionId(null);
    }
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

  if (user === null) return null;

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

      <IssueManagementPanel
        issue={issue}
        user={user}
        users={users}
        usersLoading={usersLoading}
        usersError={usersError}
        onRetryUsers={retryUsers}
        onIssueUpdated={updateIssue}
      />

      <section className="issue-detail-section" aria-labelledby="issue-actions-title">
        <header className="issue-section-header">
          <div>
            <h2 id="issue-actions-title">조치</h2>
            <span>문제 해결을 위해 등록된 작업</span>
          </div>
          <div className="issue-section-actions">
            <strong>{formatNumber(issue.actions.length)}건</strong>
            {canManageIssue(user) && issue.status !== 'CLOSED' && (
              <button
                className="secondary-button issue-add-action"
                type="button"
                onClick={() => {
                  setActionNotice(null);
                  setActionDialogOpen(true);
                }}
              >
                <Plus size={16} aria-hidden="true" />
                <span>조치 등록</span>
              </button>
            )}
          </div>
        </header>
        {actionNotice !== null && (
          <div
            className={`issue-operation-notice issue-operation-notice--${actionNotice.tone}`}
            role="status"
          >
            {actionNotice.tone === 'error' && <AlertCircle size={18} aria-hidden="true" />}
            <span>{actionNotice.message}</span>
          </div>
        )}
        {issue.actions.length === 0 ? (
          <IssueSectionEmpty
            icon={<ClipboardCheck size={24} aria-hidden="true" />}
            message="등록된 조치가 없습니다."
          />
        ) : (
          <ActionTable
            actions={issue.actions}
            user={user}
            changingActionId={changingActionId}
            onStatusChange={changeActionStatus}
          />
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

      {actionDialogOpen && (
        <ActionCreateDialog
          issueId={issue.id}
          users={users}
          onClose={closeActionDialog}
          onCreated={handleActionCreated}
        />
      )}
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

function ActionTable({
  actions,
  user,
  changingActionId,
  onStatusChange,
}: {
  actions: IssueAction[];
  user: UserProfile;
  changingActionId: number | null;
  onStatusChange: (action: IssueAction, status: IssueAction['status']) => Promise<void>;
}) {
  const showControls = actions.some(
    (action) => canChangeActionStatus(user, action) && nextActionStatuses(action.status).length > 0,
  );

  return (
    <div className="issue-action-table-wrap">
      <table className={`issue-action-table${showControls ? ' issue-action-table--managed' : ''}`}>
        <caption className="sr-only">이슈 조치 목록</caption>
        <thead>
          <tr>
            <th scope="col">상태</th>
            <th scope="col">조치</th>
            <th scope="col">담당자</th>
            <th scope="col">마감일</th>
            {showControls && <th scope="col">관리</th>}
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
              {showControls && (
                <td data-label="관리">
                  {canChangeActionStatus(user, action) &&
                  nextActionStatuses(action.status).length > 0 ? (
                    <div className="issue-action-controls">
                      {nextActionStatuses(action.status).map((status) => (
                        <button
                          type="button"
                          key={status}
                          onClick={() => void onStatusChange(action, status)}
                          disabled={changingActionId !== null}
                        >
                          {actionTransitionLabel(status)}
                        </button>
                      ))}
                    </div>
                  ) : (
                    <span className="issue-action-unavailable">-</span>
                  )}
                </td>
              )}
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
