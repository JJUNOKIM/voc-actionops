import { apiRequest } from '../lib/api-client';
import type { OrganizationUser, Role } from '../types/api';

export interface CreateOrganizationUserRequest {
  email: string;
  password: string;
  name: string;
  role: Role;
}

export function organizationUsersRequest(): Promise<OrganizationUser[]> {
  return apiRequest('/api/v1/users');
}

export function createOrganizationUserRequest(
  request: CreateOrganizationUserRequest,
): Promise<OrganizationUser> {
  return apiRequest('/api/v1/users', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function changeOrganizationUserRoleRequest(
  userId: number,
  role: Role,
): Promise<OrganizationUser> {
  return apiRequest(`/api/v1/users/${userId}/role`, {
    method: 'PATCH',
    body: JSON.stringify({ role }),
  });
}

export function changeMyPasswordRequest(
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  return apiRequest('/api/v1/users/me/password', {
    method: 'PATCH',
    body: JSON.stringify({ currentPassword, newPassword }),
  });
}
