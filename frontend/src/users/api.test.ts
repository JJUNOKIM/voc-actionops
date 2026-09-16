import { describe, expect, it, vi } from 'vitest';

import {
  changeOrganizationUserRoleRequest,
  changeMyPasswordRequest,
  createOrganizationUserRequest,
  organizationUsersRequest,
} from './api';

const apiRequestMock = vi.hoisted(() => vi.fn());

vi.mock('../lib/api-client', () => ({ apiRequest: apiRequestMock }));

describe('user API', () => {
  it('requests organization users', async () => {
    apiRequestMock.mockResolvedValue([]);

    await organizationUsersRequest();

    expect(apiRequestMock).toHaveBeenCalledWith('/api/v1/users');
  });

  it('changes an organization user role', async () => {
    apiRequestMock.mockResolvedValue({
      id: 2,
      email: 'developer@example.com',
      name: 'Developer',
      role: 'DEVELOPER',
    });

    await changeOrganizationUserRoleRequest(2, 'DEVELOPER');

    expect(apiRequestMock).toHaveBeenCalledWith('/api/v1/users/2/role', {
      method: 'PATCH',
      body: JSON.stringify({ role: 'DEVELOPER' }),
    });
  });

  it('creates an organization user', async () => {
    const request = {
      email: 'cs@example.com',
      password: 'Password123!',
      name: 'CS User',
      role: 'CS' as const,
    };
    apiRequestMock.mockResolvedValue({ id: 3, ...request });

    await createOrganizationUserRequest(request);

    expect(apiRequestMock).toHaveBeenCalledWith('/api/v1/users', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  });

  it('changes the current user password', async () => {
    apiRequestMock.mockResolvedValue(undefined);

    await changeMyPasswordRequest('Password123!', 'NewPassword123!');

    expect(apiRequestMock).toHaveBeenCalledWith('/api/v1/users/me/password', {
      method: 'PATCH',
      body: JSON.stringify({
        currentPassword: 'Password123!',
        newPassword: 'NewPassword123!',
      }),
    });
  });
});
