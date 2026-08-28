import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ANALYSIS_POLL_INTERVAL_MS, DatasetAnalysisSection } from './DatasetAnalysisSection';
import type { AnalysisJobView } from './types';
import { ApiError } from '../lib/api-client';

const analysisApiMocks = vi.hoisted(() => ({
  datasetAnalysisStatusRequest: vi.fn(),
  startDatasetAnalysisRequest: vi.fn(),
}));

vi.mock('./api', () => analysisApiMocks);

const pendingJob: AnalysisJobView = {
  datasetId: 17,
  status: 'ANALYZING',
  jobId: 'job-17',
  jobStatus: 'PENDING',
  totalCount: 10,
  processedCount: 0,
  successCount: 0,
  failedCount: 0,
  progressRate: 0,
  failureReason: null,
  startedAt: null,
  completedAt: null,
};

const runningJob: AnalysisJobView = {
  ...pendingJob,
  jobStatus: 'RUNNING',
  processedCount: 4,
  successCount: 4,
  progressRate: 40,
  startedAt: '2026-08-28T10:00:00',
};

const completedJob: AnalysisJobView = {
  ...runningJob,
  status: 'ANALYZED',
  jobStatus: 'COMPLETED',
  processedCount: 10,
  successCount: 10,
  progressRate: 100,
  completedAt: '2026-08-28T10:01:00',
};

describe('DatasetAnalysisSection', () => {
  beforeEach(() => {
    analysisApiMocks.datasetAnalysisStatusRequest.mockReset();
    analysisApiMocks.startDatasetAnalysisRequest.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('starts a validated dataset for an allowed role', async () => {
    const user = userEvent.setup();
    const onDatasetStatusChange = vi.fn();
    analysisApiMocks.startDatasetAnalysisRequest.mockResolvedValue(pendingJob);

    render(
      <DatasetAnalysisSection
        datasetId={17}
        datasetStatus="VALIDATED"
        totalCount={10}
        canStart
        onDatasetStatusChange={onDatasetStatusChange}
      />,
    );

    await user.click(screen.getByRole('button', { name: '분석 시작' }));

    expect(await screen.findByText('분석 작업이 대기 중입니다.')).toBeInTheDocument();
    expect(analysisApiMocks.startDatasetAnalysisRequest).toHaveBeenCalledWith(17);
    expect(onDatasetStatusChange).toHaveBeenCalledWith('ANALYZING');
    expect(screen.getByRole('progressbar', { name: 'AI 분석 진행률' })).toHaveAttribute(
      'aria-valuenow',
      '0',
    );
  });

  it('polls an active job and stops after completion', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const onDatasetStatusChange = vi.fn();
    analysisApiMocks.datasetAnalysisStatusRequest
      .mockResolvedValueOnce(runningJob)
      .mockResolvedValueOnce(completedJob);

    render(
      <MemoryRouter>
        <DatasetAnalysisSection
          datasetId={17}
          datasetStatus="ANALYZING"
          totalCount={10}
          canStart
          onDatasetStatusChange={onDatasetStatusChange}
        />
      </MemoryRouter>,
    );

    expect(await screen.findByText('피드백을 분석하고 있습니다.')).toBeInTheDocument();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(ANALYSIS_POLL_INTERVAL_MS);
    });

    expect(await screen.findByText('모든 피드백 분석이 완료되었습니다.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '분석 결과 보기' })).toHaveAttribute(
      'href',
      '/feedbacks?datasetId=17',
    );
    expect(analysisApiMocks.datasetAnalysisStatusRequest).toHaveBeenCalledTimes(2);
    expect(onDatasetStatusChange).toHaveBeenLastCalledWith('ANALYZED');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(ANALYSIS_POLL_INTERVAL_MS * 2);
    });
    expect(analysisApiMocks.datasetAnalysisStatusRequest).toHaveBeenCalledTimes(2);
  });

  it('does not expose the start command to a read-only role', () => {
    render(
      <DatasetAnalysisSection
        datasetId={17}
        datasetStatus="VALIDATED"
        totalCount={10}
        canStart={false}
        onDatasetStatusChange={vi.fn()}
      />,
    );

    expect(screen.queryByRole('button', { name: '분석 시작' })).not.toBeInTheDocument();
    expect(screen.getByText('관리자 또는 PM만 시작할 수 있습니다.')).toBeInTheDocument();
    expect(analysisApiMocks.datasetAnalysisStatusRequest).not.toHaveBeenCalled();
  });

  it('retries a failed status request', async () => {
    const user = userEvent.setup();
    analysisApiMocks.datasetAnalysisStatusRequest
      .mockRejectedValueOnce(new ApiError(0, 'NETWORK_ERROR', '서버에 연결할 수 없습니다.'))
      .mockResolvedValueOnce(completedJob);

    render(
      <MemoryRouter>
        <DatasetAnalysisSection
          datasetId={17}
          datasetStatus="ANALYZED"
          totalCount={10}
          canStart
          onDatasetStatusChange={vi.fn()}
        />
      </MemoryRouter>,
    );

    expect(await screen.findByText('분석 상태를 불러오지 못했습니다.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '다시 조회' }));

    await waitFor(() =>
      expect(screen.getByText('모든 피드백 분석이 완료되었습니다.')).toBeInTheDocument(),
    );
    expect(analysisApiMocks.datasetAnalysisStatusRequest).toHaveBeenCalledTimes(2);
  });
});
