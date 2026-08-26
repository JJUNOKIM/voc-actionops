import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { DatasetsPage } from './DatasetsPage';
import type { UserProfile } from '../types/api';

const pageMocks = vi.hoisted(() => ({
  datasetsRequest: vi.fn(),
  useAuth: vi.fn(),
}));

vi.mock('../datasets/api', () => ({ datasetsRequest: pageMocks.datasetsRequest }));
vi.mock('../auth/useAuth', () => ({ useAuth: pageMocks.useAuth }));

const admin: UserProfile = {
  id: 1,
  organizationId: 11,
  organizationName: 'VOC ActionOps Demo',
  email: 'admin@voc-actionops.local',
  name: 'Demo Admin',
  role: 'ADMIN',
};

const firstPage = {
  content: [
    {
      id: 1,
      name: '2026년 8월 앱 리뷰',
      sourceType: 'APP_REVIEW',
      status: 'VALIDATED',
      totalCount: 120,
      validCount: 117,
      invalidCount: 3,
      createdAt: '2026-08-20T13:30:00',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 21,
  totalPages: 2,
} as const;

describe('DatasetsPage', () => {
  beforeEach(() => {
    pageMocks.datasetsRequest.mockReset();
    pageMocks.useAuth.mockReset();
    pageMocks.useAuth.mockReturnValue({ user: admin });
    pageMocks.datasetsRequest.mockResolvedValue(firstPage);
  });

  it('renders the dataset list and sends filters and pagination to the API', async () => {
    const user = userEvent.setup();
    render(<DatasetsPage />);

    expect(await screen.findByText('2026년 8월 앱 리뷰')).toBeInTheDocument();
    expect(screen.getAllByText('분석 대기')).toHaveLength(2);
    expect(screen.getByText('117')).toBeInTheDocument();
    expect(pageMocks.datasetsRequest).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      sourceType: undefined,
      status: undefined,
    });

    await user.selectOptions(screen.getByLabelText('데이터 출처'), 'APP_REVIEW');
    await waitFor(() =>
      expect(pageMocks.datasetsRequest).toHaveBeenLastCalledWith({
        page: 0,
        size: 20,
        sourceType: 'APP_REVIEW',
        status: undefined,
      }),
    );

    await user.click(screen.getByRole('button', { name: '다음 페이지' }));
    await waitFor(() =>
      expect(pageMocks.datasetsRequest).toHaveBeenLastCalledWith({
        page: 1,
        size: 20,
        sourceType: 'APP_REVIEW',
        status: undefined,
      }),
    );
  });

  it('only exposes the upload command to allowed roles', async () => {
    const { rerender } = render(<DatasetsPage />);

    expect(await screen.findByRole('button', { name: '데이터셋 추가' })).toBeInTheDocument();

    pageMocks.useAuth.mockReturnValue({ user: { ...admin, role: 'VIEWER' } });
    rerender(<DatasetsPage />);

    expect(screen.queryByRole('button', { name: '데이터셋 추가' })).not.toBeInTheDocument();
  });
});
