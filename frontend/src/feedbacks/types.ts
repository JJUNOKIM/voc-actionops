import type { SourceType } from '../datasets/types';

export type FeedbackAnalysisStatus = 'PENDING' | 'SUCCESS' | 'FAILED';
export type Sentiment = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE';

export interface FeedbackAnalysisSummary {
  status: FeedbackAnalysisStatus;
  sentiment: Sentiment | null;
  category: string | null;
  urgencyScore: number | null;
  confidenceScore: number | null;
}

export interface FeedbackListItem {
  id: number;
  datasetId: number;
  datasetName: string;
  externalId: string | null;
  sourceType: SourceType;
  customerSegment: string | null;
  productName: string | null;
  rating: number | null;
  content: string;
  language: string | null;
  feedbackCreatedAt: string | null;
  ingestedAt: string;
  analysis: FeedbackAnalysisSummary | null;
}

export interface FeedbackAnalysisDetail extends FeedbackAnalysisSummary {
  id: number;
  sentimentScore: number | null;
  summary: string | null;
  modelName: string;
  errorMessage: string | null;
  analyzedAt: string | null;
}

export interface FeedbackDetail extends Omit<FeedbackListItem, 'analysis'> {
  analysis: FeedbackAnalysisDetail | null;
}

export interface FeedbackQuery {
  page: number;
  size: number;
  datasetId?: number;
  sourceType?: SourceType;
}
