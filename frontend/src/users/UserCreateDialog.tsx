import { AlertCircle, LoaderCircle, X } from 'lucide-react';
import { useEffect, useState, type FormEvent } from 'react';

import { ApiError } from '../lib/api-client';
import type { OrganizationUser, Role } from '../types/api';
import { createOrganizationUserRequest } from './api';
import { roleOptions } from './labels';

interface UserCreateDialogProps {
  onClose: () => void;
  onCreated: (user: OrganizationUser) => void;
}

export function UserCreateDialog({ onClose, onCreated }: UserCreateDialogProps) {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>('VIEWER');
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
    const trimmedName = name.trim();
    const trimmedEmail = email.trim();

    if (trimmedName === '') {
      setErrorMessage('이름을 입력해 주세요.');
      return;
    }
    if (!/^\S+@\S+\.\S+$/.test(trimmedEmail)) {
      setErrorMessage('올바른 이메일을 입력해 주세요.');
      return;
    }
    if (password.length < 8) {
      setErrorMessage('초기 비밀번호는 8자 이상 입력해 주세요.');
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    try {
      const createdUser = await createOrganizationUserRequest({
        email: trimmedEmail,
        password,
        name: trimmedName,
        role,
      });
      onCreated(createdUser);
    } catch (error) {
      setErrorMessage(
        error instanceof ApiError ? error.message : '사용자를 생성할 수 없습니다.',
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
        aria-label="사용자 추가 창 닫기"
        onClick={submitting ? undefined : onClose}
      />
      <section
        className="upload-dialog user-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="user-dialog-title"
      >
        <header className="dialog-header">
          <h2 id="user-dialog-title">사용자 추가</h2>
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
          <div className="dialog-body user-dialog-body">
            <label className="form-control" htmlFor="user-name">
              <span>이름</span>
              <input
                id="user-name"
                value={name}
                onChange={(event) => {
                  setName(event.target.value);
                  setErrorMessage(null);
                }}
                maxLength={100}
                autoFocus
                disabled={submitting}
                required
              />
            </label>

            <label className="form-control" htmlFor="user-email">
              <span>이메일</span>
              <input
                id="user-email"
                type="email"
                value={email}
                onChange={(event) => {
                  setEmail(event.target.value);
                  setErrorMessage(null);
                }}
                maxLength={255}
                autoComplete="off"
                disabled={submitting}
                required
              />
            </label>

            <label className="form-control" htmlFor="user-role">
              <span>역할</span>
              <select
                id="user-role"
                value={role}
                onChange={(event) => setRole(event.target.value as Role)}
                disabled={submitting}
              >
                {roleOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>

            <label className="form-control" htmlFor="user-password">
              <span>초기 비밀번호</span>
              <input
                id="user-password"
                type="password"
                value={password}
                onChange={(event) => {
                  setPassword(event.target.value);
                  setErrorMessage(null);
                }}
                minLength={8}
                maxLength={72}
                autoComplete="new-password"
                placeholder="8자 이상"
                disabled={submitting}
                required
              />
            </label>

            {errorMessage !== null && (
              <div className="dialog-error user-dialog-error" role="alert">
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
              <span>{submitting ? '추가 중' : '사용자 추가'}</span>
            </button>
          </footer>
        </form>
      </section>
    </div>
  );
}
