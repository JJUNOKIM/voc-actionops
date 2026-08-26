import { AlertCircle, FileText, FileUp, LoaderCircle, X } from 'lucide-react';
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';

import { ApiError } from '../lib/api-client';
import { uploadDatasetRequest } from './api';
import {
  buildInitialMapping,
  compactMapping,
  csvFileError,
  mappingError,
  readCsvHeaders,
} from './csv-mapping';
import { sourceTypeOptions, systemFieldOptions } from './labels';
import type {
  ColumnMappingDraft,
  DatasetUploadResult,
  SourceType,
  SystemField,
} from './types';

interface DatasetUploadDialogProps {
  onClose: () => void;
  onUploaded: (result: DatasetUploadResult) => void;
}

export function DatasetUploadDialog({ onClose, onUploaded }: DatasetUploadDialogProps) {
  const [name, setName] = useState('');
  const [sourceType, setSourceType] = useState<SourceType>('APP_REVIEW');
  const [file, setFile] = useState<File | null>(null);
  const [headers, setHeaders] = useState<string[]>([]);
  const [mapping, setMapping] = useState<ColumnMappingDraft>({});
  const [readingHeaders, setReadingHeaders] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const fileReadSequence = useRef(0);

  const selectedFields = useMemo(
    () => new Set(Object.values(mapping).filter((field): field is SystemField => field !== '')),
    [mapping],
  );

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !submitting) {
        onClose();
      }
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [onClose, submitting]);

  async function handleFileChange(selectedFile: File | undefined) {
    const sequence = fileReadSequence.current + 1;
    fileReadSequence.current = sequence;
    setErrorMessage(null);
    setHeaders([]);
    setMapping({});

    if (selectedFile === undefined) {
      setFile(null);
      return;
    }

    const fileError = csvFileError(selectedFile);
    if (fileError !== null) {
      setFile(null);
      setErrorMessage(fileError);
      return;
    }

    setFile(selectedFile);
    setReadingHeaders(true);
    if (name.trim() === '') {
      setName(selectedFile.name.replace(/\.csv$/i, ''));
    }

    try {
      const parsedHeaders = await readCsvHeaders(selectedFile);
      if (fileReadSequence.current !== sequence) {
        return;
      }
      setHeaders(parsedHeaders);
      setMapping(buildInitialMapping(parsedHeaders));
    } catch (error) {
      if (fileReadSequence.current === sequence) {
        setFile(null);
        setErrorMessage(
          error instanceof Error ? error.message : 'CSV 파일을 읽을 수 없습니다.',
        );
      }
    } finally {
      if (fileReadSequence.current === sequence) {
        setReadingHeaders(false);
      }
    }
  }

  function handleMappingChange(header: string, field: string) {
    setMapping((current) => ({
      ...current,
      [header]: field as SystemField | '',
    }));
    setErrorMessage(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedName = name.trim();
    if (trimmedName === '') {
      setErrorMessage('데이터셋 이름을 입력해 주세요.');
      return;
    }
    if (file === null || headers.length === 0) {
      setErrorMessage('업로드할 CSV 파일을 선택해 주세요.');
      return;
    }
    const currentMappingError = mappingError(mapping);
    if (currentMappingError !== null) {
      setErrorMessage(currentMappingError);
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    try {
      const result = await uploadDatasetRequest({
        name: trimmedName,
        sourceType,
        file,
        columnMapping: compactMapping(mapping),
      });
      onUploaded(result);
    } catch (error) {
      setErrorMessage(
        error instanceof ApiError ? error.message : 'CSV 업로드를 처리할 수 없습니다.',
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-layer">
      <button
        className="modal-backdrop"
        type="button"
        aria-label="업로드 창 닫기"
        onClick={submitting ? undefined : onClose}
      />
      <section
        className="upload-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="upload-dialog-title"
      >
        <header className="dialog-header">
          <div>
            <p className="section-label">CSV IMPORT</p>
            <h2 id="upload-dialog-title">데이터셋 추가</h2>
          </div>
          <button
            className="icon-button"
            type="button"
            onClick={onClose}
            disabled={submitting}
            aria-label="닫기"
            title="닫기"
          >
            <X size={20} />
          </button>
        </header>

        <form className="upload-form" onSubmit={handleSubmit}>
          <div className="dialog-body">
            <div className="form-grid">
              <label className="form-control" htmlFor="dataset-name">
                <span>데이터셋 이름</span>
                <input
                  id="dataset-name"
                  type="text"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  maxLength={150}
                  placeholder="예: 2026년 8월 앱 리뷰"
                  autoFocus
                  disabled={submitting}
                  required
                />
              </label>
              <label className="form-control" htmlFor="dataset-source">
                <span>데이터 출처</span>
                <select
                  id="dataset-source"
                  value={sourceType}
                  onChange={(event) => setSourceType(event.target.value as SourceType)}
                  disabled={submitting}
                >
                  {sourceTypeOptions.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className="file-control">
              <div className="form-section-heading">
                <div>
                  <h3>CSV 파일</h3>
                  <p>첫 행을 컬럼명으로 사용합니다. 최대 5MB, 10,000행</p>
                </div>
                <label className="secondary-button file-select-button">
                  <FileUp size={18} aria-hidden="true" />
                  <span>{file === null ? '파일 선택' : '파일 변경'}</span>
                  <input
                    type="file"
                    aria-label="CSV 파일"
                    accept=".csv,text/csv"
                    onChange={(event) => {
                      const selectedFile = event.currentTarget.files?.[0];
                      event.currentTarget.value = '';
                      void handleFileChange(selectedFile);
                    }}
                    disabled={submitting || readingHeaders}
                  />
                </label>
              </div>

              {file !== null && (
                <div className="selected-file">
                  <FileText size={19} aria-hidden="true" />
                  <div>
                    <strong>{file.name}</strong>
                    <span>{formatFileSize(file.size)}</span>
                  </div>
                  {readingHeaders && <LoaderCircle className="spin" size={18} aria-label="확인 중" />}
                </div>
              )}
            </div>

            {headers.length > 0 && (
              <div className="mapping-section">
                <div className="form-section-heading">
                  <div>
                    <h3>컬럼 매핑</h3>
                    <p>CSV 컬럼을 시스템 필드에 연결하세요. 피드백 내용은 필수입니다.</p>
                  </div>
                  <span className="column-count">{headers.length}개 컬럼</span>
                </div>
                <div className="mapping-list">
                  {headers.map((header) => {
                    const currentField = mapping[header] ?? '';
                    return (
                      <label className="mapping-row" key={header}>
                        <span className="csv-column-name">{header}</span>
                        <span className="mapping-arrow" aria-hidden="true">
                          →
                        </span>
                        <select
                          aria-label={`${header} 매핑`}
                          value={currentField}
                          onChange={(event) => handleMappingChange(header, event.target.value)}
                          disabled={submitting}
                        >
                          <option value="">사용하지 않음</option>
                          {systemFieldOptions.map((option) => (
                            <option
                              key={option.value}
                              value={option.value}
                              disabled={selectedFields.has(option.value) && currentField !== option.value}
                            >
                              {option.label}{option.value === 'content' ? ' *' : ''}
                            </option>
                          ))}
                        </select>
                      </label>
                    );
                  })}
                </div>
              </div>
            )}

            {errorMessage !== null && (
              <div className="dialog-error" role="alert">
                <AlertCircle size={18} aria-hidden="true" />
                <span>{errorMessage}</span>
              </div>
            )}
          </div>

          <footer className="dialog-footer">
            <button
              className="secondary-button"
              type="button"
              onClick={onClose}
              disabled={submitting}
            >
              취소
            </button>
            <button
              className="primary-button"
              type="submit"
              disabled={submitting || readingHeaders}
            >
              {submitting ? <LoaderCircle className="spin" size={18} /> : <FileUp size={18} />}
              <span>{submitting ? '업로드 중' : '업로드'}</span>
            </button>
          </footer>
        </form>
      </section>
    </div>
  );
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.ceil(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
