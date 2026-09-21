import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { getRoleHome } from '../../utils/roleHome';
import type { UserRole } from '../../types';

interface AuthGuardProps {
  children: ReactNode;
  allowedRoles?: UserRole[];
  requireProfileCompleted?: boolean;
}

export default function AuthGuard({ children, allowedRoles, requireProfileCompleted = true }: AuthGuardProps) {
  const { isAuthenticated, user } = useAuth();
  const location = useLocation();

  if (!isAuthenticated || !user) {
    return <Navigate to="/auth/login" state={{ from: location }} replace />;
  }

  if (allowedRoles && !allowedRoles.includes(user.role as UserRole)) {
    const fallback = getRoleHome(user.role);
    return <Navigate to={fallback} replace />;
  }

  const businessProfileMissingDocuments =
    (user.role === 'SUPPLIER' || user.role === 'ORGANIZATION')
    && user.profileCompleted === true
    && (user.profile?.licenseUrls?.length ?? 0) === 0;

  if (requireProfileCompleted
      && (user.profileCompleted === false || businessProfileMissingDocuments)
      && user.role !== 'ADMIN'
      && location.pathname !== '/auth/complete-profile') {
    return <Navigate to="/auth/complete-profile" replace />;
  }

  if (requireProfileCompleted
      && (user.role === 'SUPPLIER' || user.role === 'ORGANIZATION')
      && user.profile?.verificationStatus !== 'VERIFIED'
      && location.pathname !== '/auth/pending') {
    return <Navigate to="/auth/pending" replace />;
  }

  return children;
}
