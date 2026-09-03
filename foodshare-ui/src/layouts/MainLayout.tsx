import { useState, useEffect } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { apiFetch } from '../services/api';
import { motion, AnimatePresence } from 'framer-motion';
import {
  LayoutDashboard, UtensilsCrossed, ShoppingBag, Wallet,
  Users, ShieldAlert, BarChart3, Bell, LogOut, Menu, X,
  Settings, User, Star, BellRing, Compass, Flag,
} from 'lucide-react';
import Logo from '../components/shared/Logo';
import { useAuth } from '../contexts/AuthContext';
import { useIsMobile } from '../hooks/useMediaQuery';
import type { UserRole } from '../types';

/* ─── Nav config per role ────────────────────────────────────────────────── */
const SUPPLIER_NAV = [
  { key: 'dashboard',      icon: LayoutDashboard,  label: 'Tổng quan',      path: '/supplier' },
  { key: 'posts',          icon: UtensilsCrossed,  label: 'Bài đăng',       path: '/supplier/posts' },
  { key: 'orders',         icon: ShoppingBag,      label: 'Đơn tiếp nhận',       path: '/supplier/orders' },
  { key: 'wallet',         icon: Wallet,           label: 'Ví tiền',        path: '/supplier/wallet' },
  { key: 'reviews',        icon: Star,             label: 'Đánh giá',       path: '/supplier/reviews' },
];

const ADMIN_NAV = [
  { key: 'analytics',   icon: BarChart3,     label: 'Tổng quan',    path: '/admin' },
  { key: 'users',       icon: Users,         label: 'Người dùng',   path: '/admin/users' },
  { key: 'moderation',  icon: ShieldAlert,   label: 'Kiểm duyệt',  path: '/admin/moderation' },
  { key: 'reports',     icon: Flag,          label: 'Khiếu nại',    path: '/admin/reports' },
  { key: 'settings',    icon: Settings,      label: 'Cài đặt',      path: '/admin/settings' },
];

const RECIPIENT_NAV = [
  { key: 'explore',       icon: Compass,          label: 'Khám phá',      path: '/recipient' },
  { key: 'orders',        icon: ShoppingBag,      label: 'Đơn tiếp nhận',      path: '/recipient/orders' },
];

const ORGANIZATION_NAV = [
  { key: 'explore',       icon: Compass,          label: 'Khám phá',      path: '/organization' },
  { key: 'orders',        icon: ShoppingBag,      label: 'Đơn tiếp nhận',      path: '/organization/orders' },
];

function getNavForRole(role: UserRole) {
  if (role === 'ADMIN') return ADMIN_NAV;
  if (role === 'SUPPLIER') return SUPPLIER_NAV;
  if (role === 'ORGANIZATION') return ORGANIZATION_NAV;
  return RECIPIENT_NAV;
}

