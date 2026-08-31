import { AlertCircle, LoaderCircle, X } from 'lucide-react';
import { useEffect, useState, type FormEvent } from 'react';

import { ApiError } from '../lib/api-client';
import type { OrganizationUser } from '../types/api';
import { roleLabel } from '../users/labels';
import { createIssueActionRequest } from './api';
import type { IssueAction } from './types';

interface ActionCreateDialogProps {
  issueId: number;
  users: OrganizationUser[] | null;
  onClose: () => void;
  onCreated: (action: IssueAction) => void;
}

export function ActionCreateDialog({
  issueId,
  users,
  onClose,
  onCreated,
}: ActionCreateDialogProps) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [assigneeId, setAssigneeId] = useState('');
  const [dueDate, setDueDate] = useState('');
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
    const trimmedTitle = title.trim();
    if (trimmedTitle === '') {
      setErrorMessage('조치 제목을 입력해 주세요.');
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    try {
      const action = await createIssueActionRequest(issueId, {
        title: trimmedTitle,
        description: description.trim() || undefined,
        assigneeId: assigneeId === '' ? undefined : Number(assigneeId),
        dueDate: dueDate || undefined,
      });
      onCreated(action);
    } catch (error) {
      setErrorMessage(
        error instanceof ApiError ? error.message : '조치를 등록할 수 없습니다.',
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
        aria-label="조치 등록 창 닫기"
        onClick={submitting ? undefined : onClose}
      />
      <section
        className="upload-dialog action-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="action-dialog-title"
      >
        <header className="dialog-header">
          <h2 id="action-dialog-title">조치 등록</h2>
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

        <form className="upload-form" onSubmit={handleSubmit} noValidate>
          <div className="dialog-body action-dialog-body">
            <label className="form-control" htmlFor="action-title">
              <span>조치 제목</span>
              <input
                id="action-title"
                value={title}
                onChange={(event) => {
                  setTitle(event.target.value);
                  setErrorMessage(null);
                }}
                maxLength={150}
                placeholder="예: 인증 토큰 갱신 흐름 확인"
                autoFocus
                disabled={submitting}
                required
              />
            </label>

            <label className="form-control" htmlFor="action-description">
              <span>설명</span>
              <textarea
                id="action-description"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                maxLength={1000}
                rows={4}
                placeholder="확인할 범위와 완료 기준을 입력해 주세요."
                disabled={submitting}
              />
            </label>

            <div className="action-dialog-row">
              <label className="form-control" htmlFor="action-assignee">
                <span>담당자</span>
                <select
                  id="action-assignee"
                  value={assigneeId}
                  onChange={(event) => setAssigneeId(event.target.value)}
                  disabled={submitting}
                >
                  <option value="">미지정</option>
                  {(users ?? []).map((user) => (
                    <option key={user.id} value={user.id}>
                      {user.name} · {roleLabel(user.role)}
                    </option>
                  ))}
                </select>
              </label>

              <label className="form-control" htmlFor="action-due-date">
                <span>마감일</span>
                <input
                  id="action-due-date"
                  type="date"
                  value={dueDate}
                  onChange={(event) => setDueDate(event.target.value)}
                  disabled={submitting}
                />
              </label>
            </div>

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
              <span>{submitting ? '등록 중' : '조치 등록'}</span>
            </button>
          </footer>
        </form>
      </section>
    </div>
  );
}
