import {
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  CircleCheck,
  Database,
  FileUp,
  RefreshCw,
  X,
} from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { useAuth } from '../auth/useAuth';
import { datasetsRequest } from '../datasets/api';
import { DatasetUploadDialog } from '../datasets/DatasetUploadDialog';
import {
  datasetStatusLabel,
  datasetStatusOptions,
  datasetStatusTone,
  sourceTypeLabel,
  sourceTypeOptions,
} from '../datasets/labels';
import type {
  DatasetStatus,
  DatasetSummary,
  DatasetUploadResult,
  PageResponse,
  SourceType,
} from '../datasets/types';
import { ApiError } from '../lib/api-client';

const PAGE_SIZE = 20;

export function DatasetsPage() {
  const { user } = useAuth();
  const [sourceType, setSourceType] = useState<SourceType | ''>('');
  const [status, setStatus] = useState<DatasetStatus | ''>('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<DatasetSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploadResult, setUploadResult] = useState<DatasetUploadResult | null>(null);
  const [reloadSequence, setReloadSequence] = useState(0);
  const requestSequence = useRef(0);

  const canUpload = user?.role === 'ADMIN' || user?.role === 'PM';

  useEffect(() => {
    const sequence = requestSequence.current + 1;
    requestSequence.current = sequence;

    void datasetsRequest({
      page,
      size: PAGE_SIZE,
      sourceType: sourceType === '' ? undefined : sourceType,
      status: status === '' ? undefined : status,
    })
      .then((response) => {
        if (requestSequence.current === sequence) {
          setResult(response);
        }
      })
      .catch((error: unknown) => {
        if (requestSequence.current === sequence) {
          setErrorMessage(
            error instanceof ApiError ? error.message : '데이터셋 목록을 불러올 수 없습니다.',
          );
        }
      })
      .finally(() => {
        if (requestSequence.current === sequence) {
          setLoading(false);
        }
      });
  }, [page, reloadSequence, sourceType, status]);

  function changeSourceType(value: string) {
    setLoading(true);
    setErrorMessage(null);
    setSourceType(value as SourceType | '');
    setPage(0);
  }

  function changeStatus(value: string) {
    setLoading(true);
    setErrorMessage(null);
    setStatus(value as DatasetStatus | '');
    setPage(0);
  }

  function changePage(nextPage: number) {
    setLoading(true);
    setErrorMessage(null);
    setPage(nextPage);
  }

  function refreshDatasets() {
    setLoading(true);
    setErrorMessage(null);
    setReloadSequence((current) => current + 1);
  }

  function handleUploaded(nextResult: DatasetUploadResult) {
    setUploadOpen(false);
    setUploadResult(nextResult);
    setLoading(true);
    setErrorMessage(null);
    setPage(0);
    if (page === 0) {
      setReloadSequence((current) => current + 1);
    }
  }

  const datasets = result?.content ?? [];

  return (
    <div className="page-container datasets-page">
      <header className="page-header datasets-page-header">
        <div>
          <p className="section-label">DATA SOURCES</p>
          <h1>데이터셋</h1>
          <p className="page-description">분석할 VOC 원천 데이터와 처리 상태를 관리합니다.</p>
        </div>
        {canUpload && (
          <button className="primary-button" type="button" onClick={() => setUploadOpen(true)}>
            <FileUp size={18} aria-hidden="true" />
            <span>데이터셋 추가</span>
          </button>
        )}
      </header>

      {uploadResult !== null && (
        <div className="upload-success" role="status">
          <CircleCheck size={19} aria-hidden="true" />
          <div>
            <strong>CSV 검증이 완료되었습니다.</strong>
            <span>
              전체 {formatNumber(uploadResult.totalCount)}건 중 유효{' '}
              {formatNumber(uploadResult.validCount)}건, 오류 {formatNumber(uploadResult.invalidCount)}건
            </span>
          </div>
          <button
            className="icon-button"
            type="button"
            onClick={() => setUploadResult(null)}
            aria-label="알림 닫기"
            title="알림 닫기"
          >
            <X size={18} />
          </button>
        </div>
      )}

      <section className="dataset-workspace" aria-labelledby="dataset-list-title">
        <div className="dataset-toolbar">
          <div className="dataset-toolbar-copy">
            <h2 id="dataset-list-title">데이터 목록</h2>
            <span>{result === null ? '조회 중' : `총 ${formatNumber(result.totalElements)}개`}</span>
          </div>
          <div className="dataset-filters" aria-label="데이터셋 필터">
            <label>
              <span className="sr-only">데이터 출처</span>
              <select value={sourceType} onChange={(event) => changeSourceType(event.target.value)}>
                <option value="">모든 출처</option>
                {sourceTypeOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span className="sr-only">처리 상태</span>
              <select value={status} onChange={(event) => changeStatus(event.target.value)}>
                <option value="">모든 상태</option>
                {datasetStatusOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <button
              className="icon-button toolbar-refresh"
              type="button"
              onClick={refreshDatasets}
              disabled={loading}
              aria-label="새로고침"
              title="새로고침"
            >
              <RefreshCw className={loading ? 'spin' : undefined} size={18} />
            </button>
          </div>
        </div>

        {errorMessage !== null && (
          <div className="dataset-error" role="alert">
            <AlertCircle size={20} aria-hidden="true" />
            <div>
              <strong>목록을 불러오지 못했습니다.</strong>
              <span>{errorMessage}</span>
            </div>
            <button className="secondary-button" type="button" onClick={refreshDatasets}>
              다시 시도
            </button>
          </div>
        )}

        {errorMessage === null && loading && result === null && <DatasetTableLoading />}

        {errorMessage === null && !loading && datasets.length === 0 && (
          <div className="dataset-empty">
            <Database size={28} aria-hidden="true" />
            <h3>{sourceType === '' && status === '' ? '등록된 데이터셋이 없습니다.' : '조건에 맞는 데이터셋이 없습니다.'}</h3>
            <p>
              {sourceType === '' && status === ''
                ? 'CSV 파일을 추가하면 검증 결과와 처리 상태가 여기에 표시됩니다.'
                : '필터 조건을 변경해 다시 확인해 주세요.'}
            </p>
            {canUpload && sourceType === '' && status === '' && (
              <button className="secondary-button" type="button" onClick={() => setUploadOpen(true)}>
                <FileUp size={18} aria-hidden="true" />
                <span>CSV 추가</span>
              </button>
            )}
          </div>
        )}

        {errorMessage === null && datasets.length > 0 && (
          <DatasetTable datasets={datasets} refreshing={loading} />
        )}

        {result !== null && result.totalPages > 0 && (
          <footer className="dataset-pagination">
            <span>
              {result.page + 1} / {result.totalPages} 페이지
            </span>
            <div>
              <button
                className="icon-button"
                type="button"
                onClick={() => changePage(Math.max(0, result.page - 1))}
                disabled={loading || result.page === 0}
                aria-label="이전 페이지"
                title="이전 페이지"
              >
                <ChevronLeft size={19} />
              </button>
              <button
                className="icon-button"
                type="button"
                onClick={() => changePage(result.page + 1)}
                disabled={loading || result.page + 1 >= result.totalPages}
                aria-label="다음 페이지"
                title="다음 페이지"
              >
                <ChevronRight size={19} />
              </button>
            </div>
          </footer>
        )}
      </section>

      {uploadOpen && (
        <DatasetUploadDialog
          onClose={() => setUploadOpen(false)}
          onUploaded={handleUploaded}
        />
      )}
    </div>
  );
}

function DatasetTable({ datasets, refreshing }: { datasets: DatasetSummary[]; refreshing: boolean }) {
  return (
    <div className={`dataset-table-wrap${refreshing ? ' is-refreshing' : ''}`}>
      <table className="dataset-table">
        <caption className="sr-only">조직 데이터셋 목록</caption>
        <thead>
          <tr>
            <th scope="col">데이터셋</th>
            <th scope="col">상태</th>
            <th scope="col">전체</th>
            <th scope="col">유효</th>
            <th scope="col">오류</th>
            <th scope="col">등록 시각</th>
          </tr>
        </thead>
        <tbody>
          {datasets.map((dataset) => (
            <tr key={dataset.id}>
              <td data-label="데이터셋">
                <div className="dataset-name-cell">
                  <strong>{dataset.name}</strong>
                  <span>{sourceTypeLabel(dataset.sourceType)}</span>
                </div>
              </td>
              <td data-label="상태">
                <span className={`status-badge status-badge--${datasetStatusTone(dataset.status)}`}>
                  {datasetStatusLabel(dataset.status)}
                </span>
              </td>
              <td data-label="전체">{formatNumber(dataset.totalCount)}</td>
              <td data-label="유효" className="valid-count">
                {formatNumber(dataset.validCount)}
              </td>
              <td data-label="오류" className={dataset.invalidCount > 0 ? 'invalid-count' : undefined}>
                {formatNumber(dataset.invalidCount)}
              </td>
              <td data-label="등록 시각" className="created-at-cell">
                {formatDate(dataset.createdAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function DatasetTableLoading() {
  return (
    <div className="dataset-loading" aria-label="데이터셋 목록 로딩 중">
      {Array.from({ length: 5 }, (_, index) => (
        <div className="dataset-loading-row" key={index}>
          <span />
          <span />
          <span />
        </div>
      ))}
    </div>
  );
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat('ko-KR').format(value);
}

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}
