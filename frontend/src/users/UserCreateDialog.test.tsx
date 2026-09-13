import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../lib/api-client';
import type { OrganizationUser } from '../types/api';
import { UserCreateDialog } from './UserCreateDialog';

const apiMocks = vi.hoisted(() => ({ createOrganizationUserRequest: vi.fn() }));

vi.mock('./api', () => apiMocks);

const createdUser: OrganizationUser = {
  id: 7,
  email: 'developer@example.com',
  name: 'Demo Developer',
  role: 'DEVELOPER',
};

describe('UserCreateDialog', () => {
  beforeEach(() => {
    apiMocks.createOrganizationUserRequest.mockReset();
    apiMocks.createOrganizationUserRequest.mockResolvedValue(createdUser);
  });

  it('validates and submits normalized user data', async () => {
    const user = userEvent.setup();
    const onCreated = vi.fn();
    render(<UserCreateDialog onClose={vi.fn()} onCreated={onCreated} />);

    await user.click(screen.getByRole('button', { name: '사용자 추가' }));
    expect(screen.getByRole('alert')).toHaveTextContent('이름을 입력해 주세요.');

    await user.type(screen.getByLabelText('이름'), ' Demo Developer ');
    await user.type(screen.getByLabelText('이메일'), ' developer@example.com ');
    await user.selectOptions(screen.getByLabelText('역할'), 'DEVELOPER');
    await user.type(screen.getByLabelText('초기 비밀번호'), 'Password123!');
    await user.click(screen.getByRole('button', { name: '사용자 추가' }));

    await waitFor(() =>
      expect(apiMocks.createOrganizationUserRequest).toHaveBeenCalledWith({
        email: 'developer@example.com',
        password: 'Password123!',
        name: 'Demo Developer',
        role: 'DEVELOPER',
      }),
    );
    expect(onCreated).toHaveBeenCalledWith(createdUser);
  });

  it('shows the API error when the email already exists', async () => {
    const user = userEvent.setup();
    apiMocks.createOrganizationUserRequest.mockRejectedValue(
      new ApiError(409, 'DUPLICATED_RESOURCE', '이미 존재하는 데이터입니다.'),
    );
    render(<UserCreateDialog onClose={vi.fn()} onCreated={vi.fn()} />);

    await user.type(screen.getByLabelText('이름'), 'Existing User');
    await user.type(screen.getByLabelText('이메일'), 'existing@example.com');
    await user.type(screen.getByLabelText('초기 비밀번호'), 'Password123!');
    await user.click(screen.getByRole('button', { name: '사용자 추가' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('이미 존재하는 데이터입니다.');
  });
});
