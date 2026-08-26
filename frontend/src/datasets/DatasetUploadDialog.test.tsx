import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { DatasetUploadDialog } from './DatasetUploadDialog';

const uploadDatasetRequestMock = vi.hoisted(() => vi.fn());

vi.mock('./api', () => ({ uploadDatasetRequest: uploadDatasetRequestMock }));

describe('DatasetUploadDialog', () => {
  beforeEach(() => {
    uploadDatasetRequestMock.mockReset();
  });

  it('reads CSV headers, suggests mappings, and submits the upload', async () => {
    const user = userEvent.setup();
    const onUploaded = vi.fn();
    uploadDatasetRequestMock.mockResolvedValue({
      datasetId: 8,
      status: 'VALIDATED',
      totalCount: 2,
      validCount: 2,
      invalidCount: 0,
    });
    render(<DatasetUploadDialog onClose={vi.fn()} onUploaded={onUploaded} />);

    const file = new File(
      ['review_id,review_text,score\nr-1,좋아요,5\nr-2,느려요,2'],
      'august-reviews.csv',
      { type: 'text/csv' },
    );
    await user.upload(screen.getByLabelText('CSV 파일'), file);

    expect(await screen.findByDisplayValue('august-reviews')).toBeInTheDocument();
    expect(screen.getByLabelText('review_text 매핑')).toHaveValue('content');
    expect(screen.getByLabelText('score 매핑')).toHaveValue('rating');

    await user.click(screen.getByRole('button', { name: '업로드' }));

    await waitFor(() => expect(uploadDatasetRequestMock).toHaveBeenCalledTimes(1));
    expect(uploadDatasetRequestMock).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'august-reviews',
        sourceType: 'APP_REVIEW',
        file,
        columnMapping: {
          review_id: 'external_id',
          review_text: 'content',
          score: 'rating',
        },
      }),
    );
    expect(onUploaded).toHaveBeenCalledWith(expect.objectContaining({ datasetId: 8 }));
  });

  it('shows a client validation error when content is not mapped', async () => {
    const user = userEvent.setup();
    render(<DatasetUploadDialog onClose={vi.fn()} onUploaded={vi.fn()} />);
    const file = new File(['unknown,score\ntext,3'], 'unknown.csv', { type: 'text/csv' });

    await user.upload(screen.getByLabelText('CSV 파일'), file);
    await screen.findByDisplayValue('unknown');
    await user.click(screen.getByRole('button', { name: '업로드' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '피드백 내용 컬럼을 반드시 매핑해 주세요.',
    );
    expect(uploadDatasetRequestMock).not.toHaveBeenCalled();
  });
});
