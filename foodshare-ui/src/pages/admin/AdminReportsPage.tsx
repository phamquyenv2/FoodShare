import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Search, Loader2, X, Eye, EyeOff, CheckCircle, Flag,
  AlertTriangle, Filter, ChevronRight,
} from 'lucide-react';
import { apiFetch } from '../../services/api';
import { timeAgo } from '../../utils/format';

interface ReportItem {
  id: number;
  reporter: { fullName: string; email: string };
  targetName?: string;
  referenceId: number;
  referenceType: string;
  reportType: string;
  content: string;
  reportStatus: string;
  createdAt: string;
  response?: string;
  evidenceUrl?: string;
}

const STATUS_CONFIG: Record<string, { label: string; color: string; bg: string }> = {
  PENDING:   { label: 'Chờ xử lý',  color: '#d97706', bg: '#fef3c7' },
  REVIEWING: { label: 'Đang xem',   color: '#0891b2', bg: '#cffafe' },
  RESOLVED:  { label: 'Đã xử lý',   color: '#16a34a', bg: '#dcfce7' },
  REJECTED:  { label: 'Đã bỏ qua',  color: '#6b7280', bg: '#f3f4f6' },
};

const TYPE_LABEL: Record<string, string> = {
  ORDER: 'Đơn hàng', FOOD_POST: 'Bài đăng', USER: 'Người dùng', PAYMENT: 'Thanh toán', SYSTEM: 'Hệ thống',
  COMPLAINT: 'Thái độ/Khiếu nại', ISSUE: 'Sự cố', FEEDBACK: 'Góp ý',
  FOOD_QUALITY: 'Chất lượng', FRAUD: 'Gian lận', INAPPROPRIATE: 'Không phù hợp', HYGIENE: 'Vệ sinh', OTHER: 'Khác'
};

const STATUS_TABS = [
  { key: 'all', label: 'Tất cả' },
  { key: 'PENDING', label: 'Chờ xử lý' },
  { key: 'REVIEWING', label: 'Đang xem' },
  { key: 'RESOLVED', label: 'Đã xử lý' },
];

