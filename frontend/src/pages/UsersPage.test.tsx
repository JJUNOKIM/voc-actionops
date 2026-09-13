import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { UsersPage } from './UsersPage';

const userApiMocks = vi.hoisted(() => ({
  organizationUsersRequest: vi.fn(),
  changeOrganizationUserRoleRequest: vi.fn(),
  createOrganizationUserRequest: vi.fn(),
}));

const organizationApiMocks = vi.hoisted(() => ({
  changeOrganizationNameRequest: vi.fn(),
}));

const updateOrganizationNameMock = vi.hoisted(() => vi.fn());

vi.mock('../users/api', () => userApiMocks);
vi.mock('../organizations/api', () => organizationApiMocks);
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
    updateOrganizationName: updateOrganizationNameMock,
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
    userApiMocks.createOrganizationUserRequest.mockReset();
    organizationApiMocks.changeOrganizationNameRequest.mockReset();
    updateOrganizationNameMock.mockReset();
    userApiMocks.organizationUsersRequest.mockResolvedValue(users);
  });

  it('changes the organization name', async () => {
    const user = userEvent.setup();
    organizationApiMocks.changeOrganizationNameRequest.mockResolvedValue({
      id: 11,
      name: 'Customer Lab',
    });
    render(<UsersPage />);

    const nameInput = screen.getByLabelText('조직명');
    await user.clear(nameInput);
    await user.type(nameInput, 'Customer Lab');
    await user.click(screen.getByRole('button', { name: '저장' }));

    expect(organizationApiMocks.changeOrganizationNameRequest)
      .toHaveBeenCalledWith('Customer Lab');
    expect(updateOrganizationNameMock).toHaveBeenCalledWith('Customer Lab');
    expect(await screen.findByText('조직 정보를 변경했습니다.')).toBeInTheDocument();
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

  it('opens the user creation dialog', async () => {
    const user = userEvent.setup();
    render(<UsersPage />);

    await screen.findByText('Demo Viewer');
    await user.click(screen.getByRole('button', { name: '사용자 추가' }));

    expect(screen.getByRole('dialog', { name: '사용자 추가' })).toBeInTheDocument();
    expect(screen.getByLabelText('이름')).toHaveFocus();
  });

  it('adds a created user to the current list', async () => {
    const user = userEvent.setup();
    userApiMocks.createOrganizationUserRequest.mockResolvedValue({
      id: 3,
      email: 'cs@example.com',
      name: 'New CS',
      role: 'CS',
    });
    render(<UsersPage />);

    await screen.findByText('Demo Viewer');
    await user.click(screen.getByRole('button', { name: '사용자 추가' }));
    const dialog = screen.getByRole('dialog', { name: '사용자 추가' });
    await user.type(within(dialog).getByLabelText('이름'), 'New CS');
    await user.type(within(dialog).getByLabelText('이메일'), 'cs@example.com');
    await user.type(within(dialog).getByLabelText('초기 비밀번호'), 'Password123!');
    await user.selectOptions(within(dialog).getByLabelText('역할'), 'CS');
    await user.click(within(dialog).getByRole('button', { name: '사용자 추가' }));

    expect(await screen.findByText('New CS님을 조직 사용자로 추가했습니다.'))
      .toBeInTheDocument();
    expect(screen.getByText('3명')).toBeInTheDocument();
    expect(screen.getByText('cs@example.com')).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: '사용자 추가' })).not.toBeInTheDocument();
  });
});
