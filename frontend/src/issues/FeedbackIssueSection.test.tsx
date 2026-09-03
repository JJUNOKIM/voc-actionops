import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { UserProfile } from '../types/api';
import { FeedbackIssueSection } from './FeedbackIssueSection';
import type { FeedbackIssue, IssueCandidate } from './types';

const workflowMocks = vi.hoisted(() => ({
  confirmIssueCandidateRequest: vi.fn(),
  feedbackIssuesRequest: vi.fn(),
  issueCandidatesRequest: vi.fn(),
  changeFeedbackRepresentativeRequest: vi.fn(),
  unlinkFeedbackRequest: vi.fn(),
}));

vi.mock('./api', () => workflowMocks);
vi.mock('./IssueDraftDialog', () => ({
  IssueDraftDialog: ({ onCreated }: { onCreated: () => void }) => (
    <button type="button" onClick={onCreated}>초안 등록 완료</button>
  ),
}));
vi.mock('./IssueLinkDialog', () => ({
  IssueLinkDialog: ({
    linkedIssueIds,
    onLinked,
  }: {
    linkedIssueIds: number[];
    onLinked: () => void;
  }) => (
    <div>
      <span>연결된 이슈 번호: {linkedIssueIds.join(', ')}</span>
      <button type="button" onClick={onLinked}>기존 이슈 연결 완료</button>
    </div>
  ),
}));

const admin: UserProfile = {
  id: 1,
  organizationId: 1,
  organizationName: 'VOC Team',
  email: 'admin@example.com',
  name: '관리자',
  role: 'ADMIN',
};

const candidate: IssueCandidate = {
  issueId: 7,
  title: '쿠폰 적용 후 결제 실패',
  category: 'PAYMENT',
  priority: 'P1',
  status: 'IN_PROGRESS',
  similarityScore: 0.82,
  matchSignals: {
    categoryMatched: true,
    categoryScore: 0.35,
    characterSimilarity: 0.5,
    tokenSimilarity: 0.6,
    textSimilarity: 0.57,
  },
};

const linkedIssue: FeedbackIssue = {
  linkId: 21,
  issueId: 7,
  title: candidate.title,
  category: candidate.category,
  priority: candidate.priority,
  status: candidate.status,
  assigneeId: 3,
  assigneeName: '김개발',
  similarityScore: candidate.similarityScore,
  representative: true,
  linkedBy: 'AI',
  linkedAt: '2026-09-02T14:30:00',
};

