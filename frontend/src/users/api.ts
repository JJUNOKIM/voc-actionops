import { apiRequest } from '../lib/api-client';
import type { OrganizationUser } from '../types/api';

export function organizationUsersRequest(): Promise<OrganizationUser[]> {
  return apiRequest('/api/v1/users');
}
