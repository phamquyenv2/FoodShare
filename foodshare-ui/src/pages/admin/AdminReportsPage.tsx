import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Search, Loader2, X, Eye, EyeOff, CheckCircle, Flag, AlertCircle } from 'lucide-react';
import { apiFetch } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';
import { timeAgo, formatVND } from '../../utils/format';

interface ReportItem {
  id: number;
  reporter: { fullName: string; email: string };
  targetName?: string;
  referenceId: number;
  referenceType: string;
  targetBusinessProfileId?: number;
  targetBusinessName?: string;
  reportType: string;
  content: string;
  reportStatus: string;
  createdAt: string;
  response?: string;
  evidenceUrl?: string;
  orderCode?: string;
  amount?: number;
  paymentMethod?: string;
}

const getPaymentBadge = (method?: string) => {
  if (!method) return null;
  const m = method.toUpperCase();
  if (m === 'MOMO') {
    return <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-pink-50 text-pink-700 border border-pink-200">MoMo</span>;
  }
  if (m === 'ZALOPAY') {
    return <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-blue-50 text-blue-700 border border-blue-200">ZaloPay</span>;
  }
  if (m === 'CASH') {
    return <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">Tiền mặt</span>;
  }
  if (m === 'WALLET') {
    return <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-purple-50 text-purple-700 border border-purple-200">Ví</span>;
  }
  return <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium bg-gray-100 text-gray-700">{method}</span>;
};

const STATUS_CONFIG: Record<string, { label: string; color: string; bg: string }> = {
  PENDING:   { label: 'Chờ xử lý',  color: '#d97706', bg: '#fef3c7' },
  REVIEWING: { label: 'Đang xem',   color: '#0891b2', bg: '#cffafe' },
  RESOLVED:  { label: 'Đã xử lý',   color: '#16a34a', bg: '#dcfce7' },
  REJECTED:  { label: 'Đã bỏ qua',  color: '#6b7280', bg: '#f3f4f6' },
};

const TYPE_LABEL: Record<string, string> = {
  ORDER: 'Đơn hàng', FOOD_POST: 'Bài đăng', USER: 'Người dùng', PAYMENT: 'Thanh toán', SYSTEM: 'Hệ thống',
  COMPLAINT: 'Thái độ/Khiếu nại', ISSUE: 'Sự cố', FEEDBACK: 'Góp ý',
  FOOD_QUALITY: 'Chất lượng', FRAUD: 'Gian lận', INAPPROPRIATE: 'Không phù hợp', HYGIENE: 'Vệ sinh',
  REFUND: 'Yêu cầu hoàn tiền', OTHER: 'Khác'
};

const STATUS_TABS = [
  { key: 'all', label: 'Tất cả' },
  { key: 'PENDING', label: 'Chờ xử lý' },
  { key: 'REVIEWING', label: 'Đang xem' },
  { key: 'RESOLVED', label: 'Đã xử lý' },
];

