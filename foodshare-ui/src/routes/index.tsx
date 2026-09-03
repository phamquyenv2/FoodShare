import { createBrowserRouter, Navigate } from 'react-router-dom';
import MainLayout from '../layouts/MainLayout';
import AuthLayout from '../layouts/AuthLayout';
import AuthGuard from '../components/shared/AuthGuard';

// Auth pages
import LoginPage from '../pages/auth/LoginPage';
import RegisterPage from '../pages/auth/RegisterPage';
import CompleteProfilePage from '../pages/auth/CompleteProfilePage';
import PendingVerificationPage from '../pages/auth/PendingVerificationPage';

// Supplier pages
import SupplierDashboard from '../pages/supplier/DashboardPage';
import SupplierPostsPage from '../pages/supplier/PostsPage';
import CreatePostPage from '../pages/supplier/CreatePostPage';
import EditPostPage from '../pages/supplier/EditPostPage';
import OrdersPage from '../pages/supplier/OrdersPage';
import WalletPage from '../pages/supplier/WalletPage';
import ProfilePage from '../pages/supplier/ProfilePage';
import ReviewsPage from '../pages/supplier/ReviewsPage';
import NotificationsPage from '../pages/supplier/NotificationsPage';

// Admin pages
import AnalyticsPage from '../pages/admin/AnalyticsPage';
import UsersPage from '../pages/admin/UsersPage';
import ModerationPage from '../pages/admin/ModerationPage';
import AdminReportsPage from '../pages/admin/AdminReportsPage';
import SettingsPage from '../pages/admin/SettingsPage';
import AdminNotificationsPage from '../pages/admin/AdminNotificationsPage';

// Recipient pages
import ExplorePage from '../pages/recipient/ExplorePage';
import FoodPostDetailPage from '../pages/recipient/FoodPostDetailPage';
import MyOrdersPage from '../pages/recipient/MyOrdersPage';
import OrderDetailPage from '../pages/recipient/OrderDetailPage';
import WriteReviewPage from '../pages/recipient/WriteReviewPage';
import ReportPage from '../pages/recipient/ReportPage';
import MyReportsPage from '../pages/recipient/MyReportsPage';
import RecipientNotificationsPage from '../pages/recipient/NotificationsPage';

