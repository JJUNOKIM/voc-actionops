import {
  AlertCircle,
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  CircleCheck,
  FileWarning,
  RefreshCw,
} from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { useAuth } from '../auth/useAuth';
import { datasetDetailRequest, datasetValidationErrorsRequest } from '../datasets/api';
import { formatDate, formatNumber } from '../datasets/format';
import {
  datasetStatusLabel,
  datasetStatusTone,
  sourceTypeLabel,
  systemFieldLabel,
  validationErrorLabel,
} from '../datasets/labels';
import type {
  DatasetDetail,
  DatasetValidationError,
  PageResponse,
} from '../datasets/types';
import { ApiError } from '../lib/api-client';

const ERROR_PAGE_SIZE = 20;

type DetailState =
  | { datasetId: number; status: 'success'; data: DatasetDetail }
  | { datasetId: number; status: 'error'; message: string }
  | null;

type ValidationState =
  | {
      datasetId: number;
      page: number;
      status: 'success';
      data: PageResponse<DatasetValidationError>;
    }
  | { datasetId: number; page: number; status: 'error'; message: string }
  | null;

export function DatasetDetailPage() {
  const { datasetId: datasetIdParam } = useParams();
  const { user } = useAuth();
  const datasetId = parseDatasetId(datasetIdParam);
  const canViewValidationErrors = user?.role === 'ADMIN' || user?.role === 'PM';
  const [detailState, setDetailState] = useState<DetailState>(null);
  const [detailReload, setDetailReload] = useState(0);
  const [validationState, setValidationState] = useState<ValidationState>(null);
  const [validationCursor, setValidationCursor] = useState({ datasetId: 0, page: 0 });
  const [validationReload, setValidationReload] = useState(0);
  const detailRequestSequence = useRef(0);
  const validationRequestSequence = useRef(0);

  const validationPage =
    datasetId !== null && validationCursor.datasetId === datasetId ? validationCursor.page : 0;

  useEffect(() => {
    if (datasetId === null) return;
    const sequence = detailRequestSequence.current + 1;
    detailRequestSequence.current = sequence;

    void datasetDetailRequest(datasetId)
      .then((data) => {
        if (detailRequestSequence.current === sequence) {
          setDetailState({ datasetId, status: 'success', data });
        }
      })
      .catch((error: unknown) => {
        if (detailRequestSequence.current === sequence) {
          setDetailState({
            datasetId,
            status: 'error',
            message:
              error instanceof ApiError
                ? error.message
                : '데이터셋 상세 정보를 불러올 수 없습니다.',
          });
        }
      });
  }, [datasetId, detailReload]);

  const detail =
    datasetId !== null && detailState?.datasetId === datasetId && detailState.status === 'success'
      ? detailState.data
      : null;

  useEffect(() => {
    if (
      datasetId === null ||
      detail === null ||
      detail.invalidCount === 0 ||
      !canViewValidationErrors
    ) {
      validationRequestSequence.current += 1;
      return;
    }
    const sequence = validationRequestSequence.current + 1;
    validationRequestSequence.current = sequence;

    void datasetValidationErrorsRequest(datasetId, validationPage, ERROR_PAGE_SIZE)
      .then((data) => {
        if (validationRequestSequence.current === sequence) {
          setValidationState({
            datasetId,
            page: validationPage,
            status: 'success',
            data,
          });
        }
      })
      .catch((error: unknown) => {
        if (validationRequestSequence.current === sequence) {
          setValidationState({
            datasetId,
            page: validationPage,
            status: 'error',
            message:
              error instanceof ApiError
                ? error.message
                : '검증 오류 목록을 불러올 수 없습니다.',
          });
        }
      });
  }, [canViewValidationErrors, datasetId, detail, validationPage, validationReload]);

  function retryDetail() {
    setDetailState(null);
    setDetailReload((current) => current + 1);
  }

  function changeValidationPage(page: number) {
    if (datasetId === null) return;
    setValidationCursor({ datasetId, page });
  }

  function retryValidation() {
    setValidationState(null);
    setValidationReload((current) => current + 1);
  }

  if (datasetId === null) {
    return (
      <DetailFailure
        title="잘못된 데이터셋 주소입니다."
        description="목록에서 데이터셋을 다시 선택해 주세요."
      />
    );
  }

  const currentDetailState = detailState?.datasetId === datasetId ? detailState : null;
  if (currentDetailState === null) {
    return <DatasetDetailLoading />;
  }
  if (currentDetailState.status === 'error') {
    return (
      <DetailFailure
        title="데이터셋을 불러오지 못했습니다."
        description={currentDetailState.message}
        onRetry={retryDetail}
      />
    );
  }

  const mappingEntries = sortedEntries(currentDetailState.data.columnMapping ?? {});
  const currentValidationState =
    validationState?.datasetId === datasetId && validationState.page === validationPage
      ? validationState
      : null;

  return (
    <div className="page-container dataset-detail-page">
      <Link className="back-link" to="/datasets">
        <ArrowLeft size={17} aria-hidden="true" />
        <span>데이터셋 목록</span>
      </Link>

      <header className="page-header dataset-detail-header">
        <div>
          <p className="section-label">DATASET DETAIL</p>
          <h1>{currentDetailState.data.name}</h1>
          <p className="page-description">
            {sourceTypeLabel(currentDetailState.data.sourceType)} · 사용자 #{currentDetailState.data.createdBy}
          </p>
        </div>
        <span
          className={`status-badge detail-status status-badge--${datasetStatusTone(currentDetailState.data.status)}`}
        >
          {datasetStatusLabel(currentDetailState.data.status)}
        </span>
      </header>

      <section className="dataset-count-strip" aria-label="데이터 검증 요약">
        <CountItem label="전체" value={currentDetailState.data.totalCount} />
        <CountItem label="유효" value={currentDetailState.data.validCount} tone="valid" />
        <CountItem label="오류" value={currentDetailState.data.invalidCount} tone="invalid" />
      </section>

      <section className="dataset-detail-section" aria-labelledby="dataset-information-title">
        <header className="detail-section-header">
          <div>
            <p className="section-label">INFORMATION</p>
            <h2 id="dataset-information-title">데이터셋 정보</h2>
          </div>
        </header>
        <div className="dataset-information-grid">
          <dl className="dataset-metadata">
            <div>
              <dt>데이터 출처</dt>
              <dd>{sourceTypeLabel(currentDetailState.data.sourceType)}</dd>
            </div>
            <div>
              <dt>처리 상태</dt>
              <dd>{datasetStatusLabel(currentDetailState.data.status)}</dd>
            </div>
            <div>
              <dt>등록 시각</dt>
              <dd>{formatDate(currentDetailState.data.createdAt)}</dd>
            </div>
            <div>
              <dt>최근 변경</dt>
              <dd>{formatDate(currentDetailState.data.updatedAt)}</dd>
            </div>
          </dl>

          <div className="column-mapping" aria-labelledby="column-mapping-title">
            <div className="column-mapping-heading">
              <h3 id="column-mapping-title">컬럼 매핑</h3>
              <span>{formatNumber(mappingEntries.length)}개</span>
            </div>
            {mappingEntries.length === 0 ? (
              <p className="mapping-empty">저장된 컬럼 매핑이 없습니다.</p>
            ) : (
              <div className="mapping-detail-list">
                {mappingEntries.map(([csvColumn, systemField]) => (
                  <div key={csvColumn}>
                    <code>{csvColumn}</code>
                    <span aria-hidden="true">→</span>
                    <strong>{systemFieldLabel(systemField)}</strong>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </section>

      <ValidationErrorsSection
        canView={canViewValidationErrors}
        invalidCount={currentDetailState.data.invalidCount}
        page={validationPage}
        state={currentValidationState}
        onPageChange={changeValidationPage}
        onRetry={retryValidation}
      />
    </div>
  );
}

function CountItem({
  label,
  value,
  tone,
}: {
  label: string;
  value: number;
  tone?: 'valid' | 'invalid';
}) {
  return (
    <div className={tone === undefined ? undefined : `count-item--${tone}`}>
      <span>{label}</span>
      <strong>{formatNumber(value)}</strong>
      <small>건</small>
    </div>
  );
}

function ValidationErrorsSection({
  canView,
  invalidCount,
  page,
  state,
  onPageChange,
  onRetry,
}: {
  canView: boolean;
  invalidCount: number;
  page: number;
  state: ValidationState;
  onPageChange: (page: number) => void;
  onRetry: () => void;
}) {
  return (
    <section className="dataset-detail-section validation-section" aria-labelledby="validation-title">
      <header className="detail-section-header">
        <div>
          <p className="section-label">VALIDATION</p>
          <h2 id="validation-title">검증 오류</h2>
        </div>
        <span>{formatNumber(invalidCount)}개 행</span>
      </header>

      {invalidCount === 0 && (
        <div className="validation-empty">
          <CircleCheck size={22} aria-hidden="true" />
          <div>
            <strong>검증 오류가 없습니다.</strong>
            <span>업로드된 모든 행이 유효성 검사를 통과했습니다.</span>
          </div>
        </div>
      )}

      {invalidCount > 0 && !canView && (
        <div className="validation-permission">
          <FileWarning size={22} aria-hidden="true" />
          <div>
            <strong>검증 오류 상세를 볼 수 없습니다.</strong>
            <span>관리자 또는 PM 권한에서 원본 행을 확인할 수 있습니다.</span>
          </div>
        </div>
      )}

      {invalidCount > 0 && canView && state === null && <ValidationLoading />}

      {invalidCount > 0 && canView && state?.status === 'error' && (
        <div className="validation-load-error" role="alert">
          <AlertCircle size={20} aria-hidden="true" />
          <div>
            <strong>검증 오류를 불러오지 못했습니다.</strong>
            <span>{state.message}</span>
          </div>
          <button className="secondary-button" type="button" onClick={onRetry}>
            <RefreshCw size={17} aria-hidden="true" />
            <span>다시 시도</span>
          </button>
        </div>
      )}

      {invalidCount > 0 && canView && state?.status === 'success' && (
        <>
          <ValidationTable errors={state.data.content} />
          {state.data.totalPages > 0 && (
            <footer className="dataset-pagination validation-pagination">
              <span>
                {state.data.page + 1} / {state.data.totalPages} 페이지
              </span>
              <div>
                <button
                  className="icon-button"
                  type="button"
                  onClick={() => onPageChange(Math.max(0, page - 1))}
                  disabled={state.data.page === 0}
                  aria-label="이전 오류 페이지"
                  title="이전 오류 페이지"
                >
                  <ChevronLeft size={19} />
                </button>
                <button
                  className="icon-button"
                  type="button"
                  onClick={() => onPageChange(page + 1)}
                  disabled={state.data.page + 1 >= state.data.totalPages}
                  aria-label="다음 오류 페이지"
                  title="다음 오류 페이지"
                >
                  <ChevronRight size={19} />
                </button>
              </div>
            </footer>
          )}
        </>
      )}
    </section>
  );
}

function ValidationTable({ errors }: { errors: DatasetValidationError[] }) {
  return (
    <div className="validation-table-wrap">
      <table className="validation-table">
        <caption className="sr-only">CSV 행 검증 오류 목록</caption>
        <thead>
          <tr>
            <th scope="col">행</th>
            <th scope="col">필드</th>
            <th scope="col">오류 사유</th>
            <th scope="col">원본 데이터</th>
          </tr>
        </thead>
        <tbody>
          {errors.map((error) => (
            <tr key={error.id}>
              <td data-label="행">{formatNumber(error.rowNumber)}</td>
              <td data-label="필드">
                {systemFieldLabel(error.fieldName)}
              </td>
              <td data-label="오류 사유">
                <div className="validation-reason">
                  <strong>{validationErrorLabel(error.errorCode)}</strong>
                  <span>{error.errorMessage}</span>
                </div>
              </td>
              <td data-label="원본 데이터">
                <details className="raw-row-details">
                  <summary>원본 행 보기</summary>
                  <dl>
                    {sortedEntries(error.rawRow).map(([field, value]) => (
                      <div key={field}>
                        <dt>{field}</dt>
                        <dd>{value === '' ? '(빈 값)' : value}</dd>
                      </div>
                    ))}
                  </dl>
                </details>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function DatasetDetailLoading() {
  return (
    <div className="page-container dataset-detail-page" aria-label="데이터셋 상세 로딩 중">
      <div className="detail-loading detail-loading--back" />
      <div className="detail-loading detail-loading--title" />
      <div className="detail-loading detail-loading--counts" />
      <div className="detail-loading detail-loading--section" />
    </div>
  );
}

function ValidationLoading() {
  return (
    <div className="validation-loading" aria-label="검증 오류 로딩 중">
      {Array.from({ length: 3 }, (_, index) => (
        <span key={index} />
      ))}
    </div>
  );
}

function DetailFailure({
  title,
  description,
  onRetry,
}: {
  title: string;
  description: string;
  onRetry?: () => void;
}) {
  return (
    <div className="page-container dataset-detail-page">
      <Link className="back-link" to="/datasets">
        <ArrowLeft size={17} aria-hidden="true" />
        <span>데이터셋 목록</span>
      </Link>
      <div className="detail-failure" role="alert">
        <AlertCircle size={26} aria-hidden="true" />
        <h1>{title}</h1>
        <p>{description}</p>
        {onRetry !== undefined && (
          <button className="secondary-button" type="button" onClick={onRetry}>
            <RefreshCw size={17} aria-hidden="true" />
            <span>다시 시도</span>
          </button>
        )}
      </div>
    </div>
  );
}

function parseDatasetId(value: string | undefined): number | null {
  if (value === undefined || !/^\d+$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

function sortedEntries(values: Record<string, string>): Array<[string, string]> {
  return Object.entries(values).sort(([left], [right]) => left.localeCompare(right, 'en'));
}
