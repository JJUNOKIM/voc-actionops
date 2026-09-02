import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../lib/api-client';
import { IssueDraftDialog } from './IssueDraftDialog';
import type { IssueDraft } from './types';

const draftMocks = vi.hoisted(() => ({
  confirmIssueDraftRequest: vi.fn(),
  issueDraftRequest: vi.fn(),
  organizationUsersRequest: vi.fn(),
}));

vi.mock('./api', () => ({
  confirmIssueDraftRequest: draftMocks.confirmIssueDraftRequest,
  issueDraftRequest: draftMocks.issueDraftRequest,
}));
vi.mock('../users/api', () => ({
  organizationUsersRequest: draftMocks.organizationUsersRequest,
}));

const draft: IssueDraft = {
  feedbackId: 31,
  analysisVersion: 2,
  title: '쿠폰 적용 후 결제 실패',
  description: '쿠폰을 적용하면 결제가 완료되지 않는다.',
  category: 'PAYMENT',
  sentiment: 'NEGATIVE',
  urgencyScore: 0.9,
  confidenceScore: 0.88,
};

describe('IssueDraftDialog', () => {
  beforeEach(() => {
    draftMocks.confirmIssueDraftRequest.mockReset();
    draftMocks.issueDraftRequest.mockReset();
    draftMocks.organizationUsersRequest.mockReset();
    draftMocks.issueDraftRequest.mockResolvedValue(draft);
    draftMocks.organizationUsersRequest.mockResolvedValue([
      { id: 3, email: 'dev@example.com', name: '김개발', role: 'DEVELOPER' },
    ]);
  });

  it('allows the generated draft to be edited before confirmation', async () => {
    const user = userEvent.setup();
    const onCreated = vi.fn();
    draftMocks.confirmIssueDraftRequest.mockResolvedValue({});

    render(<IssueDraftDialog feedbackId={31} onClose={vi.fn()} onCreated={onCreated} />);

    const titleInput = await screen.findByRole('textbox', { name: '이슈 제목' });
    await user.clear(titleInput);
    await user.type(titleInput, '쿠폰 주문 결제 오류');
    await user.selectOptions(screen.getByRole('combobox', { name: '담당자' }), '3');
    await user.click(screen.getByRole('button', { name: '이슈 등록' }));

    expect(draftMocks.confirmIssueDraftRequest).toHaveBeenCalledWith(31, {
      analysisVersion: 2,
      title: '쿠폰 주문 결제 오류',
      description: draft.description,
      assigneeId: 3,
    });
    expect(onCreated).toHaveBeenCalledOnce();
  });

  it('shows a stale draft error without closing the dialog', async () => {
    const user = userEvent.setup();
    draftMocks.confirmIssueDraftRequest.mockRejectedValue(
      new ApiError(409, 'STALE_RESOURCE', '최신 상태를 다시 조회해 주세요.'),
    );

    render(<IssueDraftDialog feedbackId={31} onClose={vi.fn()} onCreated={vi.fn()} />);

    await screen.findByRole('textbox', { name: '이슈 제목' });
    await user.click(screen.getByRole('button', { name: '이슈 등록' }));

    expect(await screen.findByText('최신 상태를 다시 조회해 주세요.')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});
