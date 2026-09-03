import {
  AlertCircle,
  GitMerge,
  Link2,
  LoaderCircle,
  Plus,
  RefreshCw,
  Search,
  X,
} from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';

import { formatDate } from '../datasets/format';
import type { FeedbackAnalysisStatus } from '../feedbacks/types';
import { ApiError } from '../lib/api-client';
import type { UserProfile } from '../types/api';
import {
  confirmIssueCandidateRequest,
  feedbackIssuesRequest,
  issueCandidatesRequest,
} from './api';
import { issueStatusLabel, issueStatusTone, priorityTone } from './format';
import type { FeedbackIssue, IssueCandidate } from './types';
import { IssueDraftDialog } from './IssueDraftDialog';
import { IssueLinkDialog } from './IssueLinkDialog';
import { IssueLinkActions } from './IssueLinkActions';

type WorkflowState =
  | {
      feedbackId: number;
      analysisKey: string;
      status: 'success';
      links: FeedbackIssue[];
      candidates: IssueCandidate[];
    }
  | { feedbackId: number; analysisKey: string; status: 'error'; message: string }
  | null;

interface FeedbackIssueSectionProps {
  feedbackId: number;
  analysisStatus: FeedbackAnalysisStatus | null;
  analysisCategory: string | null;
  user: UserProfile | null;
}

