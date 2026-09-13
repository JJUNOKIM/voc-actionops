import { describe, expect, it, vi } from 'vitest';

import { changeOrganizationNameRequest } from './api';

const apiRequestMock = vi.hoisted(() => vi.fn());

vi.mock('../lib/api-client', () => ({ apiRequest: apiRequestMock }));

describe('organization API', () => {
  it('changes the current organization name', async () => {
    apiRequestMock.mockResolvedValue({ id: 11, name: 'Customer Lab' });

    await changeOrganizationNameRequest('Customer Lab');

    expect(apiRequestMock).toHaveBeenCalledWith('/api/v1/organizations/me', {
      method: 'PATCH',
      body: JSON.stringify({ name: 'Customer Lab' }),
    });
  });
});
