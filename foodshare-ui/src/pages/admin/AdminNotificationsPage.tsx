import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Bell, BellOff, ShieldAlert, Flag, Check, Loader2, UserPlus } from 'lucide-react';
import { apiFetch } from '../../services/api';
import { timeAgo } from '../../utils/format';

interface NotificationItem {
  id: number;
  notificationType: string;
  title: string;
  content: string;
  isRead: boolean;
  createdAt: string;
  referenceId?: number;
  referenceType?: string;
}

const ICON_MAP: Record<string, { icon: typeof Bell; color: string; bg: string }> = {
  MODERATION:   { icon: ShieldAlert, color: '#f59e0b', bg: '#fef3c7' },
  REPORT:       { icon: Flag,        color: '#dc2626', bg: '#fee2e2' },
  SYSTEM:       { icon: Bell,        color: '#7c3aed', bg: '#ede9fe' },
  NEW_SUPPLIER: { icon: UserPlus,    color: '#2db84c', bg: '#e6f7eb' },
};

const TABS = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'NEW_SUPPLIER', label: 'Nhà cung cấp mới' },
  { key: 'REPORT', label: 'Báo cáo/Khiếu nại' },
  { key: 'SYSTEM', label: 'Hệ thống' },
];

export default function AdminNotificationsPage() {
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [activeTab, setActiveTab] = useState('ALL');
  const [isLoading, setIsLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [isFetchingNextPage, setIsFetchingNextPage] = useState(false);
  const observerTarget = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  const fetchNotifications = useCallback(async (pageNum: number, isInitial = false) => {
    if (isInitial) setIsLoading(true);
    else setIsFetchingNextPage(true);
    
    try {
      const res = await apiFetch<any>(`/notifications?page=${pageNum}`);
      const newItems = res.content || [];
      
      if (isInitial) {
        setNotifications(newItems);
      } else {
        setNotifications(prev => {
          // Prevent duplicates
          const existingIds = new Set(prev.map(n => n.id));
          return [...prev, ...newItems.filter((n: any) => !existingIds.has(n.id))];
        });
      }
      
      setHasMore(!res.last && newItems.length > 0);
    } catch (err) {
      console.error('Failed to fetch notifications:', err);
      if (isInitial) setNotifications([]);
    } finally {
      setIsLoading(false);
      setIsFetchingNextPage(false);
    }
  }, []);

  useEffect(() => { 
    setPage(0);
    fetchNotifications(0, true); 
  }, [fetchNotifications]);

  useEffect(() => {
    const observer = new IntersectionObserver(
      entries => {
        if (entries[0].isIntersecting && hasMore && !isLoading && !isFetchingNextPage) {
          setPage(p => {
            const nextPage = p + 1;
            fetchNotifications(nextPage, false);
            return nextPage;
          });
        }
      },
      { threshold: 0.1 }
    );

    if (observerTarget.current) {
      observer.observe(observerTarget.current);
    }

    return () => observer.disconnect();
  }, [hasMore, isLoading, isFetchingNextPage, fetchNotifications]);

  const markAsRead = async (id: number) => {
    setNotifications(prev => prev.map(n => n.id === id ? { ...n, isRead: true } : n));
    try {
      await apiFetch(`/notifications/${id}/read`, { method: 'PATCH' });
    } catch (err) {}
  };

  const markAllRead = async () => {
    setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
    try {
      await apiFetch('/notifications/read-all', { method: 'PATCH' });
    } catch (err) {}
  };

  const handleNotificationClick = (notif: NotificationItem) => {
    if (!notif.isRead) {
      markAsRead(notif.id);
    }
    
    // Redirect based on reference type tailored for Admin
    if (notif.referenceType === 'USER' || notif.notificationType === 'NEW_SUPPLIER') {
      navigate(`/admin/moderation`);
    } else if (notif.referenceType === 'REPORT') {
      navigate('/admin/reports');
    }
  };

  const unreadCount = notifications.filter(n => !n.isRead).length;
  
  const filteredNotifications = notifications.filter(n => {
    if (activeTab === 'ALL') return true;
    return n.notificationType === activeTab;
  });

  return (
    <div className="p-4 md:p-6 max-w-3xl mx-auto flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-gray-900">Thông báo quản trị viên</h1>
          {unreadCount > 0 && (
            <p className="text-sm text-gray-500 mt-0.5">{unreadCount} chưa đọc</p>
          )}
        </div>
        {unreadCount > 0 && (
          <button onClick={markAllRead} className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium text-gray-600 bg-gray-100 hover:bg-gray-200 transition-colors">
            <Check size={16} /> Đánh dấu đã đọc
          </button>
        )}
      </div>

      <div className="flex items-center gap-2 overflow-x-auto pb-2 scrollbar-hide">
        {TABS.map(t => (
          <button
            key={t.key}
            onClick={() => setActiveTab(t.key)}
            className={`whitespace-nowrap px-4 py-1.5 rounded-full text-sm font-medium transition-colors border
              ${activeTab === t.key 
                ? 'bg-[#2db84c] text-white border-[#2db84c]' 
                : 'bg-white text-gray-600 border-gray-200 hover:bg-gray-50'
              }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="flex flex-col gap-3">
        {isLoading ? (
          <div className="py-20 flex justify-center"><Loader2 size={32} className="animate-spin text-gray-300" /></div>
        ) : filteredNotifications.length === 0 ? (
          <div className="py-20 flex flex-col items-center justify-center text-center">
            <BellOff size={48} className="text-gray-200 mb-3" />
            <p className="text-gray-500">Không có thông báo nào</p>
          </div>
        ) : (
          <>
            {filteredNotifications.map((n, i) => {
              const iconConfig = ICON_MAP[n.notificationType] || ICON_MAP.SYSTEM;
              const Icon = iconConfig.icon;
              return (
                <motion.div
                  initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.05 }}
                  key={n.id} onClick={() => handleNotificationClick(n)}
                  className={`flex gap-4 p-4 rounded-2xl border cursor-pointer transition-all duration-200
                    ${n.isRead ? 'bg-white border-gray-100 opacity-70 hover:opacity-100' : 'bg-green-50/30 border-green-100 shadow-sm'}`}
                >
                  <div className="w-12 h-12 rounded-full flex-shrink-0 flex items-center justify-center" style={{ backgroundColor: iconConfig.bg, color: iconConfig.color }}>
                    <Icon size={24} />
                  </div>
                  <div className="flex-1">
                    <div className="flex justify-between items-start gap-2 mb-1">
                      <h3 className={`font-semibold text-sm md:text-base line-clamp-1 ${n.isRead ? 'text-gray-700' : 'text-gray-900'}`}>{n.title}</h3>
                      <span className="text-xs text-gray-400 whitespace-nowrap">{timeAgo(n.createdAt)}</span>
                    </div>
                    <p className={`text-sm line-clamp-2 ${n.isRead ? 'text-gray-500' : 'text-gray-700'}`}>{n.content}</p>
                  </div>
                  {!n.isRead && <div className="w-2.5 h-2.5 rounded-full bg-[#2db84c] flex-shrink-0 mt-2" />}
                </motion.div>
              );
            })}
            
            {hasMore && (
              <div ref={observerTarget} className="py-4 flex justify-center">
                {isFetchingNextPage && <Loader2 size={24} className="animate-spin text-gray-300" />}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
