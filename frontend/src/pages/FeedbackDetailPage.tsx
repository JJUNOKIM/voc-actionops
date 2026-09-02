import {
  AlertCircle,
  ArrowLeft,
  BrainCircuit,
  CheckCircle2,
  Clock3,
  MessageSquareText,
  TriangleAlert,
} from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';

import { useAuth } from '../auth/useAuth';
import { formatDate } from '../datasets/format';
import { sourceTypeLabel } from '../datasets/labels';
import { feedbackDetailRequest } from '../feedbacks/api';
import { FeedbackCorrectionSection } from '../feedbacks/FeedbackCorrectionSection';
import { feedbackDisplayId, formatRating, formatScore } from '../feedbacks/format';
import {
  feedbackAnalysisStatusLabel,
  feedbackAnalysisStatusTone,
  sentimentLabel,
  sentimentTone,
} from '../feedbacks/labels';
import type { FeedbackAnalysisDetail, FeedbackDetail } from '../feedbacks/types';
import { FeedbackIssueSection } from '../issues/FeedbackIssueSection';
import { ApiError } from '../lib/api-client';

type DetailState =
  | { feedbackId: number; status: 'success'; data: FeedbackDetail }
  | { feedbackId: number; status: 'error'; message: string }
  | null;

export function FeedbackDetailPage() {
  const { feedbackId: feedbackIdParam } = useParams();
  const { user } = useAuth();
  const feedbackId = parseFeedbackId(feedbackIdParam);
  const canCorrectAnalysis =
    user?.role === 'ADMIN' || user?.role === 'PM' || user?.role === 'CS';
  const [state, setState] = useState<DetailState>(null);
  const [reloadSequence, setReloadSequence] = useState(0);
  const requestSequence = useRef(0);

  useEffect(() => {
    if (feedbackId === null) return;
    const sequence = requestSequence.current + 1;
    requestSequence.current = sequence;

    void feedbackDetailRequest(feedbackId)
      .then((data) => {
        if (requestSequence.current === sequence) {
          setState({ feedbackId, status: 'success', data });
        }
      })
      .catch((error: unknown) => {
        if (requestSequence.current === sequence) {
          setState({
            feedbackId,
            status: 'error',
            message: error instanceof ApiError ? error.message : '피드백을 불러올 수 없습니다.',
          });
        }
      });
  }, [feedbackId, reloadSequence]);

  function retry() {
    setState(null);
    setReloadSequence((current) => current + 1);
  }

  const handleAnalysisCorrected = useCallback(
    (analysis: FeedbackAnalysisDetail) => {
      setState((current) => {
        if (current?.status !== 'success' || current.feedbackId !== feedbackId) return current;
        return {
          ...current,
          data: { ...current.data, analysis },
        };
      });
    },
    [feedbackId],
  );

  if (feedbackId === null) {
    return (
      <FeedbackDetailFailure
        title="잘못된 피드백 주소입니다."
        description="피드백 목록에서 다시 선택해 주세요."
      />
    );
  }

  const currentState = state?.feedbackId === feedbackId ? state : null;
  if (currentState === null) return <FeedbackDetailLoading />;
  if (currentState.status === 'error') {
    return (
      <FeedbackDetailFailure
        title="피드백을 불러오지 못했습니다."
        description={currentState.message}
        onRetry={retry}
      />
    );
  }

  const feedback = currentState.data;
  return (
    <div className="page-container dataset-detail-page feedback-detail-page">
      <Link className="back-link" to={`/feedbacks?datasetId=${feedback.datasetId}`}>
        <ArrowLeft size={17} aria-hidden="true" />
        <span>피드백 목록</span>
      </Link>

      <header className="page-header dataset-detail-header feedback-detail-header">
        <div>
          <p className="section-label">VOC DETAIL</p>
          <h1>{feedbackDisplayId(feedback.externalId, feedback.id)}</h1>
          <p className="page-description">
            {feedback.datasetName} · {sourceTypeLabel(feedback.sourceType)}
          </p>
        </div>
        <AnalysisStatusBadge analysis={feedback.analysis} />
      </header>

      <section className="dataset-detail-section feedback-detail-section" aria-labelledby="original-feedback-title">
        <header className="detail-section-header feedback-detail-section-header">
          <div>
            <p className="section-label">ORIGINAL</p>
            <h2 id="original-feedback-title">고객 원문</h2>
          </div>
          <MessageSquareText size={19} aria-hidden="true" />
        </header>
        <blockquote className="feedback-original-content">{feedback.content}</blockquote>
        <dl className="feedback-metadata-grid">
          <MetadataItem label="데이터셋">
            <Link to={`/datasets/${feedback.datasetId}`}>{feedback.datasetName}</Link>
          </MetadataItem>
          <MetadataItem label="제품" value={feedback.productName} />
          <MetadataItem label="고객군" value={feedback.customerSegment} />
          <MetadataItem label="평점" value={formatRating(feedback.rating)} />
          <MetadataItem label="언어" value={feedback.language} />
          <MetadataItem label="작성 시각" value={formatNullableDate(feedback.feedbackCreatedAt)} />
          <MetadataItem label="수집 시각" value={formatDate(feedback.ingestedAt)} />
          <MetadataItem label="데이터 출처" value={sourceTypeLabel(feedback.sourceType)} />
        </dl>
      </section>

      <section className="dataset-detail-section feedback-detail-section" aria-labelledby="analysis-result-title">
        <header className="detail-section-header feedback-detail-section-header">
          <div>
            <p className="section-label">AI ANALYSIS</p>
            <h2 id="analysis-result-title">분석 결과</h2>
          </div>
          <BrainCircuit size={19} aria-hidden="true" />
        </header>
        <FeedbackAnalysisResult analysis={feedback.analysis} />
      </section>

      {canCorrectAnalysis && feedback.analysis?.status === 'SUCCESS' && (
        <FeedbackCorrectionSection
          feedbackId={feedback.id}
          analysis={feedback.analysis}
          onAnalysisCorrected={handleAnalysisCorrected}
        />
      )}

      <FeedbackIssueSection
        feedbackId={feedback.id}
        analysisStatus={feedback.analysis?.status ?? null}
        analysisCategory={feedback.analysis?.category ?? null}
        user={user}
      />
    </div>
  );
}

