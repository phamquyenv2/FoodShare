import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Loader2, X, CheckCircle, FileText, Eye, ShieldCheck, Building2, Store } from 'lucide-react';
import { apiFetch } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';
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

interface ModerationUser {
  id: number;
  fullName: string;
  email: string;
  phone: string;
  role: string;
  createdAt: string;
  businessProfile?: BusinessProfile;
}

const ROLE_TABS = [
  { key: 'ALL', label: 'Tất cả đối tác', icon: ShieldCheck },
  { key: 'SUPPLIER', label: 'Nhà cung cấp', icon: Store },
  { key: 'ORGANIZATION', label: 'Tổ chức từ thiện', icon: Building2 },
];

const STATUS_TABS = [
  { key: 'ALL', label: 'Tất cả', color: '#4b5563', bg: '#f3f4f6' },
  { key: 'UNVERIFIED', label: 'Chờ duyệt', color: '#d97706', bg: '#fef3c7' },
  { key: 'VERIFIED', label: 'Đã duyệt', color: '#16a34a', bg: '#dcfce7' },
  { key: 'REJECTED', label: 'Bị từ chối', color: '#dc2626', bg: '#fee2e2' }
];

const ORGANIZATION_TYPE_LABELS: Record<string, string> = {
  CHARITY: 'Tổ chức từ thiện',
  COMMUNITY_GROUP: 'Nhóm cộng đồng',
  RELIGIOUS_AFFILIATE: 'Cơ sở tôn giáo',
  SHELTER: 'Mái ấm tình thương',
  OTHER: 'Khác',
};

const SUPPLIER_TYPE_LABELS: Record<string, string> = {
  RESTAURANT: 'Nhà hàng / Quán ăn',
  BAKERY: 'Tiệm bánh',
  SUPERMARKET: 'Siêu thị / Cửa hàng',
  GROCERY: 'Nông sản / Thực phẩm',
  OTHER: 'Khác',
};