/* ─── Desktop Sidebar ────────────────────────────────────────────────────── */
function Sidebar() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const role = user?.role || 'RECIPIENT';
  const nav = getNavForRole(role);

  return (
    <aside className="hidden md:flex w-[260px] flex-shrink-0 h-screen sticky top-0 flex-col bg-white border-r border-gray-200">
      {/* Logo */}
      <div className="p-5 border-b border-gray-100">
        <Logo size="md" />
      </div>

      {/* Role Display */}
      <div className="px-4 pt-4 pb-2 relative">
        <div
          className="w-full flex items-center justify-between px-3 py-2 rounded-lg bg-green-50 text-sm text-green-700 font-medium transition-colors"
        >
          <span>{role === 'ADMIN' ? 'Admin' : role === 'SUPPLIER' ? 'Nhà cung cấp' : role === 'ORGANIZATION' ? 'Tổ chức' : 'Người nhận'}</span>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-2 flex flex-col gap-0.5 overflow-y-auto">
        {nav.map(item => {
          const active = location.pathname === item.path;
          return (
            <button
              key={item.key}
              onClick={() => navigate(item.path)}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium cursor-pointer transition-all duration-200
                ${active
                  ? 'bg-[#2db84c] text-white shadow-md shadow-green-500/20'
                  : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                }`}
            >
              <item.icon size={18} />
              {item.label}
            </button>
          );
        })}
      </nav>

      {/* Logout */}
      <div className="p-4 border-t border-gray-100">
        <button
          onClick={logout}
          className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-gray-400 hover:text-red-500 hover:bg-red-50 text-sm cursor-pointer transition-colors"
        >
          <LogOut size={16} /> Đăng xuất
        </button>
      </div>
    </aside>
  );
}

/* ─── Mobile Bottom Navbar ───────────────────────────────────────────────── */
function BottomNav() {
  const { user } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const role = user?.role || 'RECIPIENT';
  const nav = getNavForRole(role);

  return (
    <nav className="md:hidden fixed bottom-0 left-0 right-0 bg-white border-t border-gray-200 z-50 px-2 pb-[env(safe-area-inset-bottom)]">
      <div className="flex justify-around items-center h-16">
        {nav.map(item => {
          const active = location.pathname === item.path;
          return (
            <button
              key={item.key}
              onClick={() => navigate(item.path)}
              className={`flex flex-col items-center gap-0.5 px-3 py-1.5 rounded-xl cursor-pointer transition-all duration-200
                ${active ? 'text-[#2db84c]' : 'text-gray-400'}`}
            >
              <item.icon size={20} strokeWidth={active ? 2.5 : 1.8} />
              <span className={`text-[10px] font-medium ${active ? 'text-[#2db84c]' : 'text-gray-400'}`}>
                {item.label}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
}

/* ─── Mobile Header ──────────────────────────────────────────────────────── */
function MobileHeader() {
  const { user, logout } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const navigate = useNavigate();
  const role = user?.role || 'RECIPIENT';

  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    if (!user) return;
    apiFetch<number>('/notifications/unread-count')
      .then(res => {
        setUnreadCount(res || 0);
      })
      .catch(() => {});
  }, [user]);

  return (
    <>
      <header className="md:hidden sticky top-0 z-40 bg-white/95 backdrop-blur-md border-b border-gray-100 px-4 h-14 flex items-center justify-between">
        <Logo size="sm" />
        <div className="flex items-center gap-2">
          {role !== 'ADMIN' && (
            <button
              onClick={() => navigate(`/${role.toLowerCase()}/report/0?type=SYSTEM`)}
              className="w-9 h-9 rounded-full bg-gray-50 flex items-center justify-center cursor-pointer relative"
              aria-label="Báo cáo lỗi/Góp ý"
            >
              <Flag size={16} className="text-gray-500" />
            </button>
          )}
          <button
            onClick={() => navigate(`/${role.toLowerCase()}/notifications`)}
            className="w-9 h-9 rounded-full bg-gray-50 flex items-center justify-center cursor-pointer relative"
            aria-label="Thông báo"
          >
            <Bell size={18} className={`text-gray-500 ${unreadCount > 0 ? 'text-[#2db84c]' : ''}`} />
            {unreadCount > 0 && (
              <span className="absolute -top-1 -right-1 min-w-[16px] h-4 px-1 bg-red-500 rounded-full text-[10px] font-bold text-white flex items-center justify-center border border-white">
                {unreadCount > 99 ? '99+' : unreadCount}
              </span>
            )}
          </button>
          <button
            onClick={() => setMenuOpen(!menuOpen)}
            className="w-9 h-9 rounded-full bg-gray-50 flex items-center justify-center cursor-pointer"
            aria-label="Tài khoản"
          >
            {menuOpen ? <X size={18} className="text-gray-500" /> : <User size={18} className="text-gray-500" />}
          </button>
        </div>
      </header>
      <AnimatePresence>
        {menuOpen && (
          <motion.div
            initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }}
            className="md:hidden fixed top-14 left-0 right-0 bg-white border-b border-gray-200 shadow-lg z-30 p-4"
          >
            <div className="flex items-center gap-3 mb-4 pb-4 border-b border-gray-100">
              <div className="w-10 h-10 rounded-full bg-gradient-to-br from-green-400 to-green-600 flex items-center justify-center text-white font-bold">
                {user?.fullName?.charAt(0) || 'U'}
              </div>
              <div>
                <p className="font-semibold text-gray-900">{user?.fullName}</p>
                <p className="text-xs text-gray-400">{role === 'ADMIN' ? 'Quản trị viên' : role === 'ORGANIZATION' ? 'Tổ chức' : role === 'SUPPLIER' ? 'Nhà cung cấp' : 'Người nhận'}</p>
              </div>
            </div>
            <div className="flex flex-col gap-2">
              <button
                onClick={() => { setMenuOpen(false); navigate(`/${role.toLowerCase()}/profile`); }}
                className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
              >
                <User size={18} className="text-gray-400" /> Hồ sơ
              </button>
              <button
                onClick={() => { setMenuOpen(false); logout(); }}
                className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-red-600 hover:bg-red-50 transition-colors"
              >
                <LogOut size={18} className="text-red-500" /> Đăng xuất
              </button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}

/* ─── Desktop Header ─────────────────────────────────────────────────────── */
function DesktopHeader() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const role = user?.role || 'RECIPIENT';
  
  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    if (!user) return;
    apiFetch<number>('/notifications/unread-count')
      .then(res => {
        setUnreadCount(res || 0);
      })
      .catch(() => {});
  }, [user]);
  
  return (
    <header className="hidden md:flex h-16 bg-white border-b border-gray-200 items-center px-6 gap-4 sticky top-0 z-10">
      <div className="flex-1" />
      {role !== 'ADMIN' && (
        <button 
          onClick={() => navigate(`/${role.toLowerCase()}/report/0?type=SYSTEM`)}
          className="relative w-9 h-9 rounded-full bg-gray-50 flex items-center justify-center cursor-pointer hover:bg-gray-100 transition-colors" 
          aria-label="Báo cáo lỗi/Góp ý"
        >
          <Flag size={16} className="text-gray-500" />
        </button>
      )}
      <button 
        onClick={() => navigate(`/${role.toLowerCase()}/notifications`)}
        className="relative w-9 h-9 rounded-full bg-gray-50 flex items-center justify-center cursor-pointer hover:bg-gray-100 transition-colors" 
        aria-label="Thông báo"
      >
        <Bell size={18} className={`text-gray-500 ${unreadCount > 0 ? 'text-[#2db84c]' : ''}`} />
        {unreadCount > 0 && (
          <span className="absolute -top-1 -right-1 min-w-[16px] h-4 px-1 bg-red-500 rounded-full text-[10px] font-bold text-white flex items-center justify-center border border-white">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>
      <div 
        onClick={() => navigate(`/${role.toLowerCase()}/profile`)}
        className="flex items-center gap-2 cursor-pointer hover:opacity-80 transition-opacity"
      >
        <div className="w-8 h-8 rounded-full bg-gradient-to-br from-green-400 to-green-600 flex items-center justify-center text-white text-xs font-bold">
          {user?.fullName?.charAt(0) || 'U'}
        </div>
        <span className="text-sm font-medium text-gray-700">{user?.fullName}</span>
      </div>
    </header>
  );
}

/* ─── Main Layout ────────────────────────────────────────────────────────── */
export default function MainLayout() {
  const isMobile = useIsMobile();

  return (
    <div className="flex min-h-screen bg-[#f5f7f5]">
      <Sidebar />
      <div className="flex-1 flex flex-col min-w-0">
        {isMobile ? <MobileHeader /> : <DesktopHeader />}
        <main className="flex-1 overflow-auto pb-20 md:pb-0">
          <Outlet />
        </main>
        {isMobile && <BottomNav />}
      </div>
    </div>
  );
}
