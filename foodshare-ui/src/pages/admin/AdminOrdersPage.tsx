import { useEffect, useState } from 'react';
import { Search, Eye, Loader2, X, ShoppingBag, Store, User, CreditCard, RefreshCw } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { apiFetch } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';

interface OrderItem {
  id: number;
  orderCode: string;
  totalAmount: number;
  orderStatus: string;
  paymentStatus: string;
  createdAt?: string;
  receiver?: {
    id: number;
    fullName: string;
    phone?: string;
    email?: string;
  };
  supplier?: {
    id: number;
    name: string;
    phone?: string;
    address?: string;
  };
  orderDetails?: Array<{
    id: number;
    quantity: number;
    subtotal: number;
    foodPost?: {
      id: number;
      name: string;
      images?: string[];
    };
  }>;
}

const STATUS_TABS = [
  { key: 'all', label: 'Tất cả' },
  { key: 'PENDING', label: 'Chờ xử lý' },
  { key: 'ACCEPTED', label: 'Đã xác nhận' },
  { key: 'READY_FOR_PICKUP', label: 'Sẵn sàng' },
  { key: 'DELIVERED', label: 'Đang giao' },
  { key: 'COMPLETED', label: 'Hoàn tất' },
  { key: 'CANCELLED', label: 'Đã hủy' },
];

const ORDER_STATUS_MAP: Record<string, { label: string; cls: string }> = {
  PENDING: { label: 'Chờ xử lý', cls: 'bg-amber-100 text-amber-700' },
  ACCEPTED: { label: 'Đã chấp nhận', cls: 'bg-blue-100 text-blue-700' },
  READY_FOR_PICKUP: { label: 'Sẵn sàng nhận', cls: 'bg-indigo-100 text-indigo-700' },
  DELIVERED: { label: 'Đã giao', cls: 'bg-cyan-100 text-cyan-700' },
  COMPLETED: { label: 'Hoàn tất', cls: 'bg-green-100 text-green-700' },
  CANCELLED: { label: 'Đã hủy', cls: 'bg-red-100 text-red-600' },
  REJECTED: { label: 'Từ chối', cls: 'bg-gray-100 text-gray-600' },
};

