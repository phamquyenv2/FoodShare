import type { UserRole } from '../types';

const ROLE_HOME: Record<UserRole, string> = {
  RECIPIENT: '/recipient/explore',
  SUPPLIER: '/supplier/dashboard',
  ORGANIZATION: '/organization/explore',
  ADMIN: '/admin/dashboard',
};

export function getRoleHome(role?: UserRole): string {
  return role ? ROLE_HOME[role] : '/auth/login';
}
