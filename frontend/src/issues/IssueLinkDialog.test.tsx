import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { PageResponse } from '../datasets/types';
import { ApiError } from '../lib/api-client';
import { IssueLinkDialog } from './IssueLinkDialog';
import type { IssueSummary } from './types';

const dialogMocks = vi.hoisted(() => ({
  issuesRequest: vi.fn(),
  linkFeedbackToIssueRequest: vi.fn(),
}));

vi.mock('./api', () => dialogMocks);

const issues: IssueSummary[] = [
  {
    id: 7,
    title: '쿠폰 적용 후 결제 실패',
    category: 'PAYMENT',
    priority: 'P1',
    priorityScore: 71.5,
    status: 'IN_PROGRESS',
    assigneeId: 3,
    assigneeName: '김개발',
    feedbackCount: 42,
    negativeCount: 34,
    firstSeenAt: '2026-08-18T13:30:00',
    lastSeenAt: '2026-08-20T13:30:00',
    resolvedAt: null,
    createdAt: '2026-08-18T14:00:00',
    updatedAt: '2026-08-21T09:10:00',
  },
  {
    id: 8,
    title: '간편결제 승인 지연',
    category: 'PAYMENT',
    priority: 'P2',
    priorityScore: 48,
    status: 'TRIAGED',
    assigneeId: null,
    assigneeName: null,
    feedbackCount: 11,
    negativeCount: 7,
    firstSeenAt: '2026-08-22T11:00:00',
    lastSeenAt: '2026-08-23T10:30:00',
    resolvedAt: null,
    createdAt: '2026-08-22T11:10:00',
    updatedAt: '2026-08-23T10:30:00',
  },
];

const issuePage: PageResponse<IssueSummary> = {
  content: issues,
  page: 0,
  size: 8,
  totalElements: 2,
  totalPages: 1,
};

describe('IssueLinkDialog', () => {
  beforeEach(() => {
    dialogMocks.issuesRequest.mockReset();
    dialogMocks.linkFeedbackToIssueRequest.mockReset();
    dialogMocks.issuesRequest.mockResolvedValue(issuePage);
    dialogMocks.linkFeedbackToIssueRequest.mockResolvedValue({});
  });

  it('prevents duplicate selection and links the selected issue', async () => {
    const user = userEvent.setup();
    const onLinked = vi.fn();

    render(
      <IssueLinkDialog
        feedbackId={31}
        linkedIssueIds={[7]}
        onClose={vi.fn()}
        onLinked={onLinked}
      />,
    );

    expect(await screen.findByText('쿠폰 적용 후 결제 실패')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /쿠폰 적용 후 결제 실패/ })).toBeDisabled();
    expect(screen.getByText('연결됨')).toBeInTheDocument();

    await user.click(screen.getByRole('radio', { name: /간편결제 승인 지연/ }));
    await user.click(screen.getByRole('checkbox', { name: /대표 피드백으로 지정/ }));
    await user.click(screen.getByRole('button', { name: '선택 이슈 연결' }));

    expect(dialogMocks.linkFeedbackToIssueRequest).toHaveBeenCalledWith(31, 8, true);
    expect(onLinked).toHaveBeenCalledOnce();
  });

  it('trims the keyword and keeps it while changing pages', async () => {
    const user = userEvent.setup();
    dialogMocks.issuesRequest.mockImplementation(
      ({ page, keyword }: { page: number; keyword?: string }) =>
        Promise.resolve({
          ...issuePage,
          page,
          totalElements: keyword === undefined ? 2 : 9,
          totalPages: keyword === undefined ? 1 : 2,
        }),
    );

    render(
      <IssueLinkDialog
        feedbackId={31}
        linkedIssueIds={[]}
        onClose={vi.fn()}
        onLinked={vi.fn()}
      />,
    );

    await screen.findByText('최근 이슈');
    await user.type(screen.getByLabelText('이슈 검색어'), ' 결제 오류 ');
    await user.click(screen.getByRole('button', { name: '이슈 검색' }));

    await waitFor(() =>
      expect(dialogMocks.issuesRequest).toHaveBeenLastCalledWith({
        page: 0,
        size: 8,
        keyword: '결제 오류',
      }),
    );
    expect(await screen.findByText("'결제 오류' 검색 결과")).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다음 페이지' }));
    await waitFor(() =>
      expect(dialogMocks.issuesRequest).toHaveBeenLastCalledWith({
        page: 1,
        size: 8,
        keyword: '결제 오류',
      }),
    );
  });

  it('keeps the dialog open when the link request is rejected', async () => {
    const user = userEvent.setup();
    const onLinked = vi.fn();
    dialogMocks.linkFeedbackToIssueRequest.mockRejectedValue(
      new ApiError(409, 'ISSUE_FEEDBACK_ALREADY_LINKED', '이미 연결된 피드백입니다.'),
    );

    render(
      <IssueLinkDialog
        feedbackId={31}
        linkedIssueIds={[]}
        onClose={vi.fn()}
        onLinked={onLinked}
      />,
    );

    await user.click(await screen.findByRole('radio', { name: /간편결제 승인 지연/ }));
    await user.click(screen.getByRole('button', { name: '선택 이슈 연결' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('이미 연결된 피드백입니다.');
    expect(onLinked).not.toHaveBeenCalled();
    expect(screen.getByRole('dialog', { name: '기존 이슈 연결' })).toBeInTheDocument();
  });
});