export default function AdminReportsPage() {
  const [reports, setReports] = useState<ReportItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [tab, setTab] = useState('all');
  const [page, setPage] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
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
      console.error('Failed to fetch reports:', err);
    } finally {
      setIsLoading(false);
    }
  }, [page, tab]);

  useEffect(() => { fetchReports(); }, [fetchReports]);

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
      alert(err.message || 'Thao tác thất bại');
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
      console.error('Failed to mark as reviewing:', err);
    }
  };

  const handleHidePost = async (reportId: number, postId: number) => {
    setActionLoading(reportId);
    try {
      await apiFetch(`/admin/food-posts/${postId}/hide`, { method: 'PATCH' });
      await handleAction(reportId, 'resolve');
    } catch (err: any) {
      alert(err.message || 'Thao tác thất bại');
      setActionLoading(null);
    }
  };

  const filtered = reports.filter(r => {
    if (!searchTerm) return true;
    const term = searchTerm.toLowerCase();
    return (
      r.reporter?.fullName.toLowerCase().includes(term) ||
      (r.targetName && r.targetName.toLowerCase().includes(term)) ||
      r.content.toLowerCase().includes(term) ||
      r.referenceId.toString().includes(term)
    );
  });

  return (
    <div className="p-4 md:p-6 max-w-6xl mx-auto flex flex-col gap-5">
      <div className="flex flex-col md:flex-row md:items-start justify-between gap-4">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-gray-900">Khiếu nại & Báo cáo</h1>
          <p className="text-sm text-gray-500 mt-0.5">{totalElements} khiếu nại từ người dùng</p>
        </div>
        
        {/* Search Bar */}
        <div className="relative w-full md:w-72">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text"
            placeholder="Tìm kiếm người báo cáo, nội dung..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-4 py-2 rounded-xl border border-gray-200 text-sm focus:outline-none focus:border-[#2db84c] focus:ring-1 focus:ring-[#2db84c] transition-colors bg-white shadow-sm"
          />
        </div>
      </div>

      {/* Status Tabs */}
      <div className="flex gap-2 overflow-x-auto pb-1">
        {STATUS_TABS.map(t => (
          <button key={t.key} onClick={() => { setTab(t.key); setPage(0); }}
            className={`px-4 py-2 rounded-xl text-sm font-medium whitespace-nowrap cursor-pointer transition-all ${tab === t.key ? 'bg-[#2db84c] text-white shadow-sm' : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'}`}>
            {t.label}
          </button>
        ))}
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
          <div className="hidden md:block bg-white rounded-2xl border border-gray-100 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100 bg-gray-50/50">
                    {['Người khiếu nại', 'Đối tượng', 'Loại', 'Lý do', 'Trạng thái', 'Thời gian', 'Hành động'].map(h => (
                      <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide whitespace-nowrap">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((r, i) => {
                    const st = STATUS_CONFIG[r.reportStatus] || STATUS_CONFIG.PENDING;
                    return (
                      <motion.tr key={r.id} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: i * 0.03 }}
                        className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                        <td className="px-4 py-3.5 text-gray-700 text-xs">{r.reporter?.fullName}</td>
                        <td className="px-4 py-3.5 text-gray-900 font-medium text-xs max-w-[140px] truncate">{r.targetName || `${TYPE_LABEL[r.referenceType] || r.referenceType} #${r.referenceId}`}</td>
                        <td className="px-4 py-3.5">
                          <div className="flex gap-1">
                            <span className="px-2 py-0.5 rounded-full bg-gray-100 text-gray-600 text-[10px] font-medium">{TYPE_LABEL[r.referenceType] || r.referenceType}</span>
                            {r.reportType && <span className="px-2 py-0.5 rounded-full bg-orange-50 text-orange-600 text-[10px] font-medium">{TYPE_LABEL[r.reportType] || r.reportType}</span>}
                          </div>
                        </td>
                        <td className="px-4 py-3.5 text-gray-500 text-xs max-w-[200px]">
                          <p className="truncate cursor-pointer hover:text-gray-900" onClick={() => setDetailReport(r)}>{r.content}</p>
                        </td>
                        <td className="px-4 py-3.5">
                          <span className="px-2.5 py-1 rounded-full text-xs font-semibold" style={{ color: st.color, backgroundColor: st.bg }}>{st.label}</span>
                        </td>
                        <td className="px-4 py-3.5 text-gray-400 text-xs whitespace-nowrap">{timeAgo(r.createdAt)}</td>
                        <td className="px-4 py-3.5">
                          {r.reportStatus === 'PENDING' || r.reportStatus === 'REVIEWING' ? (
                            <div className="flex gap-1.5">
                              {r.reportStatus === 'PENDING' && (
                                <button onClick={() => handleMarkReviewing(r.id)} title="Đánh dấu đang xem"
                                  className="px-2.5 py-1.5 rounded-lg bg-blue-50 text-blue-600 text-xs font-medium cursor-pointer hover:bg-blue-100">
                                  <Eye size={13} />
                                </button>
                              )}
                              <button onClick={() => setConfirmModal({ id: r.id, action: 'resolve' })} title="Đánh dấu đã xử lý"
                                className="px-2.5 py-1.5 rounded-lg bg-green-50 text-green-600 text-xs font-medium cursor-pointer hover:bg-green-100">
                                <CheckCircle size={13} />
                              </button>
                              <button onClick={() => setConfirmModal({ id: r.id, action: 'dismiss' })}
                                className="px-2.5 py-1.5 rounded-lg bg-gray-50 text-gray-500 text-xs font-medium cursor-pointer hover:bg-gray-100">
                                <X size={13} />
                              </button>
                              {r.referenceType === 'FOODPOST' && (
                                <button onClick={() => handleHidePost(r.id, r.referenceId)}
                                  disabled={actionLoading === r.id}
                                  className="px-2.5 py-1.5 rounded-lg bg-red-50 text-red-500 text-xs font-medium cursor-pointer hover:bg-red-100 disabled:opacity-50">
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
              return (
                <motion.div key={r.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.03 }}
                  className="bg-white rounded-2xl border border-gray-100 p-4">
                  <div className="flex items-start justify-between mb-2">
                    <div className="flex-1 min-w-0" onClick={() => setDetailReport(r)}>
                      <p className="font-semibold text-gray-900 text-sm cursor-pointer">{r.targetName || `${TYPE_LABEL[r.referenceType] || r.referenceType} #${r.referenceId}`}</p>
                      <p className="text-xs text-gray-400 mt-0.5">bởi {r.reporter?.fullName} · {timeAgo(r.createdAt)}</p>
                    </div>
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold flex-shrink-0" style={{ color: st.color, backgroundColor: st.bg }}>{st.label}</span>
                  </div>
                  <p className="text-xs text-gray-500 mb-3 line-clamp-2">{r.content}</p>
                  <div className="flex items-center justify-between">
                    <div className="flex gap-1.5">
                      <span className="px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 text-[10px]">{TYPE_LABEL[r.referenceType] || r.referenceType}</span>
                      {r.reportType && <span className="px-2 py-0.5 rounded-full bg-orange-50 text-orange-600 text-[10px]">{TYPE_LABEL[r.reportType] || r.reportType}</span>}
                    </div>
                    {(r.reportStatus === 'PENDING' || r.reportStatus === 'REVIEWING') && (
                      <div className="flex gap-1.5">
                        {r.reportStatus === 'PENDING' && (
                          <button onClick={() => handleMarkReviewing(r.id)} title="Đánh dấu đang xem"
                            className="px-2.5 py-1.5 rounded-lg bg-blue-50 text-blue-600 text-xs cursor-pointer"><Eye size={13} /></button>
                        )}
                        <button onClick={() => setConfirmModal({ id: r.id, action: 'resolve' })} title="Đánh dấu đã xử lý"
                          className="px-2.5 py-1.5 rounded-lg bg-green-50 text-green-600 text-xs cursor-pointer"><CheckCircle size={13} /></button>
                        <button onClick={() => setConfirmModal({ id: r.id, action: 'dismiss' })}
                          className="px-2.5 py-1.5 rounded-lg bg-gray-50 text-gray-500 text-xs cursor-pointer"><X size={13} /></button>
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
        {confirmModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
            <div className="absolute inset-0 bg-black/40" onClick={() => setConfirmModal(null)} />
            <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }}
              className="relative bg-white rounded-2xl p-6 max-w-sm w-full shadow-2xl">
              <h3 className="font-bold text-gray-900 mb-2">
                {confirmModal.action === 'resolve' ? 'Đánh dấu đã giải quyết' : 'Bỏ qua khiếu nại'}
              </h3>
              <p className="text-gray-500 text-sm mb-4">
                {confirmModal.action === 'resolve' ? 'Xác nhận khiếu nại này đã được xử lý thỏa đáng.' : 'Bạn xác nhận bác bỏ/bỏ qua khiếu nại này?'}
              </p>
              <textarea value={adminNote} onChange={e => setAdminNote(e.target.value)}
                placeholder="Ghi chú quản trị (tuỳ chọn)..."
                rows={3}
                className="w-full px-3 py-2 rounded-xl border border-gray-200 text-sm mb-4 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 resize-none" />
              <div className="flex gap-3">
                <button onClick={() => { setConfirmModal(null); setAdminNote(''); }}
                  className="flex-1 py-2.5 rounded-xl border border-gray-200 text-gray-500 text-sm cursor-pointer hover:bg-gray-50">Huỷ</button>
                <button onClick={() => handleAction(confirmModal.id, confirmModal.action)}
                  disabled={actionLoading === confirmModal.id}
                  className="flex-1 py-2.5 rounded-xl bg-[#2db84c] text-white text-sm font-semibold cursor-pointer hover:bg-[#259e40] disabled:opacity-50 flex items-center justify-center gap-2">
                  {actionLoading === confirmModal.id && <Loader2 size={14} className="animate-spin" />}
                  Xác nhận
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Detail Modal */}
      {detailReport && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" onClick={() => setDetailReport(null)}>
          <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
            className="bg-white rounded-2xl p-6 w-full max-w-md max-h-[80vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold text-gray-900">Chi tiết khiếu nại</h3>
              <button onClick={() => setDetailReport(null)} className="text-gray-400 hover:text-gray-600 cursor-pointer"><X size={18} /></button>
            </div>
            <div className="flex flex-col gap-3 text-sm">
              <div className="p-3 rounded-xl bg-gray-50">
                <p className="text-xs text-gray-500 mb-0.5">Người khiếu nại</p>
                <p className="font-medium text-gray-900">{detailReport.reporter?.fullName}</p>
              </div>
              <div className="p-3 rounded-xl bg-gray-50">
                <p className="text-xs text-gray-500 mb-0.5">Đối tượng</p>
                <p className="font-medium text-gray-900">{detailReport.targetName || `#${detailReport.referenceId}`}</p>
                <p className="text-xs text-gray-400">{TYPE_LABEL[detailReport.referenceType] || detailReport.referenceType}</p>
              </div>
              <div className="p-3 rounded-xl bg-gray-50">
                <p className="text-xs text-gray-500 mb-0.5">Nội dung / Lý do</p>
                <p className="text-gray-900 whitespace-pre-wrap">{detailReport.content}</p>
              </div>
              {detailReport.evidenceUrl && (
                <div className="p-3 rounded-xl bg-gray-50">
                  <p className="text-xs text-gray-500 mb-2">Bằng chứng hình ảnh</p>
                  <img src={detailReport.evidenceUrl} alt="evidence" className="w-full rounded-lg" />
                </div>
              )}
              {detailReport.response && (
                <div className="p-3 rounded-xl bg-blue-50">
                  <p className="text-xs text-blue-500 mb-0.5">Ghi chú quản trị</p>
                  <p className="text-blue-800">{detailReport.response}</p>
                </div>
              )}
            </div>

            {detailReport.reportStatus === 'PENDING' && (
              <button 
                onClick={() => handleMarkReviewing(detailReport.id)}
                className="w-full mt-5 py-3 rounded-xl bg-blue-50 text-blue-600 font-semibold text-sm hover:bg-blue-100 transition-colors flex items-center justify-center gap-2"
              >
                <Eye size={16} /> Nhận xử lý khiếu nại này (Đang xem)
              </button>
            )}
          </motion.div>
        </div>
      )}
    </div>
  );
}