export default function AdminReportsPage() {
  const { showError } = useToast();
  const [reports, setReports] = useState<ReportItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [tab, setTab] = useState('all');
  const [page, setPage] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  const [confirmModal, setConfirmModal] = useState<{ id: number; action: string } | null>(null);
  const [adminNote, setAdminNote] = useState('');
  const [detailReport, setDetailReport] = useState<ReportItem | null>(null);

  const fetchReports = useCallback(async () => {
    setIsLoading(true);
    try {
      let url = `/admin/reports?page=${page}&size=50`; // Increase size to handle local search better
      if (tab !== 'all') url += `&status=${tab}`;
      const res = await apiFetch<any>(url);
      setReports(res.content || []);
      setTotalPages(res.totalPages || 0);
      setTotalElements(res.totalElements || 0);
    } catch (err) {
      showError(err instanceof Error ? err.message : 'Không thể tải danh sách báo cáo');
    } finally {
      setIsLoading(false);
    }
  }, [page, tab, showError]);

  useEffect(() => { fetchReports(); }, [fetchReports]);
  useEffect(() => {
    const id = Number(new URLSearchParams(window.location.search).get('reportId'));
    if (!id) return;
    apiFetch<ReportItem>(`/admin/reports/${id}`).then(setDetailReport).catch(() => undefined);
  }, []);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(searchTerm.trim()), 500);
    return () => window.clearTimeout(timer);
  }, [searchTerm]);

  const handleAction = async (reportId: number, action: string) => {
    setActionLoading(reportId);
    try {
      await apiFetch(`/admin/reports/${reportId}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ 
          reportStatus: action === 'resolve' ? 'RESOLVED' : 'REJECTED', 
          response: adminNote.trim() || undefined 
        }),
      });
      fetchReports();
      setConfirmModal(null);
      setAdminNote('');
    } catch (err: any) {
      showError(err.message || 'Thao tác thất bại');
    } finally {
      setActionLoading(null);
    }
  };

  const handleMarkReviewing = async (reportId: number) => {
    try {
      await apiFetch(`/admin/reports/${reportId}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ reportStatus: 'REVIEWING' }),
      });
      fetchReports();
      if (detailReport && detailReport.id === reportId) {
        setDetailReport({ ...detailReport, reportStatus: 'REVIEWING' });
      }
    } catch (err: any) {
      showError(err.message || 'Không thể cập nhật trạng thái báo cáo');
    }
  };

  const handleHidePost = async (reportId: number, postId: number) => {
    setActionLoading(reportId);
    try {
      await apiFetch(`/admin/food-posts/${postId}/hide`, { method: 'PATCH' });
      await handleAction(reportId, 'resolve');
    } catch (err: any) {
      showError(err.message || 'Thao tác thất bại');
      setActionLoading(null);
    }
  };

  const filtered = reports.filter(r => {
    if (!debouncedSearch) return true;
    const term = debouncedSearch.toLowerCase();
    return (
      r.reporter?.fullName.toLowerCase().includes(term) ||
      (r.targetName && r.targetName.toLowerCase().includes(term)) ||
      (r.targetBusinessName && r.targetBusinessName.toLowerCase().includes(term)) ||
      r.content.toLowerCase().includes(term) ||
      r.referenceId.toString().includes(term)
    );
  });

  return (
    <div className="p-4 md:p-6 max-w-6xl mx-auto flex flex-col gap-5">
      <div className="flex items-start justify-between flex-wrap gap-2">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-gray-900">Khiếu nại & Báo cáo</h1>
          <p className="text-sm text-gray-500 mt-0.5">{totalElements} khiếu nại từ người dùng</p>
        </div>
      </div>

      {/* Search + Filters */}
      <div className="flex flex-col sm:flex-row gap-3">
        <form onSubmit={(e) => { e.preventDefault(); setPage(0); }} className="flex-1 flex items-center gap-2 bg-white border border-gray-200 rounded-xl px-3 py-2.5">
          <Search size={16} className="text-gray-400" />
          <input
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Tìm kiếm người báo cáo, nội dung..."
            className="bg-transparent text-sm text-gray-900 outline-none flex-1 placeholder:text-gray-400"
          />
          {searchTerm && (
            <button type="button" onClick={() => { setSearchTerm(''); setPage(0); }} className="text-gray-400 hover:text-gray-600 cursor-pointer">
              <X size={14} />
            </button>
          )}
        </form>
        <div className="flex gap-2 overflow-x-auto">
          {STATUS_TABS.map(t => (
            <button key={t.key} onClick={() => { setTab(t.key); setPage(0); }}
              className={`px-3 py-2 rounded-xl text-xs font-medium whitespace-nowrap cursor-pointer transition-all ${tab === t.key ? 'bg-[#2db84c] text-white' : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'}`}>
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-16"><Loader2 size={24} className="animate-spin text-[#2db84c]" /></div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <Flag size={48} className="mx-auto mb-3 opacity-50" />
          <p className="text-sm">Không có khiếu nại nào</p>
        </div>
      ) : (
        <>
          {/* Desktop Table */}
          <div className="hidden md:block bg-white rounded-2xl border border-gray-100 overflow-hidden shadow-xs">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100 bg-gray-50/50">
                    {['Người khiếu nại', 'Đối tượng', 'Loại', 'Số tiền hoàn', 'Lý do', 'Trạng thái', 'Thời gian', 'Hành động'].map(h => (
                      <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((r, i) => {
                    const st = STATUS_CONFIG[r.reportStatus] || STATUS_CONFIG.PENDING;
                    const isRefund = r.reportType === 'REFUND' || (r.amount != null && r.amount > 0);
                    return (
                      <motion.tr key={r.id} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: i * 0.02 }}
                        className="border-b border-gray-50 hover:bg-gray-50/60 transition-colors">
                        <td className="px-4 py-3.5 text-gray-700 text-xs font-medium whitespace-nowrap">{r.reporter?.fullName}</td>
                        <td className="px-4 py-3.5 text-xs min-w-[200px] max-w-[280px]">
                          <p className="text-gray-900 font-semibold truncate cursor-pointer hover:text-[#2db84c] transition-colors" onClick={() => setDetailReport(r)}>
                            {r.targetName || `${TYPE_LABEL[r.referenceType] || r.referenceType} #${r.referenceId}`}
                          </p>
                          {r.targetBusinessName && (
                            <p className="text-[11px] text-gray-400 truncate mt-0.5">Nhà cung cấp: <span className="text-gray-600 font-medium">{r.targetBusinessName}</span></p>
                          )}
                        </td>
                        <td className="px-4 py-3.5">
                          <div className="flex flex-wrap gap-1">
                            <span className="px-2 py-0.5 rounded-full bg-gray-100 text-gray-600 text-[10px] font-medium">{TYPE_LABEL[r.referenceType] || r.referenceType}</span>
                            {r.reportType && (
                              <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                                r.reportType === 'REFUND'
                                  ? 'bg-purple-100 text-purple-700 font-semibold border border-purple-200'
                                  : 'bg-orange-50 text-orange-600'
                              }`}>
                                {TYPE_LABEL[r.reportType] || r.reportType}
                              </span>
                            )}
                          </div>
                        </td>
                        <td className="px-4 py-3.5 whitespace-nowrap">
                          {r.amount != null && r.amount > 0 ? (
                            <div className="flex flex-col gap-0.5">
                              <span className="font-bold text-xs text-purple-700">{formatVND(r.amount)}</span>
                              {r.paymentMethod && <div>{getPaymentBadge(r.paymentMethod)}</div>}
                            </div>
                          ) : r.referenceType === 'ORDER' ? (
                            <span className="text-gray-400 text-xs">0đ (Miễn phí)</span>
                          ) : (
                            <span className="text-gray-400 text-xs">—</span>
                          )}
                        </td>
                        <td className="px-4 py-3.5 text-gray-500 text-xs max-w-[220px]">
                          <p className="truncate cursor-pointer hover:text-gray-900" onClick={() => setDetailReport(r)}>{r.content}</p>
                        </td>
                        <td className="px-4 py-3.5 whitespace-nowrap">
                          <span className="px-2.5 py-1 rounded-full text-xs font-semibold" style={{ color: st.color, backgroundColor: st.bg }}>{st.label}</span>
                        </td>
                        <td className="px-4 py-3.5 text-gray-400 text-xs whitespace-nowrap">{timeAgo(r.createdAt)}</td>
                        <td className="px-4 py-3.5 whitespace-nowrap">
                          {r.reportStatus === 'PENDING' || r.reportStatus === 'REVIEWING' ? (
                            <div className="flex gap-1.5">
                              {r.reportStatus === 'PENDING' && (
                                <button onClick={() => handleMarkReviewing(r.id)} title="Đánh dấu đang xem"
                                  className="px-2.5 py-1.5 rounded-lg bg-blue-50 text-blue-600 text-xs font-medium cursor-pointer hover:bg-blue-100 transition-colors">
                                  <Eye size={13} />
                                </button>
                              )}
                              <button onClick={() => setConfirmModal({ id: r.id, action: 'resolve' })} 
                                title={isRefund ? 'Duyệt hoàn tiền' : 'Đánh dấu đã xử lý'}
                                className={`px-2.5 py-1.5 rounded-lg text-xs font-medium cursor-pointer transition-colors ${
                                  isRefund ? 'bg-purple-50 text-purple-700 hover:bg-purple-100' : 'bg-green-50 text-green-600 hover:bg-green-100'
                                }`}>
                                <CheckCircle size={13} />
                              </button>
                              <button onClick={() => setConfirmModal({ id: r.id, action: 'dismiss' })} title="Bác bỏ"
                                className="px-2.5 py-1.5 rounded-lg bg-gray-50 text-gray-500 text-xs font-medium cursor-pointer hover:bg-gray-100 transition-colors">
                                <X size={13} />
                              </button>
                              {r.referenceType === 'FOODPOST' && (
                                <button onClick={() => handleHidePost(r.id, r.referenceId)}
                                  disabled={actionLoading === r.id}
                                  className="px-2.5 py-1.5 rounded-lg bg-red-50 text-red-500 text-xs font-medium cursor-pointer hover:bg-red-100 disabled:opacity-50 transition-colors">
                                  {actionLoading === r.id ? <Loader2 size={13} className="animate-spin" /> : <EyeOff size={13} />}
                                </button>
                              )}
                            </div>
                          ) : (
                            <span className="text-xs text-gray-400">—</span>
                          )}
                        </td>
                      </motion.tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Mobile Cards */}
          <div className="md:hidden flex flex-col gap-3">
            {filtered.map((r, i) => {
              const st = STATUS_CONFIG[r.reportStatus] || STATUS_CONFIG.PENDING;
              const isRefund = r.reportType === 'REFUND' || (r.amount != null && r.amount > 0);
              return (
                <motion.div key={r.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.03 }}
                  className="bg-white rounded-2xl border border-gray-100 p-4 shadow-xs">
                  <div className="flex items-start justify-between mb-2">
                    <div className="flex-1 min-w-0 cursor-pointer" onClick={() => setDetailReport(r)}>
                      <p className="font-semibold text-gray-900 text-sm">{r.targetName || `${TYPE_LABEL[r.referenceType] || r.referenceType} #${r.referenceId}`}</p>
                      {r.targetBusinessName && (
                        <p className="text-xs text-gray-500 mt-0.5">Cửa hàng: <span className="text-gray-700 font-medium">{r.targetBusinessName}</span></p>
                      )}
                      <p className="text-xs text-gray-400 mt-0.5">bởi {r.reporter?.fullName} · {timeAgo(r.createdAt)}</p>
                    </div>
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold flex-shrink-0" style={{ color: st.color, backgroundColor: st.bg }}>{st.label}</span>
                  </div>

                  {r.amount != null && r.amount > 0 && (
                    <div className="mb-2.5 px-3 py-2 rounded-xl bg-purple-50 border border-purple-100 flex items-center justify-between text-xs">
                      <span className="text-purple-700 font-medium">Số tiền hoàn:</span>
                      <div className="flex items-center gap-1.5">
                        <span className="font-bold text-purple-900">{formatVND(r.amount)}</span>
                        {getPaymentBadge(r.paymentMethod)}
                      </div>
                    </div>
                  )}

                  <p className="text-xs text-gray-500 mb-3 line-clamp-2">{r.content}</p>
                  <div className="flex items-center justify-between">
                    <div className="flex flex-wrap gap-1.5">
                      <span className="px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 text-[10px]">{TYPE_LABEL[r.referenceType] || r.referenceType}</span>
                      {r.reportType && (
                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                          r.reportType === 'REFUND' ? 'bg-purple-100 text-purple-700 font-semibold border border-purple-200' : 'bg-orange-50 text-orange-600'
                        }`}>
                          {TYPE_LABEL[r.reportType] || r.reportType}
                        </span>
                      )}
                    </div>
                    {(r.reportStatus === 'PENDING' || r.reportStatus === 'REVIEWING') && (
                      <div className="flex gap-1.5">
                        {r.reportStatus === 'PENDING' && (
                          <button onClick={() => handleMarkReviewing(r.id)} title="Đánh dấu đang xem"
                            className="px-2.5 py-1.5 rounded-lg bg-blue-50 text-blue-600 text-xs cursor-pointer"><Eye size={13} /></button>
                        )}
                        <button onClick={() => setConfirmModal({ id: r.id, action: 'resolve' })} 
                          title={isRefund ? 'Duyệt hoàn tiền' : 'Đánh dấu đã xử lý'}
                          className={`px-2.5 py-1.5 rounded-lg text-xs cursor-pointer ${
                            isRefund ? 'bg-purple-50 text-purple-700' : 'bg-green-50 text-green-600'
                          }`}><CheckCircle size={13} /></button>
                        <button onClick={() => setConfirmModal({ id: r.id, action: 'dismiss' })} title="Bác bỏ"
                          className="px-2.5 py-1.5 rounded-lg bg-gray-50 text-gray-500 text-xs cursor-pointer hover:bg-gray-100"><X size={13} /></button>
                      </div>
                    )}
                  </div>
                </motion.div>
              );
            })}
          </div>

          {totalPages > 1 && !searchTerm && (
            <div className="flex justify-center gap-2 mt-2">
              {Array.from({ length: Math.min(totalPages, 10) }, (_, i) => (
                <button key={i} onClick={() => setPage(i)}
                  className={`w-9 h-9 rounded-xl text-sm font-medium cursor-pointer transition-all ${page === i ? 'bg-[#2db84c] text-white' : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'}`}>{i + 1}</button>
              ))}
            </div>
          )}
        </>
      )}

      {/* Action Confirm Modal */}
      <AnimatePresence>
        {confirmModal && (() => {
          const selectedReport = reports.find(r => r.id === confirmModal.id);
          const isRefund = selectedReport?.reportType === 'REFUND' || (selectedReport?.amount != null && selectedReport.amount > 0);

          return (
            <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
              <div className="absolute inset-0 bg-black/40 backdrop-blur-xs" onClick={() => setConfirmModal(null)} />
              <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }}
                className="relative bg-white rounded-2xl p-6 max-w-md w-full shadow-2xl">
                <h3 className="font-bold text-gray-900 text-base mb-2">
                  {confirmModal.action === 'resolve'
                    ? (isRefund ? 'Xác nhận duyệt hoàn tiền' : 'Đánh dấu đã giải quyết')
                    : 'Bác bỏ / Bỏ qua khiếu nại'}
                </h3>
                
                {confirmModal.action === 'resolve' ? (
                  isRefund ? (
                    <div className="space-y-3 mb-4">
                      <p className="text-gray-600 text-sm">
                        Bạn đang duyệt yêu cầu hoàn tiền cho <span className="font-semibold text-gray-900">{selectedReport?.targetName || `#${selectedReport?.referenceId}`}</span> của người dùng <span className="font-semibold text-gray-900">{selectedReport?.reporter?.fullName}</span>.
                      </p>
                      {selectedReport?.amount != null && selectedReport.amount > 0 && (
                        <div className="p-3.5 rounded-xl bg-purple-50 border border-purple-200 flex items-center justify-between">
                          <span className="text-purple-700 font-medium text-xs">Số tiền hoàn:</span>
                          <div className="flex items-center gap-2">
                            <span className="text-purple-900 font-black text-lg">{formatVND(selectedReport.amount)}</span>
                            {getPaymentBadge(selectedReport.paymentMethod)}
                          </div>
                        </div>
                      )}
                      <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-800 flex items-start gap-2">
                        <AlertCircle size={15} className="text-amber-600 flex-shrink-0 mt-0.5" />
                        <span>Hệ thống backend sẽ tự động gọi hoàn tiền trực tiếp qua cổng thanh toán ban đầu ({selectedReport?.paymentMethod || 'MoMo/ZaloPay'}) và điều chỉnh hoàn tiền trong ví nhà cung cấp.</span>
                      </div>
                    </div>
                  ) : (
                    <p className="text-gray-500 text-sm mb-4">
                      Xác nhận khiếu nại này đã được xử lý thỏa đáng.
                    </p>
                  )
                ) : (
                  <p className="text-gray-500 text-sm mb-4">
                    Bạn xác nhận bác bỏ hoặc bỏ qua khiếu nại này? Trạng thái sẽ chuyển thành "Đã bỏ qua".
                  </p>
                )}

                <textarea value={adminNote} onChange={e => setAdminNote(e.target.value)}
                  placeholder="Ghi chú quản trị / phản hồi cho người dùng (tuỳ chọn)..."
                  rows={3}
                  className="w-full px-3 py-2.5 rounded-xl border border-gray-200 text-sm mb-4 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 resize-none placeholder:text-gray-400" />
                <div className="flex gap-3">
                  <button onClick={() => { setConfirmModal(null); setAdminNote(''); }}
                    className="flex-1 py-2.5 rounded-xl border border-gray-200 text-gray-600 text-sm font-medium cursor-pointer hover:bg-gray-50">Huỷ</button>
                  <button onClick={() => handleAction(confirmModal.id, confirmModal.action)}
                    disabled={actionLoading === confirmModal.id}
                    className={`flex-1 py-2.5 rounded-xl text-white text-sm font-semibold cursor-pointer disabled:opacity-50 flex items-center justify-center gap-2 shadow-xs transition-colors ${
                      confirmModal.action === 'resolve'
                        ? (isRefund ? 'bg-purple-600 hover:bg-purple-700' : 'bg-[#2db84c] hover:bg-[#259e40]')
                        : 'bg-red-600 hover:bg-red-700'
                    }`}>
                    {actionLoading === confirmModal.id && <Loader2 size={14} className="animate-spin" />}
                    {confirmModal.action === 'resolve' ? (isRefund ? 'Duyệt hoàn tiền' : 'Xác nhận xử lý') : 'Bác bỏ khiếu nại'}
                  </button>
                </div>
              </motion.div>
            </div>
          );
        })()}
      </AnimatePresence>

      {/* Detail Modal */}
      {detailReport && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-xs flex items-center justify-center z-50 p-4" onClick={() => setDetailReport(null)}>
          <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
            className="bg-white rounded-2xl p-6 w-full max-w-lg max-h-[85vh] overflow-y-auto shadow-2xl" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4 pb-2 border-b border-gray-100">
              <div className="flex items-center gap-2">
                <h3 className="font-bold text-gray-900 text-base">Chi tiết khiếu nại #{detailReport.id}</h3>
                {detailReport.reportType === 'REFUND' && (
                  <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-purple-100 text-purple-700 border border-purple-200">Hoàn tiền</span>
                )}
              </div>
              <button onClick={() => setDetailReport(null)} className="text-gray-400 hover:text-gray-600 cursor-pointer"><X size={18} /></button>
            </div>

            <div className="flex flex-col gap-3 text-sm">
              {detailReport.amount != null && detailReport.amount > 0 && (
                <div className="p-4 rounded-xl bg-purple-50/80 border border-purple-200 flex items-center justify-between">
                  <div>
                    <p className="text-xs text-purple-700 font-semibold uppercase tracking-wider">Số tiền yêu cầu hoàn</p>
                    <p className="text-2xl font-black text-purple-950 mt-1">{formatVND(detailReport.amount)}</p>
                  </div>
                  {detailReport.paymentMethod && (
                    <div className="text-right">
                      <p className="text-xs text-purple-700 font-medium mb-1">Cổng thanh toán</p>
                      {getPaymentBadge(detailReport.paymentMethod)}
                    </div>
                  )}
                </div>
              )}

              <div className="grid grid-cols-2 gap-3">
                <div className="p-3 rounded-xl bg-gray-50">
                  <p className="text-xs text-gray-500 mb-0.5">Người khiếu nại</p>
                  <p className="font-medium text-gray-900">{detailReport.reporter?.fullName}</p>
                  <p className="text-xs text-gray-400 truncate">{detailReport.reporter?.email}</p>
                </div>
                <div className="p-3 rounded-xl bg-gray-50">
                  <p className="text-xs text-gray-500 mb-0.5">Thời gian gửi</p>
                  <p className="font-medium text-gray-900">{timeAgo(detailReport.createdAt)}</p>
                  <p className="text-[11px] text-gray-400">{new Date(detailReport.createdAt).toLocaleString('vi-VN')}</p>
                </div>
              </div>

              <div className="p-3 rounded-xl bg-gray-50">
                <p className="text-xs text-gray-500 mb-0.5">Đối tượng khiếu nại</p>
                <p className="font-medium text-gray-900">{detailReport.targetName || `#${detailReport.referenceId}`}</p>
                {detailReport.targetBusinessName && (
                  <p className="text-xs text-gray-600 mt-0.5">Nhà cung cấp: <span className="font-semibold text-gray-800">{detailReport.targetBusinessName}</span></p>
                )}
                <div className="flex gap-2 mt-1.5">
                  <span className="px-2 py-0.5 rounded-full bg-gray-200 text-gray-700 text-[10px] font-medium">{TYPE_LABEL[detailReport.referenceType] || detailReport.referenceType}</span>
                  {detailReport.reportType && (
                    <span className="px-2 py-0.5 rounded-full bg-orange-100 text-orange-700 text-[10px] font-medium">{TYPE_LABEL[detailReport.reportType] || detailReport.reportType}</span>
                  )}
                </div>
              </div>

              <div className="p-3 rounded-xl bg-gray-50">
                <p className="text-xs text-gray-500 mb-1">Nội dung / Lý do</p>
                <p className="text-gray-900 whitespace-pre-wrap leading-relaxed">{detailReport.content}</p>
              </div>

              {detailReport.evidenceUrl && (
                <div className="p-3 rounded-xl bg-gray-50">
                  <p className="text-xs text-gray-500 mb-2">Bằng chứng hình ảnh đính kèm</p>
                  <a href={detailReport.evidenceUrl} target="_blank" rel="noopener noreferrer">
                    <img src={detailReport.evidenceUrl} alt="evidence" className="w-full rounded-lg max-h-64 object-cover border border-gray-200 hover:opacity-95 transition-opacity" />
                  </a>
                </div>
              )}

              {detailReport.response && (
                <div className="p-3 rounded-xl bg-blue-50 border border-blue-100">
                  <p className="text-xs text-blue-600 font-semibold mb-0.5">Phản hồi của quản trị viên</p>
                  <p className="text-blue-900 whitespace-pre-wrap">{detailReport.response}</p>
                </div>
              )}
            </div>

            {(detailReport.reportStatus === 'PENDING' || detailReport.reportStatus === 'REVIEWING') && (
              <div className="mt-5 pt-3 border-t border-gray-100 flex flex-col sm:flex-row gap-2">
                {detailReport.reportStatus === 'PENDING' && (
                  <button 
                    onClick={() => handleMarkReviewing(detailReport.id)}
                    className="flex-1 py-2.5 rounded-xl bg-blue-50 text-blue-600 font-semibold text-xs hover:bg-blue-100 transition-colors flex items-center justify-center gap-1.5 cursor-pointer"
                  >
                    <Eye size={14} /> Nhận xem xét
                  </button>
                )}
                <button 
                  onClick={() => {
                    const id = detailReport.id;
                    setDetailReport(null);
                    setConfirmModal({ id, action: 'resolve' });
                  }}
                  className={`flex-1 py-2.5 rounded-xl text-white font-semibold text-xs transition-colors flex items-center justify-center gap-1.5 cursor-pointer ${
                    detailReport.reportType === 'REFUND' || (detailReport.amount != null && detailReport.amount > 0)
                      ? 'bg-purple-600 hover:bg-purple-700'
                      : 'bg-green-600 hover:bg-green-700'
                  }`}
                >
                  <CheckCircle size={14} /> {detailReport.reportType === 'REFUND' ? 'Duyệt hoàn tiền' : 'Xử lý xong'}
                </button>
                <button 
                  onClick={() => {
                    const id = detailReport.id;
                    setDetailReport(null);
                    setConfirmModal({ id, action: 'dismiss' });
                  }}
                  className="px-4 py-2.5 rounded-xl border border-gray-200 text-gray-600 font-semibold text-xs hover:bg-gray-50 transition-colors cursor-pointer"
                >
                  Bác bỏ
                </button>
              </div>
            )}
          </motion.div>
        </div>
      )}
    </div>
  );
}
