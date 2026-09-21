import { useCallback, useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { 
  Eye, EyeOff, Loader2, RotateCcw, UtensilsCrossed, X, 
  AlertTriangle, Search, MapPin, Clock, Calendar, CheckCircle, ExternalLink 
} from 'lucide-react';
import { apiFetch } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';
import { formatVND, timeAgo } from '../../utils/format';

type Post = {
  id: number;
  name: string;
  postStatus: string;
  postType: string;
  availableQuantity: number;
  totalQuantity?: number;
  unitPrice?: number;
  originalPrice?: number;
  pickupAddress: string;
  supplier?: { name: string; businessProfileId?: number };
  images?: string[];
  description?: string;
  expiresAt?: string;
  createdAt?: string;
  reportCount?: number;
  pendingReportCount?: number;
};

type ReportItem = {
  id: number;
  reporter?: { fullName: string; email: string };
  targetName?: string;
  referenceId: number;
  referenceType: string;
  targetBusinessProfileId?: number;
  reportType: string;
  content: string;
  reportStatus: string;
  createdAt: string;
  response?: string;
};

const TABS = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'AVAILABLE', label: 'Hoạt động' },
  { key: 'HIDDEN', label: 'Đã khoá' },
  { key: 'OUT_OF_STOCK', label: 'Hết hàng' },
  { key: 'REPORTED', label: 'Nhiều khiếu nại' },
];

const REPORT_TYPE_LABEL: Record<string, string> = {
  ORDER: 'Đơn hàng', 
  FOOD_POST: 'Bài đăng', 
  USER: 'Người dùng', 
  COMPLAINT: 'Thái độ', 
  FOOD_QUALITY: 'Chất lượng', 
  FRAUD: 'Gian lận', 
  INAPPROPRIATE: 'Không phù hợp', 
  HYGIENE: 'Vệ sinh', 
  OTHER: 'Khác'
};

const REPORT_STATUS_LABEL: Record<string, { label: string; class: string }> = {
  PENDING:   { label: 'Chờ xử lý', class: 'bg-amber-100 text-amber-800' },
  REVIEWING: { label: 'Đang xem', class: 'bg-blue-100 text-blue-800' },
  RESOLVED:  { label: 'Đã xử lý', class: 'bg-green-100 text-green-800' },
  REJECTED:  { label: 'Bỏ qua', class: 'bg-gray-100 text-gray-600' },
};

