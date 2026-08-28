import { AlertCircle, LoaderCircle, X } from 'lucide-react';
import { useEffect, useState, type FormEvent } from 'react';

import { ApiError } from '../lib/api-client';
import { correctFeedbackAnalysisRequest } from './api';
import { correctionFieldLabel, correctionValueLabel, sentimentLabel } from './labels';
import type {
  FeedbackAnalysisDetail,
  FeedbackCorrectionField,
  Sentiment,
} from './types';

const correctionFields: FeedbackCorrectionField[] = [
  'sentiment',
  'category',
  'urgency_score',
];

const sentimentOptions: Sentiment[] = ['POSITIVE', 'NEUTRAL', 'NEGATIVE'];

interface FeedbackCorrectionDialogProps {
  feedbackId: number;
  analysis: FeedbackAnalysisDetail;
  onClose: () => void;
  onCorrected: (analysis: FeedbackAnalysisDetail) => void;
}

export function FeedbackCorrectionDialog({
  feedbackId,
  analysis,
  onClose,
  onCorrected,
}: FeedbackCorrectionDialogProps) {
  const [field, setField] = useState<FeedbackCorrectionField>('category');
  const [correctedValue, setCorrectedValue] = useState(currentValue(analysis, 'category'));
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !submitting) onClose();
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [onClose, submitting]);

  function handleFieldChange(nextField: FeedbackCorrectionField) {
    setField(nextField);
    setCorrectedValue(currentValue(analysis, nextField));
    setErrorMessage(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validation = validateCorrection(analysis, field, correctedValue, reason);
    if (validation.error !== null) {
      setErrorMessage(validation.error);
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    try {
      const correctedAnalysis = await correctFeedbackAnalysisRequest(feedbackId, {
        fieldName: field,
        correctedValue: validation.value,
        reason: reason.trim(),
      });
      onCorrected(correctedAnalysis);
    } catch (error) {
      setErrorMessage(
        error instanceof ApiError ? error.message : '분석 결과를 수정할 수 없습니다.',
      );
    } finally {
      setSubmitting(false);
    }
  }

  const originalValue = currentValue(analysis, field);

  return (
    <div className="modal-layer">
      <button
        className="modal-backdrop"
        type="button"
        aria-label="분석 결과 수정 창 닫기"
        onClick={submitting ? undefined : onClose}
      />
      <section
        className="upload-dialog correction-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="correction-dialog-title"
      >
        <header className="dialog-header">
          <div>
            <p className="section-label">USER REVIEW</p>
            <h2 id="correction-dialog-title">분석 결과 수정</h2>
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

        <form className="upload-form" onSubmit={handleSubmit} noValidate>
          <div className="dialog-body correction-dialog-body">
            <label className="form-control" htmlFor="correction-field">
              <span>수정 항목</span>
              <select
                id="correction-field"
                value={field}
                onChange={(event) =>
                  handleFieldChange(event.target.value as FeedbackCorrectionField)
                }
                disabled={submitting}
              >
                {correctionFields.map((option) => (
                  <option key={option} value={option}>
                    {correctionFieldLabel(option)}
                  </option>
                ))}
              </select>
            </label>

            <div className="correction-current-value">
              <span>현재 분석값</span>
              <strong>{correctionValueLabel(field, originalValue) || '-'}</strong>
            </div>

            {field === 'sentiment' ? (
              <fieldset className="correction-sentiment-control" disabled={submitting}>
                <legend>수정값</legend>
                <div>
                  {sentimentOptions.map((sentiment) => (
                    <button
                      className={correctedValue === sentiment ? 'is-selected' : undefined}
                      type="button"
                      key={sentiment}
                      aria-pressed={correctedValue === sentiment}
                      onClick={() => {
                        setCorrectedValue(sentiment);
                        setErrorMessage(null);
                      }}
                    >
                      {sentimentLabel(sentiment)}
                    </button>
                  ))}
                </div>
              </fieldset>
            ) : (
              <label className="form-control" htmlFor="correction-value">
                <span>수정값</span>
                <input
                  id="correction-value"
                  type={field === 'urgency_score' ? 'number' : 'text'}
                  inputMode={field === 'urgency_score' ? 'decimal' : undefined}
                  min={field === 'urgency_score' ? 0 : undefined}
                  max={field === 'urgency_score' ? 1 : undefined}
                  step={field === 'urgency_score' ? 0.0001 : undefined}
                  maxLength={field === 'category' ? 100 : undefined}
                  value={correctedValue}
                  onChange={(event) => {
                    setCorrectedValue(event.target.value);
                    setErrorMessage(null);
                  }}
                  placeholder={field === 'category' ? '예: PAYMENT' : '0부터 1 사이의 값'}
                  autoFocus
                  disabled={submitting}
                  required
                />
              </label>
            )}

            <label className="form-control correction-reason" htmlFor="correction-reason">
              <span>수정 사유</span>
              <textarea
                id="correction-reason"
                value={reason}
                onChange={(event) => {
                  setReason(event.target.value);
                  setErrorMessage(null);
                }}
                maxLength={500}
                rows={4}
                placeholder="원문과 분석 결과를 비교한 판단 근거를 입력해 주세요."
                disabled={submitting}
                required
              />
              <small>{reason.length} / 500</small>
            </label>

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
            <button className="primary-button" type="submit" disabled={submitting}>
              {submitting && <LoaderCircle className="spin" size={17} aria-hidden="true" />}
              <span>{submitting ? '저장 중' : '수정 저장'}</span>
            </button>
          </footer>
        </form>
      </section>
    </div>
  );
}

function currentValue(
  analysis: FeedbackAnalysisDetail,
  field: FeedbackCorrectionField,
): string {
  if (field === 'sentiment') return analysis.sentiment ?? '';
  if (field === 'category') return analysis.category ?? '';
  return analysis.urgencyScore === null ? '' : String(analysis.urgencyScore);
}

function validateCorrection(
  analysis: FeedbackAnalysisDetail,
  field: FeedbackCorrectionField,
  correctedValue: string,
  reason: string,
): { error: string | null; value: string } {
  const trimmedValue = correctedValue.trim();
  if (trimmedValue === '') {
    return { error: '수정값을 입력해 주세요.', value: '' };
  }
  if (reason.trim() === '') {
    return { error: '수정 사유를 입력해 주세요.', value: '' };
  }

  if (field === 'urgency_score') {
    if (!/^(?:0(?:\.\d{1,4})?|1(?:\.0{1,4})?)$/.test(trimmedValue)) {
      return { error: '긴급도는 0부터 1 사이에서 소수점 넷째 자리까지 입력해 주세요.', value: '' };
    }
    const normalizedValue = String(Number(trimmedValue));
    if (analysis.urgencyScore !== null && Number(normalizedValue) === analysis.urgencyScore) {
      return { error: '현재 분석값과 다른 값을 입력해 주세요.', value: '' };
    }
    return { error: null, value: normalizedValue };
  }

  if (field === 'sentiment' && trimmedValue === analysis.sentiment) {
    return { error: '현재 분석값과 다른 값을 선택해 주세요.', value: '' };
  }
  if (field === 'category' && trimmedValue === analysis.category) {
    return { error: '현재 분석값과 다른 값을 입력해 주세요.', value: '' };
  }
  return { error: null, value: trimmedValue };
}
