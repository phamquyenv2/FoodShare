import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Search, CreditCard, CheckCircle2, Clock, AlertCircle, RefreshCw, X,
  ArrowUpRight, ArrowDownLeft, Copy, Check, ExternalLink, ChevronLeft,
  ChevronRight, Filter, Wallet, ShoppingBag, Banknote
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { apiFetch } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import { formatVND } from '../../utils/format';
import momoLogo from '../../assets/Momo.png';
import zaloLogo from '../../assets/zalo.png';

interface TransactionItem {
  id: number;
  orderId: number;
  orderCode?: string;
  amount: number;
  method: 'MOMO' | 'ZALOPAY' | 'CASH' | string;
  status: 'SUCCESS' | 'PENDING' | 'FAILED' | 'REFUNDED' | 'CANCELLED' | string;
  externalTransactionId?: string;
  refundTransactionId?: string;
  provider?: string;
  paidAt?: string;
  refundedAt?: string;
  paymentUrl?: string;
  supplierName?: string;
}

interface SummaryData {
  totalSpent: number;
  totalRefunded: number;
  totalTransactions: number;
}

const STATUS_TABS = [
  { key: 'all', label: 'Tất cả' },
  { key: 'SUCCESS', label: 'Thành công' },
  { key: 'REFUNDED', label: 'Đã hoàn tiền' },
  { key: 'PENDING', label: 'Chờ xử lý' },
  { key: 'FAILED', label: 'Thất bại' },
];

const STATUS_CONFIG: Record<string, { label: string; badgeCls: string; icon: any }> = {
  SUCCESS: { label: 'Thành công', badgeCls: 'bg-emerald-50 text-emerald-700 border border-emerald-200/80', icon: CheckCircle2 },
  PENDING: { label: 'Chờ xử lý', badgeCls: 'bg-amber-50 text-amber-700 border border-amber-200/80', icon: Clock },
  FAILED: { label: 'Thất bại', badgeCls: 'bg-rose-50 text-rose-700 border border-rose-200/80', icon: AlertCircle },
  CANCELLED: { label: 'Đã hủy', badgeCls: 'bg-gray-100 text-gray-700 border border-gray-200/80', icon: X },
  REFUNDED: { label: 'Đã hoàn tiền', badgeCls: 'bg-purple-50 text-purple-700 border border-purple-200/80', icon: RefreshCw },
};

