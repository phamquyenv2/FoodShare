import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Loader2, X, CheckCircle, FileText, Flag, Eye } from 'lucide-react';
import { apiFetch } from '../../services/api';
import { timeAgo } from '../../utils/format';

interface BusinessProfile {
  name: string;
  description: string;
  taxCode: string;
  verificationStatus: string;
  profileType: string;
  organizationType?: string;
  supplierType?: string;
  licenseUrls?: string[];
}

interface SupplierUser {
  id: number;
  fullName: string;
  email: string;
  phone: string;
  createdAt: string;
  businessProfile?: BusinessProfile;
}

const STATUS_TABS = [
  { key: 'ALL', label: 'Tất cả', color: '#4b5563', bg: '#f3f4f6' },
  { key: 'UNVERIFIED', label: 'Chờ duyệt', color: '#d97706', bg: '#fef3c7' },
  { key: 'VERIFIED', label: 'Đã duyệt', color: '#16a34a', bg: '#dcfce7' },
  { key: 'REJECTED', label: 'Bị từ chối', color: '#dc2626', bg: '#fee2e2' }
];

export default function ModerationPage() {
  const [users, setUsers] = useState<SupplierUser[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [tab, setTab] = useState('ALL');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  const [detailUser, setDetailUser] = useState<SupplierUser | null>(null);
  const [confirmAction, setConfirmAction] = useState<{ id: number; action: 'VERIFIED' | 'REJECTED' } | null>(null);

  const fetchUsers = useCallback(async () => {
    setIsLoading(true);
    try {
      let url = `/admin/users?role=SUPPLIER&page=${page}&size=20`;
      if (tab !== 'ALL') {
        url += `&verificationStatus=${tab}`;
      }
      const res = await apiFetch<any>(url);
      setUsers(res.content || []);
      setTotalPages(res.totalPages || 0);
      setTotalElements(res.totalElements || 0);
    } catch (err) {
      console.error('Failed to fetch suppliers:', err);
    } finally {
      setIsLoading(false);
    }
  }, [page, tab]);

  useEffect(() => { fetchUsers(); }, [fetchUsers]);

  const handleVerify = async (userId: number, status: 'VERIFIED' | 'REJECTED') => {
    setActionLoading(userId);
    try {
      await apiFetch(`/admin/users/${userId}/verify`, {
        method: 'PATCH',
        body: JSON.stringify({ verificationStatus: status }),
      });
      fetchUsers();
      setDetailUser(null);
      setConfirmAction(null);
    } catch (err: any) {
      alert(err.message || 'Thao tác thất bại');
    } finally {
      setActionLoading(null);
    }
  };

  const getStatusConfig = (key: string) => STATUS_TABS.find(t => t.key === key) || STATUS_TABS[0];

  return (
    <div className="p-4 md:p-6 max-w-6xl mx-auto flex flex-col gap-5">
      <div>
        <h1 className="text-xl md:text-2xl font-bold text-gray-900">Duyệt hồ sơ Nhà Cung Cấp</h1>
        <p className="text-sm text-gray-500 mt-0.5">{totalElements} hồ sơ đăng ký kinh doanh</p>
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
      ) : users.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <FileText size={48} className="mx-auto mb-3 opacity-50" />
          <p className="text-sm">Không có hồ sơ nào</p>
        </div>
      ) : (
        <>
          {/* Desktop Table */}
          <div className="hidden md:block bg-white rounded-2xl border border-gray-100 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100 bg-gray-50/50">
                    {['Nhà cung cấp', 'Đại diện', 'Liên hệ', 'Loại hình', 'Mã số thuế', 'Hành động'].map(h => (
                      <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide whitespace-nowrap">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {users.map((u, i) => {
                    const bp = u.businessProfile;
                    if (!bp) return null;
                    return (
                      <motion.tr key={u.id} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: i * 0.03 }}
                        className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                        <td className="px-4 py-3.5 text-gray-900 font-medium text-xs max-w-[200px] truncate cursor-pointer hover:text-blue-600"
                            onClick={() => setDetailUser(u)}>
                          {bp.name}
                        </td>
                        <td className="px-4 py-3.5 text-gray-700 text-xs">{u.fullName}</td>
                        <td className="px-4 py-3.5 text-gray-500 text-xs">
                          <p>{u.phone}</p>
                          <p className="text-[10px] text-gray-400">{u.email}</p>
                        </td>
                        <td className="px-4 py-3.5 text-gray-600 text-xs">
                          {bp.profileType === 'ORGANIZATION' ? bp.organizationType : bp.profileType}
                        </td>
                        <td className="px-4 py-3.5 text-gray-600 text-xs">{bp.taxCode || '—'}</td>
                        <td className="px-4 py-3.5">
                          {tab === 'UNVERIFIED' ? (
                            <div className="flex gap-1.5">
                              <button onClick={() => setConfirmAction({ id: u.id, action: 'VERIFIED' })}
                                className="px-2.5 py-1.5 rounded-lg bg-green-50 text-green-600 text-xs font-medium cursor-pointer hover:bg-green-100">
                                <CheckCircle size={13} />
                              </button>
                              <button onClick={() => setConfirmAction({ id: u.id, action: 'REJECTED' })}
                                className="px-2.5 py-1.5 rounded-lg bg-red-50 text-red-600 text-xs font-medium cursor-pointer hover:bg-red-100">
                                <X size={13} />
                              </button>
                              <button onClick={() => setDetailUser(u)} title="Xem chi tiết"
                                className="px-2.5 py-1.5 rounded-lg bg-blue-50 text-blue-600 text-xs cursor-pointer"><Eye size={13} /></button>
                            </div>
                          ) : (
                            <span className="text-xs text-gray-400 whitespace-nowrap">{timeAgo(u.createdAt)}</span>
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
            {users.map((u, i) => {
              const bp = u.businessProfile;
              if (!bp) return null;
              return (
                <motion.div key={u.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.03 }}
                  className="bg-white rounded-2xl border border-gray-100 p-4">
                  <div className="flex items-start justify-between mb-2">
                    <div className="flex-1 min-w-0" onClick={() => setDetailUser(u)}>
                      <p className="font-semibold text-gray-900 text-sm cursor-pointer hover:text-blue-600">{bp.name}</p>
                      <p className="text-xs text-gray-400 mt-0.5">Đại diện: {u.fullName} · {timeAgo(u.createdAt)}</p>
                    </div>
                  </div>
                  <p className="text-xs text-gray-500 mb-3 line-clamp-2">{bp.description}</p>
                  
                  {tab === 'UNVERIFIED' && (
                    <div className="flex gap-2 mt-4 pt-3 border-t border-gray-50">
                      <button onClick={() => setConfirmAction({ id: u.id, action: 'VERIFIED' })}
                        className="flex-1 py-2 rounded-xl bg-green-50 text-green-600 text-xs font-medium flex justify-center items-center gap-1">
                        <CheckCircle size={14} /> Duyệt
                      </button>
                      <button onClick={() => setConfirmAction({ id: u.id, action: 'REJECTED' })}
                        className="flex-1 py-2 rounded-xl bg-red-50 text-red-600 text-xs font-medium flex justify-center items-center gap-1">
                        <X size={14} /> Từ chối
                      </button>
                    </div>
                  )}
                </motion.div>
              );
            })}
          </div>

          {totalPages > 1 && (
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
        {confirmAction && (
          <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
            <div className="absolute inset-0 bg-black/40" onClick={() => setConfirmAction(null)} />
            <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }}
              className="relative bg-white rounded-2xl p-6 max-w-sm w-full shadow-2xl">
              <h3 className={`font-bold text-lg mb-2 ${confirmAction.action === 'VERIFIED' ? 'text-green-600' : 'text-red-600'}`}>
                {confirmAction.action === 'VERIFIED' ? 'Duyệt hồ sơ' : 'Từ chối hồ sơ'}
              </h3>
              <p className="text-gray-500 text-sm mb-6">
                {confirmAction.action === 'VERIFIED' 
                  ? 'Bạn xác nhận duyệt cho phép Nhà Cung Cấp này hoạt động trên hệ thống?' 
                  : 'Bạn xác nhận từ chối hồ sơ đăng ký của Nhà Cung Cấp này?'}
              </p>
              
              <div className="flex gap-3">
                <button onClick={() => setConfirmAction(null)}
                  className="flex-1 py-2.5 rounded-xl border border-gray-200 text-gray-500 text-sm cursor-pointer hover:bg-gray-50">Huỷ</button>
                <button onClick={() => handleVerify(confirmAction.id, confirmAction.action)}
                  disabled={actionLoading === confirmAction.id}
                  className={`flex-1 py-2.5 rounded-xl text-white text-sm font-semibold cursor-pointer disabled:opacity-50 flex items-center justify-center gap-2
                    ${confirmAction.action === 'VERIFIED' ? 'bg-[#2db84c] hover:bg-[#259e40]' : 'bg-red-500 hover:bg-red-600'}`}>
                  {actionLoading === confirmAction.id ? <Loader2 size={14} className="animate-spin" /> : 'Xác nhận'}
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Detail Modal */}
      {detailUser && detailUser.businessProfile && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" onClick={() => setDetailUser(null)}>
          <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
            className="bg-white rounded-2xl p-6 w-full max-w-2xl max-h-[90vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold text-gray-900 text-lg">Chi tiết hồ sơ đăng ký</h3>
              <button onClick={() => setDetailUser(null)} className="text-gray-400 hover:text-gray-600 cursor-pointer"><X size={20} /></button>
            </div>
            
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm mb-6">
              <div className="p-4 rounded-xl bg-gray-50">
                <h4 className="font-semibold text-gray-700 mb-3 border-b border-gray-200 pb-2">Thông tin doanh nghiệp</h4>
                <div className="space-y-2">
                  <p><span className="text-gray-500">Tên:</span> <span className="font-medium">{detailUser.businessProfile.name}</span></p>
                  <p><span className="text-gray-500">Mã số thuế:</span> <span>{detailUser.businessProfile.taxCode || '—'}</span></p>
                  <p><span className="text-gray-500">Loại hình:</span> <span>{detailUser.businessProfile.profileType}</span></p>
                  {detailUser.businessProfile.organizationType && <p><span className="text-gray-500">Phân loại:</span> <span>{detailUser.businessProfile.organizationType}</span></p>}
                  <p><span className="text-gray-500">Mô tả:</span> <span className="text-gray-600">{detailUser.businessProfile.description || '—'}</span></p>
                </div>
              </div>
              
              <div className="p-4 rounded-xl bg-gray-50">
                <h4 className="font-semibold text-gray-700 mb-3 border-b border-gray-200 pb-2">Thông tin đại diện</h4>
                <div className="space-y-2">
                  <p><span className="text-gray-500">Họ tên:</span> <span className="font-medium">{detailUser.fullName}</span></p>
                  <p><span className="text-gray-500">Điện thoại:</span> <span>{detailUser.phone}</span></p>
                  <p><span className="text-gray-500">Email:</span> <span>{detailUser.email}</span></p>
                  <p><span className="text-gray-500">Ngày ĐK:</span> <span>{new Date(detailUser.createdAt).toLocaleDateString()}</span></p>
                </div>
              </div>
            </div>

            <div className="mb-6">
              <h4 className="font-semibold text-gray-700 mb-3">Giấy phép / Chứng chỉ</h4>
              {detailUser.businessProfile.licenseUrls && detailUser.businessProfile.licenseUrls.filter(u => u && u.trim() !== '').length > 0 ? (
                <div className="grid grid-cols-1 gap-3">
                  {detailUser.businessProfile.licenseUrls.filter(u => u && u.trim() !== '').map((url, idx) => (
                    <img key={idx} src={url} alt="License" className="w-full rounded-xl border border-gray-200 shadow-sm object-cover" onError={(e) => { e.currentTarget.style.display = 'none'; }} />
                  ))}
                </div>
              ) : (
                <div className="p-6 bg-gray-50 rounded-xl text-center text-gray-400">
                  Chưa cung cấp hình ảnh giấy phép
                </div>
              )}
            </div>

            {tab === 'UNVERIFIED' && (
              <div className="flex gap-3 sticky bottom-0 bg-white pt-4 border-t border-gray-100 mt-2">
                <button onClick={() => { setConfirmAction({ id: detailUser.id, action: 'REJECTED' }); setDetailUser(null); }}
                  className="flex-1 py-2 rounded-xl border border-red-200 text-red-600 font-semibold cursor-pointer hover:bg-red-50 flex items-center justify-center gap-2 text-sm transition-colors">
                  <X size={16} /> Từ chối hồ sơ
                </button>
                <button onClick={() => { setConfirmAction({ id: detailUser.id, action: 'VERIFIED' }); setDetailUser(null); }}
                  className="flex-1 py-2 rounded-xl bg-[#2db84c] text-white font-semibold cursor-pointer hover:bg-[#259e40] flex items-center justify-center gap-2 text-sm transition-colors">
                  <CheckCircle size={16} /> Duyệt hồ sơ
                </button>
              </div>
            )}
          </motion.div>
        </div>
      )}
    </div>
  );
}
