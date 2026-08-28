export type SourceType =
  | 'SHOP_REVIEW'
  | 'APP_REVIEW'
  | 'CS_TICKET'
  | 'SURVEY'
  | 'INTERNAL_TEST'
  | 'ETC';

export type DatasetStatus =
  | 'UPLOADED'
  | 'VALIDATING'
  | 'VALIDATED'
  | 'ANALYZING'
  | 'ANALYZED'
  | 'FAILED';

export type SystemField =
  | 'external_id'
  | 'content'
  | 'customer_segment'
  | 'product_name'
  | 'rating'
  | 'language'
  | 'feedback_created_at';

export type DatasetValidationErrorCode =
  | 'MISSING_REQUIRED_FIELD'
  | 'EMPTY_CONTENT'
  | 'INVALID_RATING_RANGE'
  | 'INVALID_DATE_FORMAT'
  | 'DUPLICATED_EXTERNAL_ID';

export type AnalysisJobStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'COMPLETED_WITH_ERRORS'
  | 'FAILED';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface DatasetSummary {
  id: number;
  name: string;
  sourceType: SourceType;
  status: DatasetStatus;
  totalCount: number;
  validCount: number;
  invalidCount: number;
  createdAt: string;
}

export interface DatasetDetail extends DatasetSummary {
  fileUrl: string | null;
  columnMapping: Record<string, SystemField> | null;
  createdBy: number;
  updatedAt: string;
}

export interface DatasetValidationError {
  id: number;
  rowNumber: number;
  fieldName: string;
  errorCode: DatasetValidationErrorCode;
  errorMessage: string;
  rawRow: Record<string, string>;
  createdAt: string;
}

export interface AnalysisJobView {
  datasetId: number;
  status: DatasetStatus;
  jobId: string;
  jobStatus: AnalysisJobStatus;
  totalCount: number;
  processedCount: number;
  successCount: number;
  failedCount: number;
  progressRate: number;
  failureReason: string | null;
  startedAt: string | null;
  completedAt: string | null;
}

export interface DatasetQuery {
  sourceType?: SourceType;
  status?: DatasetStatus;
  page: number;
  size: number;
}

export interface DatasetUploadInput {
  name: string;
  sourceType: SourceType;
  file: File;
  columnMapping: Record<string, SystemField>;
}

export interface DatasetUploadResult {
  datasetId: number;
  status: DatasetStatus;
  totalCount: number;
  validCount: number;
  invalidCount: number;
}

export type ColumnMappingDraft = Record<string, SystemField | ''>;
