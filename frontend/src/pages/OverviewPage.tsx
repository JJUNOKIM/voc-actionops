import { CheckCircle2, KeyRound, UserRound, UsersRound } from 'lucide-react';

import { useAuth } from '../auth/useAuth';
import type { Role } from '../types/api';

const roleLabels: Record<Role, string> = {
  ADMIN: '관리자',
  PM: '프로덕트 매니저',
  CS: '고객 지원',
  DEVELOPER: '개발자',
  VIEWER: '뷰어',
};

export function OverviewPage() {
  const { user } = useAuth();
  if (user === null) {
    return null;
  }

  return (
    <div className="page-container">
      <header className="page-header">
        <div>
          <p className="section-label">WORKSPACE</p>
          <h1>개요</h1>
          <p className="page-description">{user.organizationName}</p>
        </div>
        <div className="connection-badge">
          <CheckCircle2 size={17} aria-hidden="true" />
          <span>연결됨</span>
        </div>
      </header>

      <section className="workspace-section" aria-labelledby="workspace-heading">
        <div className="section-heading-row">
          <div>
            <p className="section-label">CURRENT SESSION</p>
            <h2 id="workspace-heading">작업 공간 정보</h2>
          </div>
          <span className="workspace-id">ORG {user.organizationId}</span>
        </div>

        <dl className="workspace-details">
          <div className="detail-item">
            <dt>
              <UsersRound size={18} aria-hidden="true" />
              조직
            </dt>
            <dd>{user.organizationName}</dd>
          </div>
          <div className="detail-item">
            <dt>
              <UserRound size={18} aria-hidden="true" />
              사용자
            </dt>
            <dd>{user.name}</dd>
            <span>{user.email}</span>
          </div>
          <div className="detail-item">
            <dt>
              <KeyRound size={18} aria-hidden="true" />
              권한
            </dt>
            <dd>{roleLabels[user.role]}</dd>
            <span>{user.role}</span>
          </div>
        </dl>
      </section>
    </div>
  );
}
