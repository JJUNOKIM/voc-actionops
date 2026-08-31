import { describe, expect, it, vi } from 'vitest';

import { organizationUsersRequest } from './api';

const apiRequestMock = vi.hoisted(() => vi.fn());

vi.mock('../lib/api-client', () => ({ apiRequest: apiRequestMock }));

describe('user API', () => {
  it('requests organization users', async () => {
    apiRequestMock.mockResolvedValue([]);

    await organizationUsersRequest();

    expect(apiRequestMock).toHaveBeenCalledWith('/api/v1/users');
  });
});
