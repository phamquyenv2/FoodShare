import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Loader2, Flag, ArrowLeft, X } from 'lucide-react';
import { apiFetch } from '../../services/api';
import { timeAgo } from '../../utils/format';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';

interface ReportItem {
  id: number;
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

export default function MyReportsPage() {
  const { user } = useAuth();
  const [reports, setReports] = useState<ReportItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [detailReport, setDetailReport] = useState<ReportItem | null>(null);
  const navigate = useNavigate();

  const fetchReports = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await apiFetch<any>('/reports?size=50');
      let data = res.content || [];
      
      // Filter out non-system reports for supplier
      if (user?.role === 'SUPPLIER') {
        data = data.filter((r: ReportItem) => r.referenceType === 'SYSTEM' || r.reportType === 'SYSTEM_ERROR');
      }
      
      setReports(data);
    } catch (err) {
      console.error('Failed to fetch reports:', err);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => { fetchReports(); }, [fetchReports]);

  return (
    <div className="p-4 md:p-6 max-w-4xl mx-auto flex flex-col gap-5">
      <div className="flex items-center gap-3">
        <button onClick={() => navigate(-1)} className="p-2 rounded-full hover:bg-gray-100 transition-colors cursor-pointer">
          <ArrowLeft size={20} className="text-gray-600" />
        </button>
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-gray-900">Lịch sử hỗ trợ & Khiếu nại</h1>
          <p className="text-sm text-gray-500 mt-0.5">Theo dõi trạng thái các báo cáo của bạn</p>
        </div>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-16"><Loader2 size={24} className="animate-spin text-[#2db84c]" /></div>
      ) : reports.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <Flag size={48} className="mx-auto mb-3 opacity-50" />
          <p className="text-sm">Bạn chưa gửi khiếu nại nào</p>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {reports.map((r, i) => {
            const st = STATUS_CONFIG[r.reportStatus] || STATUS_CONFIG.PENDING;
            return (
              <motion.div key={r.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.03 }}
                onClick={() => setDetailReport(r)}
                className="bg-white rounded-2xl border border-gray-100 p-4 cursor-pointer hover:border-[#2db84c]/30 hover:shadow-md transition-all">
                <div className="flex items-start justify-between mb-2">
                  <div className="flex-1 min-w-0">
                    <p className="font-semibold text-gray-900 text-sm">
                      {r.targetName || `${TYPE_LABEL[r.referenceType] || r.referenceType} #${r.referenceId}`}
                    </p>
                    <p className="text-xs text-gray-400 mt-0.5">{timeAgo(r.createdAt)}</p>
                  </div>
                  <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold flex-shrink-0" style={{ color: st.color, backgroundColor: st.bg }}>
                    {st.label}
                  </span>
                </div>
                <p className="text-xs text-gray-500 mb-3 line-clamp-2">{r.content}</p>
                <div className="flex items-center gap-1.5">
                  <span className="px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 text-[10px]">{TYPE_LABEL[r.referenceType] || r.referenceType}</span>
                  {r.reportType && <span className="px-2 py-0.5 rounded-full bg-orange-50 text-orange-600 text-[10px]">{TYPE_LABEL[r.reportType] || r.reportType}</span>}
                </div>
              </motion.div>
            );
          })}
        </div>
      )}

      {/* Detail Modal */}
      <AnimatePresence>
        {detailReport && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <div className="absolute inset-0 bg-black/40" onClick={() => setDetailReport(null)} />
            <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }}
              className="relative bg-white rounded-2xl p-6 w-full max-w-md max-h-[80vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-semibold text-gray-900">Chi tiết khiếu nại</h3>
                <button onClick={() => setDetailReport(null)} className="text-gray-400 hover:text-gray-600 cursor-pointer"><X size={18} /></button>
              </div>
              
              <div className="flex flex-col gap-3 text-sm">
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
                
                <div className="p-3 rounded-xl bg-blue-50 border border-blue-100">
                  <p className="text-xs font-semibold text-blue-600 mb-1 flex items-center gap-1">Phản hồi từ Admin</p>
                  {detailReport.response ? (
                    <p className="text-blue-900 whitespace-pre-wrap">{detailReport.response}</p>
                  ) : (
                    <p className="text-blue-500/70 italic text-xs">Quản trị viên chưa có phản hồi cho khiếu nại này.</p>
                  )}
                </div>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}
