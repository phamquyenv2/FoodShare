import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Bell, BellOff, ShoppingBag, MessageSquare, AlertTriangle, Check, Loader2 } from 'lucide-react';
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
  ORDER:        { icon: ShoppingBag,     color: '#2db84c', bg: '#e6f7eb' },
  REVIEW:       { icon: MessageSquare,   color: '#0891b2', bg: '#cffafe' },
  REPORT:       { icon: AlertTriangle,   color: '#f59e0b', bg: '#fef3c7' },
  SYSTEM:       { icon: Bell,            color: '#7c3aed', bg: '#ede9fe' },
};

const TABS = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'ORDER', label: 'Đơn hàng' },
  { key: 'PAYMENT', label: 'Thanh toán' },
  { key: 'REPORT', label: 'Đánh giá' },
  { key: 'SYSTEM', label: 'Hệ thống' },
];

export default function NotificationsPage() {
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
          // Prevent duplicates on double-fetch
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
    // Optimistic UI update
    setNotifications(prev => prev.map(n => n.id === id ? { ...n, isRead: true } : n));
    try {
      await apiFetch(`/notifications/${id}/read`, { method: 'PATCH' });
    } catch (err) {
      // silently fail
    }
  };

  const markAllRead = async () => {
    // Optimistic UI update
    setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
    try {
      await apiFetch('/notifications/read-all', { method: 'PATCH' });
    } catch (err) {
      // silently fail
    }
  };

  const handleNotificationClick = (notif: NotificationItem) => {
    if (!notif.isRead) {
      markAsRead(notif.id);
    }
    
    // Redirect based on reference type
    if (notif.referenceType === 'ORDER') {
      navigate(`/supplier/orders?id=${notif.referenceId}`);
    } else if (notif.referenceType === 'FOOD_POST') {
      navigate(`/supplier/posts?id=${notif.referenceId}`);
    } else if (notif.referenceType === 'PAYMENT' || notif.referenceType === 'PAYOUT') {
      navigate('/supplier/wallet');
    } else if (notif.referenceType === 'REPORT') {
      navigate('/supplier/reviews');
    }
  };

  const unreadCount = notifications.filter(n => !n.isRead).length;
  
  const filteredNotifications = notifications.filter(n => {
    if (activeTab === 'ALL') return true;
    if (activeTab === 'REPORT') return n.notificationType === 'REPORT' || n.notificationType === 'REVIEW';
    return n.notificationType === activeTab;
  });

  return (
    <div className="p-4 md:p-6 max-w-3xl mx-auto flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-gray-900">Thông báo</h1>
          {unreadCount > 0 && (
            <p className="text-sm text-gray-500 mt-0.5">{unreadCount} chưa đọc</p>
          )}
        </div>
        {unreadCount > 0 && (
          <button
            onClick={markAllRead}
            className="flex items-center gap-1.5 text-sm text-[#2db84c] font-medium cursor-pointer hover:underline"
          >
            <Check size={14} /> Đọc tất cả
          </button>
        )}
      </div>

      <div className="flex gap-2 overflow-x-auto pb-2 [&::-webkit-scrollbar]:hidden">
        {TABS.map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`px-4 py-2 rounded-xl text-sm font-medium whitespace-nowrap cursor-pointer transition-all ${
              activeTab === tab.key
                ? 'bg-[#2db84c] text-white shadow-md shadow-green-500/20'
                : 'bg-white border border-gray-100 text-gray-600 hover:bg-gray-50'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-16">
          <Loader2 size={24} className="animate-spin text-[#2db84c]" />
        </div>
      ) : filteredNotifications.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <BellOff size={48} className="mx-auto mb-3 opacity-50" />
          <p className="text-sm">Không có thông báo nào</p>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {filteredNotifications.map(notif => {
            const iconInfo = ICON_MAP[notif.notificationType] || ICON_MAP.SYSTEM;
            const Icon = iconInfo.icon;
            return (
              <motion.div
                key={notif.id}
                initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
                onClick={() => handleNotificationClick(notif)}
                className={`flex items-start gap-3 p-4 rounded-2xl border transition-all cursor-pointer ${
                  notif.isRead
                    ? 'bg-white border-gray-100 hover:bg-gray-50/50'
                    : 'bg-[#2db84c]/[0.03] border-[#2db84c]/20 hover:bg-[#2db84c]/[0.06]'
                }`}
              >
                <div
                  className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
                  style={{ backgroundColor: iconInfo.bg }}
                >
                  <Icon size={18} style={{ color: iconInfo.color }} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-0.5">
                    <p className={`text-sm font-semibold ${notif.isRead ? 'text-gray-700' : 'text-gray-900'}`}>
                      {notif.title}
                    </p>
                    {!notif.isRead && <div className="w-2 h-2 rounded-full bg-[#2db84c] flex-shrink-0" />}
                  </div>
                  <p className="text-sm text-gray-500 line-clamp-2">{notif.content}</p>
                  <p className="text-xs text-gray-400 mt-1">{timeAgo(notif.createdAt)}</p>
                </div>
              </motion.div>
            );
          })}
          
          {hasMore && (
            <div ref={observerTarget} className="flex justify-center py-4">
              {isFetchingNextPage && <Loader2 size={20} className="animate-spin text-[#2db84c]" />}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
