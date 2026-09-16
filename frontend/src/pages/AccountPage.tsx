import { KeyRound, Save, UserRound } from 'lucide-react';
import { useState, type FormEvent } from 'react';

import { useAuth } from '../auth/useAuth';
import { ApiError } from '../lib/api-client';
import { changeMyPasswordRequest } from '../users/api';
import { roleLabel } from '../users/labels';

export function AccountPage() {
  const { user, logout } = useAuth();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  if (user === null) return null;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (currentPassword === '') {
      setErrorMessage('현재 비밀번호를 입력해 주세요.');
      return;
    }
    if (newPassword.length < 8) {
      setErrorMessage('새 비밀번호는 8자 이상 입력해 주세요.');
      return;
    }
    if (newPassword !== passwordConfirm) {
      setErrorMessage('새 비밀번호가 일치하지 않습니다.');
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    try {
      await changeMyPasswordRequest(currentPassword, newPassword);
      await logout();
    } catch (error) {
      setErrorMessage(
        error instanceof ApiError ? error.message : '비밀번호를 변경할 수 없습니다.',
      );
      setSubmitting(false);
    }
  }

  return (
    <div className="page-container account-page">
      <header className="page-header">
        <div>
          <h1>내 계정</h1>
          <p className="page-description">계정 정보와 로그인 비밀번호를 관리합니다.</p>
        </div>
      </header>

      <section className="account-section" aria-labelledby="account-profile-title">
        <div className="account-section-heading">
          <UserRound size={19} aria-hidden="true" />
          <div>
            <h2 id="account-profile-title">계정 정보</h2>
            <p>현재 로그인한 계정의 기본 정보입니다.</p>
          </div>
        </div>
        <dl className="account-details">
          <div>
            <dt>이름</dt>
            <dd>{user.name}</dd>
          </div>
          <div>
            <dt>이메일</dt>
            <dd>{user.email}</dd>
          </div>
          <div>
            <dt>역할</dt>
            <dd>{roleLabel(user.role)}</dd>
          </div>
        </dl>
      </section>

      <section className="account-section" aria-labelledby="password-title">
        <div className="account-section-heading">
          <KeyRound size={19} aria-hidden="true" />
          <div>
            <h2 id="password-title">비밀번호 변경</h2>
            <p>변경 후에는 다시 로그인해야 합니다.</p>
          </div>
        </div>
        <form className="account-password-form" onSubmit={handleSubmit} noValidate>
          <label htmlFor="current-password">현재 비밀번호</label>
          <input
            id="current-password"
            type="password"
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
            autoComplete="current-password"
            maxLength={72}
            disabled={submitting}
            required
          />

          <label htmlFor="new-password">새 비밀번호</label>
          <input
            id="new-password"
            type="password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            autoComplete="new-password"
            minLength={8}
            maxLength={72}
            placeholder="8자 이상"
            disabled={submitting}
            required
          />

          <label htmlFor="password-confirm">새 비밀번호 확인</label>
          <input
            id="password-confirm"
            type="password"
            value={passwordConfirm}
            onChange={(event) => setPasswordConfirm(event.target.value)}
            autoComplete="new-password"
            minLength={8}
            maxLength={72}
            disabled={submitting}
            required
          />

          {errorMessage !== null && (
            <p className="account-form-error" role="alert">{errorMessage}</p>
          )}

          <button className="primary-button account-password-submit" type="submit" disabled={submitting}>
            <Save size={16} aria-hidden="true" />
            <span>{submitting ? '변경 중' : '비밀번호 변경'}</span>
          </button>
        </form>
      </section>
    </div>
  );
}
