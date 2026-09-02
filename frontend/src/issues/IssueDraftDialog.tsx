import { AlertCircle, LoaderCircle, RefreshCw, X } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent } from 'react';

import { formatScore } from '../feedbacks/format';
import { ApiError } from '../lib/api-client';
import type { OrganizationUser } from '../types/api';
import { organizationUsersRequest } from '../users/api';
import { roleLabel } from '../users/labels';
import { confirmIssueDraftRequest, issueDraftRequest } from './api';
import type { IssueDraft } from './types';

type DraftState =
  | { feedbackId: number; status: 'success'; draft: IssueDraft; users: OrganizationUser[] }
  | { feedbackId: number; status: 'error'; message: string }
  | null;

interface IssueDraftDialogProps {
  feedbackId: number;
  onClose: () => void;
  onCreated: () => void;
}

export function IssueDraftDialog({ feedbackId, onClose, onCreated }: IssueDraftDialogProps) {
  const [state, setState] = useState<DraftState>(null);
  const [reloadSequence, setReloadSequence] = useState(0);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [assigneeId, setAssigneeId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const requestSequence = useRef(0);

  useEffect(() => {
    const sequence = requestSequence.current + 1;
    requestSequence.current = sequence;

    void Promise.all([issueDraftRequest(feedbackId), organizationUsersRequest()])
      .then(([draft, users]) => {
        if (requestSequence.current === sequence) {
          setState({ feedbackId, status: 'success', draft, users });
          setTitle(draft.title);
          setDescription(draft.description);
          setAssigneeId('');
        }
      })
      .catch((error: unknown) => {
        if (requestSequence.current === sequence) {
          setState({
            feedbackId,
            status: 'error',
            message: error instanceof ApiError ? error.message : '이슈 초안을 불러올 수 없습니다.',
          });
        }
      });
  }, [feedbackId, reloadSequence]);

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

  const currentState = state?.feedbackId === feedbackId ? state : null;

  function retry() {
    setState(null);
    setReloadSequence((current) => current + 1);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (currentState?.status !== 'success') return;
    if (title.trim() === '') {
      setErrorMessage('이슈 제목을 입력해 주세요.');
      return;
    }
    if (description.trim() === '') {
      setErrorMessage('이슈 설명을 입력해 주세요.');
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    try {
      await confirmIssueDraftRequest(feedbackId, {
        analysisVersion: currentState.draft.analysisVersion,
        title: title.trim(),
        description: description.trim(),
        assigneeId: assigneeId === '' ? undefined : Number(assigneeId),
      });
      onCreated();
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : '새 이슈를 만들 수 없습니다.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-layer">
      <button
        className="modal-backdrop"
        type="button"
        aria-label="새 이슈 작성 창 닫기"
        onClick={submitting ? undefined : onClose}
      />
      <section
        className="upload-dialog issue-draft-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="issue-draft-dialog-title"
      >
        <header className="dialog-header">
          <div>
            <p className="section-label">NEW ISSUE</p>
            <h2 id="issue-draft-dialog-title">새 이슈 작성</h2>
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

        {currentState === null && (
          <div className="issue-draft-loading" aria-label="이슈 초안 로딩 중">
            <LoaderCircle className="spin" size={22} aria-hidden="true" />
            <span>분석 결과로 이슈 초안을 준비하고 있습니다.</span>
          </div>
        )}

        {currentState?.status === 'error' && (
          <div className="issue-draft-error" role="alert">
            <AlertCircle size={21} aria-hidden="true" />
            <div>
              <strong>이슈 초안을 불러오지 못했습니다.</strong>
              <span>{currentState.message}</span>
            </div>
            <button className="secondary-button" type="button" onClick={retry}>
              <RefreshCw size={16} aria-hidden="true" />
              <span>다시 시도</span>
            </button>
          </div>
        )}

        {currentState?.status === 'success' && (
          <form className="upload-form" onSubmit={handleSubmit} noValidate>
            <div className="dialog-body issue-draft-dialog-body">
              <dl className="issue-draft-source">
                <div>
                  <dt>카테고리</dt>
                  <dd>{currentState.draft.category}</dd>
                </div>
                <div>
                  <dt>긴급도</dt>
                  <dd>{formatScore(currentState.draft.urgencyScore)}</dd>
                </div>
                <div>
                  <dt>분석 신뢰도</dt>
                  <dd>{formatScore(currentState.draft.confidenceScore)}</dd>
                </div>
              </dl>

              <label className="form-control" htmlFor="issue-draft-title">
                <span>이슈 제목</span>
                <input
                  id="issue-draft-title"
                  type="text"
                  aria-label="이슈 제목"
                  value={title}
                  onChange={(event) => {
                    setTitle(event.target.value);
                    setErrorMessage(null);
                  }}
                  maxLength={150}
                  disabled={submitting}
                  autoFocus
                  required
                />
                <small>{title.length} / 150</small>
              </label>

              <label className="form-control" htmlFor="issue-draft-description">
                <span>이슈 설명</span>
                <textarea
                  id="issue-draft-description"
                  aria-label="이슈 설명"
                  value={description}
                  onChange={(event) => {
                    setDescription(event.target.value);
                    setErrorMessage(null);
                  }}
                  maxLength={1000}
                  rows={7}
                  disabled={submitting}
                  required
                />
                <small>{description.length} / 1000</small>
              </label>

              <label className="form-control" htmlFor="issue-draft-assignee">
                <span>담당자</span>
                <select
                  id="issue-draft-assignee"
                  aria-label="담당자"
                  value={assigneeId}
                  onChange={(event) => setAssigneeId(event.target.value)}
                  disabled={submitting}
                >
                  <option value="">미지정</option>
                  {currentState.users.map((user) => (
                    <option key={user.id} value={user.id}>
                      {user.name} · {roleLabel(user.role)}
                    </option>
                  ))}
                </select>
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
                <span>{submitting ? '등록 중' : '이슈 등록'}</span>
              </button>
            </footer>
          </form>
        )}
      </section>
    </div>
  );
}
