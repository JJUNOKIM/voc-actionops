import { describe, expect, it } from 'vitest';

import {
  canChangeActionStatus,
  canChangeIssueStatus,
  canManageIssue,
  nextActionStatuses,
  nextIssueStatuses,
} from './workflow';
import type { IssueAction, IssueDetail } from './types';
import type { UserProfile } from '../types/api';

const admin = user(1, 'ADMIN');
const assignedDeveloper = user(3, 'DEVELOPER');
const otherDeveloper = user(4, 'DEVELOPER');

describe('issue workflow', () => {
  it('returns only backend-supported issue and action transitions', () => {
    expect(nextIssueStatuses('NEW')).toEqual(['TRIAGED']);
    expect(nextIssueStatuses('MONITORING')).toEqual(['CLOSED', 'IN_PROGRESS']);
    expect(nextIssueStatuses('CLOSED')).toEqual([]);
    expect(nextActionStatuses('TODO')).toEqual(['IN_PROGRESS', 'CANCELED']);
    expect(nextActionStatuses('IN_PROGRESS')).toEqual(['DONE', 'CANCELED']);
    expect(nextActionStatuses('DONE')).toEqual([]);
  });

  it('allows administrators and PMs to manage issues', () => {
    expect(canManageIssue(admin)).toBe(true);
    expect(canManageIssue(user(2, 'PM'))).toBe(true);
    expect(canManageIssue(assignedDeveloper)).toBe(false);
  });

  it('limits developer status changes to assigned work', () => {
    const issue = { assigneeId: assignedDeveloper.id } as IssueDetail;
    const action = { assigneeId: assignedDeveloper.id } as IssueAction;

    expect(canChangeIssueStatus(admin, issue)).toBe(true);
    expect(canChangeIssueStatus(assignedDeveloper, issue)).toBe(true);
    expect(canChangeIssueStatus(otherDeveloper, issue)).toBe(false);
    expect(canChangeActionStatus(assignedDeveloper, action)).toBe(true);
    expect(canChangeActionStatus(otherDeveloper, action)).toBe(false);
  });
});

function user(id: number, role: UserProfile['role']): UserProfile {
  return {
    id,
    organizationId: 11,
    organizationName: 'VOC ActionOps Demo',
    email: `user${id}@example.com`,
    name: `사용자 ${id}`,
    role,
  };
}
