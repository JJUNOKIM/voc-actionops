import {
  AlertCircle,
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  History,
  Pencil,
  RefreshCw,
  X,
} from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { formatDate } from '../datasets/format';
import type { PageResponse } from '../datasets/types';
import { ApiError } from '../lib/api-client';
import { feedbackCorrectionsRequest } from './api';
import { FeedbackCorrectionDialog } from './FeedbackCorrectionDialog';
import { correctionFieldLabel, correctionValueLabel } from './labels';
import type {
  FeedbackAnalysisDetail,
  FeedbackCorrection,
} from './types';

const HISTORY_PAGE_SIZE = 5;

type HistoryState =
  | {
      feedbackId: number;
      page: number;
      status: 'success';
      data: PageResponse<FeedbackCorrection>;
    }
  | { feedbackId: number; page: number; status: 'error'; message: string }
  | null;

interface FeedbackCorrectionSectionProps {
  feedbackId: number;
  analysis: FeedbackAnalysisDetail;
  onAnalysisCorrected: (analysis: FeedbackAnalysisDetail) => void;
}

export function FeedbackCorrectionSection({
  feedbackId,
  analysis,
  onAnalysisCorrected,
}: FeedbackCorrectionSectionProps) {
  const [cursor, setCursor] = useState({ feedbackId, page: 0 });
  const [historyState, setHistoryState] = useState<HistoryState>(null);
  const [reloadSequence, setReloadSequence] = useState(0);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const requestSequence = useRef(0);
  const page = cursor.feedbackId === feedbackId ? cursor.page : 0;

  useEffect(() => {
    const sequence = requestSequence.current + 1;
    requestSequence.current = sequence;

    void feedbackCorrectionsRequest(feedbackId, page, HISTORY_PAGE_SIZE)
      .then((data) => {
        if (requestSequence.current === sequence) {
          setHistoryState({ feedbackId, page, status: 'success', data });
        }
      })
      .catch((error: unknown) => {
        if (requestSequence.current === sequence) {
          setHistoryState({
            feedbackId,
            page,
            status: 'error',
            message: error instanceof ApiError ? error.message : '수정 이력을 불러올 수 없습니다.',
          });
        }
      });
  }, [feedbackId, page, reloadSequence]);

  const currentHistory =
    historyState?.feedbackId === feedbackId && historyState.page === page
      ? historyState
      : null;

  function reloadHistory() {
    setHistoryState(null);
    setReloadSequence((current) => current + 1);
  }

  function changePage(nextPage: number) {
    setHistoryState(null);
    setCursor({ feedbackId, page: nextPage });
  }

  function handleCorrected(correctedAnalysis: FeedbackAnalysisDetail) {
    onAnalysisCorrected(correctedAnalysis);
    setDialogOpen(false);
    setSuccessMessage('분석 결과와 수정 이력을 반영했습니다.');
    setHistoryState(null);
    setCursor({ feedbackId, page: 0 });
    setReloadSequence((current) => current + 1);
  }

  const history = currentHistory?.status === 'success' ? currentHistory.data : null;

  return (
    <section
      className="dataset-detail-section feedback-detail-section correction-section"
      aria-labelledby="correction-history-title"
    >
      <header className="detail-section-header feedback-detail-section-header correction-section-header">
        <div>
          <p className="section-label">USER REVIEW</p>
          <h2 id="correction-history-title">분석 결과 검수</h2>
        </div>
        <button
          className="secondary-button"
          type="button"
          onClick={() => {
            setSuccessMessage(null);
            setDialogOpen(true);
          }}
        >
          <Pencil size={16} aria-hidden="true" />
          <span>분석 결과 수정</span>
        </button>
      </header>

      {successMessage !== null && (
        <div className="correction-success" role="status">
          <span>{successMessage}</span>
          <button
            className="icon-button"
            type="button"
            onClick={() => setSuccessMessage(null)}
            aria-label="알림 닫기"
            title="알림 닫기"
          >
            <X size={17} />
          </button>
        </div>
      )}

      <div className="correction-history-heading">
        <div>
          <History size={18} aria-hidden="true" />
          <h3>수정 이력</h3>
        </div>
        {history !== null && <span>총 {history.totalElements}건</span>}
      </div>

      {currentHistory === null && <CorrectionHistoryLoading />}

      {currentHistory?.status === 'error' && (
        <div className="correction-history-error" role="alert">
          <AlertCircle size={20} aria-hidden="true" />
          <div>
            <strong>수정 이력을 불러오지 못했습니다.</strong>
            <span>{currentHistory.message}</span>
          </div>
          <button
            className="icon-button"
            type="button"
            onClick={reloadHistory}
            aria-label="수정 이력 다시 불러오기"
            title="다시 불러오기"
          >
            <RefreshCw size={18} />
          </button>
        </div>
      )}

      {history !== null && history.content.length === 0 && (
        <div className="correction-history-empty">
          <History size={22} aria-hidden="true" />
          <strong>아직 수정 이력이 없습니다.</strong>
          <span>분석값을 수정하면 변경 전후 값과 사유가 기록됩니다.</span>
        </div>
      )}

      {history !== null && history.content.length > 0 && (
        <div className="correction-history-list">
          {history.content.map((correction) => (
            <article className="correction-history-item" key={correction.id}>
              <div className="correction-history-meta">
                <strong>{correctionFieldLabel(correction.fieldName)}</strong>
                <span>
                  사용자 #{correction.correctedBy} · {formatDate(correction.createdAt)}
                </span>
              </div>
              <div className="correction-value-change">
                <span>{correctionValueLabel(correction.fieldName, correction.aiValue)}</span>
                <ArrowRight size={15} aria-hidden="true" />
                <strong>
                  {correctionValueLabel(correction.fieldName, correction.correctedValue)}
                </strong>
              </div>
              <p>{correction.reason}</p>
            </article>
          ))}
        </div>
      )}

      {history !== null && history.totalPages > 1 && (
        <footer className="dataset-pagination correction-pagination">
          <span>
            {history.page + 1} / {history.totalPages} 페이지
          </span>
          <div>
            <button
              className="icon-button"
              type="button"
              onClick={() => changePage(Math.max(0, history.page - 1))}
              disabled={history.page === 0}
              aria-label="이전 수정 이력 페이지"
              title="이전 페이지"
            >
              <ChevronLeft size={18} />
            </button>
            <button
              className="icon-button"
              type="button"
              onClick={() => changePage(history.page + 1)}
              disabled={history.page + 1 >= history.totalPages}
              aria-label="다음 수정 이력 페이지"
              title="다음 페이지"
            >
              <ChevronRight size={18} />
            </button>
          </div>
        </footer>
      )}

      {dialogOpen && (
        <FeedbackCorrectionDialog
          feedbackId={feedbackId}
          analysis={analysis}
          onClose={() => setDialogOpen(false)}
          onCorrected={handleCorrected}
        />
      )}
    </section>
  );
}

function CorrectionHistoryLoading() {
  return (
    <div className="correction-history-loading" aria-label="수정 이력 로딩 중">
      <span />
      <span />
    </div>
  );
}
