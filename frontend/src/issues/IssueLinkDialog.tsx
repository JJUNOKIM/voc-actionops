import {
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  LoaderCircle,
  RefreshCw,
  Search,
  X,
} from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent } from 'react';

import type { PageResponse } from '../datasets/types';
import { ApiError } from '../lib/api-client';
import { issuesRequest, linkFeedbackToIssueRequest } from './api';
import { issueStatusLabel, issueStatusTone, priorityTone } from './format';
import type { IssueSummary } from './types';

const PAGE_SIZE = 8;

interface IssueLinkDialogProps {
  feedbackId: number;
  linkedIssueIds: number[];
  onClose: () => void;
  onLinked: () => void;
}

type IssueSearchState =
  | { requestKey: string; status: 'success'; data: PageResponse<IssueSummary> }
  | { requestKey: string; status: 'error'; message: string }
  | null;

export function IssueLinkDialog({
  feedbackId,
  linkedIssueIds,
  onClose,
  onLinked,
}: IssueLinkDialogProps) {
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [reloadSequence, setReloadSequence] = useState(0);
  const [searchState, setSearchState] = useState<IssueSearchState>(null);
  const [selectedIssueId, setSelectedIssueId] = useState<number | null>(null);
  const [representative, setRepresentative] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [linkError, setLinkError] = useState<string | null>(null);
  const requestSequence = useRef(0);
  const requestKey = `${page}:${keyword}:${reloadSequence}`;

  useEffect(() => {
    const sequence = requestSequence.current + 1;
    requestSequence.current = sequence;

    void issuesRequest({
      page,
      size: PAGE_SIZE,
      ...(keyword === '' ? {} : { keyword }),
    })
      .then((data) => {
        if (requestSequence.current === sequence) {
          setSearchState({ requestKey, status: 'success', data });
        }
      })
      .catch((error: unknown) => {
        if (requestSequence.current === sequence) {
          setSearchState({
            requestKey,
            status: 'error',
            message:
              error instanceof ApiError ? error.message : '기존 이슈를 불러올 수 없습니다.',
          });
        }
      });
  }, [keyword, page, reloadSequence, requestKey]);

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

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextKeyword = keywordInput.trim();
    setSelectedIssueId(null);
    setLinkError(null);
    if (page === 0 && keyword === nextKeyword) {
      setReloadSequence((current) => current + 1);
      return;
    }
    setPage(0);
    setKeyword(nextKeyword);
  }

  function changePage(nextPage: number) {
    setSelectedIssueId(null);
    setLinkError(null);
    setPage(nextPage);
  }

  async function handleLink(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (selectedIssueId === null) return;

    setSubmitting(true);
    setLinkError(null);
    try {
      await linkFeedbackToIssueRequest(feedbackId, selectedIssueId, representative);
      onLinked();
    } catch (error) {
      setLinkError(
        error instanceof ApiError ? error.message : '선택한 이슈를 연결할 수 없습니다.',
      );
    } finally {
      setSubmitting(false);
    }
  }

  const linkedIds = new Set(linkedIssueIds);
  const currentSearchState = searchState?.requestKey === requestKey ? searchState : null;
  const loading = currentSearchState === null;
  const result = currentSearchState?.status === 'success' ? currentSearchState.data : null;
  const searchError = currentSearchState?.status === 'error' ? currentSearchState.message : null;

  return (
    <div className="modal-layer">
      <button
        className="modal-backdrop"
        type="button"
        aria-label="기존 이슈 연결 창 닫기"
        onClick={submitting ? undefined : onClose}
      />
      <section
        className="upload-dialog manual-issue-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="manual-issue-dialog-title"
      >
        <header className="dialog-header">
          <div>
            <p className="section-label">ISSUE SEARCH</p>
            <h2 id="manual-issue-dialog-title">기존 이슈 연결</h2>
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

        <form className="manual-issue-search" onSubmit={handleSearch} role="search">
          <label htmlFor="manual-issue-keyword" className="sr-only">
            이슈 검색어
          </label>
          <Search size={17} aria-hidden="true" />
          <input
            id="manual-issue-keyword"
            type="search"
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
            placeholder="이슈 제목 또는 설명 검색"
            disabled={submitting}
            autoFocus
          />
          <button
            className="icon-button"
            type="submit"
            disabled={loading || submitting}
            aria-label="이슈 검색"
            title="검색"
          >
            {loading ? (
              <LoaderCircle className="spin" size={17} aria-hidden="true" />
            ) : (
              <Search size={17} aria-hidden="true" />
            )}
          </button>
        </form>

        <form className="upload-form" onSubmit={handleLink}>
          <div className="dialog-body manual-issue-dialog-body">
            {loading && result === null && <IssueSearchLoading />}

            {!loading && searchError !== null && (
              <div className="manual-issue-search-error" role="alert">
                <AlertCircle size={19} aria-hidden="true" />
                <div>
                  <strong>이슈 목록을 불러오지 못했습니다.</strong>
                  <span>{searchError}</span>
                </div>
                <button
                  className="icon-button"
                  type="button"
                  onClick={() => setReloadSequence((current) => current + 1)}
                  aria-label="이슈 목록 다시 불러오기"
                  title="다시 불러오기"
                >
                  <RefreshCw size={17} />
                </button>
              </div>
            )}

            {!loading && searchError === null && result?.content.length === 0 && (
              <div className="manual-issue-empty">
                <Search size={22} aria-hidden="true" />
                <strong>검색 결과가 없습니다.</strong>
                <span>다른 검색어로 확인해 주세요.</span>
              </div>
            )}

            {!loading && searchError === null && result !== null && result.content.length > 0 && (
              <>
                <div className="manual-issue-result-heading">
                  <span>{keyword === '' ? '최근 이슈' : `'${keyword}' 검색 결과`}</span>
                  <strong>{result.totalElements}건</strong>
                </div>
                <ul className="manual-issue-list">
                  {result.content.map((issue) => {
                    const alreadyLinked = linkedIds.has(issue.id);
                    return (
                      <li key={issue.id}>
                        <label
                          className={`manual-issue-option${
                            selectedIssueId === issue.id ? ' is-selected' : ''
                          }${alreadyLinked ? ' is-linked' : ''}`}
                        >
                          <input
                            type="radio"
                            name="issueId"
                            value={issue.id}
                            checked={selectedIssueId === issue.id}
                            onChange={() => setSelectedIssueId(issue.id)}
                            disabled={alreadyLinked || submitting}
                          />
                          <span className="manual-issue-option-copy">
                            <strong>{issue.title}</strong>
                            <small>
                              #{issue.id} · {issue.category} ·{' '}
                              {issue.assigneeName ?? '담당자 미지정'} · 피드백 {issue.feedbackCount}건
                            </small>
                          </span>
                          <span className="manual-issue-option-badges">
                            <span
                              className={`priority-badge priority-badge--${priorityTone(
                                issue.priority,
                              )}`}
                            >
                              {issue.priority}
                            </span>
                            <span
                              className={`issue-status issue-status--${issueStatusTone(
                                issue.status,
                              )}`}
                            >
                              {issueStatusLabel(issue.status)}
                            </span>
                            {alreadyLinked && <span className="manual-linked-label">연결됨</span>}
                          </span>
                        </label>
                      </li>
                    );
                  })}
                </ul>
                {result.totalPages > 1 && (
                  <div className="manual-issue-pagination">
                    <span>
                      {result.page + 1} / {result.totalPages} 페이지
                    </span>
                    <div>
                      <button
                        className="icon-button"
                        type="button"
                        onClick={() => changePage(Math.max(0, result.page - 1))}
                        disabled={submitting || result.page === 0}
                        aria-label="이전 페이지"
                        title="이전 페이지"
                      >
                        <ChevronLeft size={18} />
                      </button>
                      <button
                        className="icon-button"
                        type="button"
                        onClick={() => changePage(result.page + 1)}
                        disabled={submitting || result.page + 1 >= result.totalPages}
                        aria-label="다음 페이지"
                        title="다음 페이지"
                      >
                        <ChevronRight size={18} />
                      </button>
                    </div>
                  </div>
                )}
              </>
            )}

            <label className="representative-control manual-representative-control">
              <input
                type="checkbox"
                checked={representative}
                onChange={(event) => setRepresentative(event.target.checked)}
                disabled={selectedIssueId === null || submitting}
              />
              <span>
                <strong>대표 피드백으로 지정</strong>
                <small>이슈의 원인과 영향을 가장 잘 보여주는 피드백일 때 선택합니다.</small>
              </span>
            </label>

            {linkError !== null && (
              <div className="dialog-error" role="alert">
                <AlertCircle size={18} aria-hidden="true" />
                <span>{linkError}</span>
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
              disabled={selectedIssueId === null || submitting}
            >
              {submitting && <LoaderCircle className="spin" size={17} aria-hidden="true" />}
              <span>{submitting ? '연결 중' : '선택 이슈 연결'}</span>
            </button>
          </footer>
        </form>
      </section>
    </div>
  );
}

function IssueSearchLoading() {
  return (
    <div className="manual-issue-loading" aria-label="기존 이슈 로딩 중">
      <span />
      <span />
      <span />
    </div>
  );
}
