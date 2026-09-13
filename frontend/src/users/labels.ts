import type { Role } from '../types/api';

const roleLabels: Record<Role, string> = {
  ADMIN: '관리자',
  PM: '프로덕트 매니저',
  CS: '고객 지원',
  DEVELOPER: '개발자',
  VIEWER: '뷰어',
};

export const roleOptions: Array<{ value: Role; label: string }> = [
  { value: 'ADMIN', label: roleLabels.ADMIN },
  { value: 'PM', label: roleLabels.PM },
  { value: 'CS', label: roleLabels.CS },
  { value: 'DEVELOPER', label: roleLabels.DEVELOPER },
  { value: 'VIEWER', label: roleLabels.VIEWER },
];

export function roleLabel(role: Role): string {
  return roleLabels[role];
}