export default function ModerationPage() {
  const { showError, showSuccess } = useToast();
  const [users, setUsers] = useState<ModerationUser[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [roleTab, setRoleTab] = useState('ALL');
  const [tab, setTab] = useState('ALL');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  const [detailUser, setDetailUser] = useState<ModerationUser | null>(null);
  const [confirmAction, setConfirmAction] = useState<{ id: number; action: 'VERIFIED' | 'REJECTED'; name: string; role: string } | null>(null);

  const fetchUsers = useCallback(async () => {
    setIsLoading(true);
    try {
      let url = `/admin/users?page=${page}&size=20`;
      if (roleTab === 'ALL') {
        url += `&hasBusinessProfile=true`;
      } else {
        url += `&role=${roleTab}`;
      }
      if (tab !== 'ALL') {
        url += `&verificationStatus=${tab}`;
      }
      const res = await apiFetch<any>(url);
      setUsers(res.content || []);
      setTotalPages(res.totalPages || 0);
      setTotalElements(res.totalElements || 0);
    } catch (err) {
      showError(err instanceof Error ? err.message : 'Không thể tải hồ sơ kiểm duyệt');
    } finally {
      setIsLoading(false);
    }
  }, [page, tab, roleTab, showError]);

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
      showSuccess(status === 'VERIFIED' ? 'Đã xác minh hồ sơ thành công' : 'Đã từ chối hồ sơ');
    } catch (err: any) {
      showError(err.message || 'Thao tác thất bại');
    } finally {
      setActionLoading(null);
    }
  };

  return (
    <div className="p-4 md:p-6 max-w-6xl mx-auto flex flex-col gap-5">
      <div>
        <h1 className="text-xl md:text-2xl font-bold text-gray-900">Duyệt hồ sơ Đối tác & Tổ chức</h1>
        <p className="text-sm text-gray-500 mt-0.5">{totalElements} hồ sơ đăng ký kinh doanh & hoạt động</p>
      </div>

      <div className="flex flex-col sm:flex-row gap-3 sm:items-center sm:justify-between">
        <div className="flex gap-1 p-1 bg-gray-100 rounded-xl w-fit">
          {ROLE_TABS.map(r => {
            const Icon = r.icon;
            const active = roleTab === r.key;
            return (
              <button
                key={r.key}
                onClick={() => { setRoleTab(r.key); setPage(0); }}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold cursor-pointer transition-all flex items-center gap-1.5 ${
                  active
                    ? 'bg-white text-gray-900 shadow-xs'
                    : 'text-gray-500 hover:text-gray-800'
                }`}
              >
                <Icon size={14} className={active ? 'text-[#2db84c]' : 'text-gray-400'} />
                <span>{r.label}</span>
              </button>
            );
          })}
        </div>

        <div className="flex gap-2 overflow-x-auto pb-1">
          {STATUS_TABS.map(t => (
            <button
              key={t.key}
              onClick={() => { setTab(t.key); setPage(0); }}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-medium whitespace-nowrap cursor-pointer transition-all ${
                tab === t.key
                  ? 'bg-[#2db84c] text-white shadow-xs'
                  : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-16"><Loader2 size={24} className="animate-spin text-[#2db84c]" /></div>
      ) : users.length === 0 ? (
        <div className="text-center py-16 text-gray-400 bg-white rounded-2xl border border-gray-100">
          <FileText size={48} className="mx-auto mb-3 opacity-50" />
          <p className="text-sm font-medium">Không có hồ sơ nào phù hợp</p>
        </div>
      ) : (
        <>
          <div className="hidden md:block bg-white rounded-2xl border border-gray-100 overflow-hidden shadow-xs">
            <div className="overflow-x-auto">
              <table className="w-full text-sm min-w-[880px]">
                <thead>
                  <tr className="border-b border-gray-100 bg-gray-50/60">
                    {['Tên đơn vị / Quán', 'Đại diện', 'Liên hệ', 'Vai trò & Loại hình', 'Mã số thuế', 'Trạng thái', 'Hành động'].map(h => (
                      <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide whitespace-nowrap">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {users.map((u, i) => {
                    const bp = u.businessProfile;
                    if (!bp) return null;
                    const isOrg = u.role === 'ORGANIZATION';
                    return (
                      <motion.tr key={u.id} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: i * 0.03 }}
                        className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                        <td className="px-4 py-3.5 text-gray-900 font-semibold text-xs max-w-[200px] truncate cursor-pointer hover:text-blue-600"
                            onClick={() => setDetailUser(u)}>
                          {bp.name}
                        </td>
                        <td className="px-4 py-3.5 text-gray-700 text-xs font-medium">{u.fullName}</td>
                        <td className="px-4 py-3.5 text-gray-500 text-xs">
                          <p className="font-mono text-gray-700">{u.phone}</p>
                          <p className="text-[11px] text-gray-400">{u.email}</p>
                        </td>
                        <td className="px-4 py-3.5 text-gray-600 text-xs">
                          <div className="flex flex-col gap-1 items-start">
                            <span className={`px-2 py-0.5 rounded-md text-[10px] font-semibold ${
                              isOrg
                                ? 'bg-purple-50 text-purple-700 border border-purple-200/60'
                                : 'bg-blue-50 text-blue-700 border border-blue-200/60'
                            }`}>
                              {isOrg ? 'Tổ chức từ thiện' : 'Nhà cung cấp'}
                            </span>
                            <span className="text-gray-500 text-[11px]">
                              {isOrg
                                ? (ORGANIZATION_TYPE_LABELS[bp.organizationType || ''] || bp.organizationType || 'Tổ chức cộng đồng')
                                : (SUPPLIER_TYPE_LABELS[bp.supplierType || ''] || bp.supplierType || bp.profileType)}
                            </span>
                          </div>
                        </td>
                        <td className="px-4 py-3.5 text-gray-600 text-xs font-mono">{bp.taxCode || '—'}</td>
                        <td className="px-4 py-3.5">
                          <div className="flex flex-col gap-1">
                            {bp.verificationStatus === 'VERIFIED' && (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-green-50 text-green-700 w-fit">
                                <span className="w-1.5 h-1.5 rounded-full bg-green-500"></span> Đã duyệt
                              </span>
                            )}
                            {bp.verificationStatus === 'REJECTED' && (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-red-50 text-red-700 w-fit">
                                <span className="w-1.5 h-1.5 rounded-full bg-red-500"></span> Bị từ chối
                              </span>
                            )}
                            {bp.verificationStatus === 'UNVERIFIED' && (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-amber-50 text-amber-700 w-fit">
                                <span className="w-1.5 h-1.5 rounded-full bg-amber-500"></span> Chờ duyệt
                              </span>
                            )}
                            <span className="text-[11px] text-gray-400 whitespace-nowrap">{timeAgo(u.createdAt)}</span>
                          </div>
                        </td>
                        <td className="px-4 py-3.5 whitespace-nowrap">
                          <div className="flex items-center gap-2 whitespace-nowrap">
                            {bp.verificationStatus === 'UNVERIFIED' && (
                              <>
                                <button
                                  onClick={() => setConfirmAction({ id: u.id, action: 'VERIFIED', name: bp.name, role: u.role })}
                                  title="Duyệt hồ sơ"
                                  className="h-8 w-[76px] rounded-lg bg-green-50 text-green-600 hover:bg-green-100 text-xs font-semibold cursor-pointer transition-colors inline-flex items-center justify-center gap-1 whitespace-nowrap shrink-0"
                                >
                                  <CheckCircle size={13} className="shrink-0" />
                                  <span>Duyệt</span>
                                </button>
                                <button
                                  onClick={() => setConfirmAction({ id: u.id, action: 'REJECTED', name: bp.name, role: u.role })}
                                  title="Từ chối hồ sơ"
                                  className="h-8 w-[76px] rounded-lg bg-red-50 text-red-600 hover:bg-red-100 text-xs font-semibold cursor-pointer transition-colors inline-flex items-center justify-center gap-1 whitespace-nowrap shrink-0"
                                >
                                  <X size={13} className="shrink-0" />
                                  <span>Từ chối</span>
                                </button>
                              </>
                            )}
                            {bp.verificationStatus === 'REJECTED' && (
                              <button
                                onClick={() => setConfirmAction({ id: u.id, action: 'VERIFIED', name: bp.name, role: u.role })}
                                title="Duyệt lại"
                                className="h-8 w-[98px] rounded-lg bg-green-50 text-green-600 hover:bg-green-100 text-xs font-semibold cursor-pointer transition-colors inline-flex items-center justify-center gap-1 whitespace-nowrap shrink-0"
                              >
                                <CheckCircle size={13} className="shrink-0" />
                                <span>Duyệt lại</span>
                              </button>
                            )}
                            {bp.verificationStatus === 'VERIFIED' && (
                              <button
                                onClick={() => setConfirmAction({ id: u.id, action: 'REJECTED', name: bp.name, role: u.role })}
                                title="Hủy duyệt"
                                className="h-8 w-[98px] rounded-lg bg-red-50 text-red-600 hover:bg-red-100 text-xs font-semibold cursor-pointer transition-colors inline-flex items-center justify-center gap-1 whitespace-nowrap shrink-0"
                              >
                                <X size={13} className="shrink-0" />
                                <span>Hủy duyệt</span>
                              </button>
                            )}
                            <button
                              onClick={() => setDetailUser(u)}
                              title="Xem chi tiết & giấy phép"
                              className="h-8 w-8 rounded-lg bg-blue-50 text-blue-600 hover:bg-blue-100 text-xs cursor-pointer transition-colors inline-flex items-center justify-center shrink-0"
                            >
                              <Eye size={14} className="shrink-0" />
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
            {users.map((u, i) => {
              const bp = u.businessProfile;
              if (!bp) return null;
              const isOrg = u.role === 'ORGANIZATION';
              return (
                <motion.div key={u.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.03 }}
                  className="bg-white rounded-2xl border border-gray-100 p-4 shadow-xs">
                  <div className="flex items-start justify-between mb-2">
                    <div className="flex-1 min-w-0" onClick={() => setDetailUser(u)}>
                      <div className="flex items-center gap-2 mb-1">
                        <span className={`px-2 py-0.5 rounded-md text-[10px] font-semibold ${
                          isOrg ? 'bg-purple-50 text-purple-700' : 'bg-blue-50 text-blue-700'
                        }`}>
                          {isOrg ? 'Tổ chức' : 'Nhà cung cấp'}
                        </span>
                        <span className="text-xs text-gray-400">{timeAgo(u.createdAt)}</span>
                      </div>
                      <p className="font-semibold text-gray-900 text-sm cursor-pointer hover:text-blue-600">{bp.name}</p>
                      <p className="text-xs text-gray-500 mt-0.5">Đại diện: {u.fullName} · {u.phone}</p>
                    </div>
                    {bp.verificationStatus === 'VERIFIED' && (
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-green-50 text-green-700 shrink-0">Đã duyệt</span>
                    )}
                    {bp.verificationStatus === 'REJECTED' && (
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-red-50 text-red-700 shrink-0">Từ chối</span>
                    )}
                    {bp.verificationStatus === 'UNVERIFIED' && (
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-amber-50 text-amber-700 shrink-0">Chờ duyệt</span>
                    )}
                  </div>
                  {bp.description && <p className="text-xs text-gray-500 mb-3 line-clamp-2">{bp.description}</p>}
                  
                  <div className="flex gap-2 mt-4 pt-3 border-t border-gray-100">
                    <button onClick={() => setDetailUser(u)}
                      className="flex-1 py-2 rounded-xl bg-blue-50 text-blue-600 text-xs font-medium flex justify-center items-center gap-1 cursor-pointer whitespace-nowrap">
                      <Eye size={14} className="shrink-0" /> Chi tiết
                    </button>
                    {bp.verificationStatus === 'UNVERIFIED' && (
                      <>
                        <button onClick={() => setConfirmAction({ id: u.id, action: 'VERIFIED', name: bp.name, role: u.role })}
                          className="flex-1 py-2 rounded-xl bg-green-50 text-green-600 text-xs font-medium flex justify-center items-center gap-1 cursor-pointer whitespace-nowrap">
                          <CheckCircle size={14} className="shrink-0" /> Duyệt
                        </button>
                        <button onClick={() => setConfirmAction({ id: u.id, action: 'REJECTED', name: bp.name, role: u.role })}
                          className="flex-1 py-2 rounded-xl bg-red-50 text-red-600 text-xs font-medium flex justify-center items-center gap-1 cursor-pointer whitespace-nowrap">
                          <X size={14} className="shrink-0" /> Từ chối
                        </button>
                      </>
                    )}
                  </div>
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

      <AnimatePresence>
        {confirmAction && (
          <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
            <div className="absolute inset-0 bg-black/40" onClick={() => setConfirmAction(null)} />
            <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }}
              className="relative bg-white rounded-2xl p-6 max-w-sm w-full shadow-2xl">
              <h3 className={`font-bold text-lg mb-2 ${confirmAction.action === 'VERIFIED' ? 'text-green-600' : 'text-red-600'}`}>
                {confirmAction.action === 'VERIFIED' ? 'Xác nhận duyệt hồ sơ' : 'Xác nhận từ chối hồ sơ'}
              </h3>
              <p className="text-gray-600 text-sm mb-6 leading-relaxed">
                {confirmAction.action === 'VERIFIED' 
                  ? `Bạn xác nhận duyệt cho phép ${confirmAction.role === 'ORGANIZATION' ? 'Tổ chức' : 'Nhà Cung Cấp'} "${confirmAction.name}" hoạt động trên hệ thống?` 
                  : `Bạn xác nhận từ chối hồ sơ đăng ký của ${confirmAction.role === 'ORGANIZATION' ? 'Tổ chức' : 'Nhà Cung Cấp'} "${confirmAction.name}"?`}
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

      {detailUser && detailUser.businessProfile && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" onClick={() => setDetailUser(null)}>
          <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
            className="bg-white rounded-2xl p-6 w-full max-w-2xl max-h-[90vh] overflow-y-auto shadow-2xl" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <h3 className="font-semibold text-gray-900 text-lg">Chi tiết hồ sơ đăng ký</h3>
                <span className={`px-2 py-0.5 rounded-md text-xs font-semibold ${
                  detailUser.role === 'ORGANIZATION' ? 'bg-purple-100 text-purple-700' : 'bg-blue-100 text-blue-700'
                }`}>
                  {detailUser.role === 'ORGANIZATION' ? 'Tổ chức' : 'Nhà cung cấp'}
                </span>
              </div>
              <button onClick={() => setDetailUser(null)} className="text-gray-400 hover:text-gray-600 cursor-pointer"><X size={20} /></button>
            </div>
            
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm mb-6">
              <div className="p-4 rounded-xl bg-gray-50">
                <h4 className="font-semibold text-gray-700 mb-3 border-b border-gray-200 pb-2">Thông tin doanh nghiệp / Tổ chức</h4>
                <div className="space-y-2">
                  <p><span className="text-gray-500">Tên:</span> <span className="font-semibold text-gray-900">{detailUser.businessProfile.name}</span></p>
                  <p><span className="text-gray-500">Mã số thuế:</span> <span>{detailUser.businessProfile.taxCode || '—'}</span></p>
                  <p><span className="text-gray-500">Vai trò:</span> <span>{detailUser.role === 'ORGANIZATION' ? 'Tổ chức từ thiện' : 'Nhà cung cấp'}</span></p>
                  {detailUser.businessProfile.organizationType && (
                    <p><span className="text-gray-500">Phân loại tổ chức:</span> <span>{ORGANIZATION_TYPE_LABELS[detailUser.businessProfile.organizationType] || detailUser.businessProfile.organizationType}</span></p>
                  )}
                  {detailUser.businessProfile.supplierType && (
                    <p><span className="text-gray-500">Phân loại quán:</span> <span>{SUPPLIER_TYPE_LABELS[detailUser.businessProfile.supplierType] || detailUser.businessProfile.supplierType}</span></p>
                  )}
                  <p><span className="text-gray-500">Mô tả:</span> <span className="text-gray-600">{detailUser.businessProfile.description || '—'}</span></p>
                </div>
              </div>
              
              <div className="p-4 rounded-xl bg-gray-50">
                <h4 className="font-semibold text-gray-700 mb-3 border-b border-gray-200 pb-2">Thông tin đại diện</h4>
                <div className="space-y-2">
                  <p><span className="text-gray-500">Họ tên:</span> <span className="font-medium text-gray-900">{detailUser.fullName}</span></p>
                  <p><span className="text-gray-500">Điện thoại:</span> <span className="font-mono">{detailUser.phone}</span></p>
                  <p><span className="text-gray-500">Email:</span> <span>{detailUser.email}</span></p>
                  <p><span className="text-gray-500">Ngày ĐK:</span> <span>{new Date(detailUser.createdAt).toLocaleDateString('vi-VN')} ({timeAgo(detailUser.createdAt)})</span></p>
                </div>
              </div>
            </div>

            <div className="mb-6">
              <h4 className="font-semibold text-gray-700 mb-3">Giấy phép / Chứng chỉ hoạt động</h4>
              {detailUser.businessProfile.licenseUrls && detailUser.businessProfile.licenseUrls.filter(u => u && u.trim() !== '').length > 0 ? (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {detailUser.businessProfile.licenseUrls.filter(u => u && u.trim() !== '').map((url, idx) => (
                    <a key={idx} href={url} target="_blank" rel="noreferrer" className="group relative block overflow-hidden rounded-xl border border-gray-200 shadow-sm bg-gray-100">
                      <img src={url} alt="License" className="w-full h-48 object-cover group-hover:scale-105 transition-transform" onError={(e) => { e.currentTarget.style.display = 'none'; }} />
                      <div className="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-colors flex items-center justify-center opacity-0 group-hover:opacity-100 text-white text-xs font-semibold">
                        Xem ảnh gốc
                      </div>
                    </a>
                  ))}
                </div>
              ) : (
                <div className="p-6 bg-gray-50 rounded-xl text-center text-gray-400">
                  Chưa cung cấp hình ảnh giấy phép
                </div>
              )}
            </div>

            {detailUser.businessProfile.verificationStatus === 'UNVERIFIED' && (
              <div className="flex gap-3 sticky bottom-0 bg-white pt-4 border-t border-gray-100 mt-2">
                <button onClick={() => { setConfirmAction({ id: detailUser.id, action: 'REJECTED', name: detailUser.businessProfile!.name, role: detailUser.role }); setDetailUser(null); }}
                  className="flex-1 py-2.5 rounded-xl border border-red-200 text-red-600 font-semibold cursor-pointer hover:bg-red-50 flex items-center justify-center gap-2 text-sm transition-colors">
                  <X size={16} /> Từ chối hồ sơ
                </button>
                <button onClick={() => { setConfirmAction({ id: detailUser.id, action: 'VERIFIED', name: detailUser.businessProfile!.name, role: detailUser.role }); setDetailUser(null); }}
                  className="flex-1 py-2.5 rounded-xl bg-[#2db84c] text-white font-semibold cursor-pointer hover:bg-[#259e40] flex items-center justify-center gap-2 text-sm transition-colors">
                  <CheckCircle size={16} /> Duyệt hồ sơ
                </button>
              </div>
            )}
            {detailUser.businessProfile.verificationStatus === 'VERIFIED' && (
              <div className="flex justify-end sticky bottom-0 bg-white pt-4 border-t border-gray-100 mt-2">
                <button onClick={() => { setConfirmAction({ id: detailUser.id, action: 'REJECTED', name: detailUser.businessProfile!.name, role: detailUser.role }); setDetailUser(null); }}
                  className="px-5 py-2.5 rounded-xl border border-red-200 text-red-600 font-semibold cursor-pointer hover:bg-red-50 flex items-center justify-center gap-2 text-sm transition-colors">
                  <X size={16} /> Hủy duyệt hồ sơ
                </button>
              </div>
            )}
            {detailUser.businessProfile.verificationStatus === 'REJECTED' && (
              <div className="flex justify-end sticky bottom-0 bg-white pt-4 border-t border-gray-100 mt-2">
                <button onClick={() => { setConfirmAction({ id: detailUser.id, action: 'VERIFIED', name: detailUser.businessProfile!.name, role: detailUser.role }); setDetailUser(null); }}
                  className="px-5 py-2.5 rounded-xl bg-[#2db84c] text-white font-semibold cursor-pointer hover:bg-[#259e40] flex items-center justify-center gap-2 text-sm transition-colors">
                  <CheckCircle size={16} /> Duyệt lại hồ sơ
                </button>
              </div>
            )}
          </motion.div>
        </div>
      )}
    </div>
  );
}