export default function AdminFoodPostsPage() {
  const navigate = useNavigate();
  const { showError, showSuccess } = useToast();
  const [posts, setPosts] = useState<Post[]>([]);
  const [allReports, setAllReports] = useState<ReportItem[]>([]);
  const [tab, setTab] = useState('ALL');
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<Post | null>(null);
  const [reportsPost, setReportsPost] = useState<Post | null>(null);
  const [activeImageIdx, setActiveImageIdx] = useState(0);
  const [busy, setBusy] = useState<number | null>(null);
  const [confirmAction, setConfirmAction] = useState<{ id: number; action: 'hide' | 'restore'; name: string } | null>(null);

  const loadPosts = useCallback(async () => {
    setLoading(true);
    try {
      const status = tab === 'ALL' || tab === 'REPORTED' ? '' : `&status=${tab}`;
      const [response, reportResponse] = await Promise.all([
        apiFetch<any>(`/admin/food-posts?page=${page}&size=20${status}`),
        apiFetch<any>('/admin/reports?referenceType=FOOD_POST&page=0&size=200'),
      ]);
      const reports: ReportItem[] = reportResponse.content || [];
      setAllReports(reports);
      const enriched = (response.content || []).map((post: Post) => {
        const related = reports.filter((report: ReportItem) =>
          (report.referenceType === 'FOOD_POST' && report.referenceId === post.id)
          || (report.targetBusinessProfileId != null
            && report.targetBusinessProfileId === post.supplier?.businessProfileId)
        );
        return { 
          ...post, 
          reportCount: related.length, 
          pendingReportCount: related.filter((report: any) => report.reportStatus === 'PENDING' || report.reportStatus === 'REVIEWING').length 
        };
      });
      setPosts(tab === 'REPORTED' ? enriched.filter((post: Post) => (post.reportCount || 0) >= 1) : enriched);
      setTotalPages(response.totalPages || 0);
      setTotal(response.totalElements || 0);
    } catch (error: any) {
      showError(error.message || 'Không thể tải bài đăng');
    } finally {
      setLoading(false);
    }
  }, [page, tab, showError]);

  useEffect(() => { loadPosts(); }, [loadPosts]);
  useEffect(() => {
    const id = Number(new URLSearchParams(window.location.search).get('postId'));
    if (!id) return;
    apiFetch<Post>(`/admin/food-posts/${id}`).then(post => setDetail(post)).catch(() => undefined);
  }, []);
  useEffect(() => { const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 500); return () => window.clearTimeout(timer); }, [search]);

  const filteredPosts = useMemo(() => {
    if (!debouncedSearch) return posts;
    const q = debouncedSearch.toLowerCase();
    return posts.filter(p => 
      p.name?.toLowerCase().includes(q) || 
      p.supplier?.name?.toLowerCase().includes(q) ||
      p.pickupAddress?.toLowerCase().includes(q)
    );
  }, [posts, debouncedSearch]);

  const postReports = useMemo(() => {
    if (!reportsPost) return [];
    return allReports.filter(r => r.referenceType === 'FOOD_POST' && r.referenceId === reportsPost.id);
  }, [reportsPost, allReports]);

  const handleAction = async (id: number, action: 'hide' | 'restore') => {
    setBusy(id);
    try {
      await apiFetch(`/admin/food-posts/${id}/${action}`, { method: 'PATCH' });
      showSuccess(action === 'hide' ? 'Đã khoá bài đăng thành công' : 'Đã mở khoá bài đăng thành công');
      setConfirmAction(null);
      if (detail && detail.id === id) {
        setDetail(prev => prev ? { ...prev, postStatus: action === 'hide' ? 'HIDDEN' : 'AVAILABLE' } : null);
      }
      await loadPosts();
    } catch (error: any) {
      showError(error.message || 'Thao tác thất bại');
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="p-4 md:p-6 max-w-6xl mx-auto flex flex-col gap-5">
      <div className="flex items-start justify-between flex-wrap gap-2">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-gray-900">Quản lý bài đăng thực phẩm</h1>
          <p className="text-sm text-gray-500 mt-0.5">{total} bài đăng trên hệ thống</p>
        </div>
      </div>

      <div className="flex flex-col sm:flex-row gap-3">
        <form onSubmit={(e) => { e.preventDefault(); setPage(0); }} className="flex-1 flex items-center gap-2 bg-white border border-gray-200 rounded-xl px-3 py-2.5">
          <Search size={16} className="text-gray-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Tìm theo tên bài đăng, quán ăn, địa chỉ..."
            className="bg-transparent text-sm text-gray-900 outline-none flex-1 placeholder:text-gray-400"
          />
          {search && (
            <button type="button" onClick={() => { setSearch(''); setPage(0); }} className="text-gray-400 hover:text-gray-600 cursor-pointer">
              <X size={14} />
            </button>
          )}
        </form>
        <div className="flex gap-2 overflow-x-auto">
          {TABS.map((item) => (
            <button 
              key={item.key} 
              onClick={() => { setTab(item.key); setPage(0); }} 
              className={`px-3 py-2 rounded-xl text-xs font-medium whitespace-nowrap cursor-pointer transition-all ${
                tab === item.key 
                  ? 'bg-[#2db84c] text-white' 
                  : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="py-16 flex justify-center"><Loader2 className="animate-spin text-[#2db84c]" size={26} /></div>
      ) : filteredPosts.length === 0 ? (
        <div className="py-16 text-center text-gray-400 bg-white rounded-2xl border border-gray-100">
          <UtensilsCrossed className="mx-auto mb-2 text-gray-300" size={36} />
          <p className="text-sm font-medium">Không tìm thấy bài đăng nào</p>
        </div>
      ) : (
        <>
          <div className="hidden md:block bg-white rounded-2xl border border-gray-200 overflow-hidden">
            <table className="w-full table-fixed text-sm">
              <colgroup>
                <col className="w-[28%]" />
                <col className="w-[18%]" />
                <col className="w-[11%]" />
                <col className="w-[15%]" />
                <col className="w-[14%]" />
                <col className="w-[14%]" />
              </colgroup>
              <thead className="bg-gray-50 text-gray-500">
                <tr className="border-b border-gray-200">
                  <th className="px-4 py-3.5 text-left text-xs font-medium text-gray-500 uppercase tracking-wide whitespace-nowrap">Bài đăng</th>
                  <th className="px-4 py-3.5 text-left text-xs font-medium text-gray-500 uppercase tracking-wide whitespace-nowrap">Nhà cung cấp</th>
                  <th className="px-4 py-3.5 text-left text-xs font-medium text-gray-500 uppercase tracking-wide whitespace-nowrap">Loại hình</th>
                  <th className="px-4 py-3.5 text-left text-xs font-medium text-gray-500 uppercase tracking-wide whitespace-nowrap">Khiếu nại</th>
                  <th className="px-4 py-3.5 text-left text-xs font-medium text-gray-500 uppercase tracking-wide whitespace-nowrap">Trạng thái</th>
                  <th className="px-4 py-3.5 text-center text-xs font-medium text-gray-500 uppercase tracking-wide whitespace-nowrap">Hành động</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filteredPosts.map((post, i) => (
                  <motion.tr 
                    key={post.id} 
                    initial={{ opacity: 0 }} 
                    animate={{ opacity: 1 }} 
                    transition={{ delay: i * 0.015 }}
                    className="hover:bg-gray-50/60 transition-colors"
                  >
                    <td className="px-4 py-3.5">
                      <div className="flex items-center gap-3 min-w-0">
                        {post.images && post.images.length > 0 ? (
                          <img 
                            src={post.images[0]} 
                            alt={post.name} 
                            className="w-10 h-10 rounded-lg object-cover shrink-0 cursor-pointer hover:opacity-90 transition-opacity"
                            onClick={() => { setDetail(post); setActiveImageIdx(0); }}
                          />
                        ) : (
                          <div 
                            className="w-10 h-10 rounded-lg bg-gray-100 flex items-center justify-center shrink-0 text-gray-400 cursor-pointer"
                            onClick={() => { setDetail(post); setActiveImageIdx(0); }}
                          >
                            <UtensilsCrossed size={16} />
                          </div>
                        )}
                        <div className="min-w-0 flex-1">
                          <p 
                            className="font-medium text-gray-900 truncate cursor-pointer hover:text-[#2db84c] transition-colors text-sm"
                            onClick={() => { setDetail(post); setActiveImageIdx(0); }}
                            title={post.name}
                          >
                            {post.name}
                          </p>
                          <p className="text-xs text-gray-400 truncate mt-0.5" title={post.pickupAddress}>
                            Còn {post.availableQuantity} phần · {post.pickupAddress}
                          </p>
                        </div>
                      </div>
                    </td>

                    <td className="px-4 py-3.5 text-gray-700 text-sm truncate" title={post.supplier?.name}>
                      {post.supplier?.name || '—'}
                    </td>

                    <td className="px-4 py-3.5 whitespace-nowrap">
                      {post.postType === 'FREE' ? (
                        <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-700">
                          Miễn phí
                        </span>
                      ) : (
                        <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-blue-100 text-blue-700">
                          {post.unitPrice ? formatVND(post.unitPrice) : 'Có phí'}
                        </span>
                      )}
                    </td>

                    <td className="px-4 py-3.5 whitespace-nowrap">
                      {(post.reportCount || 0) > 0 ? (
                        <button 
                          type="button"
                          onClick={() => setReportsPost(post)}
                          className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold cursor-pointer hover:opacity-80 transition-opacity ${
                            (post.pendingReportCount || 0) > 0
                              ? 'bg-amber-100 text-amber-800'
                              : 'bg-gray-100 text-gray-700'
                          }`}
                          title="Bấm để xem danh sách khiếu nại của bài này"
                        >
                          <AlertTriangle size={12} className={(post.pendingReportCount || 0) > 0 ? "text-amber-700" : "text-gray-500"} />
                          {(post.pendingReportCount || 0) > 0 
                            ? `${post.reportCount} tổng · ${post.pendingReportCount} chờ` 
                            : `${post.reportCount} khiếu nại`}
                        </button>
                      ) : (
                        <span className="text-gray-400 text-xs">0 khiếu nại</span>
                      )}
                    </td>

                    <td className="px-4 py-3.5 whitespace-nowrap">
                      <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${
                        post.postStatus === 'AVAILABLE'
                          ? 'bg-green-100 text-green-700'
                          : post.postStatus === 'HIDDEN'
                          ? 'bg-red-100 text-red-600'
                          : post.postStatus === 'OUT_OF_STOCK'
                          ? 'bg-amber-100 text-amber-700'
                          : 'bg-gray-100 text-gray-600'
                      }`}>
                        {post.postStatus === 'AVAILABLE' ? 'Hoạt động' : post.postStatus === 'HIDDEN' ? 'Đã khoá' : post.postStatus === 'OUT_OF_STOCK' ? 'Hết hàng' : post.postStatus}
                      </span>
                    </td>

                    <td className="px-3 py-2.5 whitespace-nowrap text-center">
                      <div className="flex items-center gap-2 justify-center">
                        <button 
                          title="Xem chi tiết" 
                          onClick={() => { setDetail(post); setActiveImageIdx(0); }} 
                          className="px-3 py-1.5 rounded-lg bg-gray-50 text-gray-500 text-xs font-medium cursor-pointer hover:bg-gray-100 transition-colors"
                        >
                          <Eye size={13} />
                        </button>
                        {post.postStatus === 'AVAILABLE' ? (
                          <button 
                            title="Khoá bài đăng" 
                            disabled={busy === post.id} 
                            onClick={() => setConfirmAction({ id: post.id, action: 'hide', name: post.name })} 
                            className="px-3 py-1.5 rounded-lg text-xs font-medium cursor-pointer transition-colors bg-red-50 text-red-500 hover:bg-red-100 disabled:opacity-50"
                          >
                            {busy === post.id ? <Loader2 size={13} className="animate-spin" /> : 'Khoá'}
                          </button>
                        ) : (
                          <button 
                            title="Mở khoá bài đăng" 
                            disabled={busy === post.id} 
                            onClick={() => setConfirmAction({ id: post.id, action: 'restore', name: post.name })} 
                            className="px-3 py-1.5 rounded-lg text-xs font-medium cursor-pointer transition-colors bg-green-50 text-green-600 hover:bg-green-100 disabled:opacity-50"
                          >
                            {busy === post.id ? <Loader2 size={13} className="animate-spin" /> : 'Mở khoá'}
                          </button>
                        )}
                      </div>
                    </td>
                  </motion.tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="md:hidden flex flex-col gap-2.5">
            {filteredPosts.map((post, i) => (
              <motion.div 
                key={post.id} 
                initial={{ opacity: 0, y: 6 }} 
                animate={{ opacity: 1, y: 0 }} 
                transition={{ delay: i * 0.015 }}
                className="bg-white rounded-xl border border-gray-100 p-3 shadow-xs"
              >
                <div className="flex gap-2.5">
                  {post.images && post.images.length > 0 ? (
                    <img 
                      src={post.images[0]} 
                      alt={post.name} 
                      className="w-12 h-12 rounded-lg object-cover shrink-0 cursor-pointer"
                      onClick={() => { setDetail(post); setActiveImageIdx(0); }}
                    />
                  ) : (
                    <div 
                      className="w-12 h-12 rounded-lg bg-gray-100 flex items-center justify-center shrink-0 text-gray-400 cursor-pointer"
                      onClick={() => { setDetail(post); setActiveImageIdx(0); }}
                    >
                      <UtensilsCrossed size={16} />
                    </div>
                  )}
                  <div className="flex-1 min-w-0">
                    <p 
                      className="font-semibold text-gray-900 text-xs truncate cursor-pointer hover:text-[#2db84c]"
                      onClick={() => { setDetail(post); setActiveImageIdx(0); }}
                    >
                      {post.name}
                    </p>
                    <p className="text-[11px] text-gray-500 truncate mt-0.5">{post.supplier?.name || '—'}</p>
                    <p className="text-[10px] text-gray-400 truncate">{post.pickupAddress}</p>
                  </div>
                </div>

                <div className="flex items-center justify-between mt-2.5 pt-2 border-t border-gray-50 text-xs">
                  <div className="flex items-center gap-1.5">
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                      post.postType === 'FREE' ? 'bg-emerald-50 text-emerald-700' : 'bg-blue-50 text-blue-700'
                    }`}>
                      {post.postType === 'FREE' ? 'Miễn phí' : (post.unitPrice ? formatVND(post.unitPrice) : 'Có phí')}
                    </span>
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                      post.postStatus === 'AVAILABLE' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'
                    }`}>
                      {post.postStatus === 'AVAILABLE' ? 'Hoạt động' : 'Đã khoá'}
                    </span>
                    {(post.reportCount || 0) > 0 && (
                      <button 
                        onClick={() => setReportsPost(post)}
                        className="px-1.5 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200"
                      >
                        ⚠️ {post.reportCount}
                      </button>
                    )}
                  </div>

                  <div className="flex items-center gap-1.5">
                    <button 
                      onClick={() => { setDetail(post); setActiveImageIdx(0); }} 
                      className="px-2.5 py-1 rounded-lg bg-gray-50 text-gray-500 hover:bg-gray-100"
                    >
                      <Eye size={12} />
                    </button>
                    {post.postStatus === 'AVAILABLE' ? (
                      <button 
                        onClick={() => setConfirmAction({ id: post.id, action: 'hide', name: post.name })} 
                        className="px-2.5 py-1 rounded-lg text-xs font-medium bg-red-50 text-red-500 hover:bg-red-100"
                      >
                        Khoá
                      </button>
                    ) : (
                      <button 
                        onClick={() => setConfirmAction({ id: post.id, action: 'restore', name: post.name })} 
                        className="px-2.5 py-1 rounded-lg text-xs font-medium bg-green-50 text-green-600 hover:bg-green-100"
                      >
                        Mở khoá
                      </button>
                    )}
                  </div>
                </div>
              </motion.div>
            ))}
          </div>

          {totalPages > 1 && (
            <div className="flex justify-center gap-1.5 mt-1">
              {Array.from({ length: Math.min(totalPages, 10) }, (_, index) => (
                <button 
                  key={index} 
                  onClick={() => setPage(index)} 
                  className={`w-7 h-7 rounded-lg text-xs font-semibold cursor-pointer transition-all ${
                    page === index ? 'bg-[#2db84c] text-white' : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'
                  }`}
                >
                  {index + 1}
                </button>
              ))}
            </div>
          )}
        </>
      )}

      <AnimatePresence>
        {confirmAction && (
          <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
            <div className="absolute inset-0 bg-black/40" onClick={() => setConfirmAction(null)} />
            <motion.div 
              initial={{ opacity: 0, scale: 0.95 }} 
              animate={{ opacity: 1, scale: 1 }} 
              exit={{ opacity: 0, scale: 0.95 }}
              className="relative bg-white rounded-2xl p-5 max-w-sm w-full shadow-xl"
            >
              <h3 className={`font-bold text-base mb-1.5 ${confirmAction.action === 'hide' ? 'text-red-600' : 'text-green-600'}`}>
                {confirmAction.action === 'hide' ? 'Khoá bài đăng' : 'Mở khoá bài đăng'}
              </h3>
              <p className="text-gray-500 text-xs mb-5 leading-relaxed">
                {confirmAction.action === 'hide'
                  ? `Bạn có chắc chắn muốn khoá bài đăng "${confirmAction.name}" khỏi hệ thống?`
                  : `Bạn có chắc chắn muốn mở khoá bài đăng "${confirmAction.name}"?`}
              </p>
              
              <div className="flex gap-2.5">
                <button 
                  onClick={() => setConfirmAction(null)}
                  className="flex-1 py-2 rounded-xl border border-gray-200 text-gray-500 text-xs font-semibold cursor-pointer hover:bg-gray-50"
                >
                  Hủy
                </button>
                <button 
                  onClick={() => handleAction(confirmAction.id, confirmAction.action)}
                  disabled={busy === confirmAction.id}
                  className={`flex-1 py-2 rounded-xl text-white text-xs font-semibold cursor-pointer disabled:opacity-50 flex items-center justify-center gap-1.5 ${
                    confirmAction.action === 'hide' ? 'bg-red-500 hover:bg-red-600' : 'bg-[#2db84c] hover:bg-[#259e40]'
                  }`}
                >
                  {busy === confirmAction.id ? <Loader2 size={13} className="animate-spin" /> : confirmAction.action === 'hide' ? 'Khoá' : 'Mở khoá'}
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {detail && (
          <div className="fixed inset-0 bg-black/45 backdrop-blur-xs flex items-center justify-center z-50 p-4" onClick={() => setDetail(null)}>
            <motion.div 
              initial={{ opacity: 0, scale: 0.94 }} 
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.94 }}
              className="bg-white rounded-3xl p-5 sm:p-7 w-full max-w-[620px] shadow-2xl relative max-h-[90vh] overflow-y-auto border border-gray-100" 
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-start justify-between pb-2.5 border-b border-gray-100">
                <div className="min-w-0 pr-2">
                  <div className="flex items-center gap-2">
                    <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wide">#{detail.id}</span>
                    <span className={`px-2 py-0.2 rounded-full text-[10px] font-semibold ${
                      detail.postStatus === 'AVAILABLE' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'
                    }`}>
                      {detail.postStatus === 'AVAILABLE' ? 'Hoạt động' : 'Đã khoá'}
                    </span>
                  </div>
                  <h3 className="font-bold text-gray-900 text-base leading-snug truncate mt-0.5" title={detail.name}>{detail.name}</h3>
                </div>
                <button 
                  onClick={() => setDetail(null)} 
                  className="p-1 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100 cursor-pointer shrink-0"
                >
                  <X size={16} />
                </button>
              </div>

              {detail.images && detail.images.length > 0 ? (
                <div className="mt-3 space-y-2">
                  <img 
                    src={detail.images[activeImageIdx] || detail.images[0]} 
                    alt={detail.name} 
                    className="w-full h-64 object-cover rounded-xl border border-gray-100 shadow-2xs" 
                  />
                  {detail.images.length > 1 && (
                    <div className="flex gap-1.5 overflow-x-auto pb-1">
                      {detail.images.map((img, idx) => (
                        <img 
                          key={idx} 
                          src={img} 
                          alt="" 
                          onClick={() => setActiveImageIdx(idx)}
                          className={`w-11 h-11 object-cover rounded-lg border cursor-pointer transition-all ${
                            activeImageIdx === idx ? 'border-[#2db84c] ring-1 ring-[#2db84c]' : 'border-gray-200 opacity-70 hover:opacity-100'
                          }`} 
                        />
                      ))}
                    </div>
                  )}
                </div>
              ) : (
                <div className="mt-3 h-28 bg-gray-50 rounded-xl flex items-center justify-center text-gray-400 text-xs">
                  Chưa có hình ảnh
                </div>
              )}

              {detail.description && (
                <p className="text-xs text-gray-600 mt-2.5 p-2.5 bg-gray-50 rounded-xl leading-relaxed">
                  {detail.description}
                </p>
              )}

              <div className="grid grid-cols-2 gap-2 mt-2.5 text-xs">
                <div className="p-2.5 rounded-xl bg-gray-50">
                  <p className="text-gray-400 text-[10px] font-medium">Nhà cung cấp</p>
                  <p className="font-semibold text-gray-800 truncate mt-0.5">{detail.supplier?.name || '—'}</p>
                </div>
                <div className="p-2.5 rounded-xl bg-gray-50">
                  <p className="text-gray-400 text-[10px] font-medium">Giá & Số lượng</p>
                  <p className="font-semibold text-gray-800 mt-0.5">
                    {detail.postType === 'FREE' ? 'Miễn phí' : (detail.unitPrice ? formatVND(detail.unitPrice) : 'Có phí')} 
                    <span className="text-gray-400 font-normal"> · Còn {detail.availableQuantity}</span>
                  </p>
                </div>
              </div>

              <div className="mt-2 p-2.5 rounded-xl bg-gray-50 text-xs space-y-1.5">
                <div className="flex items-start gap-1.5">
                  <MapPin size={13} className="text-gray-400 shrink-0 mt-0.5" />
                  <span className="text-gray-700 leading-snug">{detail.pickupAddress}</span>
                </div>
                {detail.expiresAt && (
                  <div className="flex items-center gap-1.5">
                    <Clock size={13} className="text-gray-400 shrink-0" />
                    <span className="text-gray-700">Hạn dùng: {new Date(detail.expiresAt).toLocaleString('vi-VN')}</span>
                  </div>
                )}
                {detail.createdAt && (
                  <div className="flex items-center gap-1.5">
                    <Calendar size={13} className="text-gray-400 shrink-0" />
                    <span className="text-gray-500">Đăng: {timeAgo(detail.createdAt)}</span>
                  </div>
                )}
              </div>

              <div 
                className={`mt-2 flex items-center justify-between p-2.5 rounded-xl border text-xs cursor-pointer transition-colors ${
                  (detail.reportCount || 0) > 0 
                    ? 'border-amber-200/80 bg-amber-50/50 hover:bg-amber-100/50' 
                    : 'border-gray-100 bg-white'
                }`}
                onClick={() => {
                  const d = detail;
                  if ((d.reportCount || 0) > 0) {
                    setDetail(null);
                    setReportsPost(d);
                  }
                }}
              >
                <span className="text-gray-500 text-[11px] font-medium">Khiếu nại:</span>
                {(detail.reportCount || 0) > 0 ? (
                  <span className="font-bold text-amber-700 flex items-center gap-1">
                    <AlertTriangle size={12} className="text-amber-600" />
                    {detail.reportCount} khiếu nại ({detail.pendingReportCount || 0} chờ) →
                  </span>
                ) : (
                  <span className="text-emerald-600 font-medium flex items-center gap-1">
                    <CheckCircle size={12} /> Không có khiếu nại
                  </span>
                )}
              </div>

              <div className="flex gap-2 mt-3 pt-3 border-t border-gray-100">
                <button 
                  onClick={() => setDetail(null)}
                  className="flex-1 py-2 rounded-xl border border-gray-200 text-gray-600 text-xs font-medium cursor-pointer hover:bg-gray-50"
                >
                  Đóng
                </button>
                {detail.postStatus === 'AVAILABLE' ? (
                  <button 
                    onClick={() => {
                      const d = detail;
                      setDetail(null);
                      setConfirmAction({ id: d.id, action: 'hide', name: d.name });
                    }}
                    className="flex-1 py-2 rounded-xl bg-red-50 text-red-600 hover:bg-red-100 text-xs font-medium cursor-pointer transition-colors flex items-center justify-center gap-1.5"
                  >
                    <EyeOff size={13} /> Khoá bài đăng
                  </button>
                ) : (
                  <button 
                    onClick={() => {
                      const d = detail;
                      setDetail(null);
                      setConfirmAction({ id: d.id, action: 'restore', name: d.name });
                    }}
                    className="flex-1 py-2 rounded-xl bg-green-50 text-green-700 hover:bg-green-100 text-xs font-medium cursor-pointer transition-colors flex items-center justify-center gap-1.5"
                  >
                    <RotateCcw size={13} /> Mở khoá bài
                  </button>
                )}
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {reportsPost && (
          <div className="fixed inset-0 bg-black/45 backdrop-blur-xs flex items-center justify-center z-50 p-4" onClick={() => setReportsPost(null)}>
            <motion.div 
              initial={{ opacity: 0, scale: 0.94 }} 
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.94 }}
              className="bg-white rounded-3xl p-6 sm:p-7 w-full max-w-[620px] max-h-[88vh] flex flex-col shadow-2xl relative border border-gray-100" 
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-start justify-between pb-3.5 border-b border-gray-100">
                <div className="min-w-0 pr-3">
                  <div className="flex items-center gap-2">
                    <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-amber-100 text-amber-800">
                      {postReports.length} khiếu nại
                    </span>
                    <span className="text-xs text-gray-400 font-medium">#{reportsPost.id}</span>
                  </div>
                  <h3 className="font-bold text-gray-900 text-base sm:text-lg mt-1.5 leading-snug" title={reportsPost.name}>
                    {reportsPost.name}
                  </h3>
                </div>
                <button 
                  onClick={() => setReportsPost(null)} 
                  className="p-1.5 rounded-xl text-gray-400 hover:text-gray-600 hover:bg-gray-100 cursor-pointer shrink-0 transition-colors"
                >
                  <X size={18} />
                </button>
              </div>

              <div className="flex-1 overflow-y-auto py-3.5 space-y-3 my-1 pr-1">
                {postReports.length === 0 ? (
                  <div className="py-12 text-center text-gray-400 text-sm">
                    Chưa có khiếu nại nào cho bài đăng này
                  </div>
                ) : (
                  postReports.map((report) => {
                    const st = REPORT_STATUS_LABEL[report.reportStatus] || { label: report.reportStatus, class: 'bg-gray-100 text-gray-600' };
                    return (
                      <div key={report.id} className="p-4 rounded-2xl bg-gray-50 border border-gray-100 text-xs sm:text-sm space-y-2.5">
                        <div className="flex items-center justify-between gap-2">
                          <div className="min-w-0">
                            <span className="font-semibold text-gray-900 text-sm">
                              {report.reporter?.fullName || 'Người dùng ẩn danh'}
                            </span>
                            {report.reporter?.email && (
                              <span className="text-xs text-gray-400 ml-1.5 hidden sm:inline">
                                ({report.reporter.email})
                              </span>
                            )}
                          </div>
                          <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold border ${st.class} shrink-0`}>
                            {st.label}
                          </span>
                        </div>

                        <div className="flex items-center gap-2 text-xs text-gray-500">
                          <span className="font-medium text-amber-700 bg-amber-50 border border-amber-200/60 px-2 py-0.5 rounded-md">
                            {REPORT_TYPE_LABEL[report.reportType] || report.reportType}
                          </span>
                          <span>·</span>
                          <span className="text-gray-400">{timeAgo(report.createdAt)}</span>
                        </div>

                        <p className="text-gray-800 bg-white p-3 rounded-xl border border-gray-100 leading-relaxed text-xs sm:text-sm shadow-2xs">
                          "{report.content}"
                        </p>

                        {report.response && (
                          <p className="text-emerald-800 bg-emerald-50/70 border border-emerald-100/70 p-3 rounded-xl text-xs sm:text-[13px] leading-relaxed">
                            <span className="font-semibold">Phản hồi:</span> {report.response}
                          </p>
                        )}
                      </div>
                    );
                  })
                )}
              </div>

              <div className="flex items-center gap-2.5 pt-3.5 border-t border-gray-100 mt-2">
                <button 
                  onClick={() => setReportsPost(null)}
                  className="flex-1 py-2.5 rounded-xl border border-gray-200 text-gray-600 text-xs sm:text-sm font-medium cursor-pointer hover:bg-gray-50 transition-colors"
                >
                  Đóng
                </button>
                <button 
                  onClick={() => {
                    navigate('/admin/reports');
                  }}
                  className="py-2.5 px-4 rounded-xl bg-gray-100 hover:bg-gray-200 text-gray-700 text-xs sm:text-sm font-medium cursor-pointer flex items-center gap-1.5 transition-colors"
                  title="Đi đến trang Quản lý Báo cáo & Khiếu nại"
                >
                  Trang khiếu nại <ExternalLink size={14} />
                </button>
                {reportsPost.postStatus === 'AVAILABLE' && (
                  <button 
                    onClick={() => {
                      const p = reportsPost;
                      setReportsPost(null);
                      setConfirmAction({ id: p.id, action: 'hide', name: p.name });
                    }}
                    className="py-2.5 px-4 rounded-xl bg-red-50 hover:bg-red-100 text-red-600 text-xs sm:text-sm font-medium cursor-pointer flex items-center gap-1.5 transition-colors"
                  >
                    <EyeOff size={14} /> Khoá bài này
                  </button>
                )}
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}
