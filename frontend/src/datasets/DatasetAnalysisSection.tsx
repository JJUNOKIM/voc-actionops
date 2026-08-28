import {
  AlertCircle,
  BrainCircuit,
  CheckCircle2,
  Clock3,
  LoaderCircle,
  RefreshCw,
  TriangleAlert,
} from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';

import { ApiError } from '../lib/api-client';
import { datasetAnalysisStatusRequest, startDatasetAnalysisRequest } from './api';
import { formatDate, formatNumber } from './format';
import { analysisJobStatusLabel, isActiveAnalysisJob } from './labels';
import type { AnalysisJobView, DatasetStatus } from './types';

export const ANALYSIS_POLL_INTERVAL_MS = 1_500;

type AnalysisState =
  | { datasetId: number; phase: 'starting' }
  | { datasetId: number; phase: 'success'; job: AnalysisJobView }
  | { datasetId: number; phase: 'error'; message: string }
  | null;

interface DatasetAnalysisSectionProps {
  datasetId: number;
  datasetStatus: DatasetStatus;
  totalCount: number;
  canStart: boolean;
  onDatasetStatusChange: (status: DatasetStatus) => void;
}

export function DatasetAnalysisSection({
  datasetId,
  datasetStatus,
  totalCount,
  canStart,
  onDatasetStatusChange,
}: DatasetAnalysisSectionProps) {
  const [state, setState] = useState<AnalysisState>(null);
  const [statusReload, setStatusReload] = useState(0);
  const requestSequence = useRef(0);
  const currentState = state?.datasetId === datasetId ? state : null;
  const job = currentState?.phase === 'success' ? currentState.job : null;
  const shouldLoadExisting =
    datasetStatus === 'ANALYZING' || datasetStatus === 'ANALYZED' || datasetStatus === 'FAILED';

  const loadStatus = useCallback(() => {
    const sequence = requestSequence.current + 1;
    requestSequence.current = sequence;
    void datasetAnalysisStatusRequest(datasetId)
      .then((nextJob) => {
        if (requestSequence.current === sequence) {
          setState({ datasetId, phase: 'success', job: nextJob });
          onDatasetStatusChange(nextJob.status);
        }
      })
      .catch((error: unknown) => {
        if (requestSequence.current === sequence) {
          setState({
            datasetId,
            phase: 'error',
            message:
              error instanceof ApiError ? error.message : '분석 상태를 불러올 수 없습니다.',
          });
        }
      });
  }, [datasetId, onDatasetStatusChange]);

  useEffect(() => {
    if (!shouldLoadExisting) {
      requestSequence.current += 1;
      return;
    }
    loadStatus();
  }, [loadStatus, shouldLoadExisting, statusReload]);

  useEffect(() => {
    if (job === null || !isActiveAnalysisJob(job.jobStatus)) return;
    const timeoutId = window.setTimeout(loadStatus, ANALYSIS_POLL_INTERVAL_MS);
    return () => window.clearTimeout(timeoutId);
  }, [job, loadStatus]);

  async function startAnalysis() {
    const sequence = requestSequence.current + 1;
    requestSequence.current = sequence;
    setState({ datasetId, phase: 'starting' });
    try {
      const nextJob = await startDatasetAnalysisRequest(datasetId);
      if (requestSequence.current === sequence) {
        setState({ datasetId, phase: 'success', job: nextJob });
        onDatasetStatusChange(nextJob.status);
      }
    } catch (error) {
      if (requestSequence.current === sequence) {
        setState({
          datasetId,
          phase: 'error',
          message: error instanceof ApiError ? error.message : '분석 작업을 시작할 수 없습니다.',
        });
      }
    }
  }

  function retryStatus() {
    setState(null);
    setStatusReload((current) => current + 1);
  }

  return (
    <section className="dataset-detail-section analysis-section" aria-labelledby="analysis-title">
      <header className="detail-section-header">
        <div>
          <p className="section-label">AI ANALYSIS</p>
          <h2 id="analysis-title">AI 분석</h2>
        </div>
        {job !== null && (
          <span className={`analysis-job-badge analysis-job-badge--${analysisJobTone(job)}`}>
            {analysisJobStatusLabel(job.jobStatus)}
          </span>
        )}
      </header>

      {datasetStatus === 'VALIDATED' && currentState?.phase !== 'starting' && job === null && (
        <AnalysisReady
          canStart={canStart}
          totalCount={totalCount}
          errorMessage={currentState?.phase === 'error' ? currentState.message : null}
          onStart={() => void startAnalysis()}
        />
      )}

      {currentState?.phase === 'starting' && (
        <div className="analysis-loading" aria-label="AI 분석 시작 중">
          <LoaderCircle className="spin" size={23} aria-hidden="true" />
          <div>
            <strong>분석 작업을 준비하고 있습니다.</strong>
            <span>작업이 등록되면 진행 상태를 자동으로 갱신합니다.</span>
          </div>
        </div>
      )}

      {shouldLoadExisting && currentState === null && (
        <div className="analysis-loading" aria-label="AI 분석 상태 로딩 중">
          <LoaderCircle className="spin" size={23} aria-hidden="true" />
          <div>
            <strong>분석 상태를 확인하고 있습니다.</strong>
            <span>최근 작업 정보를 불러오는 중입니다.</span>
          </div>
        </div>
      )}

      {shouldLoadExisting && currentState?.phase === 'error' && (
        <div className="analysis-load-error" role="alert">
          <AlertCircle size={21} aria-hidden="true" />
          <div>
            <strong>분석 상태를 불러오지 못했습니다.</strong>
            <span>{currentState.message}</span>
          </div>
          <button className="secondary-button" type="button" onClick={retryStatus}>
            <RefreshCw size={17} aria-hidden="true" />
            <span>다시 조회</span>
          </button>
        </div>
      )}

      {job !== null && <AnalysisProgress job={job} />}

      {!shouldLoadExisting && datasetStatus !== 'VALIDATED' && (
        <div className="analysis-waiting">
          <Clock3 size={22} aria-hidden="true" />
          <div>
            <strong>아직 분석을 시작할 수 없습니다.</strong>
            <span>CSV 검증이 완료된 데이터셋에서 분석을 시작할 수 있습니다.</span>
          </div>
        </div>
      )}
    </section>
  );
}

