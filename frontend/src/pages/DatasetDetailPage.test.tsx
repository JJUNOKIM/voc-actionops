import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { DatasetDetailPage } from './DatasetDetailPage';
import type { DatasetDetail, PageResponse, DatasetValidationError } from '../datasets/types';
import type { UserProfile } from '../types/api';
import { ApiError } from '../lib/api-client';

const detailMocks = vi.hoisted(() => ({
  datasetDetailRequest: vi.fn(),
  datasetValidationErrorsRequest: vi.fn(),
  useAuth: vi.fn(),
}));

vi.mock('../datasets/api', () => ({
  datasetDetailRequest: detailMocks.datasetDetailRequest,
  datasetValidationErrorsRequest: detailMocks.datasetValidationErrorsRequest,
}));
vi.mock('../auth/useAuth', () => ({ useAuth: detailMocks.useAuth }));

const admin: UserProfile = {
  id: 1,
  organizationId: 11,
  organizationName: 'VOC ActionOps Demo',
  email: 'admin@voc-actionops.local',
  name: 'Demo Admin',
  role: 'ADMIN',
};

const detail: DatasetDetail = {
  id: 17,
  name: '8월 앱 리뷰',
  sourceType: 'APP_REVIEW',
  fileUrl: 'local://dataset-files/11/file.csv',
  columnMapping: {
    review_text: 'content',
    score: 'rating',
  },
  status: 'VALIDATED',
  totalCount: 120,
  validCount: 117,
  invalidCount: 3,
  createdBy: 1,
  createdAt: '2026-08-20T13:30:00',
  updatedAt: '2026-08-20T13:31:00',
};

const validationErrors: PageResponse<DatasetValidationError> = {
  content: [
    {
      id: 31,
      rowNumber: 4,
      fieldName: 'rating',
      errorCode: 'INVALID_RATING_RANGE',
      errorMessage: 'rating must be between 0 and 5',
      rawRow: { review_text: '결제가 느려요', score: '6' },
      createdAt: '2026-08-20T13:30:00',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

describe('DatasetDetailPage', () => {
  beforeEach(() => {
    detailMocks.datasetDetailRequest.mockReset();
    detailMocks.datasetValidationErrorsRequest.mockReset();
    detailMocks.useAuth.mockReset();
    detailMocks.useAuth.mockReturnValue({ user: admin });
    detailMocks.datasetDetailRequest.mockResolvedValue(detail);
    detailMocks.datasetValidationErrorsRequest.mockResolvedValue(validationErrors);
  });

  it('renders detail, column mapping, and validation errors for an admin', async () => {
    renderDetailPage('/datasets/17');

    expect(await screen.findByRole('heading', { name: '8월 앱 리뷰' })).toBeInTheDocument();
    const countSummary = screen.getByRole('region', { name: '데이터 검증 요약' });
    expect(within(countSummary).getByText('120')).toBeInTheDocument();
    const mapping = screen.getByRole('heading', { name: '컬럼 매핑' }).closest('.column-mapping');
    expect(within(mapping as HTMLElement).getByText('review_text')).toBeInTheDocument();
    expect(within(mapping as HTMLElement).getByText('피드백 내용')).toBeInTheDocument();
    expect(await screen.findByText('평점 범위 오류')).toBeInTheDocument();
    expect(screen.getByText('원본 행 보기')).toBeInTheDocument();
    expect(detailMocks.datasetDetailRequest).toHaveBeenCalledWith(17);
    expect(detailMocks.datasetValidationErrorsRequest).toHaveBeenCalledWith(17, 0, 20);
  });

  it('does not request raw validation errors for a viewer', async () => {
    detailMocks.useAuth.mockReturnValue({ user: { ...admin, role: 'VIEWER' } });

    renderDetailPage('/datasets/17');

    expect(await screen.findByText('검증 오류 상세를 볼 수 없습니다.')).toBeInTheDocument();
    expect(detailMocks.datasetValidationErrorsRequest).not.toHaveBeenCalled();
  });

  it('skips the error request when every row is valid and rejects an invalid route id', async () => {
    detailMocks.datasetDetailRequest.mockResolvedValue({
      ...detail,
      validCount: 120,
      invalidCount: 0,
    });
    const { unmount } = renderDetailPage('/datasets/17');

    expect(await screen.findByText('검증 오류가 없습니다.')).toBeInTheDocument();
    expect(detailMocks.datasetValidationErrorsRequest).not.toHaveBeenCalled();

    unmount();
    detailMocks.datasetDetailRequest.mockClear();
    renderDetailPage('/datasets/not-a-number');

    await waitFor(() =>
      expect(screen.getByText('잘못된 데이터셋 주소입니다.')).toBeInTheDocument(),
    );
    expect(detailMocks.datasetDetailRequest).not.toHaveBeenCalled();
  });

  it('retries the detail request after an API error', async () => {
    const user = userEvent.setup();
    detailMocks.datasetDetailRequest
      .mockRejectedValueOnce(new ApiError(404, 'NOT_FOUND', '요청한 데이터를 찾을 수 없습니다.'))
      .mockResolvedValueOnce({ ...detail, invalidCount: 0, validCount: 120 });

    renderDetailPage('/datasets/17');

    expect(await screen.findByText('데이터셋을 불러오지 못했습니다.')).toBeInTheDocument();
    expect(screen.getByText('요청한 데이터를 찾을 수 없습니다.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    expect(await screen.findByRole('heading', { name: '8월 앱 리뷰' })).toBeInTheDocument();
    expect(detailMocks.datasetDetailRequest).toHaveBeenCalledTimes(2);
  });
});

function renderDetailPage(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/datasets/:datasetId" element={<DatasetDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}
