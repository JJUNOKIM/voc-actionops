import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';

import { AppShell } from './AppShell';
import { OverviewPage } from '../pages/OverviewPage';
import { LoginPage } from '../auth/LoginPage';
import { useAuth } from '../auth/useAuth';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route index element={<OverviewPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

function ProtectedRoute() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'initializing') {
    return <AppLoadingScreen />;
  }
  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
}

function AppLoadingScreen() {
  return (
    <main className="app-loading" aria-label="인증 상태 확인 중">
      <div className="brand-mark" aria-hidden="true">
        VA
      </div>
      <div className="loading-line" />
    </main>
  );
}
