import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  assignIssueRequest,
  changeActionStatusRequest,
  changeIssueStatusRequest,
  createIssueActionRequest,
  issueDetailRequest,
  issueFeedbacksRequest,
  issuesRequest,
} from './api';

const apiRequestMock = vi.hoisted(() => vi.fn());

vi.mock('../lib/api-client', () => ({ apiRequest: apiRequestMock }));

describe('issue API', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
  });

  it('serializes issue list filters', async () => {
    apiRequestMock.mockResolvedValue({ content: [] });

    await issuesRequest({
      page: 1,
      size: 20,
      status: 'IN_PROGRESS',
      priority: 'P1',
      category: 'PAYMENT',
      assigneeId: 3,
      keyword: '결제 오류',
      from: '2026-08-01',
      to: '2026-08-31',
    });

    expect(apiRequestMock).toHaveBeenCalledWith(
      '/api/v1/issues?page=1&size=20&status=IN_PROGRESS&priority=P1&category=PAYMENT&assigneeId=3&keyword=%EA%B2%B0%EC%A0%9C+%EC%98%A4%EB%A5%98&from=2026-08-01&to=2026-08-31',
    );
  });

  it('requests issue detail and related feedback', async () => {
    apiRequestMock.mockResolvedValue({});

    await issueDetailRequest(7);
    await issueFeedbacksRequest(7, 2, 10, true);

    expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/v1/issues/7');
    expect(apiRequestMock).toHaveBeenNthCalledWith(
      2,
      '/api/v1/issues/7/feedbacks?representativeOnly=true&page=2&size=10',
    );
  });

  it('sends issue and action mutations to their dedicated endpoints', async () => {
    apiRequestMock.mockResolvedValue({});

    await assignIssueRequest(7, 3);
    await changeIssueStatusRequest(7, 'IN_PROGRESS');
    await createIssueActionRequest(7, {
      title: '로그 확인',
      assigneeId: 3,
      dueDate: '2026-09-05',
    });
    await changeActionStatusRequest(21, 'DONE');

    expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/v1/issues/7/assignee', {
      method: 'PATCH',
      body: JSON.stringify({ assigneeId: 3 }),
    });
    expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/v1/issues/7/status', {
      method: 'PATCH',
      body: JSON.stringify({ status: 'IN_PROGRESS' }),
    });
    expect(apiRequestMock).toHaveBeenNthCalledWith(3, '/api/v1/issues/7/actions', {
      method: 'POST',
      body: JSON.stringify({ title: '로그 확인', assigneeId: 3, dueDate: '2026-09-05' }),
    });
    expect(apiRequestMock).toHaveBeenNthCalledWith(4, '/api/v1/actions/21/status', {
      method: 'PATCH',
      body: JSON.stringify({ status: 'DONE' }),
    });
  });
});
