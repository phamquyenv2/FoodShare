import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Package, Loader2, ChevronRight, CreditCard, CheckCircle, XCircle, Clock, Truck, ShoppingBag } from 'lucide-react';
import { apiFetch } from '../../services/api';
import { formatVND, timeAgo } from '../../utils/format';

interface OrderItem {
  id: number; orderCode: string; status: string; totalAmount: number;
  createdAt: string; supplierName: string; foodPostName: string;
  quantity: number; unitPrice: number; rejectionReason?: string;
  paymentStatus?: string;
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
  { key: 'all', label: 'Tất cả' }, { key: 'PENDING', label: 'Chờ xử lý' },
  { key: 'ACCEPTED', label: 'Đang xử lý' }, { key: 'COMPLETED', label: 'Hoàn thành' },
];

export default function OrgOrdersPage() {
  const navigate = useNavigate();
  const [orders, setOrders] = useState<OrderItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [tab, setTab] = useState('all');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [actionLoading, setActionLoading] = useState<number | null>(null);

  const fetchOrders = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await apiFetch<any>(`/orders/my?page=${page}&size=20`);
      setOrders(res.content || []);
      setTotalPages(res.totalPages || 0);
    } catch (err) { console.error(err); } finally { setIsLoading(false); }
  }, [page]);

  useEffect(() => { fetchOrders(); }, [fetchOrders]);

  const handleAction = async (orderId: number, action: string) => {
    setActionLoading(orderId);
    try {
      await apiFetch(`/orders/${orderId}/${action}`, { method: 'PATCH' });
      fetchOrders();
    } catch (err: any) { alert(err.message || 'Thao tác thất bại'); }
    finally { setActionLoading(null); }
  };

  const filtered = tab === 'all' ? orders : orders.filter(o => o.status === tab);

  // Group by supplier for overview
  const supplierGroups = filtered.reduce<Record<string, OrderItem[]>>((acc, o) => {
    const key = o.supplierName || 'Khác';
    if (!acc[key]) acc[key] = [];
    acc[key].push(o);
    return acc;
  }, {});

  return (
    <div className="p-4 md:p-6 max-w-4xl mx-auto flex flex-col gap-5">
      <h1 className="text-xl md:text-2xl font-bold text-gray-900">Quản lý đơn tiếp nhận</h1>

      {/* Tabs */}
      <div className="flex gap-2 overflow-x-auto pb-1">
        {TABS.map(t => (
          <button key={t.key} onClick={() => { setTab(t.key); setPage(0); }}
            className={`px-4 py-2 rounded-xl text-sm font-medium whitespace-nowrap cursor-pointer transition-all ${tab === t.key ? 'bg-[#2db84c] text-white shadow-sm' : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'}`}>
            {t.label}
            {t.key !== 'all' && <span className="ml-1.5 text-xs opacity-70">({orders.filter(o => o.status === t.key).length})</span>}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-16"><Loader2 size={24} className="animate-spin text-[#2db84c]" /></div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <Package size={48} className="mx-auto mb-3 opacity-50" />
          <p className="text-sm">Chưa có đơn hàng nào</p>
          <button onClick={() => navigate('/organization')} className="mt-4 px-4 py-2 rounded-xl bg-[#2db84c] text-white text-sm font-medium cursor-pointer hover:bg-[#259e40] transition-all">Khám phá ngay</button>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {Object.entries(supplierGroups).map(([supplier, items]) => (
            <motion.div key={supplier} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
              className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
              {/* Supplier header */}
              <div className="px-4 py-3 bg-gray-50/80 border-b border-gray-100 flex items-center gap-2">
                <div className="w-7 h-7 rounded-full bg-gradient-to-br from-[#2db84c] to-[#1a9e3a] flex items-center justify-center text-white text-[10px] font-bold">{supplier.charAt(0).toUpperCase()}</div>
                <p className="text-sm font-semibold text-gray-900">{supplier}</p>
                <span className="text-xs text-gray-400 ml-auto">{items.length} đơn</span>
              </div>
              <div className="divide-y divide-gray-50">
                {items.map(order => {
                  const status = STATUS_MAP[order.status] || STATUS_MAP.PENDING;
                  return (
                    <div key={order.id} className="p-4 hover:bg-gray-50/50 transition-colors cursor-pointer"
                      onClick={() => navigate(`/organization/orders/${order.id}`)}>
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 mb-1">
                            <p className="text-sm font-semibold text-gray-900 truncate">{order.foodPostName}</p>
                            <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold whitespace-nowrap ${status.bg} ${status.text}`}>{status.label}</span>
                          </div>
                          <p className="text-xs text-gray-500">{order.orderCode} · x{order.quantity}</p>
                        </div>
                        <div className="flex items-center gap-2 flex-shrink-0">
                          <div className="text-right">
                            <p className="text-sm font-bold text-gray-900">{order.totalAmount > 0 ? formatVND(order.totalAmount) : '🎁'}</p>
                            <p className="text-xs text-gray-400">{timeAgo(order.createdAt)}</p>
                          </div>
                          <ChevronRight size={16} className="text-gray-300" />
                        </div>
                      </div>
                      {/* Quick actions */}
                      {order.status === 'DELIVERED' && (
                        <div className="mt-2 flex gap-2">
                          <button onClick={(e) => { e.stopPropagation(); handleAction(order.id, 'complete'); }}
                            disabled={actionLoading === order.id}
                            className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-xl bg-green-50 text-green-600 text-xs font-semibold cursor-pointer hover:bg-green-100 disabled:opacity-50">
                            {actionLoading === order.id ? <Loader2 size={13} className="animate-spin" /> : <CheckCircle size={14} />} Xác nhận nhận hàng
                          </button>
                        </div>
                      )}
                      {order.status === 'PENDING' && (
                        <div className="mt-2 flex gap-2">
                          <button onClick={(e) => { e.stopPropagation(); handleAction(order.id, 'cancel'); }}
                            disabled={actionLoading === order.id}
                            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-red-50 text-red-500 text-xs font-medium cursor-pointer hover:bg-red-100 disabled:opacity-50">
                            <XCircle size={13} /> Hủy
                          </button>
                        </div>
                      )}
                      {order.rejectionReason && (
                        <div className="mt-2 p-2 rounded-lg bg-red-50 text-xs text-red-600">Lý do: {order.rejectionReason}</div>
                      )}
                    </div>
                  );
                })}
              </div>
            </motion.div>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-2">
          {Array.from({ length: totalPages }, (_, i) => (
            <button key={i} onClick={() => setPage(i)} className={`w-9 h-9 rounded-xl text-sm font-medium cursor-pointer transition-all ${page === i ? 'bg-[#2db84c] text-white' : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'}`}>{i + 1}</button>
          ))}
        </div>
      )}
    </div>
  );
}
