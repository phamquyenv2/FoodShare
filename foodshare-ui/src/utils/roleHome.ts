import type { User, UserRole } from '../types';

const ROLE_HOME: Record<UserRole, string> = {
  RECIPIENT: '/recipient/explore',
  SUPPLIER: '/supplier/dashboard',
  ORGANIZATION: '/organization/explore',
  ADMIN: '/admin/dashboard',
};

export function getRoleHome(role?: UserRole): string {
  return role ? ROLE_HOME[role] : '/auth/login';
}

export function getAuthenticatedHome(user?: Partial<User> | null): string {
  if (!user || !user.role) return '/auth/login';

  if (user.role === 'ADMIN') {
    return ROLE_HOME.ADMIN;
  }

  const businessProfileMissingDocuments =
    (user.role === 'SUPPLIER' || user.role === 'ORGANIZATION')
    && user.profileCompleted === true
    && (user.profile?.licenseUrls?.length ?? 0) === 0;

  if (user.profileCompleted === false || businessProfileMissingDocuments) {
    return '/auth/complete-profile';
  }

  if ((user.role === 'SUPPLIER' || user.role === 'ORGANIZATION')
      && user.profile?.verificationStatus !== 'VERIFIED') {
    return '/auth/pending';
  }

  return getRoleHome(user.role);
}
