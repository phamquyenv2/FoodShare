import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ShoppingBag, UtensilsCrossed, TrendingUp, Clock, Check, X,
  ChevronRight, Leaf, Loader2,
} from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { apiFetch } from '../../services/api';
import { OrderBadge } from '../../components/shared/StatusBadge';
import { formatVND, timeAgo } from '../../utils/format';

const stagger = { hidden: {}, show: { transition: { staggerChildren: 0.06 } } };
const fadeUp = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.25 } } };

interface DashOrder {
  id: number;
  orderCode: string;
  status: string;
  totalAmount: number;
  createdAt: string;
  recipientName: string;
  foodPostName: string;
  quantity: number;
}

export default function SupplierDashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [orders, setOrders] = useState<DashOrder[]>([]);
  const [postCount, setPostCount] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<number | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [ordersRes, postsRes] = await Promise.all([
          apiFetch<any>('/orders/supplier?page=0&size=20'),
          apiFetch<any>('/food-posts/my?page=0&size=1'),
        ]);
        
        const mappedOrders: DashOrder[] = (ordersRes.content || []).map((o: any) => {
          const detail = o.orderDetails && o.orderDetails.length > 0 ? o.orderDetails[0] : null;
          return {
            id: o.id,
            orderCode: o.orderCode,
            status: o.orderStatus,
            totalAmount: o.totalAmount,
            createdAt: o.createdAt,
            recipientName: o.receiver?.fullName || 'N/A',
            foodPostName: detail?.foodPost?.name || 'Món ăn',
            quantity: detail?.quantity || 0,
          };
        });

        setOrders(mappedOrders);
        setPostCount(postsRes.totalElements || 0);
      } catch (err) {
        console.error('Dashboard fetch failed:', err);
      } finally {
        setIsLoading(false);
      }
    };
    fetchData();
  }, []);

  const handleAction = async (orderId: number, action: string) => {
    setActionLoading(orderId);
    try {
      await apiFetch(`/orders/${orderId}/${action}`, { method: 'PATCH' });
      setOrders(prev => prev.map(o => o.id === orderId ? { ...o, status: action === 'accept' ? 'ACCEPTED' : 'REJECTED' } : o));
    } catch (err: any) {
      alert(err.message || 'Thao tác thất bại');
    } finally {
      setActionLoading(null);
    }
  };

  const pendingOrders = orders.filter(o => o.status === 'PENDING');
  const todayOrders = orders.length;
  const totalRevenue = orders.filter(o => o.status === 'COMPLETED').reduce((s, o) => s + o.totalAmount, 0);

  const STATS = [
    { icon: ShoppingBag,     label: 'Tổng đơn',      value: `${todayOrders}`,   color: '#2db84c', bg: '#e6f7eb' },
    { icon: UtensilsCrossed, label: 'Bài đăng',       value: `${postCount}`,     color: '#0891b2', bg: '#cffafe' },
    { icon: TrendingUp,      label: 'Doanh thu',      value: formatVND(totalRevenue), color: '#7c3aed', bg: '#ede9fe' },
    { icon: Leaf,            label: 'Đơn chờ',        value: `${pendingOrders.length}`, color: '#f59e0b', bg: '#fef3c7' },
  ];

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 size={24} className="animate-spin text-[#2db84c]" />
      </div>
    );
  }

  return (
    <div className="p-4 md:p-6 flex flex-col gap-5 max-w-6xl mx-auto">
      <div>
        <h1 className="text-xl md:text-2xl font-bold text-gray-900">
          Xin chào, {(user as any)?.fullName || 'bạn'} 👋
        </h1>
        <p className="text-sm text-gray-500 mt-1">Tổng quan hoạt động</p>
      </div>

      {/* Stat Cards */}
      <motion.div className="grid grid-cols-2 md:grid-cols-4 gap-3 md:gap-4" variants={stagger} initial="hidden" animate="show">
        {STATS.map(s => (
          <motion.div key={s.label} variants={fadeUp}
            className="bg-white rounded-2xl border border-gray-100 p-4 md:p-5 hover:shadow-md transition-shadow cursor-default">
            <div className="w-10 h-10 rounded-xl flex items-center justify-center mb-3" style={{ backgroundColor: s.bg }}>
              <s.icon size={20} style={{ color: s.color }} />
            </div>
            <p className="text-xs text-gray-500 mb-1">{s.label}</p>
            <p className="text-xl md:text-2xl font-bold text-gray-900">{s.value}</p>
          </motion.div>
        ))}
      </motion.div>

      {/* Pending Orders */}
      {pendingOrders.length > 0 && (
        <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
          className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
          <div className="flex items-center justify-between p-4 md:px-6 border-b border-gray-100">
            <div className="flex items-center gap-2">
              <Clock size={18} className="text-amber-500" />
              <h2 className="font-semibold text-gray-900 text-sm md:text-base">Đơn chờ xác nhận</h2>
              <span className="ml-1 px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 text-xs font-semibold">{pendingOrders.length}</span>
            </div>
          </div>
          <div className="divide-y divide-gray-50">
            {pendingOrders.slice(0, 5).map(order => (
              <div key={order.id} className="flex items-center gap-3 p-4 md:px-6 hover:bg-amber-50/30 transition-colors">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900">{order.foodPostName}</p>
                  <p className="text-xs text-gray-400 mt-0.5">
                    {order.recipientName} · x{order.quantity} · {order.totalAmount > 0 ? formatVND(order.totalAmount) : 'Miễn phí'}
                  </p>
                </div>
                <div className="flex gap-2 flex-shrink-0">
                  <button
                    onClick={() => handleAction(order.id, 'accept')}
                    disabled={actionLoading === order.id}
                    className="w-9 h-9 rounded-xl bg-green-50 text-green-600 flex items-center justify-center cursor-pointer hover:bg-green-100 transition-colors disabled:opacity-50"
                    title="Chấp nhận"
                  >
                    {actionLoading === order.id ? <Loader2 size={16} className="animate-spin" /> : <Check size={18} />}
                  </button>
                  <button
                    onClick={() => handleAction(order.id, 'reject')}
                    disabled={actionLoading === order.id}
                    className="w-9 h-9 rounded-xl bg-red-50 text-red-500 flex items-center justify-center cursor-pointer hover:bg-red-100 transition-colors disabled:opacity-50"
                    title="Từ chối"
                  >
                    <X size={18} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </motion.div>
      )}

      {/* Recent Orders */}
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}
        className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
        <div className="flex items-center justify-between p-4 md:px-6 border-b border-gray-100">
          <h2 className="font-semibold text-gray-900 text-sm md:text-base">Đơn hàng gần đây</h2>
          <button
            onClick={() => navigate('/supplier/orders')}
            className="text-sm text-[#2db84c] font-medium cursor-pointer hover:underline flex items-center gap-1"
          >
            Xem tất cả <ChevronRight size={14} />
          </button>
        </div>
        <div className="divide-y divide-gray-50">
          {orders.slice(0, 5).map(order => (
            <div key={order.id} className="flex items-center gap-4 p-4 md:px-6 hover:bg-gray-50/50 transition-colors">
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-900 truncate">{order.foodPostName}</p>
                <p className="text-xs text-gray-400 mt-0.5">
                  {order.orderCode} · {order.recipientName} · x{order.quantity}
                </p>
              </div>
              <div className="text-right flex-shrink-0">
                <OrderBadge status={order.status as any} />
                <p className="text-xs text-gray-400 mt-1">{timeAgo(order.createdAt)}</p>
              </div>
            </div>
          ))}
          {orders.length === 0 && (
            <div className="p-8 text-center text-sm text-gray-400">Chưa có đơn hàng nào</div>
          )}
        </div>
      </motion.div>
    </div>
  );
}