export function FeedbackIssueSection({
  feedbackId,
  analysisStatus,
  analysisCategory,
  user,
}: FeedbackIssueSectionProps) {
  const analysisKey = `${analysisStatus ?? 'NONE'}:${analysisCategory ?? ''}`;
  const analysisReady = analysisStatus === 'SUCCESS';
  const canLinkIssue =
    user?.role === 'ADMIN' || user?.role === 'PM' || user?.role === 'CS';
  const canCreateIssue = user?.role === 'ADMIN' || user?.role === 'PM';
  const [state, setState] = useState<WorkflowState>(null);
  const [reloadSequence, setReloadSequence] = useState(0);
  const [selectedCandidate, setSelectedCandidate] = useState<IssueCandidate | null>(null);
  const [draftDialogOpen, setDraftDialogOpen] = useState(false);
  const [linkDialogOpen, setLinkDialogOpen] = useState(false);
  const [notice, setNotice] = useState<{ message: string; status: 'success' | 'error' } | null>(null);
  const requestSequence = useRef(0);

  useEffect(() => {
    const sequence = requestSequence.current + 1;
    requestSequence.current = sequence;

    void Promise.all([
      feedbackIssuesRequest(feedbackId),
      analysisReady ? issueCandidatesRequest(feedbackId) : Promise.resolve([]),
    ])
      .then(([links, candidates]) => {
        if (requestSequence.current === sequence) {
          setState({ feedbackId, analysisKey, status: 'success', links, candidates });
        }
      })
      .catch((error: unknown) => {
        if (requestSequence.current === sequence) {
          setState({
            feedbackId,
            analysisKey,
            status: 'error',
            message:
              error instanceof ApiError ? error.message : '이슈 처리 상태를 불러올 수 없습니다.',
          });
        }
      });
  }, [analysisKey, analysisReady, feedbackId, reloadSequence]);

  const currentState =
    state?.feedbackId === feedbackId && state.analysisKey === analysisKey ? state : null;

  function reload() {
    setState(null);
    setReloadSequence((current) => current + 1);
  }

  function handleCandidateConfirmed() {
    setSelectedCandidate(null);
    setNotice({ message: '추천 이슈 연결을 확정했습니다.', status: 'success' });
    reload();
  }

  function handleIssueCreated() {
    setDraftDialogOpen(false);
    setNotice({ message: '새 이슈를 만들고 대표 피드백으로 연결했습니다.', status: 'success' });
    reload();
  }

  function handleIssueLinked() {
    setLinkDialogOpen(false);
    setNotice({ message: '기존 이슈를 직접 연결했습니다.', status: 'success' });
    reload();
  }

  function handleLinkChanged(message: string) {
    setNotice({ message, status: 'success' });
    reload();
  }

  function handleLinkError(message: string) {
    setNotice({ message, status: 'error' });
  }

  return (
    <section
      className="dataset-detail-section feedback-detail-section feedback-issue-section"
      aria-labelledby="feedback-issue-title"
    >
      <header className="detail-section-header feedback-detail-section-header feedback-issue-section-header">
        <div>
          <p className="section-label">ISSUE REVIEW</p>
          <h2 id="feedback-issue-title">이슈 처리</h2>
        </div>
        <div className="feedback-issue-section-actions">
          {canLinkIssue && (
            <button
              className="secondary-button feedback-issue-link-button"
              type="button"
              onClick={() => setLinkDialogOpen(true)}
              disabled={currentState?.status !== 'success'}
            >
              <Search size={16} aria-hidden="true" />
              <span>기존 이슈 연결</span>
            </button>
          )}
          {!canLinkIssue && <GitMerge size={19} aria-hidden="true" />}
        </div>
      </header>

      {notice !== null && (
        <div
          className={`issue-workflow-notice issue-workflow-notice--${notice.status}`}
          role={notice.status === 'error' ? 'alert' : 'status'}
        >
          <span>{notice.message}</span>
          <button
            className="icon-button"
            type="button"
            onClick={() => setNotice(null)}
            aria-label="알림 닫기"
            title="알림 닫기"
          >
            <X size={17} />
          </button>
        </div>
      )}

      {currentState === null && <IssueWorkflowLoading />}

      {currentState?.status === 'error' && (
        <div className="issue-workflow-error" role="alert">
          <AlertCircle size={20} aria-hidden="true" />
          <div>
            <strong>이슈 처리 상태를 불러오지 못했습니다.</strong>
            <span>{currentState.message}</span>
          </div>
          <button
            className="icon-button"
            type="button"
            onClick={reload}
            aria-label="이슈 처리 상태 다시 불러오기"
            title="다시 불러오기"
          >
            <RefreshCw size={18} />
          </button>
        </div>
      )}

      {currentState?.status === 'success' && (
        <div className="issue-workflow-body">
          {currentState.links.length > 0 && (
            <div className="feedback-linked-issues">
              <div className="issue-workflow-heading">
                <h3>연결된 이슈</h3>
                <span>{currentState.links.length}건</span>
              </div>
              <div className="feedback-issue-list">
                {currentState.links.map((issue) => (
                  <LinkedIssueRow
                    issue={issue}
                    key={issue.linkId}
                    feedbackId={feedbackId}
                    canManage={canLinkIssue}
                    onChanged={handleLinkChanged}
                    onError={handleLinkError}
                  />
                ))}
              </div>
            </div>
          )}

          {!analysisReady && currentState.links.length === 0 && (
            <div className="issue-workflow-empty">
              <GitMerge size={22} aria-hidden="true" />
              <strong>분석 완료 후 이슈 후보를 확인할 수 있습니다.</strong>
              <span>현재 연결된 이슈는 없습니다.</span>
            </div>
          )}

          {analysisReady && currentState.candidates.length > 0 && (
            <div className="feedback-issue-candidates">
              <div className="issue-workflow-heading">
                <div>
                  <h3>유사 이슈 후보</h3>
                  <span>분석 결과와 기존 이슈를 비교한 추천입니다.</span>
                </div>
                <span>{currentState.candidates.length}건</span>
              </div>
              <div className="feedback-candidate-list">
                {currentState.candidates.map((candidate) => (
                  <CandidateRow
                    candidate={candidate}
                    canConfirm={canLinkIssue}
                    key={candidate.issueId}
                    onSelect={() => setSelectedCandidate(candidate)}
                  />
                ))}
              </div>
            </div>
          )}

          {analysisReady && currentState.candidates.length === 0 && (
            <div className="issue-workflow-empty">
              <GitMerge size={22} aria-hidden="true" />
              <strong>
                {currentState.links.length > 0
                  ? '추가로 추천할 이슈가 없습니다.'
                  : '유사한 기존 이슈가 없습니다.'}
              </strong>
              {currentState.links.length === 0 && canCreateIssue && (
                <button
                  className="primary-button"
                  type="button"
                  onClick={() => setDraftDialogOpen(true)}
                >
                  <Plus size={16} aria-hidden="true" />
                  <span>새 이슈 작성</span>
                </button>
              )}
              {currentState.links.length === 0 && !canCreateIssue && (
                <span>새 이슈 등록 권한이 있는 담당자의 검토가 필요합니다.</span>
              )}
            </div>
          )}
        </div>
      )}

      {selectedCandidate !== null && (
        <CandidateConfirmDialog
          feedbackId={feedbackId}
          candidate={selectedCandidate}
          onClose={() => setSelectedCandidate(null)}
          onConfirmed={handleCandidateConfirmed}
        />
      )}

      {draftDialogOpen && (
        <IssueDraftDialog
          feedbackId={feedbackId}
          onClose={() => setDraftDialogOpen(false)}
          onCreated={handleIssueCreated}
        />
      )}

      {linkDialogOpen && currentState?.status === 'success' && (
        <IssueLinkDialog
          feedbackId={feedbackId}
          linkedIssueIds={currentState.links.map((issue) => issue.issueId)}
          onClose={() => setLinkDialogOpen(false)}
          onLinked={handleIssueLinked}
        />
      )}
    </section>
  );
}

function LinkedIssueRow({
  issue,
  feedbackId,
  canManage,
  onChanged,
  onError,
}: {
  issue: FeedbackIssue;
  feedbackId: number;
  canManage: boolean;
  onChanged: (message: string) => void;
  onError: (message: string) => void;
}) {
  return (
    <article className="feedback-issue-row">
      <div className="feedback-issue-main">
        <Link to={`/issues/${issue.issueId}`}>{issue.title}</Link>
        <span>
          #{issue.issueId} · {issue.category} · {issue.assigneeName ?? '담당자 미지정'} ·{' '}
          {formatDate(issue.linkedAt)}
        </span>
      </div>
      <div className="feedback-issue-badges">
        <span className={`priority-badge priority-badge--${priorityTone(issue.priority)}`}>
          {issue.priority}
        </span>
        <span className={`issue-status issue-status--${issueStatusTone(issue.status)}`}>
          {issueStatusLabel(issue.status)}
        </span>
        {issue.representative && <span className="representative-label">대표 피드백</span>}
      </div>
      <div className="feedback-link-controls">
        <div className="feedback-link-source">
          <Link2 size={15} aria-hidden="true" />
          <span>{issue.linkedBy === 'AI' ? '추천 확정' : '직접 연결'}</span>
        </div>
        {canManage && (
          <IssueLinkActions
            feedbackId={feedbackId}
            issue={issue}
            onChanged={onChanged}
            onError={onError}
          />
        )}
      </div>
    </article>
  );
}

