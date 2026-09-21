import { useEffect, useState } from 'react';
import { Search, Eye, Loader2, X, CreditCard, CheckCircle2, Clock, AlertCircle, RefreshCw } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { apiFetch } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';

interface PaymentItem {
  id: number;
  orderId: number;
  orderCode?: string;
  amount: number;
  method: string;
  status: string;
  externalTransactionId?: string;
  refundTransactionId?: string;
  provider?: string;
  paidAt?: string;
  refundedAt?: string;
  paymentUrl?: string;
}

const STATUS_TABS = [
  { key: 'all', label: 'Tất cả' },
  { key: 'SUCCESS', label: 'Thành công' },
  { key: 'PENDING', label: 'Chờ xử lý' },
  { key: 'FAILED', label: 'Thất bại' },
  { key: 'REFUNDED', label: 'Đã hoàn tiền' },
];

const STATUS_CONFIG: Record<string, { label: string; badgeCls: string; icon: any }> = {
  SUCCESS: { label: 'Thành công', badgeCls: 'bg-green-100 text-green-700', icon: CheckCircle2 },
  PENDING: { label: 'Chờ xử lý', badgeCls: 'bg-amber-100 text-amber-700', icon: Clock },
  FAILED: { label: 'Thất bại', badgeCls: 'bg-red-100 text-red-600', icon: AlertCircle },
  REFUNDED: { label: 'Đã hoàn tiền', badgeCls: 'bg-purple-100 text-purple-700', icon: RefreshCw },
};

const METHOD_CONFIG: Record<string, { label: string; cls: string }> = {
  MOMO: { label: 'MoMo', cls: 'bg-pink-50 text-pink-700 border border-pink-200/60' },
  ZALOPAY: { label: 'ZaloPay', cls: 'bg-blue-50 text-blue-700 border border-blue-200/60' },
  CASH: { label: 'Tiền mặt', cls: 'bg-emerald-50 text-emerald-700 border border-emerald-200/60' },
  VNPAY: { label: 'VNPay', cls: 'bg-cyan-50 text-cyan-700 border border-cyan-200/60' },
};

