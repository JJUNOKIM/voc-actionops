import { ArrowRight, Eye, EyeOff, ShieldCheck, UserRoundCheck } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';

import { useAuth } from './useAuth';
import { ApiError } from '../lib/api-client';

const DEMO_EMAIL = 'admin@voc-actionops.local';
const DEMO_PASSWORD = 'demo-password';

interface LocationState {
  from?: string;
}

export function LoginPage() {
  const { status, login } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  if (status === 'authenticated') {
    return <Navigate to="/" replace />;
  }

  const redirectPath = getRedirectPath(location.state);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (email.trim() === '' || password === '') {
      setErrorMessage('이메일과 비밀번호를 입력해 주세요.');
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    try {
      await login(email.trim(), password);
      navigate(redirectPath, { replace: true });
    } catch (error) {
      setErrorMessage(
        error instanceof ApiError ? error.message : '로그인 요청을 처리할 수 없습니다.',
      );
    } finally {
      setSubmitting(false);
    }
  }

  function fillDemoCredentials() {
    setEmail(DEMO_EMAIL);
    setPassword(DEMO_PASSWORD);
    setErrorMessage(null);
  }

  return (
    <div className="login-layout">
      <aside className="login-rail" aria-label="VOC ActionOps">
        <div className="login-rail-brand">
          <span className="brand-mark brand-mark--inverse" aria-hidden="true">
            VA
          </span>
          <span>VOC ActionOps</span>
        </div>
        <div className="login-rail-footer">
          <ShieldCheck size={18} aria-hidden="true" />
          <span>Secure workspace</span>
        </div>
      </aside>

      <main className="login-main">
        <section className="login-panel" aria-labelledby="login-title">
          <div className="login-mobile-brand">
            <span className="brand-mark" aria-hidden="true">
              VA
            </span>
            <span>VOC ActionOps</span>
          </div>

          <div className="login-heading">
            <p className="section-label">WORKSPACE ACCESS</p>
            <h1 id="login-title">로그인</h1>
            <p>계정 정보를 입력해 작업 공간에 접속하세요.</p>
          </div>

          <form className="login-form" onSubmit={handleSubmit} noValidate>
            <label className="field-label" htmlFor="email">
              이메일
            </label>
            <input
              id="email"
              name="email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="username"
              placeholder="name@company.com"
              disabled={submitting}
              required
            />

            <label className="field-label" htmlFor="password">
              비밀번호
            </label>
            <div className="password-field">
              <input
                id="password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
                placeholder="비밀번호"
                disabled={submitting}
                required
              />
              <button
                className="icon-button password-toggle"
                type="button"
                onClick={() => setShowPassword((visible) => !visible)}
                aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
                title={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
                disabled={submitting}
              >
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>

            {errorMessage !== null && (
              <div className="form-error" role="alert">
                {errorMessage}
              </div>
            )}

            <button className="primary-button login-submit" type="submit" disabled={submitting}>
              <span>{submitting ? '로그인 중' : '로그인'}</span>
              <ArrowRight size={18} aria-hidden="true" />
            </button>

            <button
              className="secondary-button demo-button"
              type="button"
              onClick={fillDemoCredentials}
              disabled={submitting}
            >
              <UserRoundCheck size={18} aria-hidden="true" />
              <span>데모 계정 입력</span>
            </button>
          </form>
        </section>
      </main>
    </div>
  );
}

function getRedirectPath(state: unknown): string {
  if (
    typeof state === 'object' &&
    state !== null &&
    'from' in state &&
    typeof (state as LocationState).from === 'string'
  ) {
    return (state as LocationState).from ?? '/';
  }
  return '/';
}