function CandidateRow({
  candidate,
  canConfirm,
  onSelect,
}: {
  candidate: IssueCandidate;
  canConfirm: boolean;
  onSelect: () => void;
}) {
  return (
    <article className="feedback-candidate-row">
      <div className="feedback-issue-main">
        <Link to={`/issues/${candidate.issueId}`}>{candidate.title}</Link>
        <span>#{candidate.issueId} · {candidate.category}</span>
      </div>
      <div className="candidate-match-score">
        <strong>{formatPercent(candidate.similarityScore)}</strong>
        <span>추천 일치율</span>
      </div>
      <div className="candidate-match-signals">
        <span>{candidate.matchSignals.categoryMatched ? '카테고리 일치' : '카테고리 다름'}</span>
        <span>텍스트 {formatPercent(candidate.matchSignals.textSimilarity)}</span>
      </div>
      <div className="feedback-candidate-action">
        <span className={`priority-badge priority-badge--${priorityTone(candidate.priority)}`}>
          {candidate.priority}
        </span>
        <span className={`issue-status issue-status--${issueStatusTone(candidate.status)}`}>
          {issueStatusLabel(candidate.status)}
        </span>
        {canConfirm && (
          <button className="secondary-button" type="button" onClick={onSelect}>
            <Link2 size={15} aria-hidden="true" />
            <span>연결 검토</span>
          </button>
        )}
      </div>
    </article>
  );
}

function CandidateConfirmDialog({
  feedbackId,
  candidate,
  onClose,
  onConfirmed,
}: {
  feedbackId: number;
  candidate: IssueCandidate;
  onClose: () => void;
  onConfirmed: () => void;
}) {
  const [representative, setRepresentative] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !submitting) onClose();
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [onClose, submitting]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await confirmIssueCandidateRequest(
        feedbackId,
        candidate.issueId,
        representative,
      );
      onConfirmed();
    } catch (error) {
      setErrorMessage(
        error instanceof ApiError ? error.message : '추천 이슈 연결을 확정할 수 없습니다.',
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-layer">
      <button
        className="modal-backdrop"
        type="button"
        aria-label="이슈 연결 검토 창 닫기"
        onClick={submitting ? undefined : onClose}
      />
      <section
        className="upload-dialog issue-link-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="issue-link-dialog-title"
      >
        <header className="dialog-header">
          <div>
            <p className="section-label">ISSUE REVIEW</p>
            <h2 id="issue-link-dialog-title">추천 이슈 연결</h2>
          </div>
          <button
            className="icon-button"
            type="button"
            onClick={onClose}
            disabled={submitting}
            aria-label="닫기"
            title="닫기"
          >
            <X size={20} />
          </button>
        </header>
        <form className="upload-form" onSubmit={handleSubmit}>
          <div className="dialog-body issue-link-dialog-body">
            <div className="issue-link-candidate-summary">
              <span>연결 대상</span>
              <strong>{candidate.title}</strong>
              <small>
                #{candidate.issueId} · {candidate.category} · 일치율{' '}
                {formatPercent(candidate.similarityScore)}
              </small>
            </div>
            <label className="representative-control">
              <input
                type="checkbox"
                checked={representative}
                onChange={(event) => setRepresentative(event.target.checked)}
                disabled={submitting}
              />
              <span>
                <strong>대표 피드백으로 지정</strong>
                <small>이슈의 원인과 영향을 가장 잘 보여주는 피드백일 때 선택합니다.</small>
              </span>
            </label>
            {errorMessage !== null && (
              <div className="dialog-error" role="alert">
                <AlertCircle size={18} aria-hidden="true" />
                <span>{errorMessage}</span>
              </div>
            )}
          </div>
          <footer className="dialog-footer">
            <button
              className="secondary-button"
              type="button"
              onClick={onClose}
              disabled={submitting}
            >
              취소
            </button>
            <button className="primary-button" type="submit" disabled={submitting}>
              {submitting && <LoaderCircle className="spin" size={17} aria-hidden="true" />}
              <span>{submitting ? '연결 중' : '연결 확정'}</span>
            </button>
          </footer>
        </form>
      </section>
    </div>
  );
}

function IssueWorkflowLoading() {
  return (
    <div className="issue-workflow-loading" aria-label="이슈 처리 상태 로딩 중">
      <span />
      <span />
      <span />
    </div>
  );
}

function formatPercent(value: number): string {
  return `${(value * 100).toFixed(1)}%`;
}
