import {
  Activity,
  Database,
  LayoutDashboard,
  LogOut,
  Menu,
  MessageSquareText,
  X,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';

import { useAuth } from '../auth/useAuth';
import type { Role, UserProfile } from '../types/api';

const roleLabels: Record<Role, string> = {
  ADMIN: '관리자',
  PM: '프로덕트 매니저',
  CS: '고객 지원',
  DEVELOPER: '개발자',
  VIEWER: '뷰어',
};

export function AppShell() {
  const { user, logout } = useAuth();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    if (!drawerOpen) {
      return undefined;
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setDrawerOpen(false);
      }
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [drawerOpen]);

  if (user === null) {
    return null;
  }

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await logout();
    } finally {
      setLoggingOut(false);
    }
  }

  return (
    <div className="app-layout">
      <aside className="app-sidebar app-sidebar--desktop">
        <SidebarContent user={user} loggingOut={loggingOut} onLogout={handleLogout} />
      </aside>

      <header className="mobile-header">
        <button
          className="icon-button mobile-menu-button"
          type="button"
          onClick={() => setDrawerOpen(true)}
          aria-label="메뉴 열기"
          title="메뉴 열기"
          aria-expanded={drawerOpen}
        >
          <Menu size={21} />
        </button>
        <div className="mobile-brand">
          <span className="brand-mark brand-mark--small" aria-hidden="true">
            VA
          </span>
          <span>VOC ActionOps</span>
        </div>
        <span className="mobile-user-avatar" aria-label={user.name}>
          {initials(user.name)}
        </span>
      </header>

      {drawerOpen && (
        <div className="mobile-drawer-layer">
          <button
            className="drawer-backdrop"
            type="button"
            aria-label="메뉴 닫기"
            onClick={() => setDrawerOpen(false)}
          />
          <aside className="app-sidebar app-sidebar--mobile" aria-label="모바일 메뉴">
            <button
              className="icon-button drawer-close"
              type="button"
              onClick={() => setDrawerOpen(false)}
              aria-label="메뉴 닫기"
              title="메뉴 닫기"
            >
              <X size={20} />
            </button>
            <SidebarContent
              user={user}
              loggingOut={loggingOut}
              onLogout={handleLogout}
              onNavigate={() => setDrawerOpen(false)}
            />
          </aside>
        </div>
      )}

      <main className="app-content">
        <Outlet />
      </main>
    </div>
  );
}

interface SidebarContentProps {
  user: UserProfile;
  loggingOut: boolean;
  onLogout: () => Promise<void>;
  onNavigate?: () => void;
}

function SidebarContent({ user, loggingOut, onLogout, onNavigate }: SidebarContentProps) {
  return (
    <>
      <div className="sidebar-brand">
        <span className="brand-mark brand-mark--inverse" aria-hidden="true">
          VA
        </span>
        <div>
          <strong>VOC ActionOps</strong>
          <span>Operations</span>
        </div>
      </div>

      <nav className="sidebar-nav" aria-label="주요 메뉴">
        <p className="sidebar-section-label">WORKSPACE</p>
        <NavLink className="nav-item" to="/" end onClick={onNavigate}>
          <LayoutDashboard size={19} aria-hidden="true" />
          <span>개요</span>
        </NavLink>
        <NavLink className="nav-item" to="/datasets" onClick={onNavigate}>
          <Database size={19} aria-hidden="true" />
          <span>데이터셋</span>
        </NavLink>
        <NavLink className="nav-item" to="/feedbacks" onClick={onNavigate}>
          <MessageSquareText size={19} aria-hidden="true" />
          <span>피드백</span>
        </NavLink>
      </nav>

      <div className="sidebar-system-status">
        <Activity size={17} aria-hidden="true" />
        <div>
          <span>API</span>
          <strong>Connected</strong>
        </div>
      </div>

      <div className="sidebar-account">
        <div className="account-avatar" aria-hidden="true">
          {initials(user.name)}
        </div>
        <div className="account-copy">
          <strong>{user.name}</strong>
          <span>{roleLabels[user.role]}</span>
        </div>
        <button
          className="icon-button account-logout"
          type="button"
          onClick={() => void onLogout()}
          disabled={loggingOut}
          aria-label="로그아웃"
          title="로그아웃"
        >
          <LogOut size={18} />
        </button>
      </div>
    </>
  );
}

function initials(name: string): string {
  return name.trim().slice(0, 2).toUpperCase();
}
