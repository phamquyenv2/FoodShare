import { useEffect, useState, useMemo, useCallback } from 'react';
import {
  Search,
  Check,
  X,
  Loader2,
  Wallet,
  Eye,
  CheckCircle2,
  Clock,
  AlertCircle,
  XCircle,
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { apiFetch } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';
import { formatVND } from '../../utils/format';

interface PayoutItem {
  id: number;
  orderId?: number;
  payoutAccountId?: number;
  businessProfileId?: number;
  supplierName: string;
  payoutCode?: string;
  grossAmount?: number;
  platformFee?: number;
  netAmount?: number;
  requestedAmount?: number;
  status: 'PENDING' | 'SUCCESS' | 'FAILED' | 'CANCELLED';
  bankCode?: string;
  bankName?: string;
  accountNumber?: string;
  accountHolderName?: string;
  externalTransactionId?: string;
  completedAt?: string;
  failedAt?: string;
  failureReason?: string;
  rejectionReason?: string;
  reviewedAt?: string;
  createdAt?: string;
}

const STATUS_TABS = [
  { key: 'all', label: 'Tất cả' },
  { key: 'PENDING', label: 'Chờ duyệt' },
  { key: 'SUCCESS', label: 'Đã duyệt' },
  { key: 'FAILED', label: 'Từ chối' },
  { key: 'CANCELLED', label: 'Đã hủy' },
];

const STATUS_CONFIG: Record<string, { label: string; badgeCls: string; icon: any }> = {
  PENDING: { label: 'Chờ duyệt', badgeCls: 'bg-amber-100 text-amber-700', icon: Clock },
  SUCCESS: { label: 'Đã duyệt', badgeCls: 'bg-green-100 text-green-700', icon: CheckCircle2 },
  FAILED: { label: 'Từ chối', badgeCls: 'bg-red-100 text-red-600', icon: AlertCircle },
  CANCELLED: { label: 'Đã hủy', badgeCls: 'bg-gray-100 text-gray-600', icon: XCircle },
};

export default function AdminPayoutsPage() {
  const { showError, showSuccess } = useToast();
  const [items, setItems] = useState<PayoutItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState('PENDING');
  const [page, setPage] = useState(0);
  const [meta, setMeta] = useState<any>({});
  const [search, setSearch] = useState('');

  const [selectedPayout, setSelectedPayout] = useState<PayoutItem | null>(null);
  const [approveConfirmPayout, setApproveConfirmPayout] = useState<PayoutItem | null>(null);
  const [rejectModalPayout, setRejectModalPayout] = useState<PayoutItem | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [actionBusy, setActionBusy] = useState<number | null>(null);

  const loadData = useCallback(() => {
    setLoading(true);
    const statusQuery = status !== 'all' ? `&status=${status}` : '';
    apiFetch<any>(`/admin/payouts?page=${page}&size=20${statusQuery}`)
      .then((r) => {
        setItems(r.content || []);
        setMeta(r || {});
      })
      .catch((e) => showError(e.message || 'Không thể tải yêu cầu rút tiền'))
      .finally(() => setLoading(false));
  }, [page, status, showError]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const filteredItems = useMemo(() => {
    if (!search.trim()) return items;
    const term = search.trim().toLowerCase();
    return items.filter((p) => {
      const code = (p.payoutCode || `#PO-${p.id}`).toLowerCase();
      const name = (p.supplierName || '').toLowerCase();
      const bank = (p.bankName || p.bankCode || '').toLowerCase();
      const acc = (p.accountNumber || '').toLowerCase();
      const holder = (p.accountHolderName || '').toLowerCase();
      return (
        code.includes(term) ||
        name.includes(term) ||
        bank.includes(term) ||
        acc.includes(term) ||
        holder.includes(term)
      );
    });
  }, [items, search]);

  const handleApprove = async (payout: PayoutItem) => {
    setActionBusy(payout.id);
    try {
      await apiFetch(`/admin/payouts/${payout.id}/approve`, { method: 'PATCH' });
      showSuccess(`Đã duyệt yêu cầu ${payout.payoutCode || `#PO-${payout.id}`}`);
      setApproveConfirmPayout(null);
      if (selectedPayout?.id === payout.id) {
        setSelectedPayout(null);
      }
      loadData();
    } catch (e: any) {
      showError(e.message || 'Duyệt yêu cầu thất bại');
    } finally {
      setActionBusy(null);
    }
  };

  const handleReject = async (payout: PayoutItem) => {
    if (!rejectReason.trim()) {
      showError('Vui lòng nhập lý do từ chối');
      return;
    }
    setActionBusy(payout.id);
    try {
      await apiFetch(`/admin/payouts/${payout.id}/reject`, {
        method: 'PATCH',
        body: JSON.stringify({ reason: rejectReason.trim() }),
      });
      showSuccess(`Đã từ chối yêu cầu ${payout.payoutCode || `#PO-${payout.id}`}`);
      setRejectModalPayout(null);
      setRejectReason('');
      if (selectedPayout?.id === payout.id) {
        setSelectedPayout(null);
      }
      loadData();
    } catch (e: any) {
      showError(e.message || 'Từ chối yêu cầu thất bại');
    } finally {
      setActionBusy(null);
    }
  };

  const formatDateTime = (dateStr?: string) => {
    if (!dateStr) return '—';
    const d = new Date(dateStr);
    return d.toLocaleString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  };

  return (
    <div className="p-4 md:p-6 max-w-6xl mx-auto flex flex-col gap-5">
      <div className="flex items-start justify-between flex-wrap gap-2">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-gray-900">Duyệt yêu cầu rút tiền</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            {meta.totalElements ?? items.length} yêu cầu trên hệ thống
          </p>
        </div>
      </div>

      <div className="flex flex-col sm:flex-row gap-3">
        <form
          onSubmit={(e) => {
            e.preventDefault();
          }}
          className="flex-1 flex items-center gap-2 bg-white border border-gray-200 rounded-xl px-3 py-2.5"
        >
          <Search size={16} className="text-gray-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Tìm mã yêu cầu, nhà cung cấp, số tài khoản..."
            className="bg-transparent text-sm text-gray-900 outline-none flex-1 placeholder:text-gray-400"
          />
          {search && (
            <button
              type="button"
              onClick={() => setSearch('')}
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
      ) : filteredItems.length === 0 ? (
        <div className="text-center py-16 text-gray-400 bg-white rounded-2xl border border-gray-100">
          <Wallet size={48} className="mx-auto mb-3 opacity-50 text-gray-400" />
          <p className="text-sm font-medium">Không tìm thấy yêu cầu rút tiền nào</p>
        </div>
      ) : (
        <>
          <div className="hidden md:block bg-white rounded-2xl border border-gray-100 overflow-hidden shadow-xs">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100 bg-gray-50/50">
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">
                      Mã yêu cầu
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">
                      Nhà cung cấp
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">
                      Số tiền nhận
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">
                      Ngân hàng
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">
                      Thời gian
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">
                      Trạng thái
                    </th>
                    <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wide">
                      Hành động
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {filteredItems.map((p, i) => {
                    const st = STATUS_CONFIG[p.status] || {
                      label: p.status,
                      badgeCls: 'bg-gray-100 text-gray-600',
                      icon: Clock,
                    };
                    const netAmount = Number(p.netAmount ?? p.requestedAmount ?? 0);

                    return (
                      <motion.tr
                        key={p.id}
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        transition={{ delay: i * 0.02 }}
                        className="hover:bg-gray-50/50 transition-colors"
                      >
                        <td className="px-4 py-3.5 text-xs font-semibold text-gray-900 font-mono">
                          {p.payoutCode || `#PO-${p.id}`}
                        </td>

                        <td className="px-4 py-3.5 text-xs text-gray-800 font-medium max-w-[200px] truncate">
                          {p.supplierName}
                        </td>

                        <td className="px-4 py-3.5">
                          <div className="text-sm font-bold text-gray-900">
                            {formatVND(netAmount)}
                          </div>
                          {p.platformFee && p.platformFee > 0 ? (
                            <div className="text-[11px] text-gray-400">
                              Phí: {formatVND(p.platformFee)}
                            </div>
                          ) : null}
                        </td>

                        <td className="px-4 py-3.5 text-xs">
                          <div className="font-medium text-gray-900">
                            {p.bankName || p.bankCode || '—'}
                          </div>
                          <div className="text-gray-500 font-mono mt-0.5">
                            {p.accountNumber || ''}
                          </div>
                          {p.accountHolderName && (
                            <div className="text-[11px] text-gray-400 uppercase truncate max-w-[160px]">
                              {p.accountHolderName}
                            </div>
                          )}
                        </td>

                        <td className="px-4 py-3.5 text-xs text-gray-500 whitespace-nowrap">
                          {formatDateTime(p.createdAt)}
                        </td>

                        <td className="px-4 py-3.5">
                          <span
                            className={`px-2.5 py-1 rounded-full text-xs font-semibold inline-block ${st.badgeCls}`}
                          >
                            {st.label}
                          </span>
                        </td>

                        <td className="px-4 py-3.5 text-center">
                          <div className="flex items-center justify-center gap-1.5">
                            {p.status === 'PENDING' && (
                              <>
                                <button
                                  onClick={() => setApproveConfirmPayout(p)}
                                  disabled={actionBusy === p.id}
                                  className="p-1.5 rounded-lg bg-green-50 text-green-600 hover:bg-green-100 transition-colors cursor-pointer"
                                  title="Duyệt yêu cầu"
                                >
                                  <Check size={16} />
                                </button>
                                <button
                                  onClick={() => {
                                    setRejectReason('');
                                    setRejectModalPayout(p);
                                  }}
                                  disabled={actionBusy === p.id}
                                  className="p-1.5 rounded-lg bg-red-50 text-red-600 hover:bg-red-100 transition-colors cursor-pointer"
                                  title="Từ chối yêu cầu"
                                >
                                  <X size={16} />
                                </button>
                              </>
                            )}
                            <button
                              onClick={() => setSelectedPayout(p)}
                              className="p-1.5 text-gray-500 hover:text-[#2db84c] hover:bg-green-50 rounded-lg transition-colors cursor-pointer"
                              title="Xem chi tiết"
                            >
                              <Eye size={16} />
                            </button>
                          </div>
                        </td>
                      </motion.tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          <div className="md:hidden flex flex-col gap-3">
            {filteredItems.map((p, i) => {
              const st = STATUS_CONFIG[p.status] || {
                label: p.status,
                badgeCls: 'bg-gray-100 text-gray-600',
                icon: Clock,
              };
              const netAmount = Number(p.netAmount ?? p.requestedAmount ?? 0);

              return (
                <motion.div
                  key={p.id}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.03 }}
                  className="bg-white rounded-2xl border border-gray-100 p-4 space-y-3 shadow-xs"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-gray-900 font-mono">
                      {p.payoutCode || `#PO-${p.id}`}
                    </span>
                    <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${st.badgeCls}`}>
                      {st.label}
                    </span>
                  </div>

                  <div>
                    <p className="text-xs text-gray-400">Nhà cung cấp</p>
                    <p className="text-sm font-medium text-gray-900">{p.supplierName}</p>
                  </div>

                  <div className="flex items-center justify-between pt-1">
                    <div>
                      <p className="text-xs text-gray-400">Số tiền nhận</p>
                      <p className="text-base font-bold text-gray-900">{formatVND(netAmount)}</p>
                    </div>
                    <div className="text-right">
                      <p className="text-xs text-gray-400">Ngân hàng</p>
                      <p className="text-xs font-medium text-gray-800">
                        {p.bankName || p.bankCode || '—'}
                      </p>
                      <p className="text-xs text-gray-500 font-mono">{p.accountNumber || ''}</p>
                    </div>
                  </div>

                  <div className="flex items-center justify-between pt-2 border-t border-gray-50 text-xs text-gray-500">
                    <span>{formatDateTime(p.createdAt)}</span>
                    <div className="flex items-center gap-1">
                      {p.status === 'PENDING' && (
                        <>
                          <button
                            onClick={() => setApproveConfirmPayout(p)}
                            disabled={actionBusy === p.id}
                            className="p-1.5 rounded-lg bg-green-50 text-green-600 hover:bg-green-100"
                            title="Duyệt"
                          >
                            <Check size={14} />
                          </button>
                          <button
                            onClick={() => {
                              setRejectReason('');
                              setRejectModalPayout(p);
                            }}
                            disabled={actionBusy === p.id}
                            className="p-1.5 rounded-lg bg-red-50 text-red-600 hover:bg-red-100"
                            title="Từ chối"
                          >
                            <X size={14} />
                          </button>
                        </>
                      )}
                      <button
                        onClick={() => setSelectedPayout(p)}
                        className="p-1.5 text-gray-600 hover:text-[#2db84c] hover:bg-green-50 rounded-lg flex items-center gap-1 font-medium cursor-pointer"
                      >
                        <Eye size={14} /> Chi tiết
                      </button>
                    </div>
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
        {selectedPayout && (
          <div
            className="fixed inset-0 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4 z-50"
            onClick={() => setSelectedPayout(null)}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="bg-white rounded-2xl p-6 w-full max-w-md shadow-xl max-h-[90vh] overflow-y-auto"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center justify-between pb-4 border-b border-gray-100">
                <div className="flex items-center gap-2.5">
                  <div className="w-10 h-10 rounded-xl bg-green-50 flex items-center justify-center text-[#2db84c]">
                    <Wallet size={20} />
                  </div>
                  <div>
                    <h2 className="font-bold text-gray-900 text-base">Chi tiết yêu cầu rút tiền</h2>
                    <p className="text-xs text-gray-400 font-mono">
                      {selectedPayout.payoutCode || `#PO-${selectedPayout.id}`}
                    </p>
                  </div>
                </div>
                <button
                  onClick={() => setSelectedPayout(null)}
                  className="p-1 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100 cursor-pointer"
                >
                  <X size={18} />
                </button>
              </div>

              <div className="my-4 p-3 rounded-xl bg-gray-50/80 flex items-center justify-between">
                <span className="text-xs text-gray-500 font-medium">Trạng thái</span>
                {(() => {
                  const st = STATUS_CONFIG[selectedPayout.status] || {
                    label: selectedPayout.status,
                    badgeCls: 'bg-gray-100 text-gray-600',
                  };
                  return (
                    <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${st.badgeCls}`}>
                      {st.label}
                    </span>
                  );
                })()}
              </div>

              <div className="space-y-3 text-sm">
                <div className="flex items-center justify-between py-1 border-b border-gray-50">
                  <span className="text-gray-500 text-xs">Nhà cung cấp:</span>
                  <span className="font-medium text-gray-900 text-xs text-right max-w-[220px] truncate">
                    {selectedPayout.supplierName}
                  </span>
                </div>

                <div className="flex items-center justify-between py-1 border-b border-gray-50">
                  <span className="text-gray-500 text-xs">Số tiền yêu cầu:</span>
                  <span className="font-medium text-gray-700">
                    {formatVND(Number(selectedPayout.requestedAmount ?? selectedPayout.netAmount ?? 0))}
                  </span>
                </div>

                {selectedPayout.platformFee !== undefined && selectedPayout.platformFee > 0 && (
                  <div className="flex items-center justify-between py-1 border-b border-gray-50">
                    <span className="text-gray-500 text-xs">Phí sàn:</span>
                    <span className="text-gray-500 text-xs">
                      {formatVND(selectedPayout.platformFee)}
                    </span>
                  </div>
                )}

                <div className="flex items-center justify-between py-1 border-b border-gray-50">
                  <span className="text-gray-500 text-xs">Thực nhận:</span>
                  <span className="font-bold text-base text-[#2db84c]">
                    {formatVND(Number(selectedPayout.netAmount ?? selectedPayout.requestedAmount ?? 0))}
                  </span>
                </div>

                <div className="flex items-center justify-between py-1 border-b border-gray-50">
                  <span className="text-gray-500 text-xs">Ngân hàng:</span>
                  <span className="font-medium text-gray-900 text-xs text-right">
                    {selectedPayout.bankName || selectedPayout.bankCode || '—'}
                  </span>
                </div>

                <div className="flex items-center justify-between py-1 border-b border-gray-50">
                  <span className="text-gray-500 text-xs">Số tài khoản:</span>
                  <span className="font-mono font-semibold text-gray-900 text-xs">
                    {selectedPayout.accountNumber || '—'}
                  </span>
                </div>

                {selectedPayout.accountHolderName && (
                  <div className="flex items-center justify-between py-1 border-b border-gray-50">
                    <span className="text-gray-500 text-xs">Chủ tài khoản:</span>
                    <span className="font-medium text-gray-800 text-xs uppercase">
                      {selectedPayout.accountHolderName}
                    </span>
                  </div>
                )}

                <div className="flex items-center justify-between py-1 border-b border-gray-50">
                  <span className="text-gray-500 text-xs">Thời gian tạo:</span>
                  <span className="text-gray-700 text-xs">
                    {formatDateTime(selectedPayout.createdAt)}
                  </span>
                </div>

                {selectedPayout.reviewedAt && (
                  <div className="flex items-center justify-between py-1 border-b border-gray-50">
                    <span className="text-gray-500 text-xs">Thời gian xử lý:</span>
                    <span className="text-gray-700 text-xs">
                      {formatDateTime(selectedPayout.reviewedAt)}
                    </span>
                  </div>
                )}

                {selectedPayout.externalTransactionId && (
                  <div className="flex items-center justify-between py-1 border-b border-gray-50">
                    <span className="text-gray-500 text-xs">Mã giao dịch:</span>
                    <span
                      className="text-gray-700 font-mono text-xs max-w-[180px] truncate"
                      title={selectedPayout.externalTransactionId}
                    >
                      {selectedPayout.externalTransactionId}
                    </span>
                  </div>
                )}

                {(selectedPayout.rejectionReason || selectedPayout.failureReason) && (
                  <div className="p-3 rounded-xl bg-red-50/70 border border-red-100 text-xs mt-2">
                    <span className="font-semibold text-red-700 block mb-0.5">Lý do từ chối:</span>
                    <p className="text-red-600 leading-relaxed">
                      {selectedPayout.rejectionReason || selectedPayout.failureReason}
                    </p>
                  </div>
                )}
              </div>

              {selectedPayout.status === 'PENDING' && (
                <div className="flex gap-2.5 mt-5">
                  <button
                    onClick={() => {
                      const p = selectedPayout;
                      setSelectedPayout(null);
                      setApproveConfirmPayout(p);
                    }}
                    className="flex-1 py-2.5 bg-[#2db84c] hover:bg-green-600 text-white font-medium rounded-xl text-sm transition-colors cursor-pointer flex items-center justify-center gap-1.5 shadow-xs"
                  >
                    <Check size={16} /> Duyệt chuyển khoản
                  </button>
                  <button
                    onClick={() => {
                      const p = selectedPayout;
                      setSelectedPayout(null);
                      setRejectReason('');
                      setRejectModalPayout(p);
                    }}
                    className="px-4 py-2.5 bg-red-50 hover:bg-red-100 text-red-600 font-medium rounded-xl text-sm transition-colors cursor-pointer flex items-center justify-center gap-1.5"
                  >
                    <X size={16} /> Từ chối
                  </button>
                </div>
              )}

              <div className="mt-4">
                <button
                  onClick={() => setSelectedPayout(null)}
                  className="w-full py-2.5 bg-gray-100 hover:bg-gray-200 text-gray-700 font-medium rounded-xl text-sm transition-colors cursor-pointer"
                >
                  Đóng
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {approveConfirmPayout && (
          <div
            className="fixed inset-0 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4 z-50"
            onClick={() => !actionBusy && setApproveConfirmPayout(null)}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="bg-white rounded-2xl p-6 w-full max-w-sm shadow-xl"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="w-12 h-12 rounded-2xl bg-green-50 text-green-600 flex items-center justify-center mx-auto mb-3">
                <CheckCircle2 size={26} />
              </div>
              <h3 className="text-base font-bold text-gray-900 text-center">
                Xác nhận duyệt yêu cầu rút tiền?
              </h3>
              <p className="text-xs text-gray-500 text-center mt-1">
                Hệ thống sẽ ghi nhận chuyển khoản cho nhà cung cấp:
              </p>

              <div className="my-4 p-3.5 rounded-xl bg-gray-50 text-xs space-y-1.5 border border-gray-100">
                <div className="flex justify-between">
                  <span className="text-gray-400">Mã yêu cầu:</span>
                  <span className="font-mono font-medium text-gray-800">
                    {approveConfirmPayout.payoutCode || `#PO-${approveConfirmPayout.id}`}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-400">Nhà cung cấp:</span>
                  <span className="font-medium text-gray-800 max-w-[170px] truncate">
                    {approveConfirmPayout.supplierName}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-400">Số tiền chuyển:</span>
                  <span className="font-bold text-sm text-[#2db84c]">
                    {formatVND(
                      Number(
                        approveConfirmPayout.netAmount ??
                          approveConfirmPayout.requestedAmount ??
                          0
                      )
                    )}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-400">Tài khoản nhận:</span>
                  <span className="text-gray-700 text-right">
                    {approveConfirmPayout.bankName || approveConfirmPayout.bankCode || ''} (
                    <span className="font-mono">{approveConfirmPayout.accountNumber}</span>)
                  </span>
                </div>
              </div>

              <div className="flex gap-2">
                <button
                  type="button"
                  disabled={actionBusy !== null}
                  onClick={() => setApproveConfirmPayout(null)}
                  className="flex-1 py-2.5 bg-gray-100 hover:bg-gray-200 text-gray-700 font-medium rounded-xl text-sm transition-colors cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="button"
                  disabled={actionBusy !== null}
                  onClick={() => handleApprove(approveConfirmPayout)}
                  className="flex-1 py-2.5 bg-[#2db84c] hover:bg-green-600 text-white font-medium rounded-xl text-sm transition-colors cursor-pointer flex items-center justify-center gap-1.5"
                >
                  {actionBusy === approveConfirmPayout.id ? (
                    <Loader2 size={16} className="animate-spin" />
                  ) : (
                    'Duyệt ngay'
                  )}
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {rejectModalPayout && (
          <div
            className="fixed inset-0 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4 z-50"
            onClick={() => !actionBusy && setRejectModalPayout(null)}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="bg-white rounded-2xl p-6 w-full max-w-md shadow-xl"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="w-12 h-12 rounded-2xl bg-red-50 text-red-600 flex items-center justify-center mx-auto mb-3">
                <AlertCircle size={26} />
              </div>
              <h3 className="text-base font-bold text-gray-900 text-center">
                Từ chối yêu cầu rút tiền
              </h3>
              <p className="text-xs text-gray-500 text-center mt-1">
                Yêu cầu: <span className="font-mono font-medium text-gray-800">{rejectModalPayout.payoutCode || `#PO-${rejectModalPayout.id}`}</span> — {rejectModalPayout.supplierName}
              </p>

              <div className="my-4">
                <label className="block text-xs font-semibold text-gray-700 mb-1.5">
                  Lý do từ chối <span className="text-red-500">*</span>
                </label>
                <textarea
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  placeholder="Nhập lý do từ chối (ví dụ: Sai số tài khoản ngân hàng, thông tin thụ hưởng không khớp...)"
                  rows={3}
                  className="w-full text-xs text-gray-900 bg-gray-50 border border-gray-200 rounded-xl p-3 outline-none focus:border-[#2db84c] focus:bg-white transition-all resize-none placeholder:text-gray-400"
                />
              </div>

              <div className="flex gap-2">
                <button
                  type="button"
                  disabled={actionBusy !== null}
                  onClick={() => setRejectModalPayout(null)}
                  className="flex-1 py-2.5 bg-gray-100 hover:bg-gray-200 text-gray-700 font-medium rounded-xl text-sm transition-colors cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="button"
                  disabled={actionBusy !== null || !rejectReason.trim()}
                  onClick={() => handleReject(rejectModalPayout)}
                  className="flex-1 py-2.5 bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white font-medium rounded-xl text-sm transition-colors cursor-pointer flex items-center justify-center gap-1.5"
                >
                  {actionBusy === rejectModalPayout.id ? (
                    <Loader2 size={16} className="animate-spin" />
                  ) : (
                    'Xác nhận từ chối'
                  )}
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}
