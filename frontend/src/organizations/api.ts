import { apiRequest } from '../lib/api-client';
import type { Organization } from '../types/api';

export function changeOrganizationNameRequest(name: string): Promise<Organization> {
  return apiRequest('/api/v1/organizations/me', {
    method: 'PATCH',
    body: JSON.stringify({ name }),
  });
}
