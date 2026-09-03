import { AlertCircle, LoaderCircle, Star, Unlink, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { ApiError } from '../lib/api-client';
import { changeFeedbackRepresentativeRequest, unlinkFeedbackRequest } from './api';
import type { FeedbackIssue } from './types';

interface IssueLinkActionsProps {
  feedbackId: number;
  issue: FeedbackIssue;
  onChanged: (message: string) => void;
  onError: (message: string) => void;
}

export function IssueLinkActions({ feedbackId, issue, onChanged, onError }: IssueLinkActionsProps) {
  const [saving, setSaving] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const unlinkButton = useRef<HTMLButtonElement>(null);
  const representativeLabel = issue.representative ? '대표 피드백 지정 해제' : '대표 피드백 지정';

  async function changeRepresentative() {
    setSaving(true);
    try {
      await changeFeedbackRepresentativeRequest(feedbackId, issue.issueId, !issue.representative);
      onChanged(issue.representative ? '대표 피드백 지정을 해제했습니다.' : '대표 피드백으로 지정했습니다.');
    } catch (error) {
      onError(error instanceof ApiError ? error.message : '대표 피드백 지정을 변경할 수 없습니다.');
    } finally {
      setSaving(false);
    }
  }

  function closeConfirmation() {
    setConfirmOpen(false);
    unlinkButton.current?.focus();
  }

  return (
    <div className="feedback-link-actions">
      <button
        className="icon-button feedback-representative-button"
        type="button"
        aria-label={representativeLabel}
        title={representativeLabel}
        aria-pressed={issue.representative}
        disabled={saving}
        onClick={() => void changeRepresentative()}
      >
        {saving ? <LoaderCircle className="spin" size={16} /> : <Star size={16} />}
      </button>
      <button
        ref={unlinkButton}
        className="icon-button feedback-unlink-button"
        type="button"
        aria-label="이슈 연결 해제"
        title="연결 해제"
        disabled={saving}
        onClick={() => setConfirmOpen(true)}
      >
        <Unlink size={16} />
      </button>
      {confirmOpen && (
        <UnlinkConfirmDialog
          feedbackId={feedbackId}
          issue={issue}
          onClose={closeConfirmation}
          onUnlinked={() => {
            setConfirmOpen(false);
            onChanged('이슈 연결을 해제했습니다.');
          }}
        />
      )}
    </div>
  );
}

function UnlinkConfirmDialog({
  feedbackId,
  issue,
  onClose,
  onUnlinked,
}: {
  feedbackId: number;
  issue: FeedbackIssue;
  onClose: () => void;
  onUnlinked: () => void;
}) {
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const dialog = useRef<HTMLElement>(null);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !submitting) onClose();
      if (event.key !== 'Tab') return;
      const buttons = dialog.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)');
      if (!buttons?.length) {
        event.preventDefault();
        return;
      }
      const first = buttons[0];
      const last = buttons[buttons.length - 1];
      if (event.shiftKey && (document.activeElement === first || !dialog.current?.contains(document.activeElement))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (document.activeElement === last || !dialog.current?.contains(document.activeElement))) {
        event.preventDefault();
        first.focus();
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [onClose, submitting]);

  async function unlink() {
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await unlinkFeedbackRequest(feedbackId, issue.issueId);
      onUnlinked();
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : '이슈 연결을 해제할 수 없습니다.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-layer">
      <button className="modal-backdrop" type="button" aria-label="연결 해제 창 닫기"
        disabled={submitting} onClick={onClose} />
      <section ref={dialog} className="upload-dialog issue-link-dialog" role="dialog"
        aria-modal="true" aria-labelledby="unlink-title" aria-describedby="unlink-description">
        <header className="dialog-header">
          <h2 id="unlink-title">이슈 연결 해제</h2>
          <button className="icon-button" type="button" onClick={onClose}
            disabled={submitting} aria-label="닫기" title="닫기"><X size={20} /></button>
        </header>
        <div className="dialog-body issue-unlink-body">
          <strong>{issue.title}</strong>
          <span>#{issue.issueId}</span>
          <p id="unlink-description">이 피드백과 이슈의 연결을 해제합니다. 원문과 이슈, 등록된 액션은 삭제되지 않습니다.</p>
          {errorMessage !== null && (
            <div className="dialog-error" role="alert">
              <AlertCircle size={18} aria-hidden="true" /><span>{errorMessage}</span>
            </div>
          )}
        </div>
        <footer className="dialog-footer">
          <button className="secondary-button" type="button" onClick={onClose}
            disabled={submitting} autoFocus>취소</button>
          <button className="primary-button issue-unlink-confirm" type="button"
            onClick={() => void unlink()} disabled={submitting}>
            {submitting ? <LoaderCircle className="spin" size={16} /> : <Unlink size={16} />}
            <span>{submitting ? '해제 중' : '연결 해제'}</span>
          </button>
        </footer>
      </section>
    </div>
  );
}