// Organization pages
import OrgExplorePage from '../pages/organization/OrgExplorePage';
import OrgCartPage from '../pages/organization/OrgCartPage';
import OrgOrdersPage from '../pages/organization/OrgOrdersPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/recipient" replace />,
  },
  {
    path: '/auth',
    element: <AuthLayout />,
    children: [
      { path: 'login',    element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
      { path: 'complete-profile', element: <AuthGuard requireProfileCompleted={false}><CompleteProfilePage /></AuthGuard> },
      { path: 'pending', element: <AuthGuard requireProfileCompleted={false}><PendingVerificationPage /></AuthGuard> },
    ],
  },
  {
    path: '/',
    element: <MainLayout />,
    children: [
      // Shared
      { path: ':role/report/:id',        element: <AuthGuard><ReportPage /></AuthGuard> },
      // Supplier
      { path: 'supplier',                element: <AuthGuard allowedRoles={['SUPPLIER']}><SupplierDashboard /></AuthGuard> },
      { path: 'supplier/posts',          element: <AuthGuard allowedRoles={['SUPPLIER']}><SupplierPostsPage /></AuthGuard> },
      { path: 'supplier/posts/create',   element: <AuthGuard allowedRoles={['SUPPLIER']}><CreatePostPage /></AuthGuard> },
      { path: 'supplier/posts/:id/edit', element: <AuthGuard allowedRoles={['SUPPLIER']}><EditPostPage /></AuthGuard> },
      { path: 'supplier/orders',         element: <AuthGuard allowedRoles={['SUPPLIER']}><OrdersPage /></AuthGuard> },
      { path: 'supplier/wallet',         element: <AuthGuard allowedRoles={['SUPPLIER']}><WalletPage /></AuthGuard> },
      { path: 'supplier/profile',        element: <AuthGuard allowedRoles={['SUPPLIER']}><ProfilePage /></AuthGuard> },
      { path: 'supplier/reviews',        element: <AuthGuard allowedRoles={['SUPPLIER']}><ReviewsPage /></AuthGuard> },
      { path: 'supplier/reports',        element: <AuthGuard allowedRoles={['SUPPLIER']}><MyReportsPage /></AuthGuard> },
      { path: 'supplier/notifications',  element: <AuthGuard allowedRoles={['SUPPLIER']}><NotificationsPage /></AuthGuard> },
      // Admin
      { path: 'admin',            element: <AuthGuard allowedRoles={['ADMIN']}><AnalyticsPage /></AuthGuard> },
      { path: 'admin/users',      element: <AuthGuard allowedRoles={['ADMIN']}><UsersPage /></AuthGuard> },
      { path: 'admin/moderation', element: <AuthGuard allowedRoles={['ADMIN']}><ModerationPage /></AuthGuard> },
      { path: 'admin/reports',    element: <AuthGuard allowedRoles={['ADMIN']}><AdminReportsPage /></AuthGuard> },
      { path: 'admin/notifications', element: <AuthGuard allowedRoles={['ADMIN']}><AdminNotificationsPage /></AuthGuard> },
      { path: 'admin/settings',   element: <AuthGuard allowedRoles={['ADMIN']}><SettingsPage /></AuthGuard> },
      { path: 'admin/profile',    element: <AuthGuard allowedRoles={['ADMIN']}><ProfilePage /></AuthGuard> },
      // Recipient
      { path: 'recipient',                         element: <AuthGuard allowedRoles={['RECIPIENT', 'ORGANIZATION']}><ExplorePage /></AuthGuard> },
      { path: 'recipient/posts/:id',               element: <AuthGuard allowedRoles={['RECIPIENT', 'ORGANIZATION']}><FoodPostDetailPage /></AuthGuard> },
      { path: 'recipient/orders',                  element: <AuthGuard allowedRoles={['RECIPIENT', 'ORGANIZATION']}><MyOrdersPage /></AuthGuard> },
      { path: 'recipient/orders/:id',              element: <AuthGuard allowedRoles={['RECIPIENT', 'ORGANIZATION']}><OrderDetailPage /></AuthGuard> },
      { path: 'recipient/orders/:id/review',       element: <AuthGuard allowedRoles={['RECIPIENT', 'ORGANIZATION']}><WriteReviewPage /></AuthGuard> },
      { path: 'recipient/orders/:id/report',       element: <AuthGuard allowedRoles={['RECIPIENT', 'ORGANIZATION']}><ReportPage /></AuthGuard> },
      { path: 'recipient/reports',                 element: <AuthGuard allowedRoles={['RECIPIENT', 'ORGANIZATION']}><MyReportsPage /></AuthGuard> },
      { path: 'recipient/notifications',           element: <AuthGuard allowedRoles={['RECIPIENT', 'ORGANIZATION']}><RecipientNotificationsPage /></AuthGuard> },
      { path: 'recipient/profile',                 element: <AuthGuard allowedRoles={['RECIPIENT', 'ORGANIZATION']}><ProfilePage /></AuthGuard> },
      // Organization
      { path: 'organization',                      element: <AuthGuard allowedRoles={['ORGANIZATION']}><OrgExplorePage /></AuthGuard> },
      { path: 'organization/cart',                 element: <AuthGuard allowedRoles={['ORGANIZATION']}><OrgCartPage /></AuthGuard> },
      { path: 'organization/posts/:id',            element: <AuthGuard allowedRoles={['ORGANIZATION']}><FoodPostDetailPage /></AuthGuard> },
      { path: 'organization/orders',               element: <AuthGuard allowedRoles={['ORGANIZATION']}><OrgOrdersPage /></AuthGuard> },
      { path: 'organization/orders/:id',           element: <AuthGuard allowedRoles={['ORGANIZATION']}><OrderDetailPage /></AuthGuard> },
      { path: 'organization/orders/:id/review',    element: <AuthGuard allowedRoles={['ORGANIZATION']}><WriteReviewPage /></AuthGuard> },
      { path: 'organization/orders/:id/report',    element: <AuthGuard allowedRoles={['ORGANIZATION']}><ReportPage /></AuthGuard> },
      { path: 'organization/reports',              element: <AuthGuard allowedRoles={['ORGANIZATION']}><MyReportsPage /></AuthGuard> },
      { path: 'organization/notifications',        element: <AuthGuard allowedRoles={['ORGANIZATION']}><RecipientNotificationsPage /></AuthGuard> },
    ],
  },
]);
