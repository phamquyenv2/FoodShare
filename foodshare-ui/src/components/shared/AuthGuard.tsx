import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
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
    // Redirect to correct dashboard or a 403 page
    const fallback = user.role === 'ADMIN' ? '/admin' : '/supplier';
    return <Navigate to={fallback} replace />;
  }

  // Check profile completion (skip if on the complete-profile page itself, and skip for ADMIN)
  if (requireProfileCompleted && user.profileCompleted === false && user.role !== 'ADMIN' && location.pathname !== '/auth/complete-profile') {
    return <Navigate to="/auth/complete-profile" replace />;
  }

  // Check verification status for SUPPLIER
  if (requireProfileCompleted && user.role === 'SUPPLIER' && user.profile?.verificationStatus !== 'VERIFIED' && location.pathname !== '/auth/pending') {
    return <Navigate to="/auth/pending" replace />;
  }

  return children;
}