function AnalysisReady({
  canStart,
  totalCount,
  errorMessage,
  onStart,
}: {
  canStart: boolean;
  totalCount: number;
  errorMessage: string | null;
  onStart: () => void;
}) {
  return (
    <div className="analysis-ready">
      <BrainCircuit size={24} aria-hidden="true" />
      <div>
        <strong>분석 준비가 완료되었습니다.</strong>
        <span>
          유효한 피드백 {formatNumber(totalCount)}건의 감성, 이슈 유형과 요약을 분석합니다.
        </span>
        {errorMessage !== null && <small role="alert">{errorMessage}</small>}
      </div>
      {canStart ? (
        <button className="primary-button" type="button" onClick={onStart}>
          <BrainCircuit size={18} aria-hidden="true" />
          <span>{errorMessage === null ? '분석 시작' : '다시 시작'}</span>
        </button>
      ) : (
        <span className="analysis-permission">관리자 또는 PM만 시작할 수 있습니다.</span>
      )}
    </div>
  );
}

function AnalysisProgress({ job }: { job: AnalysisJobView }) {
  const progressRate = Math.min(100, Math.max(0, job.progressRate));
  const terminal = !isActiveAnalysisJob(job.jobStatus);
  return (
    <div className="analysis-progress">
      <div className="analysis-progress-heading">
        <div>
          {job.jobStatus === 'COMPLETED' && <CheckCircle2 size={21} aria-hidden="true" />}
          {job.jobStatus === 'COMPLETED_WITH_ERRORS' && (
            <TriangleAlert className="analysis-progress-warning" size={21} aria-hidden="true" />
          )}
          {job.jobStatus === 'FAILED' && (
            <AlertCircle className="analysis-progress-danger" size={21} aria-hidden="true" />
          )}
          {!terminal && <LoaderCircle className="spin" size={21} aria-hidden="true" />}
          <strong>{analysisProgressTitle(job)}</strong>
        </div>
        <span>{progressRate.toFixed(1)}%</span>
      </div>

      <div
        className="analysis-progress-track"
        role="progressbar"
        aria-label="AI 분석 진행률"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={progressRate}
      >
        <span style={{ width: `${progressRate}%` }} />
      </div>

      <dl className="analysis-counts">
        <AnalysisCount label="처리" value={job.processedCount} total={job.totalCount} />
        <AnalysisCount label="성공" value={job.successCount} tone="success" />
        <AnalysisCount label="실패" value={job.failedCount} tone="failure" />
      </dl>

      <dl className="analysis-timestamps">
        <div>
          <dt>시작 시각</dt>
          <dd>{job.startedAt === null ? '-' : formatDate(job.startedAt)}</dd>
        </div>
        <div>
          <dt>완료 시각</dt>
          <dd>{job.completedAt === null ? '-' : formatDate(job.completedAt)}</dd>
        </div>
      </dl>

      {job.failureReason !== null && (
        <div className="analysis-failure-reason" role="alert">
          <AlertCircle size={18} aria-hidden="true" />
          <span>{job.failureReason}</span>
        </div>
      )}
    </div>
  );
}

function AnalysisCount({
  label,
  value,
  total,
  tone,
}: {
  label: string;
  value: number;
  total?: number;
  tone?: 'success' | 'failure';
}) {
  return (
    <div className={tone === undefined ? undefined : `analysis-count--${tone}`}>
      <dt>{label}</dt>
      <dd>
        {formatNumber(value)}
        {total === undefined ? '' : ` / ${formatNumber(total)}`}
      </dd>
    </div>
  );
}

function analysisProgressTitle(job: AnalysisJobView): string {
  if (job.jobStatus === 'PENDING') return '분석 작업이 대기 중입니다.';
  if (job.jobStatus === 'RUNNING') return '피드백을 분석하고 있습니다.';
  if (job.jobStatus === 'COMPLETED') return '모든 피드백 분석이 완료되었습니다.';
  if (job.jobStatus === 'COMPLETED_WITH_ERRORS') return '일부 피드백을 제외하고 분석했습니다.';
  return '분석 작업이 중단되었습니다.';
}

function analysisJobTone(job: AnalysisJobView): 'progress' | 'success' | 'warning' | 'danger' {
  if (job.jobStatus === 'PENDING' || job.jobStatus === 'RUNNING') return 'progress';
  if (job.jobStatus === 'COMPLETED') return 'success';
  if (job.jobStatus === 'COMPLETED_WITH_ERRORS') return 'warning';
  return 'danger';
}
