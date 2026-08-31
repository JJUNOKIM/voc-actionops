import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { IssuesPage } from './IssuesPage';
import type { PageResponse } from '../datasets/types';
import type { IssueSummary } from '../issues/types';

const pageMocks = vi.hoisted(() => ({ issuesRequest: vi.fn() }));

vi.mock('../issues/api', () => ({ issuesRequest: pageMocks.issuesRequest }));

const issuePage: PageResponse<IssueSummary> = {
  content: [
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
  ],
  page: 0,
  size: 20,
  totalElements: 21,
  totalPages: 2,
};

describe('IssuesPage', () => {
  beforeEach(() => {
    pageMocks.issuesRequest.mockReset();
    pageMocks.issuesRequest.mockResolvedValue(issuePage);
  });

  it('renders issue operations data and applies search filters', async () => {
    const user = userEvent.setup();
    renderIssuesPage();

    expect(await screen.findByRole('link', { name: '쿠폰 적용 후 결제 실패' })).toHaveAttribute(
      'href',
      '/issues/7',
    );
    expect(within(screen.getByRole('table', { name: '조직 이슈 목록' })).getByText('처리 중')).toBeInTheDocument();
    expect(screen.getByText('81.0%')).toBeInTheDocument();
    expect(screen.getByText('34건')).toBeInTheDocument();
    expect(pageMocks.issuesRequest).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      priority: undefined,
      status: undefined,
      keyword: undefined,
    });

    await user.selectOptions(screen.getByLabelText('우선순위'), 'P1');
    await user.selectOptions(screen.getByLabelText('이슈 상태'), 'IN_PROGRESS');
    await user.type(screen.getByLabelText('이슈 제목 검색'), ' 결제 오류 ');
    await user.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() =>
      expect(pageMocks.issuesRequest).toHaveBeenLastCalledWith({
        page: 0,
        size: 20,
        priority: 'P1',
        status: 'IN_PROGRESS',
        keyword: '결제 오류',
      }),
    );

    await user.click(screen.getByRole('button', { name: '다음 페이지' }));
    await waitFor(() =>
      expect(pageMocks.issuesRequest).toHaveBeenLastCalledWith({
        page: 1,
        size: 20,
        priority: 'P1',
        status: 'IN_PROGRESS',
        keyword: '결제 오류',
      }),
    );
  });

  it('clears active filters as one operation', async () => {
    const user = userEvent.setup();
    renderIssuesPage();
    await screen.findByText('쿠폰 적용 후 결제 실패');

    await user.selectOptions(screen.getByLabelText('우선순위'), 'P0');
    await user.click(screen.getByRole('button', { name: '필터 초기화' }));

    expect(screen.getByLabelText('우선순위')).toHaveValue('');
    await waitFor(() =>
      expect(pageMocks.issuesRequest).toHaveBeenLastCalledWith({
        page: 0,
        size: 20,
        priority: undefined,
        status: undefined,
        keyword: undefined,
      }),
    );
  });
});

function renderIssuesPage() {
  return render(
    <MemoryRouter>
      <IssuesPage />
    </MemoryRouter>,
  );
}
