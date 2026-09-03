import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import { useSearchParams } from 'react-router-dom';
import { Check, X, Package, Truck, Loader2, Clock, ChevronDown, Search } from 'lucide-react';
import { apiFetch } from '../../services/api';
import { formatVND, timeAgo } from '../../utils/format';

interface OrderItem {
  id: number;
  orderCode: string;
  orderStatus: string;
  totalAmount: number;
  createdAt: string;
  receiver: { fullName: string; phone: string };
  orderDetails: {
    foodPost: { name: string };
    quantity: number;
    unitPrice: number;
  }[];
  rejectionReason?: string;
}

const STATUS_MAP: Record<string, { bg: string; text: string; label: string }> = {
  PENDING: { bg: 'bg-amber-100', text: 'text-amber-700', label: 'Chờ xác nhận' },
  ACCEPTED: { bg: 'bg-blue-100', text: 'text-blue-700', label: 'Đã chấp nhận' },
  READY_FOR_PICKUP: { bg: 'bg-indigo-100', text: 'text-indigo-700', label: 'Sẵn sàng lấy' },
  DELIVERED: { bg: 'bg-cyan-100', text: 'text-cyan-700', label: 'Đã giao' },
  COMPLETED: { bg: 'bg-green-100', text: 'text-green-700', label: 'Hoàn thành' },
  CANCELLED: { bg: 'bg-gray-100', text: 'text-gray-600', label: 'Đã hủy' },
  REJECTED: { bg: 'bg-red-100', text: 'text-red-600', label: 'Từ chối' },
};

const TABS = [
  { key: 'all', label: 'Tất cả' },
  { key: 'PENDING', label: 'Chờ xác nhận' },
  { key: 'ACCEPTED', label: 'Đã chấp nhận' },
  { key: 'READY_FOR_PICKUP', label: 'Sẵn sàng' },
  { key: 'COMPLETED', label: 'Hoàn thành' },
];