export default function AdminPaymentsPage() {
  const { showError } = useToast();
  const [items, setItems] = useState<PaymentItem[]>([]);
  const [q, setQ] = useState('');
  const [dq, setDq] = useState('');
  const [status, setStatus] = useState('all');
  const [page, setPage] = useState(0);
  const [meta, setMeta] = useState<any>({});
  const [selected, setSelected] = useState<PaymentItem | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => {
      setDq(q.trim());
      setPage(0);
    }, 500);
    return () => clearTimeout(t);
  }, [q]);

  useEffect(() => {
    setLoading(true);
    apiFetch<any>(`/admin/payments?page=${page}&size=20&keyword=${encodeURIComponent(dq)}&status=${status}`)
      .then((r) => {
        setItems(r.content || []);
        setMeta(r);
      })
      .catch((e) => showError(e.message || 'Không thể tải giao dịch'))
      .finally(() => setLoading(false));
  }, [page, dq, status, showError]);

  useEffect(() => {
    const id = Number(new URLSearchParams(window.location.search).get('paymentId'));
    if (id) apiFetch<PaymentItem>(`/admin/payments/${id}`).then(setSelected).catch(() => undefined);
  }, []);

  const formatCurrency = (val?: number) => {
    return Number(val || 0).toLocaleString('vi-VN') + ' đ';
  };

  const handleRefund = async (paymentId: number) => {
    if (!window.confirm('Xác nhận hoàn tiền giao dịch này?')) return;
    try {
      const updated = await apiFetch<PaymentItem>(`/payments/${paymentId}/refund`, { method: 'PATCH' });
      setItems(prev => prev.map(item => item.id === paymentId ? updated : item));
      setSelected(updated);
    } catch (e: any) { showError(e.message || 'Hoàn tiền thất bại'); }
  };

  const formatDateTime = (dateStr?: string) => {
    if (!dateStr) return '—';
    const d = new Date(dateStr);
    return d.toLocaleString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  };

  return (
    <div className="p-4 md:p-6 max-w-6xl mx-auto flex flex-col gap-5">
      <div className="flex items-start justify-between flex-wrap gap-2">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-gray-900">Quản lý giao dịch thanh toán</h1>
          <p className="text-sm text-gray-500 mt-0.5">{meta.totalElements || 0} giao dịch trên nền tảng</p>
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
            placeholder="Tìm mã đơn hoặc mã giao dịch..."
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
          <CreditCard size={48} className="mx-auto mb-3 opacity-50 text-gray-400" />
          <p className="text-sm font-medium">Không tìm thấy giao dịch nào</p>
        </div>
      ) : (
        <>
          <div className="hidden md:block bg-white rounded-2xl border border-gray-100 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100 bg-gray-50/50">
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Mã GD</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Đơn hàng</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Số tiền</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Phương thức</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Trạng thái</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Thời gian</th>
                    <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wide">Hành động</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {items.map((p, i) => {
                    const st = STATUS_CONFIG[p.status] || { label: p.status, badgeCls: 'bg-gray-100 text-gray-600', icon: Clock };
                    const meth = METHOD_CONFIG[p.method] || { label: p.method || '—', cls: 'bg-gray-50 text-gray-600 border border-gray-100' };

                    return (
                      <motion.tr
                        key={p.id}
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        transition={{ delay: i * 0.02 }}
                        className="hover:bg-gray-50/50 transition-colors"
                      >
                        <td className="px-4 py-3.5 text-xs text-gray-500 font-mono">
                          #{p.id}
                        </td>

                        <td className="px-4 py-3.5">
                          <span className="font-semibold text-gray-900 text-xs font-mono">
                            #{p.orderId}
                          </span>
                        </td>

                        <td className="px-4 py-3.5 text-sm font-bold text-gray-900">
                          {formatCurrency(p.amount)}
                        </td>

                        <td className="px-4 py-3.5">
                          <span className={`px-2.5 py-1 rounded-full text-xs font-medium inline-flex items-center gap-1 ${meth.cls}`}>
                            {meth.label}
                          </span>
                        </td>

                        <td className="px-4 py-3.5">
                          <span className={`px-2.5 py-1 rounded-full text-xs font-semibold inline-block ${st.badgeCls}`}>
                            {st.label}
                          </span>
                        </td>

                        <td className="px-4 py-3.5 text-xs text-gray-500">
                          {formatDateTime(p.paidAt)}
                        </td>

                        <td className="px-4 py-3.5 text-center">
                          <button
                            onClick={() => {
                              apiFetch<any>(`/admin/payments/${p.id}`)
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
            {items.map((p, i) => {
              const st = STATUS_CONFIG[p.status] || { label: p.status, badgeCls: 'bg-gray-100 text-gray-600', icon: Clock };
              const meth = METHOD_CONFIG[p.method] || { label: p.method || '—', cls: 'bg-gray-50 text-gray-600 border border-gray-100' };

              return (
                <motion.div
                  key={p.id}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.03 }}
                  className="bg-white rounded-2xl border border-gray-100 p-4 space-y-3 shadow-xs"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="text-xs text-gray-400 font-mono">#{p.id}</span>
                      <span className="text-xs font-semibold text-gray-900 font-mono">Đơn #{p.orderId}</span>
                    </div>
                    <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${st.badgeCls}`}>
                      {st.label}
                    </span>
                  </div>

                  <div className="flex items-center justify-between pt-1">
                    <div>
                      <p className="text-xs text-gray-400">Số tiền</p>
                      <p className="text-base font-bold text-gray-900">{formatCurrency(p.amount)}</p>
                    </div>
                    <div className="text-right">
                      <p className="text-xs text-gray-400">Phương thức</p>
                      <span className={`px-2 py-0.5 rounded-full text-xs font-medium inline-block mt-0.5 ${meth.cls}`}>
                        {meth.label}
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center justify-between pt-2 border-t border-gray-50 text-xs text-gray-500">
                    <span>{formatDateTime(p.paidAt)}</span>
                    <button
                      onClick={() => {
                        apiFetch<any>(`/admin/payments/${p.id}`)
                          .then(setSelected)
                          .catch((e) => showError(e.message));
                      }}
                      className="p-1.5 text-gray-600 hover:text-[#2db84c] hover:bg-green-50 rounded-lg flex items-center gap-1 cursor-pointer font-medium"
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
              className="bg-white rounded-2xl p-6 w-full max-w-md shadow-xl"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center justify-between pb-4 border-b border-gray-100">
                <div className="flex items-center gap-2">
                  <div className="w-9 h-9 rounded-xl bg-green-50 flex items-center justify-center text-[#2db84c]">
                    <CreditCard size={18} />
                  </div>
                  <div>
                    <h2 className="font-bold text-gray-900 text-base">Chi tiết giao dịch</h2>
                    <p className="text-xs text-gray-400 font-mono">ID: #{selected.id}</p>
                  </div>
                </div>
                <button
                  onClick={() => setSelected(null)}
                  className="p-1 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100 cursor-pointer"
                >
                  <X size={18} />
                </button>
              </div>

              <div className="my-4 p-3 rounded-xl bg-gray-50/80 flex items-center justify-between">
                <span className="text-xs text-gray-500 font-medium">Trạng thái giao dịch</span>
                {(() => {
                  const st = STATUS_CONFIG[selected.status] || { label: selected.status, badgeCls: 'bg-gray-100 text-gray-600' };
                  return (
                    <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${st.badgeCls}`}>
                      {st.label}
                    </span>
                  );
                })()}
              </div>

              <div className="space-y-3 text-sm">
                <div className="flex items-center justify-between py-1 border-b border-gray-50">
                  <span className="text-gray-500 text-xs">Mã đơn hàng:</span>
                  <span className="font-mono font-bold text-gray-900">#{selected.orderId}</span>
                </div>

                <div className="flex items-center justify-between py-1 border-b border-gray-50">
                  <span className="text-gray-500 text-xs">Số tiền thanh toán:</span>
                  <span className="font-bold text-base text-[#2db84c]">{formatCurrency(selected.amount)}</span>
                </div>

                <div className="flex items-center justify-between py-1 border-b border-gray-50">
                  <span className="text-gray-500 text-xs">Phương thức:</span>
                  {(() => {
                    const meth = METHOD_CONFIG[selected.method] || { label: selected.method || '—', cls: 'bg-gray-100 text-gray-600' };
                    return (
                      <span className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${meth.cls}`}>
                        {meth.label}
                      </span>
                    );
                  })()}
                </div>

                <div className="flex items-center justify-between py-1 border-b border-gray-50">
                  <span className="text-gray-500 text-xs">Nhà cung cấp cổng:</span>
                  <span className="text-gray-700 font-medium text-xs">{selected.provider || '—'}</span>
                </div>

                <div className="flex items-center justify-between py-1 border-b border-gray-50">
                  <span className="text-gray-500 text-xs">Mã giao dịch ngoài:</span>
                  <span className="text-gray-700 font-mono text-xs max-w-[200px] truncate" title={selected.externalTransactionId}>
                    {selected.externalTransactionId || '—'}
                  </span>
                </div>

                <div className="flex items-center justify-between py-1 border-b border-gray-50">
                  <span className="text-gray-500 text-xs">Thời gian thanh toán:</span>
                  <span className="text-gray-700 text-xs">{formatDateTime(selected.paidAt)}</span>
                </div>

                {selected.refundTransactionId && (
                  <div className="flex items-center justify-between py-1 border-b border-gray-50">
                    <span className="text-gray-500 text-xs">Mã hoàn tiền:</span>
                    <span className="text-purple-700 font-mono text-xs max-w-[200px] truncate" title={selected.refundTransactionId}>
                      {selected.refundTransactionId}
                    </span>
                  </div>
                )}

                {selected.refundedAt && (
                  <div className="flex items-center justify-between py-1">
                    <span className="text-gray-500 text-xs">Thời gian hoàn tiền:</span>
                    <span className="text-purple-700 text-xs">{formatDateTime(selected.refundedAt)}</span>
                  </div>
                )}
              </div>

              {selected.status === 'SUCCESS' && (
                <button onClick={() => handleRefund(selected.id)} className="mt-4 w-full py-2.5 bg-red-50 hover:bg-red-100 text-red-600 font-medium rounded-xl text-sm cursor-pointer">
                  Xác nhận hoàn tiền
                </button>
              )}

              <div className="mt-6">
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
