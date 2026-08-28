import type { FeedbackAnalysisStatus, Sentiment } from './types';

const sentimentLabels: Record<Sentiment, string> = {
  POSITIVE: '긍정',
  NEUTRAL: '중립',
  NEGATIVE: '부정',
};

const analysisStatusLabels: Record<FeedbackAnalysisStatus, string> = {
  PENDING: '분석 중',
  SUCCESS: '분석 완료',
  FAILED: '분석 실패',
};

export function sentimentLabel(sentiment: Sentiment): string {
  return sentimentLabels[sentiment];
}

export function sentimentTone(sentiment: Sentiment): 'positive' | 'neutral' | 'negative' {
  if (sentiment === 'POSITIVE') return 'positive';
  if (sentiment === 'NEGATIVE') return 'negative';
  return 'neutral';
}

export function feedbackAnalysisStatusLabel(status: FeedbackAnalysisStatus): string {
  return analysisStatusLabels[status];
}

export function feedbackAnalysisStatusTone(
  status: FeedbackAnalysisStatus,
): 'progress' | 'success' | 'danger' {
  if (status === 'PENDING') return 'progress';
  if (status === 'SUCCESS') return 'success';
  return 'danger';
}