export default function OrdersPage() {
  const [orders, setOrders] = useState<OrderItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  const [tab, setTab] = useState('all');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [rejectModal, setRejectModal] = useState<{ id: number; open: boolean }>({ id: 0, open: false });
  const [rejectReason, setRejectReason] = useState('');
  const [keyword, setKeyword] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [isDesktop, setIsDesktop] = useState(window.innerWidth >= 768);
  const [searchParams] = useSearchParams();
  const targetOrderId = searchParams.get('id') ? Number(searchParams.get('id')) : null;

  useEffect(() => {
    const handleResize = () => setIsDesktop(window.innerWidth >= 768);
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // Debounce logic for mobile
  useEffect(() => {
    if (!isDesktop) {
      const timer = setTimeout(() => {
        setKeyword(searchInput);
        setPage(0);
      }, 500);
      return () => clearTimeout(timer);
    }
  }, [searchInput, isDesktop]);

  const handleSearchKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (isDesktop && e.key === 'Enter') {
      setKeyword(searchInput);
      setPage(0);
    }
  };

  const fetchOrders = useCallback(async () => {
    setIsLoading(true);
    try {
      const statusParam = tab !== 'all' ? `&status=${tab}` : '';
      const keywordParam = keyword ? `&keyword=${encodeURIComponent(keyword)}` : '';
      const res = await apiFetch<any>(`/orders/supplier?page=${page}${statusParam}${keywordParam}`);

      const mappedOrders: OrderItem[] = (res.content || []).map((o: any) => ({
        id: o.id,
        orderCode: o.orderCode,
        orderStatus: o.orderStatus,
        totalAmount: o.totalAmount,
        createdAt: o.createdAt,
        receiver: o.receiver,
        orderDetails: o.orderDetails,
        rejectionReason: o.rejectedReason || o.rejectionReason,
      }));

      setOrders(mappedOrders);
      setTotalPages(res.totalPages || 0);
    } catch (err) {
      console.error('Failed to fetch orders:', err);
    } finally {
      setIsLoading(false);
    }
  }, [page, tab, keyword]);

  useEffect(() => { fetchOrders(); }, [fetchOrders]);

  const handleAction = async (orderId: number, action: string, body?: any) => {
    setActionLoading(orderId);
    try {
      await apiFetch(`/orders/${orderId}/${action}`, {
        method: 'PATCH',
        ...(body ? { body: JSON.stringify(body) } : {}),
      });
      fetchOrders();
    } catch (err: any) {
      alert(err.message || 'Thao tác thất bại');
    } finally {
      setActionLoading(null);
    }
  };

  const handleReject = async () => {
    if (!rejectReason.trim()) return;
    await handleAction(rejectModal.id, 'reject', { reason: rejectReason });
    setRejectModal({ id: 0, open: false });
    setRejectReason('');
  };

  const filtered = tab === 'all'
    ? orders.filter(o => {
      if (!keyword) return true;
      const term = keyword.toLowerCase();
      return o.orderCode.toLowerCase().includes(term) ||
        o.receiver.fullName.toLowerCase().includes(term) ||
        o.orderDetails?.[0]?.foodPost?.name.toLowerCase().includes(term);
    })
    : orders.filter(o => o.orderStatus === tab && (!keyword ||
      o.orderCode.toLowerCase().includes(keyword.toLowerCase()) ||
      o.receiver.fullName.toLowerCase().includes(keyword.toLowerCase()) ||
      o.orderDetails?.[0]?.foodPost?.name.toLowerCase().includes(keyword.toLowerCase())
    ));

  return (
    <div className="p-4 md:p-6 max-w-6xl mx-auto flex flex-col gap-5 min-h-[calc(100vh-80px)] relative">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <h1 className="text-xl md:text-2xl font-bold text-gray-900">Quản lý đơn tiếp nhận</h1>
        <div className="relative w-full md:w-72">
          <input
            type="text"
            placeholder={"Tìm mã đơn, tên..."}
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={handleSearchKeyDown}
            className="w-full pl-10 pr-4 py-2 bg-white border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/20 focus:border-[#2db84c] transition-all"
          />
          <Search size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        </div>
      </div>

      <div className="flex gap-2 overflow-x-auto pb-2 [&::-webkit-scrollbar]:hidden snap-x">
        {TABS.map(t => (
          <button
            key={t.key}
            onClick={() => { setTab(t.key); setPage(0); }}
            className={`px-4 py-2 rounded-xl text-sm font-medium whitespace-nowrap cursor-pointer transition-all snap-start ${tab === t.key
              ? 'bg-[#2db84c] text-white shadow-md shadow-green-500/20'
              : 'bg-white border border-gray-100 text-gray-600 hover:bg-gray-50'
              }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-16">
          <Loader2 size={24} className="animate-spin text-[#2db84c]" />
        </div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <Package size={48} className="mx-auto mb-3 opacity-50" />
          <p className="text-sm">Không có đơn hàng nào</p>
        </div>
      ) : (
        <motion.div
          className="flex flex-col gap-3"
          initial={{ opacity: 0 }} animate={{ opacity: 1 }}
        >
          {filtered.map(order => (
            <OrderCard
              key={order.id}
              order={order}
              actionLoading={actionLoading}
              initialExpanded={order.id === targetOrderId}
              onAccept={() => handleAction(order.id, 'accept')}
              onReject={() => setRejectModal({ id: order.id, open: true })}
              onReady={() => handleAction(order.id, 'ready')}
              onDeliver={() => handleAction(order.id, 'deliver')}
            />
          ))}
        </motion.div>
      )}

      {totalPages > 1 && filtered.length > 0 && (
        <div className="sticky bottom-0 -mx-4 md:-mx-6 px-4 md:px-6 py-4 flex justify-center gap-2 z-40 mt-auto">
          {Array.from({ length: totalPages }, (_, i) => (
            <button
              key={i} onClick={() => setPage(i)}
              className={`w-10 h-10 rounded-xl text-sm font-semibold cursor-pointer transition-all ${page === i
                  ? 'bg-[#2db84c] text-white shadow-md shadow-green-500/20'
                  : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50 hover:border-gray-300'
                }`}
            >
              {i + 1}
            </button>
          ))}
        </div>
      )}

      {rejectModal.open && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" onClick={() => setRejectModal({ id: 0, open: false })}>
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
            className="bg-white rounded-2xl p-6 w-full max-w-sm" onClick={e => e.stopPropagation()}
          >
            <h3 className="font-semibold text-gray-900 mb-3">Lý do từ chối</h3>
            <textarea
              value={rejectReason} onChange={e => setRejectReason(e.target.value)}
              rows={3} placeholder="Nhập lý do từ chối đơn..."
              className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-red-300 focus:border-red-400 transition-all resize-none mb-4"
            />
            <div className="flex gap-3">
              <button
                onClick={() => setRejectModal({ id: 0, open: false })}
                className="flex-1 py-2.5 rounded-xl border border-gray-200 text-sm font-medium text-gray-600 cursor-pointer hover:bg-gray-50"
              >
                Hủy
              </button>
              <button
                onClick={handleReject}
                disabled={!rejectReason.trim() || actionLoading === rejectModal.id}
                className="flex-1 py-2.5 rounded-xl bg-red-500 text-white text-sm font-medium cursor-pointer hover:bg-red-600 disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {actionLoading === rejectModal.id && <Loader2 size={14} className="animate-spin" />}
                Từ chối
              </button>
            </div>
          </motion.div>
        </div>
      )}
    </div>
  );
}

function OrderCard({
  order, actionLoading, initialExpanded = false, onAccept, onReject, onReady, onDeliver,
}: {
  order: OrderItem;
  actionLoading: number | null;
  initialExpanded?: boolean;
  onAccept: () => void;
  onReject: () => void;
  onReady: () => void;
  onDeliver: () => void;
}) {
  const [expanded, setExpanded] = useState(initialExpanded);
  const status = STATUS_MAP[order.orderStatus] || STATUS_MAP.PENDING;

  const firstDetail = order.orderDetails?.[0];
  const foodName = firstDetail?.foodPost?.name || 'Món ăn';
  const quantity = firstDetail?.quantity || 1;
  const recipientName = order.receiver?.fullName || 'Khách hàng';
  const recipientPhone = order.receiver?.phone || '';

  return (
    <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden shadow-sm transition-all">
      <div
        className="p-4 cursor-pointer hover:bg-gray-50 flex items-center justify-between gap-4"
        onClick={() => setExpanded(!expanded)}
      >
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className="text-sm font-semibold text-gray-900 truncate">{foodName}</span>
            <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold whitespace-nowrap ${status.bg} ${status.text}`}>
              {status.label}
            </span>
          </div>
          <div className="text-xs text-gray-500 flex flex-wrap gap-x-3 gap-y-1">
            <span>{order.orderCode}</span>
            <span>· {recipientName}</span>
            <span>· x{quantity}</span>
            <span className="hidden sm:inline">· {timeAgo(order.createdAt)}</span>
          </div>
        </div>

        <div className="flex items-center gap-3 flex-shrink-0">
          <div className="text-right hidden sm:block">
            <p className="text-sm font-bold text-gray-900">
              {order.totalAmount > 0 ? formatVND(order.totalAmount) : '🎁 Miễn phí'}
            </p>
          </div>
          <ChevronDown size={18} className={`transition-transform duration-300 ${expanded ? 'rotate-180 text-[#2db84c]' : 'text-gray-400'}`} />
        </div>
      </div>

      {expanded && (
        <div className="border-t border-gray-100 p-4 bg-gray-50/50">
          <div className="grid grid-cols-2 gap-4 text-sm mb-4">
            <div>
              <p className="text-xs text-gray-500 mb-0.5">Khách hàng</p>
              <p className="font-medium text-gray-900">{recipientName}</p>
              <p className="text-gray-600">{recipientPhone}</p>
            </div>
            <div>
              <p className="text-xs text-gray-500 mb-0.5">Thời gian đặt</p>
              <p className="font-medium text-gray-900">{new Date(order.createdAt).toLocaleString('vi-VN')}</p>
              <p className="text-sm font-bold text-gray-900 sm:hidden mt-2">
                Tổng: {order.totalAmount > 0 ? formatVND(order.totalAmount) : 'Miễn phí'}
              </p>
            </div>
          </div>

          {/* Rejection Reason */}
          {order.orderStatus === 'REJECTED' && order.rejectionReason && (
            <div className="p-3 bg-red-50 text-red-600 text-xs rounded-xl mb-4 border border-red-100">
              <span className="font-semibold">Lý do từ chối:</span> {order.rejectionReason}
            </div>
          )}

          {/* Action Buttons */}
          <div className="flex flex-wrap gap-2 pt-2 border-t border-gray-100">
            {order.orderStatus === 'PENDING' && (
              <>
                <button
                  onClick={(e) => { e.stopPropagation(); onAccept(); }}
                  disabled={actionLoading === order.id}
                  className="flex-1 min-w-[120px] py-2 bg-[#2db84c] text-white text-sm font-medium rounded-xl cursor-pointer hover:bg-[#259e40] flex items-center justify-center gap-1 transition-all disabled:opacity-70"
                >
                  {actionLoading === order.id ? <Loader2 size={16} className="animate-spin" /> : <Check size={16} />}
                  Chấp nhận
                </button>
                <button
                  onClick={(e) => { e.stopPropagation(); onReject(); }}
                  disabled={actionLoading === order.id}
                  className="flex-1 min-w-[120px] py-2 bg-red-50 text-red-500 text-sm font-medium rounded-xl cursor-pointer hover:bg-red-100 flex items-center justify-center gap-1 transition-all disabled:opacity-70"
                >
                  <X size={16} /> Từ chối
                </button>
              </>
            )}

            {order.orderStatus === 'ACCEPTED' && (
              <button
                onClick={(e) => { e.stopPropagation(); onReady(); }}
                disabled={actionLoading === order.id}
                className="w-full py-2 bg-indigo-50 text-indigo-600 border border-indigo-100 text-sm font-medium rounded-xl cursor-pointer hover:bg-indigo-100 flex items-center justify-center gap-2 transition-all disabled:opacity-70"
              >
                {actionLoading === order.id ? <Loader2 size={16} className="animate-spin" /> : <Package size={16} />}
                Sẵn sàng lấy món
              </button>
            )}

            {order.orderStatus === 'READY_FOR_PICKUP' && (
              <button
                onClick={(e) => { e.stopPropagation(); onDeliver(); }}
                disabled={actionLoading === order.id}
                className="w-full py-2 bg-cyan-50 text-cyan-600 border border-cyan-100 text-sm font-medium rounded-xl cursor-pointer hover:bg-cyan-100 flex items-center justify-center gap-2 transition-all disabled:opacity-70"
              >
                {actionLoading === order.id ? <Loader2 size={16} className="animate-spin" /> : <Truck size={16} />}
                Đã giao cho khách
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