export default function AdminOrdersPage() {
  const { showSuccess, showError } = useToast();
  const [items, setItems] = useState<OrderItem[]>([]);
  const [q, setQ] = useState('');
  const [dq, setDq] = useState('');
  const [status, setStatus] = useState('all');
  const [page, setPage] = useState(0);
  const [meta, setMeta] = useState<any>({});
  const [selected, setSelected] = useState<OrderItem | null>(null);
  const [loading, setLoading] = useState(false);
  const [refunding, setRefunding] = useState(false);

  const handleRefund = async (orderId: number) => {
    if (!window.confirm('Bạn có chắc chắn muốn hoàn tiền cho đơn hàng này không? Tiền sẽ được hoàn trả về tài khoản của người dùng.')) return;
    setRefunding(true);
    try {
      const res = await apiFetch<any>(`/admin/orders/${orderId}/refund`, { method: 'POST' });
      showSuccess('Hoàn tiền thành công');
      setSelected(res);
      setItems((prev) => prev.map((o) => (o.id === orderId ? { ...o, paymentStatus: 'REFUNDED' } : o)));
    } catch (e: any) {
      showError(e.message || 'Hoàn tiền thất bại');
    } finally {
      setRefunding(false);
    }
  };

  useEffect(() => {
    const t = setTimeout(() => {
      setDq(q.trim());
      setPage(0);
    }, 400);
    return () => clearTimeout(t);
  }, [q]);

  useEffect(() => {
    setLoading(true);
    apiFetch<any>(`/admin/orders?page=${page}&size=20&keyword=${encodeURIComponent(dq)}&status=${status}`)
      .then((r) => {
        setItems(r.content || []);
        setMeta(r);
      })
      .catch((e) => showError(e.message || 'Không thể tải đơn hàng'))
      .finally(() => setLoading(false));
  }, [page, dq, status, showError]);

  useEffect(() => {
    const id = Number(new URLSearchParams(window.location.search).get('orderId'));
    if (id) {
      apiFetch<any>(`/admin/orders/${id}`)
        .then(setSelected)
        .catch(() => undefined);
    }
  }, []);

  const formatCurrency = (val?: number) => {
    return Number(val || 0).toLocaleString('vi-VN') + ' đ';
  };

  return (
    <div className="p-4 md:p-6 max-w-6xl mx-auto flex flex-col gap-5">
      <div className="flex items-start justify-between flex-wrap gap-2">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-gray-900">Quản lý đơn hàng</h1>
          <p className="text-sm text-gray-500 mt-0.5">{meta.totalElements || 0} đơn hàng trên nền tảng</p>
        </div>
      </div>

      <div className="flex flex-col sm:flex-row gap-3">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            setPage(0);
          }}
          className="flex-1 flex items-center gap-2 bg-white border border-gray-200 rounded-xl px-3 py-2.5"
        >
          <Search size={16} className="text-gray-400" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Tìm mã đơn, người nhận, quán ăn..."
            className="bg-transparent text-sm text-gray-900 outline-none flex-1 placeholder:text-gray-400"
          />
          {q && (
            <button
              type="button"
              onClick={() => {
                setQ('');
                setPage(0);
              }}
              className="text-gray-400 hover:text-gray-600 cursor-pointer"
            >
              <X size={14} />
            </button>
          )}
        </form>

        <div className="flex gap-2 overflow-x-auto">
          {STATUS_TABS.map((t) => (
            <button
              key={t.key}
              onClick={() => {
                setStatus(t.key);
                setPage(0);
              }}
              className={`px-3 py-2 rounded-xl text-xs font-medium whitespace-nowrap cursor-pointer transition-all ${
                status === t.key
                  ? 'bg-[#2db84c] text-white shadow-sm'
                  : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-16">
          <Loader2 size={24} className="animate-spin text-[#2db84c]" />
        </div>
      ) : items.length === 0 ? (
        <div className="text-center py-16 text-gray-400 bg-white rounded-2xl border border-gray-100">
          <ShoppingBag size={48} className="mx-auto mb-3 opacity-50 text-gray-400" />
          <p className="text-sm font-medium">Không tìm thấy đơn hàng nào</p>
        </div>
      ) : (
        <>
          <div className="hidden md:block bg-white rounded-2xl border border-gray-100 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100 bg-gray-50/50">
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Mã đơn</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Người nhận</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Nhà cung cấp</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Tổng tiền</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Trạng thái</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Thanh toán</th>
                    <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wide">Hành động</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {items.map((o, i) => {
                    const st = ORDER_STATUS_MAP[o.orderStatus] || { label: o.orderStatus, cls: 'bg-gray-100 text-gray-600' };

                    return (
                      <motion.tr
                        key={o.id}
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        transition={{ delay: i * 0.02 }}
                        className="hover:bg-gray-50/50 transition-colors"
                      >
                        <td className="px-4 py-3.5 font-semibold text-gray-900 font-mono text-xs">
                          {o.orderCode}
                        </td>
                        <td className="px-4 py-3.5 text-xs text-gray-800">
                          {o.receiver?.fullName || '—'}
                        </td>
                        <td className="px-4 py-3.5 text-xs text-gray-700 max-w-[180px] truncate" title={o.supplier?.name}>
                          {o.supplier?.name || '—'}
                        </td>
                        <td className="px-4 py-3.5 text-xs font-bold text-gray-900">
                          {formatCurrency(o.totalAmount)}
                        </td>
                        <td className="px-4 py-3.5">
                          <span className={`px-2.5 py-1 rounded-full text-xs font-semibold inline-block ${st.cls}`}>
                            {st.label}
                          </span>
                        </td>
                        <td className="px-4 py-3.5 text-xs">
                          <span className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${
                            o.paymentStatus === 'SUCCESS' 
                              ? 'bg-green-50 text-green-700 border border-green-200/60' 
                              : 'bg-amber-50 text-amber-700 border border-amber-200/60'
                          }`}>
                            {o.paymentStatus === 'SUCCESS' ? 'Đã thanh toán' : o.paymentStatus || 'Chưa thanh toán'}
                          </span>
                        </td>
                        <td className="px-4 py-3.5 text-center">
                          <button
                            onClick={() => {
                              apiFetch<any>(`/admin/orders/${o.id}`)
                                .then(setSelected)
                                .catch((e) => showError(e.message));
                            }}
                            className="p-1.5 text-gray-500 hover:text-[#2db84c] hover:bg-green-50 rounded-lg transition-colors cursor-pointer"
                            title="Xem chi tiết"
                          >
                            <Eye size={16} />
                          </button>
                        </td>
                      </motion.tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          <div className="md:hidden flex flex-col gap-3">
            {items.map((o, i) => {
              const st = ORDER_STATUS_MAP[o.orderStatus] || { label: o.orderStatus, cls: 'bg-gray-100 text-gray-600' };

              return (
                <motion.div
                  key={o.id}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.03 }}
                  className="bg-white rounded-2xl border border-gray-100 p-4 space-y-3 shadow-xs"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-gray-900 font-mono">{o.orderCode}</span>
                    <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${st.cls}`}>
                      {st.label}
                    </span>
                  </div>

                  <div className="text-xs text-gray-600 space-y-1">
                    <p><span className="text-gray-400">Người nhận:</span> {o.receiver?.fullName || '—'}</p>
                    <p><span className="text-gray-400">Quán ăn:</span> {o.supplier?.name || '—'}</p>
                  </div>

                  <div className="flex items-center justify-between pt-2 border-t border-gray-50">
                    <div>
                      <p className="text-xs text-gray-400">Tổng tiền</p>
                      <p className="text-sm font-bold text-gray-900">{formatCurrency(o.totalAmount)}</p>
                    </div>
                    <button
                      onClick={() => {
                        apiFetch<any>(`/admin/orders/${o.id}`)
                          .then(setSelected)
                          .catch((e) => showError(e.message));
                      }}
                      className="p-1.5 text-gray-600 hover:text-[#2db84c] hover:bg-green-50 rounded-lg flex items-center gap-1 cursor-pointer font-medium text-xs"
                    >
                      <Eye size={14} /> Chi tiết
                    </button>
                  </div>
                </motion.div>
              );
            })}
          </div>

          {meta.totalPages > 1 && (
            <div className="flex justify-center gap-2 mt-2">
              {Array.from({ length: Math.min(meta.totalPages, 10) }, (_, i) => (
                <button
                  key={i}
                  onClick={() => setPage(i)}
                  className={`w-9 h-9 rounded-xl text-sm font-medium cursor-pointer transition-all ${
                    page === i
                      ? 'bg-[#2db84c] text-white'
                      : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'
                  }`}
                >
                  {i + 1}
                </button>
              ))}
            </div>
          )}
        </>
      )}

      <AnimatePresence>
        {selected && (
          <div
            className="fixed inset-0 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4 z-50"
            onClick={() => setSelected(null)}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="bg-white rounded-2xl w-full max-w-lg max-h-[88vh] overflow-y-auto p-6 shadow-xl"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center justify-between pb-4 border-b border-gray-100">
                <div className="flex items-center gap-2">
                  <div className="w-9 h-9 rounded-xl bg-green-50 flex items-center justify-center text-[#2db84c]">
                    <ShoppingBag size={18} />
                  </div>
                  <div>
                    <h2 className="font-bold text-gray-900 text-base">Đơn hàng {selected.orderCode}</h2>
                    <p className="text-xs text-gray-400">ID hệ thống: #{selected.id}</p>
                  </div>
                </div>
                <button
                  onClick={() => setSelected(null)}
                  className="p-1 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100 cursor-pointer"
                >
                  <X size={18} />
                </button>
              </div>

              <div className="my-4 p-3 rounded-xl bg-gray-50 flex items-center justify-between">
                <span className="text-xs text-gray-500 font-medium">Trạng thái</span>
                {(() => {
                  const st = ORDER_STATUS_MAP[selected.orderStatus] || { label: selected.orderStatus, cls: 'bg-gray-100 text-gray-600' };
                  return (
                    <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${st.cls}`}>
                      {st.label}
                    </span>
                  );
                })()}
              </div>

              <div className="grid grid-cols-2 gap-3 text-xs mb-4">
                <div className="p-3 bg-gray-50/70 rounded-xl">
                  <div className="flex items-center gap-1.5 text-gray-500 mb-1">
                    <User size={13} />
                    <span>Người nhận</span>
                  </div>
                  <p className="font-bold text-gray-900">{selected.receiver?.fullName || '—'}</p>
                  {selected.receiver?.phone && <p className="text-gray-500 mt-0.5">{selected.receiver.phone}</p>}
                </div>

                <div className="p-3 bg-gray-50/70 rounded-xl">
                  <div className="flex items-center gap-1.5 text-gray-500 mb-1">
                    <Store size={13} />
                    <span>Nhà cung cấp</span>
                  </div>
                  <p className="font-bold text-gray-900 truncate" title={selected.supplier?.name}>{selected.supplier?.name || '—'}</p>
                  {selected.supplier?.phone && <p className="text-gray-500 mt-0.5">{selected.supplier.phone}</p>}
                </div>
              </div>

              <div className="mb-4">
                <h4 className="text-xs font-bold text-gray-700 uppercase tracking-wide mb-2">Món ăn trong đơn</h4>
                <div className="divide-y divide-gray-100 border border-gray-100 rounded-xl overflow-hidden">
                  {(selected.orderDetails || []).map((d) => (
                    <div key={d.id} className="p-3 flex items-center justify-between text-xs hover:bg-gray-50/50">
                      <div className="flex items-center gap-2.5">
                        {d.foodPost?.images && d.foodPost.images.length > 0 ? (
                          <img src={d.foodPost.images[0]} alt="" className="w-8 h-8 rounded-lg object-cover" />
                        ) : (
                          <div className="w-8 h-8 rounded-lg bg-gray-100 flex items-center justify-center text-gray-400">
                            <ShoppingBag size={14} />
                          </div>
                        )}
                        <div>
                          <p className="font-semibold text-gray-900">{d.foodPost?.name || 'Món ăn'}</p>
                          <p className="text-gray-400">Số lượng: {d.quantity}</p>
                        </div>
                      </div>
                      <span className="font-bold text-gray-900">{formatCurrency(d.subtotal)}</span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="pt-3 border-t border-gray-100 flex items-center justify-between text-sm">
                <div className="flex items-center gap-1.5 text-gray-500 text-xs">
                  <CreditCard size={14} />
                  <span>Thanh toán: <b className="text-gray-900">{selected.paymentStatus || 'Chưa thanh toán'}</b></span>
                </div>
                <div>
                  <span className="text-xs text-gray-500 mr-2">Tổng thanh toán:</span>
                  <span className="font-bold text-base text-[#2db84c]">{formatCurrency(selected.totalAmount)}</span>
                </div>
              </div>

              {selected.paymentStatus === 'SUCCESS' && (
                <div className="mt-4">
                  <button
                    onClick={() => handleRefund(selected.id)}
                    disabled={refunding}
                    className="w-full py-2.5 bg-red-50 hover:bg-red-100 text-red-600 font-semibold rounded-xl text-sm transition-colors cursor-pointer flex items-center justify-center gap-2 border border-red-200"
                  >
                    {refunding ? <Loader2 size={16} className="animate-spin" /> : <RefreshCw size={16} />}
                    <span>Hoàn tiền giao dịch này</span>
                  </button>
                </div>
              )}

              <div className="mt-3">
                <button
                  onClick={() => setSelected(null)}
                  className="w-full py-2.5 bg-gray-100 hover:bg-gray-200 text-gray-700 font-medium rounded-xl text-sm transition-colors cursor-pointer"
                >
                  Đóng
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}
