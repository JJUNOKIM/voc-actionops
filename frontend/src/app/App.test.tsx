import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from './App';
import { AuthProvider } from '../auth/AuthContext';
import type { TokenResponse, UserProfile } from '../types/api';

const authApiMocks = vi.hoisted(() => ({
  loginRequest: vi.fn(),
  logoutRequest: vi.fn(),
  currentUserRequest: vi.fn(),
}));

vi.mock('../auth/auth-api', () => authApiMocks);

const tokens: TokenResponse = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  tokenType: 'Bearer',
  expiresIn: 1_800,
  refreshTokenExpiresIn: 1_209_600,
};

const profile: UserProfile = {
  id: 1,
  organizationId: 11,
  organizationName: 'VOC ActionOps Demo',
  email: 'admin@voc-actionops.local',
  name: 'Demo Admin',
  role: 'ADMIN',
};

describe('authentication flow', () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    authApiMocks.loginRequest.mockReset();
    authApiMocks.logoutRequest.mockReset();
    authApiMocks.currentUserRequest.mockReset();
  });

  it('redirects to login, signs in with the demo account, and logs out', async () => {
    const user = userEvent.setup();
    authApiMocks.loginRequest.mockResolvedValue(tokens);
    authApiMocks.currentUserRequest.mockResolvedValue(profile);
    authApiMocks.logoutRequest.mockResolvedValue(undefined);

    render(
      <MemoryRouter initialEntries={['/']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: '로그인' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '데모 계정 입력' }));
    expect(screen.getByLabelText('이메일')).toHaveValue('admin@voc-actionops.local');
    expect(screen.getByLabelText('비밀번호')).toHaveValue('demo-password');

    await user.click(screen.getByRole('button', { name: '로그인' }));

    expect(await screen.findByRole('heading', { name: '운영 개요' })).toBeInTheDocument();
    expect(screen.getByRole('complementary')).toHaveTextContent('VOC ActionOps Demo');
    expect(authApiMocks.loginRequest).toHaveBeenCalledWith(
      'admin@voc-actionops.local',
      'demo-password',
    );

    await user.click(screen.getByRole('button', { name: '로그아웃' }));

    expect(await screen.findByRole('heading', { name: '로그인' })).toBeInTheDocument();
    expect(authApiMocks.logoutRequest).toHaveBeenCalledWith('refresh-token');
  });
});
