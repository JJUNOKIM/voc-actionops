import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { UsersPage } from './UsersPage';

const userApiMocks = vi.hoisted(() => ({
  organizationUsersRequest: vi.fn(),
  changeOrganizationUserRoleRequest: vi.fn(),
}));

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
  }),
}));

const users = [
  {
    id: 1,
    email: 'admin@example.com',
    name: 'Demo Admin',
    role: 'ADMIN' as const,
  },
  {
    id: 2,
    email: 'viewer@example.com',
    name: 'Demo Viewer',
    role: 'VIEWER' as const,
  },
];

describe('UsersPage', () => {
  beforeEach(() => {
    userApiMocks.organizationUsersRequest.mockReset();
    userApiMocks.changeOrganizationUserRoleRequest.mockReset();
    userApiMocks.organizationUsersRequest.mockResolvedValue(users);
  });

  it('shows organization users and keeps the current account read only', async () => {
    render(<UsersPage />);

    expect(await screen.findByText('Demo Viewer')).toBeInTheDocument();
    expect(screen.getByText('admin@example.com')).toBeInTheDocument();
    expect(screen.getByText('내 계정')).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Demo Admin 역할' })).not.toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Demo Viewer 역할' })).toHaveValue('VIEWER');
  });

  it('updates another organization user role', async () => {
    const user = userEvent.setup();
    userApiMocks.changeOrganizationUserRoleRequest.mockResolvedValue({
      ...users[1],
      role: 'DEVELOPER',
    });
    render(<UsersPage />);

    const roleSelect = await screen.findByRole('combobox', { name: 'Demo Viewer 역할' });
    await user.selectOptions(roleSelect, 'DEVELOPER');

    expect(userApiMocks.changeOrganizationUserRoleRequest).toHaveBeenCalledWith(2, 'DEVELOPER');
    expect(await screen.findByText('Demo Viewer님의 역할을 변경했습니다. (개발자)'))
      .toBeInTheDocument();
    expect(roleSelect).toHaveValue('DEVELOPER');
  });
});