function AnalysisStatusBadge({ analysis }: { analysis: FeedbackAnalysisDetail | null }) {
  if (analysis === null) {
    return <span className="status-badge detail-status status-badge--ready">미분석</span>;
  }
  return (
    <span
      className={`status-badge detail-status status-badge--${feedbackAnalysisStatusTone(analysis.status)}`}
    >
      {feedbackAnalysisStatusLabel(analysis.status)}
    </span>
  );
}

function FeedbackAnalysisResult({ analysis }: { analysis: FeedbackAnalysisDetail | null }) {
  if (analysis === null) {
    return (
      <div className="feedback-analysis-state">
        <Clock3 size={22} aria-hidden="true" />
        <div>
          <strong>아직 분석 결과가 없습니다.</strong>
          <span>데이터셋에서 AI 분석을 실행하면 결과가 여기에 표시됩니다.</span>
        </div>
      </div>
    );
  }
  if (analysis.status === 'PENDING') {
    return (
      <div className="feedback-analysis-state feedback-analysis-state--progress">
        <Clock3 size={22} aria-hidden="true" />
        <div>
          <strong>분석을 진행하고 있습니다.</strong>
          <span>데이터셋 분석 작업이 완료된 뒤 결과를 확인할 수 있습니다.</span>
        </div>
      </div>
    );
  }
  if (analysis.status === 'FAILED') {
    return (
      <div className="feedback-analysis-state feedback-analysis-state--failed" role="alert">
        <AlertCircle size={22} aria-hidden="true" />
        <div>
          <strong>이 피드백을 분석하지 못했습니다.</strong>
          <span>{analysis.errorMessage ?? '분석 과정에서 오류가 발생했습니다.'}</span>
        </div>
      </div>
    );
  }

  const lowConfidence = analysis.confidenceScore !== null && analysis.confidenceScore < 0.7;
  return (
    <div className="feedback-analysis-result">
      <div className="analysis-summary-block">
        <CheckCircle2 size={21} aria-hidden="true" />
        <div>
          <span>AI 요약</span>
          <strong>{analysis.summary ?? '요약 정보가 없습니다.'}</strong>
        </div>
      </div>

      <dl className="analysis-result-grid">
        <div>
          <dt>감성</dt>
          <dd>
            {analysis.sentiment === null ? (
              '-'
            ) : (
              <span className={`sentiment-badge sentiment-badge--${sentimentTone(analysis.sentiment)}`}>
                {sentimentLabel(analysis.sentiment)}
              </span>
            )}
          </dd>
          <small>점수 {formatScore(analysis.sentimentScore)}</small>
        </div>
        <div>
          <dt>카테고리</dt>
          <dd>{analysis.category ?? '-'}</dd>
          <small>AI 분류 결과</small>
        </div>
        <div>
          <dt>긴급도</dt>
          <dd>{formatScore(analysis.urgencyScore)}</dd>
          <small>높을수록 빠른 대응 필요</small>
        </div>
        <div className={lowConfidence ? 'analysis-result--warning' : undefined}>
          <dt>신뢰도</dt>
          <dd>{formatScore(analysis.confidenceScore)}</dd>
          <small>{lowConfidence ? '사용자 검토 필요' : '모델 예측 신뢰도'}</small>
        </div>
      </dl>

      {lowConfidence && (
        <div className="analysis-confidence-warning" role="status">
          <TriangleAlert size={18} aria-hidden="true" />
          <span>신뢰도가 70% 미만입니다. 원문과 분석 결과를 함께 검토해 주세요.</span>
        </div>
      )}

      <dl className="analysis-model-metadata">
        <div>
          <dt>분석 모델</dt>
          <dd>{analysis.modelName}</dd>
        </div>
        <div>
          <dt>분석 시각</dt>
          <dd>{formatNullableDate(analysis.analyzedAt)}</dd>
        </div>
      </dl>
    </div>
  );
}

function MetadataItem({
  label,
  value,
  children,
}: {
  label: string;
  value?: string | null;
  children?: ReactNode;
}) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{children ?? value ?? '-'}</dd>
    </div>
  );
}

function FeedbackDetailLoading() {
  return (
    <div className="page-container dataset-detail-page" aria-label="피드백 상세 로딩 중">
      <div className="detail-loading detail-loading--back" />
      <div className="detail-loading detail-loading--title" />
      <div className="detail-loading detail-loading--section" />
      <div className="detail-loading detail-loading--section" />
    </div>
  );
}

function FeedbackDetailFailure({
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
      <Link className="back-link" to="/feedbacks">
        <ArrowLeft size={17} aria-hidden="true" />
        <span>피드백 목록</span>
      </Link>
      <div className="detail-failure">
        <AlertCircle size={28} aria-hidden="true" />
        <h1>{title}</h1>
        <p>{description}</p>
        {onRetry !== undefined && (
          <button className="secondary-button" type="button" onClick={onRetry}>
            다시 시도
          </button>
        )}
      </div>
    </div>
  );
}

function parseFeedbackId(value: string | undefined): number | null {
  if (value === undefined) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

function formatNullableDate(value: string | null): string {
  return value === null ? '-' : formatDate(value);
}
