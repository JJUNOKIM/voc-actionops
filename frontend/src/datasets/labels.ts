import type {
  AnalysisJobStatus,
  DatasetStatus,
  DatasetValidationErrorCode,
  SourceType,
  SystemField,
} from './types';

export const sourceTypeOptions: Array<{ value: SourceType; label: string }> = [
  { value: 'SHOP_REVIEW', label: '쇼핑몰 리뷰' },
  { value: 'APP_REVIEW', label: '앱 리뷰' },
  { value: 'CS_TICKET', label: '고객 문의' },
  { value: 'SURVEY', label: '설문' },
  { value: 'INTERNAL_TEST', label: '내부 테스트' },
  { value: 'ETC', label: '기타' },
];

export const datasetStatusOptions: Array<{ value: DatasetStatus; label: string }> = [
  { value: 'UPLOADED', label: '업로드됨' },
  { value: 'VALIDATING', label: '검증 중' },
  { value: 'VALIDATED', label: '분석 대기' },
  { value: 'ANALYZING', label: '분석 중' },
  { value: 'ANALYZED', label: '분석 완료' },
  { value: 'FAILED', label: '처리 실패' },
];

export const systemFieldOptions: Array<{
  value: SystemField;
  label: string;
  description: string;
}> = [
  { value: 'external_id', label: '외부 ID', description: '원본 시스템의 식별자' },
  { value: 'content', label: '피드백 내용', description: '필수' },
  { value: 'customer_segment', label: '고객 구분', description: '고객 등급 또는 세그먼트' },
  { value: 'product_name', label: '제품명', description: '제품 또는 서비스 이름' },
  { value: 'rating', label: '평점', description: '0에서 5 사이의 값' },
  { value: 'language', label: '언어', description: 'ko, en 등의 언어 코드' },
  { value: 'feedback_created_at', label: '작성 시각', description: '날짜 또는 날짜시간' },
];

export function sourceTypeLabel(sourceType: SourceType): string {
  return sourceTypeOptions.find((option) => option.value === sourceType)?.label ?? sourceType;
}

export function datasetStatusLabel(status: DatasetStatus): string {
  return datasetStatusOptions.find((option) => option.value === status)?.label ?? status;
}

export function datasetStatusTone(status: DatasetStatus): string {
  if (status === 'ANALYZED') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'ANALYZING' || status === 'VALIDATING') return 'progress';
  if (status === 'VALIDATED') return 'ready';
  return 'neutral';
}

export function systemFieldLabel(field: string): string {
  return systemFieldOptions.find((option) => option.value === field)?.label ?? field;
}

const validationErrorLabels: Record<DatasetValidationErrorCode, string> = {
  MISSING_REQUIRED_FIELD: '필수 값 누락',
  EMPTY_CONTENT: '내용 없음',
  INVALID_RATING_RANGE: '평점 범위 오류',
  INVALID_DATE_FORMAT: '날짜 형식 오류',
  DUPLICATED_EXTERNAL_ID: '외부 ID 중복',
};

export function validationErrorLabel(code: DatasetValidationErrorCode): string {
  return validationErrorLabels[code];
}

const analysisJobStatusLabels: Record<AnalysisJobStatus, string> = {
  PENDING: '시작 대기',
  RUNNING: '분석 중',
  COMPLETED: '분석 완료',
  COMPLETED_WITH_ERRORS: '일부 실패',
  FAILED: '작업 실패',
};

export function analysisJobStatusLabel(status: AnalysisJobStatus): string {
  return analysisJobStatusLabels[status];
}

export function isActiveAnalysisJob(status: AnalysisJobStatus): boolean {
  return status === 'PENDING' || status === 'RUNNING';
}
