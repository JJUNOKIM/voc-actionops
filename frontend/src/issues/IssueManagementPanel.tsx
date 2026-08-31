import { AlertCircle, ArrowRight, CheckCircle2, LoaderCircle, RefreshCw } from 'lucide-react';
import { useState, type FormEvent } from 'react';

import { ApiError } from '../lib/api-client';
import type { OrganizationUser, UserProfile } from '../types/api';
import { roleLabel } from '../users/labels';
import { assignIssueRequest, changeIssueStatusRequest } from './api';
import { issueStatusLabel } from './format';
import type { IssueDetail, IssueStatus } from './types';
import {
  canChangeIssueStatus,
  canManageIssue,
  issueTransitionLabel,
  nextIssueStatuses,
} from './workflow';

interface IssueManagementPanelProps {
  issue: IssueDetail;
  user: UserProfile;
  users: OrganizationUser[] | null;
  usersLoading: boolean;
  usersError: string | null;
  onRetryUsers: () => void;
  onIssueUpdated: (issue: IssueDetail) => void;
}

export function IssueManagementPanel({
  issue,
  user,
  users,
  usersLoading,
  usersError,
  onRetryUsers,
  onIssueUpdated,
}: IssueManagementPanelProps) {
  const currentAssignee = issue.assigneeId === null ? '' : String(issue.assigneeId);
  const [assignmentDraft, setAssignmentDraft] = useState({
    issueId: issue.id,
    assigneeId: currentAssignee,
  });
  const [operation, setOperation] = useState<'assignment' | IssueStatus | null>(null);
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; message: string } | null>(
    null,
  );

  const selectedAssignee =
    assignmentDraft.issueId === issue.id ? assignmentDraft.assigneeId : currentAssignee;
  const canAssign = canManageIssue(user) && issue.status !== 'CLOSED';
  const statusTargets = canChangeIssueStatus(user, issue)
    ? nextIssueStatuses(issue.status)
    : [];
  const assignmentRequired = issue.status === 'TRIAGED' && issue.assigneeId === null;

  if (!canAssign && statusTargets.length === 0) return null;

  async function assignIssue(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const assigneeId = Number(selectedAssignee);
    if (!Number.isSafeInteger(assigneeId) || assigneeId <= 0) {
      setNotice({ tone: 'error', message: '담당자를 선택해 주세요.' });
      return;
    }

    setOperation('assignment');
    setNotice(null);
    try {
      const updatedIssue = await assignIssueRequest(issue.id, assigneeId);
      setAssignmentDraft({ issueId: issue.id, assigneeId: String(updatedIssue.assigneeId) });
      onIssueUpdated(updatedIssue);
      setNotice({ tone: 'success', message: '담당자를 변경했습니다.' });
    } catch (error) {
      setNotice({
        tone: 'error',
        message: error instanceof ApiError ? error.message : '담당자를 변경할 수 없습니다.',
      });
    } finally {
      setOperation(null);
    }
  }

  async function changeStatus(status: IssueStatus) {
    setOperation(status);
    setNotice(null);
    try {
      const updatedIssue = await changeIssueStatusRequest(issue.id, status);
      onIssueUpdated(updatedIssue);
      setNotice({
        tone: 'success',
        message: `이슈 상태를 변경했습니다. 현재 상태: ${issueStatusLabel(updatedIssue.status)}`,
      });
    } catch (error) {
      setNotice({
        tone: 'error',
        message: error instanceof ApiError ? error.message : '이슈 상태를 변경할 수 없습니다.',
      });
    } finally {
      setOperation(null);
    }
  }

  return (
    <section className="issue-detail-section issue-management-section" aria-labelledby="issue-management-title">
      <header className="issue-section-header">
        <div>
          <h2 id="issue-management-title">이슈 관리</h2>
          <span>담당자와 처리 단계를 관리합니다.</span>
        </div>
      </header>

      <div className={`issue-management-grid${canAssign && statusTargets.length > 0 ? '' : ' is-single'}`}>
        {canAssign && (
          <form className="issue-assignment-control" onSubmit={assignIssue}>
            <div>
              <h3>담당자</h3>
              <span>이슈를 처리할 조직 구성원</span>
            </div>
            {usersError !== null ? (
              <div className="issue-users-error" role="alert">
                <AlertCircle size={18} aria-hidden="true" />
                <span>{usersError}</span>
                <button
                  className="icon-button"
                  type="button"
                  onClick={onRetryUsers}
                  aria-label="구성원 다시 불러오기"
                  title="구성원 다시 불러오기"
                >
                  <RefreshCw size={17} />
                </button>
              </div>
            ) : (
              <div className="issue-assignment-form">
                <label>
                  <span className="sr-only">담당자 선택</span>
                  <select
                    value={selectedAssignee}
                    onChange={(event) => {
                      setAssignmentDraft({ issueId: issue.id, assigneeId: event.target.value });
                      setNotice(null);
                    }}
                    disabled={usersLoading || operation !== null}
                  >
                    <option value="">담당자 선택</option>
                    {(users ?? []).map((organizationUser) => (
                      <option key={organizationUser.id} value={organizationUser.id}>
                        {organizationUser.name} · {roleLabel(organizationUser.role)}
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  className="secondary-button"
                  type="submit"
                  disabled={
                    usersLoading ||
                    operation !== null ||
                    selectedAssignee === '' ||
                    selectedAssignee === currentAssignee
                  }
                >
                  {operation === 'assignment' && (
                    <LoaderCircle className="spin" size={16} aria-hidden="true" />
                  )}
                  <span>{usersLoading ? '구성원 조회 중' : '담당자 저장'}</span>
                </button>
              </div>
            )}
          </form>
        )}

        {statusTargets.length > 0 && (
          <div className="issue-status-control">
            <div>
              <h3>처리 단계</h3>
              <span>현재 상태에서 가능한 다음 단계</span>
            </div>
            <div className="issue-status-flow">
              <strong>{issueStatusLabel(issue.status)}</strong>
              <ArrowRight size={17} aria-hidden="true" />
              <div>
                {statusTargets.map((status) => (
                  <button
                    className="secondary-button"
                    type="button"
                    key={status}
                    onClick={() => void changeStatus(status)}
                    disabled={operation !== null || (status === 'ASSIGNED' && assignmentRequired)}
                  >
                    {operation === status && (
                      <LoaderCircle className="spin" size={16} aria-hidden="true" />
                    )}
                    <span>{issueTransitionLabel(issue.status, status)}</span>
                  </button>
                ))}
              </div>
            </div>
            {assignmentRequired && (
              <p className="issue-management-note">담당자를 지정한 뒤 다음 단계로 이동할 수 있습니다.</p>
            )}
          </div>
        )}
      </div>

      {notice !== null && (
        <div className={`issue-operation-notice issue-operation-notice--${notice.tone}`} role="status">
          {notice.tone === 'success' ? (
            <CheckCircle2 size={18} aria-hidden="true" />
          ) : (
            <AlertCircle size={18} aria-hidden="true" />
          )}
          <span>{notice.message}</span>
        </div>
      )}
    </section>
  );
}
