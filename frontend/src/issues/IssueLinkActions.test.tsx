import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../lib/api-client';
import { IssueLinkActions } from './IssueLinkActions';
import type { FeedbackIssue } from './types';

const actionMocks = vi.hoisted(() => ({
  changeFeedbackRepresentativeRequest: vi.fn(),
  unlinkFeedbackRequest: vi.fn(),
}));

vi.mock('./api', () => actionMocks);

const issue: FeedbackIssue = {
  linkId: 21,
  issueId: 7,
  title: '쿠폰 적용 후 결제 실패',
  category: 'PAYMENT',
  priority: 'P1',
  status: 'IN_PROGRESS',
  assigneeId: 3,
  assigneeName: '김개발',
  similarityScore: null,
  representative: false,
  linkedBy: 'MANUAL',
  linkedAt: '2026-09-02T14:30:00',
};

describe('IssueLinkActions', () => {
  beforeEach(() => {
    actionMocks.changeFeedbackRepresentativeRequest.mockReset();
    actionMocks.unlinkFeedbackRequest.mockReset();
  });

  it.each([false, true])('changes representative from %s to its opposite', async (representative) => {
    const user = userEvent.setup();
    const onChanged = vi.fn();
    actionMocks.changeFeedbackRepresentativeRequest.mockResolvedValue({});
    render(<IssueLinkActions feedbackId={31} issue={{ ...issue, representative }}
      onChanged={onChanged} onError={vi.fn()} />);

    const button = screen.getByRole('button', { name: /대표 피드백 지정/ });
    expect(button).toHaveAttribute('aria-pressed', String(representative));
    await user.click(button);

    expect(actionMocks.changeFeedbackRepresentativeRequest).toHaveBeenCalledWith(31, 7, !representative);
    expect(onChanged).toHaveBeenCalledOnce();
  });

  it('reports a failed representative change without changing the displayed value', async () => {
    const user = userEvent.setup();
    const onError = vi.fn();
    const onChanged = vi.fn();
    actionMocks.changeFeedbackRepresentativeRequest.mockRejectedValue(
      new ApiError(500, 'INTERNAL_ERROR', '일시적인 오류입니다.'),
    );
    render(<IssueLinkActions feedbackId={31} issue={issue} onChanged={onChanged} onError={onError} />);

    await user.click(screen.getByRole('button', { name: '대표 피드백 지정' }));

    expect(onError).toHaveBeenCalledWith('일시적인 오류입니다.');
    expect(onChanged).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: '대표 피드백 지정' })).toHaveAttribute('aria-pressed', 'false');
  });

  it('requires confirmation, restores focus on cancel, and allows retry after a failed unlink', async () => {
    const user = userEvent.setup();
    const onChanged = vi.fn();
    actionMocks.unlinkFeedbackRequest
      .mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', '연결 해제에 실패했습니다.'))
      .mockResolvedValueOnce(undefined);
    render(<IssueLinkActions feedbackId={31} issue={issue} onChanged={onChanged} onError={vi.fn()} />);

    const trigger = screen.getByRole('button', { name: '이슈 연결 해제' });
    await user.click(trigger);
    expect(screen.getByRole('dialog', { name: '이슈 연결 해제' })).toHaveTextContent(issue.title);
    await user.click(screen.getByRole('button', { name: '취소' }));
    expect(trigger).toHaveFocus();
    expect(actionMocks.unlinkFeedbackRequest).not.toHaveBeenCalled();

    await user.click(trigger);
    await user.click(screen.getByRole('button', { name: '연결 해제' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('연결 해제에 실패했습니다.');
    expect(onChanged).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: '연결 해제' }));

    expect(actionMocks.unlinkFeedbackRequest).toHaveBeenLastCalledWith(31, 7);
    expect(onChanged).toHaveBeenCalledWith('이슈 연결을 해제했습니다.');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(document.body.style.overflow).toBe('');
  });
});
