import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AccountPage } from './AccountPage';

const userApiMocks = vi.hoisted(() => ({
  changeMyPasswordRequest: vi.fn(),
}));

const logoutMock = vi.hoisted(() => vi.fn());

vi.mock('../users/api', () => userApiMocks);
vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({
    user: {
      id: 1,
      organizationId: 11,
      organizationName: 'VOC ActionOps Demo',
      email: 'admin@example.com',
      name: 'Demo Admin',
      role: 'ADMIN',
    },
    logout: logoutMock,
  }),
}));

describe('AccountPage', () => {
  beforeEach(() => {
    userApiMocks.changeMyPasswordRequest.mockReset();
    logoutMock.mockReset();
  });

  it('shows the current account information', () => {
    render(<AccountPage />);

    expect(screen.getByText('Demo Admin')).toBeInTheDocument();
    expect(screen.getByText('admin@example.com')).toBeInTheDocument();
    expect(screen.getByText('관리자')).toBeInTheDocument();
  });

  it('validates the password confirmation', async () => {
    const user = userEvent.setup();
    render(<AccountPage />);

    await user.type(screen.getByLabelText('현재 비밀번호'), 'Password123!');
    await user.type(screen.getByLabelText('새 비밀번호'), 'NewPassword123!');
    await user.type(screen.getByLabelText('새 비밀번호 확인'), 'Different123!');
    await user.click(screen.getByRole('button', { name: '비밀번호 변경' }));

    expect(screen.getByRole('alert')).toHaveTextContent('새 비밀번호가 일치하지 않습니다.');
    expect(userApiMocks.changeMyPasswordRequest).not.toHaveBeenCalled();
  });

  it('changes the password and logs out', async () => {
    const user = userEvent.setup();
    userApiMocks.changeMyPasswordRequest.mockResolvedValue(undefined);
    logoutMock.mockResolvedValue(undefined);
    render(<AccountPage />);

    await user.type(screen.getByLabelText('현재 비밀번호'), 'Password123!');
    await user.type(screen.getByLabelText('새 비밀번호'), 'NewPassword123!');
    await user.type(screen.getByLabelText('새 비밀번호 확인'), 'NewPassword123!');
    await user.click(screen.getByRole('button', { name: '비밀번호 변경' }));

    expect(userApiMocks.changeMyPasswordRequest)
      .toHaveBeenCalledWith('Password123!', 'NewPassword123!');
    expect(logoutMock).toHaveBeenCalledOnce();
  });
});
