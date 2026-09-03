import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import {
  Search, Loader2, ChevronDown, Shield, ShieldOff, Eye,
  X, User as UserIcon, Mail, Phone, Calendar,
} from 'lucide-react';
import { apiFetch } from '../../services/api';
import { ROLE_MAP } from '../../constants';

interface UserItem {
  id: number;
  fullName: string;
  email: string;
  phone: string;
  role: string;
  active: boolean;
  createdAt: string;
  orderCount?: number;
  avatar?: string;
}

const ROLE_FILTERS = [
  { key: 'all', label: 'Tất cả' },
  { key: 'SUPPLIER', label: 'Nhà cung cấp' },
  { key: 'RECIPIENT', label: 'Người nhận' },
  { key: 'ORGANIZATION', label: 'Tổ chức' },
];

function formatDate(iso: string) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export default function UsersPage() {
  const [users, setUsers] = useState<UserItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('all');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  const [selectedUser, setSelectedUser] = useState<UserItem | null>(null);

  const fetchUsers = useCallback(async () => {
    setIsLoading(true);
    try {
      let url = `/admin/users?page=${page}&size=20`;
      if (search.trim()) url += `&keyword=${encodeURIComponent(search.trim())}`;
      if (roleFilter !== 'all') url += `&role=${roleFilter}`;
      const res = await apiFetch<any>(url);
      setUsers(res.content || []);
      setTotalPages(res.totalPages || 0);
      setTotalElements(res.totalElements || 0);
    } catch (err) {
      console.error('Failed to fetch users:', err);
    } finally {
      setIsLoading(false);
    }
  }, [page, search, roleFilter]);

  useEffect(() => { fetchUsers(); }, [fetchUsers]);

  const handleToggleStatus = async (userId: number, activate: boolean) => {
    setActionLoading(userId);
    try {
      await apiFetch(`/admin/users/${userId}/${activate ? 'activate' : 'deactivate'}`, { method: 'PATCH' });
      setUsers(prev => prev.map(u => u.id === userId ? { ...u, active: activate } : u));
      if (selectedUser?.id === userId) setSelectedUser(prev => prev ? { ...prev, active: activate } : null);
    } catch (err: any) {
      alert(err.message || 'Thao tác thất bại');
    } finally {
      setActionLoading(null);
    }
  };

  const handleSearch = (e: React.FormEvent) => { e.preventDefault(); setPage(0); };

  return (
    <div className="p-4 md:p-6 max-w-6xl mx-auto flex flex-col gap-5">
      <div className="flex items-start justify-between flex-wrap gap-2">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-gray-900">Quản lý người dùng</h1>
          <p className="text-sm text-gray-500 mt-0.5">{totalElements} tài khoản trên nền tảng</p>
        </div>
      </div>

      {/* Search + Filters */}
      <div className="flex flex-col sm:flex-row gap-3">
        <form onSubmit={handleSearch} className="flex-1 flex items-center gap-2 bg-white border border-gray-200 rounded-xl px-3 py-2.5">
          <Search size={16} className="text-gray-400" />
          <input value={search} onChange={e => setSearch(e.target.value)}
            placeholder="Tìm theo tên, email, SĐT..."
            className="bg-transparent text-sm text-gray-900 outline-none flex-1 placeholder:text-gray-400" />
          {search && <button type="button" onClick={() => { setSearch(''); setPage(0); }} className="text-gray-400 hover:text-gray-600 cursor-pointer"><X size={14} /></button>}
        </form>
        <div className="flex gap-2 overflow-x-auto">
          {ROLE_FILTERS.map(r => (
            <button key={r.key} onClick={() => { setRoleFilter(r.key); setPage(0); }}
              className={`px-3 py-2 rounded-xl text-xs font-medium whitespace-nowrap cursor-pointer transition-all ${roleFilter === r.key ? 'bg-[#2db84c] text-white' : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'}`}>
              {r.label}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-16"><Loader2 size={24} className="animate-spin text-[#2db84c]" /></div>
      ) : users.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <UserIcon size={48} className="mx-auto mb-3 opacity-50" />
          <p className="text-sm">Không tìm thấy người dùng</p>
        </div>
      ) : (
        <>
          {/* Desktop Table */}
          <div className="hidden md:block bg-white rounded-2xl border border-gray-100 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100 bg-gray-50/50">
                    {['Người dùng', 'Liên hệ', 'Vai trò', 'Ngày tạo', 'Trạng thái', 'Hành động'].map(h => (
                      <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {users.map((u, i) => (
                    <motion.tr key={u.id} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: i * 0.03 }}
                      className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                      <td className="px-4 py-3.5">
                        <div className="flex items-center gap-3 cursor-pointer" onClick={() => setSelectedUser(u)}>
                          <div className="w-9 h-9 rounded-full bg-gradient-to-br from-green-400 to-green-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
                            {u.avatar ? <img src={u.avatar} className="w-full h-full rounded-full object-cover" alt="" /> : u.fullName.charAt(0)}
                          </div>
                          <span className="font-medium text-gray-900 hover:text-[#2db84c] transition-colors">{u.fullName}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3.5">
                        <p className="text-gray-700 text-xs">{u.phone}</p>
                        <p className="text-gray-400 text-xs">{u.email}</p>
                      </td>
                      <td className="px-4 py-3.5">
                        <span className="px-2.5 py-1 rounded-full bg-gray-100 text-gray-600 text-xs font-medium">{ROLE_MAP[u.role] || u.role}</span>
                      </td>
                      <td className="px-4 py-3.5 text-gray-500 text-xs">{formatDate(u.createdAt)}</td>
                      <td className="px-4 py-3.5">
                        <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${u.active ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}`}>
                          {u.active ? 'Hoạt động' : 'Đã khoá'}
                        </span>
                      </td>
                      <td className="px-4 py-3.5">
                        <div className="flex gap-2">
                          <button onClick={() => setSelectedUser(u)}
                            className="px-3 py-1.5 rounded-lg bg-gray-50 text-gray-500 text-xs font-medium cursor-pointer hover:bg-gray-100 transition-colors">
                            <Eye size={13} />
                          </button>
                          <button onClick={() => handleToggleStatus(u.id, !u.active)}
                            disabled={actionLoading === u.id}
                            className={`px-3 py-1.5 rounded-lg text-xs font-medium cursor-pointer transition-colors disabled:opacity-50 ${u.active ? 'bg-red-50 text-red-500 hover:bg-red-100' : 'bg-green-50 text-green-600 hover:bg-green-100'}`}>
                            {actionLoading === u.id ? <Loader2 size={13} className="animate-spin" /> : u.active ? 'Khoá' : 'Mở khoá'}
                          </button>
                        </div>
                      </td>
                    </motion.tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Mobile Cards */}
          <div className="md:hidden flex flex-col gap-3">
            {users.map((u, i) => (
              <motion.div key={u.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.03 }}
                className="bg-white rounded-2xl border border-gray-100 p-4">
                <div className="flex items-center gap-3 mb-3">
                  <div className="w-10 h-10 rounded-full bg-gradient-to-br from-green-400 to-green-600 flex items-center justify-center text-white text-sm font-bold">
                    {u.fullName.charAt(0)}
                  </div>
                  <div className="flex-1 min-w-0" onClick={() => setSelectedUser(u)}>
                    <p className="font-semibold text-gray-900 text-sm truncate cursor-pointer">{u.fullName}</p>
                    <p className="text-xs text-gray-400">{u.email}</p>
                  </div>
                  <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${u.active ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}`}>
                    {u.active ? 'Hoạt động' : 'Khoá'}
                  </span>
                </div>
                <div className="flex items-center justify-between text-xs text-gray-500">
                  <span>{ROLE_MAP[u.role] || u.role} · {formatDate(u.createdAt)}</span>
                  <button onClick={() => handleToggleStatus(u.id, !u.active)} disabled={actionLoading === u.id}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium cursor-pointer ${u.active ? 'bg-red-50 text-red-500' : 'bg-green-50 text-green-600'}`}>
                    {u.active ? 'Khoá' : 'Mở khoá'}
                  </button>
                </div>
              </motion.div>
            ))}
          </div>

          {/* Pagination */}
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

      {/* User Detail Modal */}
      {selectedUser && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" onClick={() => setSelectedUser(null)}>
          <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
            className="bg-white rounded-2xl p-6 w-full max-w-md" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold text-gray-900">Chi tiết người dùng</h3>
              <button onClick={() => setSelectedUser(null)} className="text-gray-400 hover:text-gray-600 cursor-pointer"><X size={18} /></button>
            </div>
            <div className="flex items-center gap-3 mb-5">
              <div className="w-14 h-14 rounded-full bg-gradient-to-br from-green-400 to-green-600 flex items-center justify-center text-white text-xl font-bold">
                {selectedUser.fullName.charAt(0)}
              </div>
              <div>
                <p className="font-bold text-gray-900">{selectedUser.fullName}</p>
                <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${selectedUser.active ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}`}>
                  {selectedUser.active ? 'Hoạt động' : 'Đã khoá'}
                </span>
              </div>
            </div>
            <div className="flex flex-col gap-3 text-sm mb-5">
              <div className="flex items-center gap-3 p-3 rounded-xl bg-gray-50">
                <Mail size={14} className="text-gray-400" /><span className="text-gray-700">{selectedUser.email}</span>
              </div>
              <div className="flex items-center gap-3 p-3 rounded-xl bg-gray-50">
                <Phone size={14} className="text-gray-400" /><span className="text-gray-700">{selectedUser.phone || 'Chưa cập nhật'}</span>
              </div>
              <div className="flex items-center gap-3 p-3 rounded-xl bg-gray-50">
                <Shield size={14} className="text-gray-400" /><span className="text-gray-700">{ROLE_MAP[selectedUser.role] || selectedUser.role}</span>
              </div>
              <div className="flex items-center gap-3 p-3 rounded-xl bg-gray-50">
                <Calendar size={14} className="text-gray-400" /><span className="text-gray-700">Tham gia {formatDate(selectedUser.createdAt)}</span>
              </div>
            </div>
            <button onClick={() => { handleToggleStatus(selectedUser.id, !selectedUser.active); }}
              disabled={actionLoading === selectedUser.id}
              className={`w-full py-2.5 rounded-xl text-sm font-semibold cursor-pointer transition-all disabled:opacity-50 flex items-center justify-center gap-2 ${selectedUser.active ? 'bg-red-500 text-white hover:bg-red-600' : 'bg-[#2db84c] text-white hover:bg-[#259e40]'}`}>
              {actionLoading === selectedUser.id ? <Loader2 size={14} className="animate-spin" /> : selectedUser.active ? <ShieldOff size={14} /> : <Shield size={14} />}
              {selectedUser.active ? 'Khoá tài khoản' : 'Mở khoá tài khoản'}
            </button>
          </motion.div>
        </div>
      )}
    </div>
  );
}
