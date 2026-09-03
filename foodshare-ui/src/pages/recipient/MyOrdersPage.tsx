import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ShoppingBag, ChevronDown, Loader2, Package, Clock,
  ChevronRight, Eye,
} from 'lucide-react';
import { apiFetch } from '../../services/api';
import { formatVND, timeAgo } from '../../utils/format';

interface OrderItem {
  id: number;
  orderCode: string;
  orderStatus: string;
  totalAmount: number;
  createdAt: string;
  supplier: { name: string };
  orderDetails: {
    foodPost: { name: string };
    quantity: number;
    unitPrice: number;
  }[];
  receiverNote?: string;
}

const STATUS_MAP: Record<string, { bg: string; text: string; label: string }> = {
  PENDING:          { bg: 'bg-amber-100',  text: 'text-amber-700',  label: 'Chờ xác nhận' },
  ACCEPTED:         { bg: 'bg-blue-100',   text: 'text-blue-700',   label: 'Đã chấp nhận' },
  READY_FOR_PICKUP: { bg: 'bg-indigo-100', text: 'text-indigo-700', label: 'Sẵn sàng lấy' },
  DELIVERED:        { bg: 'bg-cyan-100',   text: 'text-cyan-700',   label: 'Đã giao' },
  COMPLETED:        { bg: 'bg-green-100',  text: 'text-green-700',  label: 'Hoàn thành' },
  CANCELLED:        { bg: 'bg-gray-100',   text: 'text-gray-600',   label: 'Đã hủy' },
  REJECTED:         { bg: 'bg-red-100',    text: 'text-red-600',    label: 'Từ chối' },
};

const TABS = [
  { key: 'all', label: 'Tất cả' },
  { key: 'PENDING', label: 'Chờ xử lý' },
  { key: 'ACCEPTED', label: 'Đang xử lý' },
  { key: 'DELIVERED', label: 'Đã giao' },
  { key: 'COMPLETED', label: 'Hoàn thành' },
];

export default function MyOrdersPage() {
  const navigate = useNavigate();
  const [orders, setOrders] = useState<OrderItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [tab, setTab] = useState('all');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const fetchOrders = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await apiFetch<any>(`/orders/my?page=${page}`);
      setOrders(res.content || []);
      setTotalPages(res.totalPages || 0);
    } catch (err) {
      console.error('Failed to fetch orders:', err);
    } finally {
      setIsLoading(false);
    }
  }, [page]);

  useEffect(() => { fetchOrders(); }, [fetchOrders]);

  const filtered = tab === 'all' ? orders : orders.filter(o => o.orderStatus === tab);

  return (
    <div className="p-4 md:p-6 max-w-4xl mx-auto flex flex-col gap-5">
      <h1 className="text-xl md:text-2xl font-bold text-gray-900">Đơn hàng của tôi</h1>

      {/* Tabs */}
      <div className="flex gap-2 overflow-x-auto pb-1">
        {TABS.map(t => (
          <button
            key={t.key}
            onClick={() => { setTab(t.key); setPage(0); }}
            className={`px-4 py-2 rounded-xl text-sm font-medium whitespace-nowrap cursor-pointer transition-all ${
              tab === t.key
                ? 'bg-[#2db84c] text-white shadow-sm'
                : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'
            }`}
          >
            {t.label}
            {t.key !== 'all' && (
              <span className="ml-1.5 text-xs opacity-70">
                ({orders.filter(o => o.orderStatus === t.key).length})
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Loading / Empty / Orders */}
      {isLoading ? (
        <div className="flex items-center justify-center py-16">
          <Loader2 size={24} className="animate-spin text-[#2db84c]" />
        </div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <Package size={48} className="mx-auto mb-3 opacity-50" />
          <p className="text-sm">Chưa có đơn hàng nào</p>
          <button
            onClick={() => navigate('/recipient')}
            className="mt-4 px-4 py-2 rounded-xl bg-[#2db84c] text-white text-sm font-medium cursor-pointer hover:bg-[#259e40] transition-all"
          >
            Khám phá ngay
          </button>
        </div>
      ) : (
        <motion.div className="flex flex-col gap-3" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
          {filtered.map(order => {
            const status = STATUS_MAP[order.orderStatus] || STATUS_MAP.PENDING;
            const firstDetail = order.orderDetails?.[0];
            const foodName = firstDetail?.foodPost?.name || 'Món ăn';
            const quantity = firstDetail?.quantity || 1;
            
            return (
              <motion.div
                key={order.id}
                initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
                onClick={() => navigate(`/recipient/orders/${order.id}`)}
                className="bg-white rounded-2xl border border-gray-100 p-4 hover:shadow-md transition-all cursor-pointer"
              >
                <div className="flex items-start justify-between gap-3 mb-2">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <p className="text-sm font-semibold text-gray-900 truncate">{foodName}</p>
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold whitespace-nowrap ${status.bg} ${status.text}`}>
                        {status.label}
                      </span>
                    </div>
                    <p className="text-xs text-gray-500">
                      {order.orderCode} · {order.supplier?.name} · x{quantity}
                    </p>
                  </div>
                  <div className="flex items-center gap-2 flex-shrink-0">
                    <div className="text-right">
                      <p className="text-sm font-bold text-gray-900">
                        {order.totalAmount > 0 ? formatVND(order.totalAmount) : '🎁 Miễn phí'}
                      </p>
                      <p className="text-xs text-gray-400">{timeAgo(order.createdAt)}</p>
                    </div>
                    <ChevronRight size={16} className="text-gray-300" />
                  </div>
                </div>

                {order.orderStatus === 'REJECTED' && (
                  <div className="mt-2 p-2 rounded-lg bg-red-50 text-xs text-red-600">
                    Đơn hàng đã bị từ chối
                  </div>
                )}
              </motion.div>
            );
          })}
        </motion.div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-2">
          {Array.from({ length: totalPages }, (_, i) => (
            <button
              key={i} onClick={() => setPage(i)}
              className={`w-9 h-9 rounded-xl text-sm font-medium cursor-pointer transition-all ${
                page === i ? 'bg-[#2db84c] text-white' : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'
              }`}
            >
              {i + 1}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
