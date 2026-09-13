import { apiRequest } from '../lib/api-client';
import type { OrganizationUser, Role } from '../types/api';

export function organizationUsersRequest(): Promise<OrganizationUser[]> {
  return apiRequest('/api/v1/users');
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
