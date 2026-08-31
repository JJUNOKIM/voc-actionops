import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { IssueManagementPanel } from './IssueManagementPanel';
import type { IssueDetail } from './types';
import type { UserProfile } from '../types/api';

const apiMocks = vi.hoisted(() => ({
  assignIssueRequest: vi.fn(),
  changeIssueStatusRequest: vi.fn(),
}));

vi.mock('./api', () => apiMocks);

const admin: UserProfile = {
  id: 1,
  organizationId: 11,
  organizationName: 'VOC ActionOps Demo',
  email: 'admin@example.com',
  name: '관리자',
  role: 'ADMIN',
};

const issue = {
  id: 7,
  status: 'TRIAGED',
  assigneeId: null,
  assigneeName: null,
} as IssueDetail;

const users = [
  { id: 1, email: admin.email, name: admin.name, role: admin.role },
  { id: 3, email: 'developer@example.com', name: '김개발', role: 'DEVELOPER' as const },
];

describe('IssueManagementPanel', () => {
  beforeEach(() => {
    apiMocks.assignIssueRequest.mockReset();
    apiMocks.changeIssueStatusRequest.mockReset();
  });

  it('requires an assignee before moving a triaged issue forward', async () => {
    const user = userEvent.setup();
    const onIssueUpdated = vi.fn();
    const assignedIssue = { ...issue, assigneeId: 3, assigneeName: '김개발' };
    apiMocks.assignIssueRequest.mockResolvedValue(assignedIssue);

    renderPanel(issue, onIssueUpdated);

    expect(screen.getByRole('button', { name: '담당 지정으로 변경' })).toBeDisabled();
    expect(screen.getByText(/담당자를 지정한 뒤/)).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('담당자 선택'), '3');
    await user.click(screen.getByRole('button', { name: '담당자 저장' }));

    await waitFor(() => expect(apiMocks.assignIssueRequest).toHaveBeenCalledWith(7, 3));
    expect(onIssueUpdated).toHaveBeenCalledWith(assignedIssue);
    expect(await screen.findByText('담당자를 변경했습니다.')).toBeInTheDocument();
  });

  it('changes only to the next supported issue status', async () => {
    const user = userEvent.setup();
    const onIssueUpdated = vi.fn();
    const inProgressIssue = {
      ...issue,
      status: 'IN_PROGRESS' as const,
      assigneeId: 3,
      assigneeName: '김개발',
    };
    const resolvedIssue = { ...inProgressIssue, status: 'RESOLVED' as const };
    apiMocks.changeIssueStatusRequest.mockResolvedValue(resolvedIssue);

    renderPanel(inProgressIssue, onIssueUpdated);
    expect(screen.queryByRole('button', { name: '이슈 종료' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '해결로 변경' }));

    await waitFor(() =>
      expect(apiMocks.changeIssueStatusRequest).toHaveBeenCalledWith(7, 'RESOLVED'),
    );
    expect(onIssueUpdated).toHaveBeenCalledWith(resolvedIssue);
  });
});

function renderPanel(currentIssue: IssueDetail, onIssueUpdated: (issue: IssueDetail) => void) {
  return render(
    <IssueManagementPanel
      issue={currentIssue}
      user={admin}
      users={users}
      usersLoading={false}
      usersError={null}
      onRetryUsers={vi.fn()}
      onIssueUpdated={onIssueUpdated}
    />,
  );
}