describe('FeedbackIssueSection', () => {
  beforeEach(() => {
    workflowMocks.confirmIssueCandidateRequest.mockReset();
    workflowMocks.feedbackIssuesRequest.mockReset();
    workflowMocks.issueCandidatesRequest.mockReset();
    workflowMocks.changeFeedbackRepresentativeRequest.mockReset();
    workflowMocks.unlinkFeedbackRequest.mockReset();
  });

  it('confirms a candidate and reloads the persisted link', async () => {
    const user = userEvent.setup();
    workflowMocks.feedbackIssuesRequest
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([linkedIssue]);
    workflowMocks.issueCandidatesRequest
      .mockResolvedValueOnce([candidate])
      .mockResolvedValueOnce([]);
    workflowMocks.confirmIssueCandidateRequest.mockResolvedValue({});

    renderSection(admin);

    expect(await screen.findByText('쿠폰 적용 후 결제 실패')).toBeInTheDocument();
    expect(screen.getByText('82.0%')).toBeInTheDocument();
    expect(screen.getByText('텍스트 57.0%')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '연결 검토' }));
    await user.click(screen.getByRole('checkbox', { name: /대표 피드백으로 지정/ }));
    await user.click(screen.getByRole('button', { name: '연결 확정' }));

    expect(workflowMocks.confirmIssueCandidateRequest).toHaveBeenCalledWith(31, 7, true);
    expect(await screen.findByText('추천 이슈 연결을 확정했습니다.')).toBeInTheDocument();
    expect(await screen.findByText('대표 피드백')).toBeInTheDocument();
    expect(screen.getByText('추천 확정')).toBeInTheDocument();
    expect(workflowMocks.feedbackIssuesRequest).toHaveBeenCalledTimes(2);
  });

  it('keeps recommendation data read-only for a viewer', async () => {
    workflowMocks.feedbackIssuesRequest.mockResolvedValue([{ ...linkedIssue, title: '이미 확인한 이슈' }]);
    workflowMocks.issueCandidatesRequest.mockResolvedValue([candidate]);

    renderSection({ ...admin, role: 'VIEWER' });

    expect(await screen.findByText('쿠폰 적용 후 결제 실패')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '연결 검토' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '기존 이슈 연결' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '이슈 연결 해제' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '대표 피드백 지정 해제' })).not.toBeInTheDocument();
  });

  it('reloads the workflow after unlinking an issue', async () => {
    const user = userEvent.setup();
    workflowMocks.feedbackIssuesRequest.mockResolvedValueOnce([linkedIssue]).mockResolvedValueOnce([]);
    workflowMocks.issueCandidatesRequest.mockResolvedValue([]);
    workflowMocks.unlinkFeedbackRequest.mockResolvedValue(undefined);

    renderSection(admin);
    await user.click(await screen.findByRole('button', { name: '이슈 연결 해제' }));
    await user.click(screen.getByRole('button', { name: '연결 해제' }));

    expect(workflowMocks.unlinkFeedbackRequest).toHaveBeenCalledWith(31, 7);
    expect(await screen.findByText('이슈 연결을 해제했습니다.')).toBeInTheDocument();
    expect(await screen.findByText('유사한 기존 이슈가 없습니다.')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: linkedIssue.title })).not.toBeInTheDocument();
  });

  it('opens the manual link flow and reloads linked issues after completion', async () => {
    const user = userEvent.setup();
    workflowMocks.feedbackIssuesRequest
      .mockResolvedValueOnce([linkedIssue])
      .mockResolvedValueOnce([linkedIssue, { ...linkedIssue, linkId: 22, issueId: 8 }]);
    workflowMocks.issueCandidatesRequest.mockResolvedValue([]);

    renderSection({ ...admin, role: 'CS' });

    await user.click(await screen.findByRole('button', { name: '기존 이슈 연결' }));
    expect(screen.getByText('연결된 이슈 번호: 7')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '기존 이슈 연결 완료' }));

    expect(await screen.findByText('기존 이슈를 직접 연결했습니다.')).toBeInTheDocument();
    expect(workflowMocks.feedbackIssuesRequest).toHaveBeenCalledTimes(2);
  });

  it('waits for a completed analysis before requesting candidates', async () => {
    workflowMocks.feedbackIssuesRequest.mockResolvedValue([]);

    renderSection(admin, null, null);

    expect(
      await screen.findByText('분석 완료 후 이슈 후보를 확인할 수 있습니다.'),
    ).toBeInTheDocument();
    expect(workflowMocks.issueCandidatesRequest).not.toHaveBeenCalled();
  });

  it('opens the new issue flow only when no candidate or link exists', async () => {
    const user = userEvent.setup();
    workflowMocks.feedbackIssuesRequest
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([linkedIssue]);
    workflowMocks.issueCandidatesRequest.mockResolvedValue([]);

    renderSection(admin);

    await user.click(await screen.findByRole('button', { name: '새 이슈 작성' }));
    await user.click(screen.getByRole('button', { name: '초안 등록 완료' }));

    expect(await screen.findByText('새 이슈를 만들고 대표 피드백으로 연결했습니다.'))
      .toBeInTheDocument();
  });
});

function renderSection(
  user: UserProfile,
  analysisStatus: 'SUCCESS' | null = 'SUCCESS',
  analysisCategory: string | null = 'PAYMENT',
) {
  return render(
    <MemoryRouter>
      <FeedbackIssueSection
        feedbackId={31}
        analysisStatus={analysisStatus}
        analysisCategory={analysisCategory}
        user={user}
      />
    </MemoryRouter>,
  );
}
