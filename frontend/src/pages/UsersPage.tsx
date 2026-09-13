import { AlertCircle, RefreshCw, UsersRound } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { useAuth } from '../auth/useAuth';
import { ApiError } from '../lib/api-client';
import type { OrganizationUser, Role } from '../types/api';
import {
  changeOrganizationUserRoleRequest,
  organizationUsersRequest,
} from '../users/api';
import { roleLabel, roleOptions } from '../users/labels';

export function UsersPage() {
  const { user } = useAuth();
  const [users, setUsers] = useState<OrganizationUser[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [updatingUserId, setUpdatingUserId] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [reloadSequence, setReloadSequence] = useState(0);
  const requestSequence = useRef(0);

  useEffect(() => {
    const sequence = requestSequence.current + 1;
    requestSequence.current = sequence;

    void organizationUsersRequest()
      .then((response) => {
        if (requestSequence.current === sequence) {
          setUsers(response);
          setErrorMessage(null);
        }
      })
      .catch((error: unknown) => {
        if (requestSequence.current === sequence) {
          setErrorMessage(
            error instanceof ApiError ? error.message : '사용자 목록을 불러올 수 없습니다.',
          );
        }
      })
      .finally(() => {
        if (requestSequence.current === sequence) {
          setLoading(false);
        }
      });
  }, [reloadSequence]);

  function refreshUsers() {
    setLoading(true);
    setErrorMessage(null);
    setNotice(null);
    setReloadSequence((current) => current + 1);
  }

  async function changeRole(organizationUser: OrganizationUser, role: Role) {
    if (organizationUser.role === role) return;

    setUpdatingUserId(organizationUser.id);
    setErrorMessage(null);
    setNotice(null);
    try {
      const updatedUser = await changeOrganizationUserRoleRequest(organizationUser.id, role);
      setUsers((current) =>
        current?.map((item) => (item.id === updatedUser.id ? updatedUser : item)) ?? null,
      );
      setNotice(`${updatedUser.name}님의 역할을 변경했습니다. (${roleLabel(updatedUser.role)})`);
    } catch (error) {
      setErrorMessage(
        error instanceof ApiError ? error.message : '사용자 역할을 변경할 수 없습니다.',
      );
    } finally {
      setUpdatingUserId(null);
    }
  }

  const organizationUsers = users ?? [];

  return (
    <div className="page-container users-page">
      <header className="page-header">
        <div>
          <h1>사용자 관리</h1>
          <p className="page-description">조직 구성원과 서비스 접근 역할을 관리합니다.</p>
        </div>
      </header>

      <section className="user-workspace" aria-labelledby="user-list-title">
        <div className="user-toolbar">
          <div>
            <h2 id="user-list-title">조직 구성원</h2>
            <span>{users === null ? '조회 중' : `${organizationUsers.length}명`}</span>
          </div>
          <button
            className="icon-button"
            type="button"
            onClick={refreshUsers}
            disabled={loading || updatingUserId !== null}
            aria-label="사용자 목록 새로고침"
            title="새로고침"
          >
            <RefreshCw className={loading ? 'spin' : undefined} size={18} />
          </button>
        </div>

        {notice !== null && (
          <div className="user-notice" role="status">
            {notice}
          </div>
        )}

        {errorMessage !== null && (
          <div className="dataset-error user-error" role="alert">
            <AlertCircle size={20} aria-hidden="true" />
            <div>
              <strong>요청을 처리하지 못했습니다.</strong>
              <span>{errorMessage}</span>
            </div>
            {users === null && (
              <button className="secondary-button" type="button" onClick={refreshUsers}>
                다시 시도
              </button>
            )}
          </div>
        )}

        {errorMessage === null && loading && users === null && (
          <div className="user-loading" aria-label="사용자 목록 불러오는 중">
            <span />
            <span />
            <span />
          </div>
        )}

        {errorMessage === null && !loading && organizationUsers.length === 0 && (
          <div className="dataset-empty">
            <UsersRound size={29} aria-hidden="true" />
            <h3>등록된 사용자가 없습니다.</h3>
          </div>
        )}

        {organizationUsers.length > 0 && (
          <div className={`user-table-wrap${loading ? ' is-refreshing' : ''}`}>
            <table className="user-table">
              <thead>
                <tr>
                  <th>구성원</th>
                  <th>이메일</th>
                  <th>역할</th>
                </tr>
              </thead>
              <tbody>
                {organizationUsers.map((organizationUser) => {
                  const isCurrentUser = organizationUser.id === user?.id;
                  const isUpdating = updatingUserId === organizationUser.id;
                  return (
                    <tr key={organizationUser.id}>
                      <td>
                        <div className="user-name-cell">
                          <span aria-hidden="true">{initials(organizationUser.name)}</span>
                          <strong>{organizationUser.name}</strong>
                        </div>
                      </td>
                      <td className="user-email-cell">{organizationUser.email}</td>
                      <td>
                        {isCurrentUser ? (
                          <div className="user-current-role">
                            <strong>{roleLabel(organizationUser.role)}</strong>
                            <span>내 계정</span>
                          </div>
                        ) : (
                          <label className="user-role-field">
                            <span className="sr-only">{organizationUser.name} 역할</span>
                            <select
                              value={organizationUser.role}
                              onChange={(event) =>
                                void changeRole(organizationUser, event.target.value as Role)
                              }
                              disabled={updatingUserId !== null}
                              aria-label={`${organizationUser.name} 역할`}
                            >
                              {roleOptions.map((option) => (
                                <option key={option.value} value={option.value}>
                                  {option.label}
                                </option>
                              ))}
                            </select>
                            <span>{isUpdating ? '변경 중' : '변경 가능'}</span>
                          </label>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

function initials(name: string): string {
  const words = name.trim().split(/\s+/);
  if (words.length === 1) {
    return words[0].slice(0, 2).toUpperCase();
  }
  return words
    .slice(0, 2)
    .map((word) => word[0])
    .join('')
    .toUpperCase();
}