export default function UserTransactionsPage() {
  const { user } = useAuth();
  const { showError, showSuccess } = useToast();
  const navigate = useNavigate();

  const rolePrefix = user?.role === 'ORGANIZATION' ? '/organization' : '/recipient';

  const [items, setItems] = useState<TransactionItem[]>([]);
  const [summary, setSummary] = useState<SummaryData>({ totalSpent: 0, totalRefunded: 0, totalTransactions: 0 });
  const [loading, setLoading] = useState(true);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [q, setQ] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [status, setStatus] = useState('all');
  const [method, setMethod] = useState('all');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [selectedTx, setSelectedTx] = useState<TransactionItem | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  useEffect(() => {
    const timer = setTimeout(() => {
      setSearchQuery(q.trim());
      setPage(0);
    }, 400);
    return () => clearTimeout(timer);
  }, [q]);

  const fetchSummary = useCallback(async () => {
    setSummaryLoading(true);
    try {
      const res = await apiFetch<SummaryData>('/payments/my-summary');
      setSummary({
        totalSpent: Number(res?.totalSpent || 0),
        totalRefunded: Number(res?.totalRefunded || 0),
        totalTransactions: Number(res?.totalTransactions || 0),
      });
    } catch {
    } finally {
      setSummaryLoading(false);
    }
  }, []);

  const fetchTransactions = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', String(page));
      params.set('size', '12');
      if (searchQuery) params.set('keyword', searchQuery);
      if (status !== 'all') params.set('status', status);
      if (method !== 'all') params.set('method', method);

      const res = await apiFetch<any>(`/payments/my-history?${params.toString()}`);
      setItems(res?.content || []);
      setTotalPages(res?.totalPages || 0);
      setTotalElements(res?.totalElements || 0);
    } catch (err: any) {
      showError(err?.message || 'Không thể tải lịch sử giao dịch');
    } finally {
      setLoading(false);
    }
  }, [page, searchQuery, status, method, showError]);

  useEffect(() => {
    fetchSummary();
  }, [fetchSummary]);

  useEffect(() => {
    fetchTransactions();
  }, [fetchTransactions]);

  const handleCopy = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    showSuccess('Đã sao chép mã giao dịch');
    setTimeout(() => setCopiedId(null), 2000);
  };

  const formatDateTime = (dateStr?: string) => {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  };

  const renderMethodBadge = (m: string) => {
    if (m === 'MOMO') {
      return (
        <span className="inline-flex items-center justify-center gap-1.5 w-[105px] py-1.5 rounded-xl text-xs font-semibold bg-pink-50 text-pink-700 border border-pink-200">
          <img src={momoLogo} alt="MoMo" className="w-3.5 h-3.5 object-contain rounded-xs shrink-0" />
          MoMo
        </span>
      );
    }
    if (m === 'ZALOPAY') {
      return (
        <span className="inline-flex items-center justify-center gap-1.5 w-[105px] py-1.5 rounded-xl text-xs font-semibold bg-blue-50 text-blue-700 border border-blue-200">
          <img src={zaloLogo} alt="ZaloPay" className="w-3.5 h-3.5 object-contain shrink-0" />
          ZaloPay
        </span>
      );
    }
    return (
      <span className="inline-flex items-center justify-center gap-1.5 w-[105px] py-1.5 rounded-xl text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
        <Banknote size={14} className="shrink-0" />
        Tiền mặt
      </span>
    );
  };

  return (
    <div className="p-4 sm:p-6 md:p-8 max-w-6xl mx-auto flex flex-col gap-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-gray-900">
            Lịch sử giao dịch
          </h1>
          <p className="text-sm text-gray-500 mt-1">
            Theo dõi dòng tiền thanh toán đơn hàng và các khoản hoàn trả
          </p>
        </div>

        <button
          onClick={() => {
            fetchSummary();
            fetchTransactions();
          }}
          disabled={loading}
          className="self-start sm:self-auto inline-flex items-center gap-2 px-3.5 py-2 rounded-xl border border-gray-200 bg-white hover:bg-gray-50 text-sm font-medium text-gray-700 shadow-xs transition-colors cursor-pointer disabled:opacity-50"
        >
          <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />
          Làm mới
        </button>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5 sm:gap-4">
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          className="p-4 sm:p-5 rounded-2xl bg-gradient-to-br from-emerald-50/70 to-white border border-emerald-100 shadow-xs relative overflow-hidden"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-emerald-700">Đã thanh toán</span>
            <div className="w-8 h-8 rounded-xl bg-emerald-100/80 flex items-center justify-center text-emerald-700">
              <ArrowUpRight size={18} />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl sm:text-2xl font-bold text-gray-900">
              {summaryLoading ? '...' : formatVND(summary.totalSpent)}
            </div>
            <p className="text-xs text-gray-500 mt-0.5">Tổng tiền đã chi cho các đơn hàng</p>
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.05 }}
          className="p-4 sm:p-5 rounded-2xl bg-gradient-to-br from-purple-50/70 to-white border border-purple-100 shadow-xs relative overflow-hidden"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-purple-700">Đã hoàn lại</span>
            <div className="w-8 h-8 rounded-xl bg-purple-100/80 flex items-center justify-center text-purple-700">
              <ArrowDownLeft size={18} />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl sm:text-2xl font-bold text-purple-900">
              {summaryLoading ? '...' : summary.totalRefunded > 0 ? formatVND(summary.totalRefunded) : '0 đ'}
            </div>
            <p className="text-xs text-gray-500 mt-0.5">Tiền hoàn khi đơn bị hủy/từ chối</p>
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="p-4 sm:p-5 rounded-2xl bg-gradient-to-br from-blue-50/70 to-white border border-blue-100 shadow-xs relative overflow-hidden"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-blue-700">Lượt giao dịch</span>
            <div className="w-8 h-8 rounded-xl bg-blue-100/80 flex items-center justify-center text-blue-700">
              <Wallet size={18} />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl sm:text-2xl font-bold text-gray-900">
              {summaryLoading ? '...' : `${summary.totalTransactions} GD`}
            </div>
            <p className="text-xs text-gray-500 mt-0.5">Tất cả giao dịch phát sinh</p>
          </div>
        </motion.div>
      </div>

      <div className="flex flex-col gap-3.5 bg-white p-4 rounded-2xl border border-gray-100 shadow-xs">
        <div className="flex flex-col sm:flex-row gap-3">
          <div className="relative flex-1">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400" size={17} />
            <input
              type="text"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Tìm theo mã đơn hàng hoặc mã tham chiếu ví..."
              className="w-full pl-10 pr-9 py-2.5 bg-gray-50/70 border border-gray-200/80 rounded-xl text-sm text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/20 focus:border-[#2db84c] transition-all"
            />
            {q && (
              <button
                onClick={() => setQ('')}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 p-0.5"
              >
                <X size={15} />
              </button>
            )}
          </div>

          <div className="flex items-center gap-2">
            <Filter size={16} className="text-gray-400 shrink-0 hidden sm:inline" />
            <select
              value={method}
              onChange={(e) => {
                setMethod(e.target.value);
                setPage(0);
              }}
              className="px-3.5 py-2.5 bg-gray-50/70 border border-gray-200/80 rounded-xl text-sm font-medium text-gray-700 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/20 focus:border-[#2db84c] cursor-pointer"
            >
              <option value="all">Tất cả phương thức</option>
              <option value="MOMO">Ví MoMo</option>
              <option value="ZALOPAY">Ví ZaloPay</option>
              <option value="CASH">Tiền mặt</option>
            </select>
          </div>
        </div>

        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none">
          {STATUS_TABS.map((tab) => {
            const active = status === tab.key;
            return (
              <button
                key={tab.key}
                onClick={() => {
                  setStatus(tab.key);
                  setPage(0);
                }}
                className={`px-3.5 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap cursor-pointer transition-all ${
                  active
                    ? 'bg-[#2db84c] text-white shadow-xs shadow-green-500/20'
                    : 'bg-gray-100/80 text-gray-600 hover:bg-gray-200/60'
                }`}
              >
                {tab.label}
              </button>
            );
          })}
        </div>
      </div>

      {loading ? (
        <div className="py-20 flex flex-col items-center justify-center gap-3">
          <div className="w-9 h-9 border-3 border-green-500 border-t-transparent rounded-full animate-spin" />
          <p className="text-sm text-gray-400">Đang tải lịch sử giao dịch...</p>
        </div>
      ) : items.length === 0 ? (
        <div className="py-16 px-4 bg-white rounded-2xl border border-gray-100 flex flex-col items-center justify-center text-center shadow-xs">
          <div className="w-16 h-16 rounded-2xl bg-green-50 flex items-center justify-center text-[#2db84c] mb-3">
            <CreditCard size={32} />
          </div>
          <h3 className="text-base font-bold text-gray-800">Chưa có giao dịch nào</h3>
          <p className="text-sm text-gray-500 max-w-sm mt-1">
            {q || status !== 'all' || method !== 'all'
              ? 'Không tìm thấy giao dịch nào phù hợp với bộ lọc hiện tại.'
              : 'Các giao dịch thanh toán đơn hàng hoặc tiền hoàn lại sẽ hiển thị tại đây.'}
          </p>
          <button
            onClick={() => navigate(`${rolePrefix}/explore`)}
            className="mt-5 px-5 py-2.5 rounded-xl bg-[#2db84c] hover:bg-[#259e40] text-white text-sm font-semibold shadow-md shadow-green-500/20 transition-all cursor-pointer inline-flex items-center gap-2"
          >
            <ShoppingBag size={16} />
            Khám phá thực phẩm ngay
          </button>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {items.map((tx) => {
            const statusInfo = STATUS_CONFIG[tx.status] || {
              label: tx.status,
              badgeCls: 'bg-gray-100 text-gray-700',
              icon: Clock,
            };
            const StatusIcon = statusInfo.icon;
            const isRefund = tx.status === 'REFUNDED';

            return (
              <motion.div
                key={tx.id}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                className="bg-white rounded-2xl border border-gray-100/90 p-4 sm:p-5 shadow-xs hover:border-gray-200 transition-all flex flex-col sm:grid sm:grid-cols-[minmax(0,1fr)_140px_230px] items-start sm:items-center gap-3.5 sm:gap-4"
              >
                <div className="min-w-0 w-full">
                  <div className="flex items-center gap-2 flex-wrap">
                    <button
                      type="button"
                      onClick={() => navigate(`${rolePrefix}/orders/${tx.orderId}`)}
                      className="text-sm font-bold text-gray-900 hover:text-[#2db84c] hover:underline cursor-pointer transition-colors text-left"
                    >
                      Đơn #{tx.orderCode || tx.orderId}
                    </button>
                    {tx.supplierName && (
                      <span className="text-xs text-gray-500 font-medium truncate">
                        • {tx.supplierName}
                      </span>
                    )}
                  </div>

                  <div className="flex items-center gap-2 mt-1 text-xs text-gray-500 flex-wrap">
                    {tx.externalTransactionId ? (
                      <span className="inline-flex items-center gap-1 font-mono text-gray-600 bg-gray-50 px-2 py-0.5 rounded-md border border-gray-200/50">
                        Mã GD: {tx.externalTransactionId}
                        <button
                          type="button"
                          onClick={() => handleCopy(tx.externalTransactionId!, `ext-${tx.id}`)}
                          className="text-gray-400 hover:text-gray-700 ml-0.5 cursor-pointer"
                          title="Sao chép mã"
                        >
                          {copiedId === `ext-${tx.id}` ? <Check size={12} className="text-green-600" /> : <Copy size={12} />}
                        </button>
                      </span>
                    ) : (
                      <span className="text-gray-400">Giao dịch nội bộ #{tx.id}</span>
                    )}

                    <span className="text-gray-300">•</span>
                    <span>{formatDateTime(tx.paidAt || tx.refundedAt)}</span>
                  </div>
                </div>

                <div className="flex items-center sm:justify-center w-full">
                  {renderMethodBadge(tx.method)}
                </div>

                <div className="w-full flex items-center justify-between sm:justify-end gap-3 sm:gap-4 border-t sm:border-t-0 pt-2.5 sm:pt-0 border-gray-100">
                  <div className="text-left sm:text-right">
                    <div
                      className={`text-base font-bold ${
                        isRefund ? 'text-purple-600' : tx.status === 'SUCCESS' ? 'text-gray-900' : 'text-gray-600'
                      }`}
                    >
                      {isRefund ? `+ ${formatVND(tx.amount)}` : `- ${formatVND(tx.amount)}`}
                    </div>
                    <div className="mt-1">
                      <span
                        className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold ${statusInfo.badgeCls}`}
                      >
                        <StatusIcon size={11} />
                        {statusInfo.label}
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      onClick={() => setSelectedTx(tx)}
                      className="px-3 py-1.5 rounded-xl bg-gray-50 hover:bg-gray-100 text-xs font-semibold text-gray-700 transition-colors cursor-pointer"
                    >
                      Chi tiết
                    </button>
                  </div>
                </div>
              </motion.div>
            );
          })}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between py-2 border-t border-gray-100 text-sm">
          <p className="text-xs text-gray-500">
            Hiển thị <span className="font-semibold">{items.length}</span> / {totalElements} giao dịch
          </p>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setPage((prev) => Math.max(prev - 1, 0))}
              disabled={page === 0}
              className="p-2 rounded-xl border border-gray-200 bg-white text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
            >
              <ChevronLeft size={16} />
            </button>
            <span className="text-xs font-semibold text-gray-700">
              Trang {page + 1} / {totalPages}
            </span>
            <button
              onClick={() => setPage((prev) => Math.min(prev + 1, totalPages - 1))}
              disabled={page >= totalPages - 1}
              className="p-2 rounded-xl border border-gray-200 bg-white text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
            >
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      )}

      <AnimatePresence>
        {selectedTx && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-xs">
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 10 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 10 }}
              className="w-full max-w-md bg-white rounded-3xl p-6 shadow-2xl border border-gray-100 flex flex-col gap-5 relative max-h-[90vh] overflow-y-auto"
            >
              <button
                onClick={() => setSelectedTx(null)}
                className="absolute right-4 top-4 w-8 h-8 rounded-full bg-gray-100 hover:bg-gray-200 text-gray-600 flex items-center justify-center transition-colors cursor-pointer"
              >
                <X size={16} />
              </button>

              <div className="text-center pt-2">
                <div className="inline-flex items-center justify-center w-12 h-12 rounded-2xl bg-green-50 text-[#2db84c] mb-2">
                  <CreditCard size={24} />
                </div>
                <h3 className="text-base font-bold text-gray-900">Chi tiết biên lai giao dịch</h3>
                <div className="mt-2 text-2xl font-extrabold text-gray-900">
                  {selectedTx.status === 'REFUNDED'
                    ? `+ ${formatVND(selectedTx.amount)}`
                    : formatVND(selectedTx.amount)}
                </div>
                <div className="mt-2">
                  {(() => {
                    const s = STATUS_CONFIG[selectedTx.status] || {
                      label: selectedTx.status,
                      badgeCls: 'bg-gray-100 text-gray-700',
                      icon: Clock,
                    };
                    const SIcon = s.icon;
                    return (
                      <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold ${s.badgeCls}`}>
                        <SIcon size={13} />
                        {s.label}
                      </span>
                    );
                  })()}
                </div>
              </div>

              <div className="divide-y divide-gray-100 bg-gray-50/70 rounded-2xl p-3.5 border border-gray-100 text-sm">
                <div className="py-2.5 flex items-center justify-between gap-3">
                  <span className="text-gray-500 text-xs">Phương thức</span>
                  <div>{renderMethodBadge(selectedTx.method)}</div>
                </div>

                <div className="py-2.5 flex items-center justify-between gap-3">
                  <span className="text-gray-500 text-xs">Mã đơn hàng</span>
                  <button
                    onClick={() => {
                      const oid = selectedTx.orderId;
                      setSelectedTx(null);
                      navigate(`${rolePrefix}/orders/${oid}`);
                    }}
                    className="font-semibold text-[#2db84c] hover:underline inline-flex items-center gap-1 cursor-pointer"
                  >
                    #{selectedTx.orderCode || selectedTx.orderId}
                    <ExternalLink size={13} />
                  </button>
                </div>

                {selectedTx.supplierName && (
                  <div className="py-2.5 flex items-center justify-between gap-3">
                    <span className="text-gray-500 text-xs">Nhà cung cấp</span>
                    <span className="font-medium text-gray-800 text-right">{selectedTx.supplierName}</span>
                  </div>
                )}

                {selectedTx.externalTransactionId && (
                  <div className="py-2.5 flex items-center justify-between gap-3">
                    <span className="text-gray-500 text-xs">Mã đối soát ví</span>
                    <div className="flex items-center gap-1 font-mono text-xs font-semibold text-gray-800">
                      <span>{selectedTx.externalTransactionId}</span>
                      <button
                        type="button"
                        onClick={() => handleCopy(selectedTx.externalTransactionId!, 'modal-ext')}
                        className="text-gray-400 hover:text-gray-700 cursor-pointer p-0.5"
                      >
                        {copiedId === 'modal-ext' ? <Check size={13} className="text-green-600" /> : <Copy size={13} />}
                      </button>
                    </div>
                  </div>
                )}

                {selectedTx.refundTransactionId && (
                  <div className="py-2.5 flex items-center justify-between gap-3">
                    <span className="text-gray-500 text-xs">Mã giao dịch hoàn</span>
                    <div className="flex items-center gap-1 font-mono text-xs font-semibold text-purple-700">
                      <span>{selectedTx.refundTransactionId}</span>
                      <button
                        type="button"
                        onClick={() => handleCopy(selectedTx.refundTransactionId!, 'modal-refund')}
                        className="text-gray-400 hover:text-gray-700 cursor-pointer p-0.5"
                      >
                        {copiedId === 'modal-refund' ? <Check size={13} className="text-green-600" /> : <Copy size={13} />}
                      </button>
                    </div>
                  </div>
                )}

                {selectedTx.paidAt && (
                  <div className="py-2.5 flex items-center justify-between gap-3">
                    <span className="text-gray-500 text-xs">Thời gian thanh toán</span>
                    <span className="text-xs font-medium text-gray-800">{formatDateTime(selectedTx.paidAt)}</span>
                  </div>
                )}

                {selectedTx.refundedAt && (
                  <div className="py-2.5 flex items-center justify-between gap-3">
                    <span className="text-gray-500 text-xs">Thời gian hoàn tiền</span>
                    <span className="text-xs font-medium text-purple-700">{formatDateTime(selectedTx.refundedAt)}</span>
                  </div>
                )}
              </div>

              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => {
                    const oid = selectedTx.orderId;
                    setSelectedTx(null);
                    navigate(`${rolePrefix}/orders/${oid}`);
                  }}
                  className="w-full py-3 rounded-xl bg-[#2db84c] hover:bg-[#259e40] text-white text-sm font-semibold shadow-md shadow-green-500/20 transition-all cursor-pointer flex items-center justify-center gap-2"
                >
                  <ShoppingBag size={16} />
                  Xem chi tiết đơn hàng
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}
