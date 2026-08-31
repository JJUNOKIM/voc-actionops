import type { Role } from '../types/api';

const roleLabels: Record<Role, string> = {
  ADMIN: '관리자',
  PM: '프로덕트 매니저',
  CS: '고객 지원',
  DEVELOPER: '개발자',
  VIEWER: '뷰어',
};

export function roleLabel(role: Role): string {
  return roleLabels[role];
}
