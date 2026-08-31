import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ActionCreateDialog } from './ActionCreateDialog';
import type { IssueAction } from './types';

const apiMocks = vi.hoisted(() => ({ createIssueActionRequest: vi.fn() }));

vi.mock('./api', () => apiMocks);

const createdAction: IssueAction = {
  id: 21,
  issueId: 7,
  title: '인증 토큰 갱신 흐름 확인',
  description: '변경 직후 세션 충돌 구간을 확인한다.',
  status: 'TODO',
  assigneeId: 3,
  assigneeName: '김개발',
  dueDate: '2026-09-05',
  createdAt: '2026-08-31T10:00:00',
  updatedAt: '2026-08-31T10:00:00',
  completedAt: null,
};

describe('ActionCreateDialog', () => {
  beforeEach(() => {
    apiMocks.createIssueActionRequest.mockReset();
    apiMocks.createIssueActionRequest.mockResolvedValue(createdAction);
  });

  it('validates required fields and submits normalized action data', async () => {
    const user = userEvent.setup();
    const onCreated = vi.fn();

    render(
      <ActionCreateDialog
        issueId={7}
        users={[
          { id: 3, email: 'developer@example.com', name: '김개발', role: 'DEVELOPER' },
        ]}
        onClose={vi.fn()}
        onCreated={onCreated}
      />,
    );

    await user.click(screen.getByRole('button', { name: '조치 등록' }));
    expect(screen.getByRole('alert')).toHaveTextContent('조치 제목을 입력해 주세요.');

    await user.type(screen.getByLabelText('조치 제목'), ' 인증 토큰 갱신 흐름 확인 ');
    await user.type(screen.getByLabelText('설명'), '변경 직후 세션 충돌 구간을 확인한다.');
    await user.selectOptions(screen.getByLabelText('담당자'), '3');
    await user.type(screen.getByLabelText('마감일'), '2026-09-05');
    await user.click(screen.getByRole('button', { name: '조치 등록' }));

    await waitFor(() =>
      expect(apiMocks.createIssueActionRequest).toHaveBeenCalledWith(7, {
        title: '인증 토큰 갱신 흐름 확인',
        description: '변경 직후 세션 충돌 구간을 확인한다.',
        assigneeId: 3,
        dueDate: '2026-09-05',
      }),
    );
    expect(onCreated).toHaveBeenCalledWith(createdAction);
  });
});
