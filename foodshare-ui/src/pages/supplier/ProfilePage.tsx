import { useState, useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import { User, Camera, Phone, Mail, Save, Loader2, LogOut, Flag, ChevronRight } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { apiFetch } from '../../services/api';
import { useNavigate } from 'react-router-dom';
import LocationField from '../../components/shared/LocationField';
import { useToast } from '../../contexts/ToastContext';
import { formatDisplayName } from '../../utils/format';

export default function SupplierProfilePage() {
  const { user, checkAuth, logout } = useAuth();
  const { showError, showSuccess } = useToast();
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isUploadingAvatar, setIsUploadingAvatar] = useState(false);
  const [form, setForm] = useState({
    fullName: '',
    phone: '',
    email: '',
    specificAddress: '',
    latitude: null as number | null,
    longitude: null as number | null,
  });

  useEffect(() => {
    if (user) {
      setForm({
        fullName: (user as any).fullName || '',
        phone: (user as any).phone || '',
        email: (user as any).email || '',
        specificAddress: (user as any).specificAddress || '',
        latitude: (user as any).latitude ?? null,
        longitude: (user as any).longitude ?? null,
      });
    }
  }, [user]);

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      showError('Vui lòng chọn file hình ảnh hợp lệ (PNG, JPG, JPEG, WebP)');
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      showError('Kích thước ảnh tối đa là 5MB');
      return;
    }

    setIsUploadingAvatar(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const res = await apiFetch<{ url: string }>('/media/upload', {
        method: 'POST',
        body: formData,
      });

      if (!res?.url) {
        throw new Error('Không nhận được đường dẫn ảnh từ máy chủ');
      }

      await apiFetch('/users/me', {
        method: 'PATCH',
        body: JSON.stringify({ avatarUrl: res.url }),
      });

      await checkAuth();
      showSuccess('Cập nhật ảnh đại diện thành công!');
    } catch (err: any) {
      showError(err.message || 'Tải ảnh đại diện lên thất bại');
    } finally {
      setIsUploadingAvatar(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  const handleSave = async () => {
    setIsLoading(true);
    try {
      await apiFetch('/users/me', {
        method: 'PATCH',
        body: JSON.stringify({
          fullName: form.fullName,
          specificAddress: form.specificAddress,
          latitude: form.latitude,
          longitude: form.longitude,
        }),
      });
      await checkAuth();
      showSuccess('Cập nhật thành công!');
      setIsEditing(false);
    } catch (err: any) {
      showError(err.message || 'Cập nhật thất bại');
    } finally {
      setIsLoading(false);
    }
  };

  const fadeUp = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0 } };
  const currentAvatar = user?.avatarUrl || (user as any)?.avatar;

  return (
    <div className="p-4 md:p-6 max-w-3xl mx-auto flex flex-col gap-5">
      <h1 className="text-xl md:text-2xl font-bold text-gray-900">Hồ sơ cá nhân</h1>

      <motion.div
        variants={fadeUp} initial="hidden" animate="show"
        className="bg-white rounded-2xl border border-gray-100 p-6 flex flex-col items-center gap-4"
      >
        <div className="relative">
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleAvatarChange}
            accept="image/*"
            className="hidden"
          />
          <div 
            onClick={() => !isUploadingAvatar && fileInputRef.current?.click()}
            className="w-24 h-24 rounded-full bg-gradient-to-br from-[#2db84c] to-[#1a9e3a] flex items-center justify-center text-white text-3xl font-bold shadow-lg shadow-green-500/20 overflow-hidden cursor-pointer relative group"
            title="Nhấn để đổi ảnh đại diện"
          >
            {currentAvatar ? (
              <img src={currentAvatar} className="w-full h-full rounded-full object-cover" alt="avatar" />
            ) : (
              (user as any)?.fullName?.charAt(0)?.toUpperCase() || <User size={32} />
            )}
            <div className="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
              <Camera size={22} className="text-white drop-shadow-md" />
            </div>
            {isUploadingAvatar && (
              <div className="absolute inset-0 bg-black/50 flex items-center justify-center z-10">
                <Loader2 size={24} className="animate-spin text-white" />
              </div>
            )}
          </div>
          <button 
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={isUploadingAvatar}
            className="absolute bottom-0 right-0 w-8 h-8 rounded-full bg-white border border-gray-200 flex items-center justify-center shadow-md cursor-pointer hover:bg-gray-50 transition-colors disabled:opacity-50"
            title="Đổi ảnh đại diện"
          >
            {isUploadingAvatar ? (
              <Loader2 size={14} className="animate-spin text-gray-600" />
            ) : (
              <Camera size={14} className="text-gray-600" />
            )}
          </button>
        </div>
        <div className="text-center">
          <h2 className="text-lg font-bold text-gray-900">{formatDisplayName((user as any)?.fullName, 'Chưa đặt tên')}</h2>
          <span className="inline-block mt-1 px-3 py-1 rounded-full bg-[#2db84c]/10 text-[#2db84c] text-xs font-semibold">
            {user?.role === 'SUPPLIER' ? 'Nhà cung cấp' : user?.role === 'ORGANIZATION' ? 'Tổ chức' : 'Người nhận'}
          </span>
        </div>
      </motion.div>

      <motion.div
        variants={fadeUp} initial="hidden" animate="show" transition={{ delay: 0.1 }}
        className="bg-white rounded-2xl border border-gray-100 p-6"
      >
        <div className="flex items-center justify-between mb-5">
          <h3 className="font-semibold text-gray-900">Thông tin cá nhân</h3>
          {!isEditing ? (
            <button
              onClick={() => setIsEditing(true)}
              className="text-sm text-[#2db84c] font-medium cursor-pointer hover:underline"
            >
              Chỉnh sửa
            </button>
          ) : (
            <button
              onClick={() => setIsEditing(false)}
              className="text-sm text-gray-500 font-medium cursor-pointer hover:underline"
            >
              Hủy
            </button>
          )}
        </div>

        <div className="flex flex-col gap-4">
          <div>
            <label className="flex items-center gap-2 text-sm font-medium text-gray-600 mb-1.5">
              <User size={14} /> Họ tên
            </label>
            <input
              type="text"
              value={form.fullName}
              onChange={e => setForm({ ...form, fullName: e.target.value })}
              disabled={!isEditing}
              className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all disabled:bg-gray-50 disabled:text-gray-500"
            />
          </div>
          <div>
            <label className="flex items-center gap-2 text-sm font-medium text-gray-600 mb-1.5">
              <Phone size={14} /> Số điện thoại
            </label>
            <input
              type="tel"
              value={form.phone}
              onChange={e => setForm({ ...form, phone: e.target.value })}
              disabled={!isEditing}
              className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all disabled:bg-gray-50 disabled:text-gray-500"
            />
          </div>
          <div>
            <label className="flex items-center gap-2 text-sm font-medium text-gray-600 mb-1.5">
              <Mail size={14} /> Email
            </label>
            <input
              type="email"
              value={form.email}
              disabled
              className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm text-gray-500 bg-gray-50"
            />
          </div>
          <LocationField
            label="Địa chỉ"
            value={{
              address: form.specificAddress,
              latitude: form.latitude,
              longitude: form.longitude,
            }}
            onChange={location => setForm(current => ({
              ...current,
              specificAddress: location.address,
              latitude: location.latitude,
              longitude: location.longitude,
            }))}
            disabled={!isEditing}
            required
          />
        </div>

        {isEditing && (
          <button
            onClick={handleSave}
            disabled={isLoading}
            className="mt-5 w-full py-3 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 disabled:opacity-70 flex items-center justify-center gap-2"
          >
            {isLoading ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
            {isLoading ? 'Đang lưu...' : 'Lưu thay đổi'}
          </button>
        )}
      </motion.div>

      <motion.div variants={fadeUp} initial="hidden" animate="show" transition={{ delay: 0.15 }} className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
        <div 
          onClick={() => navigate(`/${user?.role?.toLowerCase() || 'recipient'}/reports`)} 
          className="w-full flex items-center justify-between px-6 py-4 cursor-pointer hover:bg-gray-50 transition-colors border-b border-gray-100"
        >
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-full bg-orange-50 flex items-center justify-center"><Flag size={16} className="text-orange-500" /></div>
            <span className="font-medium text-gray-700 text-sm">Lịch sử khiếu nại & hỗ trợ</span>
          </div>
          <ChevronRight size={18} className="text-gray-400" />
        </div>
      </motion.div>

      <motion.div
        variants={fadeUp} initial="hidden" animate="show" transition={{ delay: 0.2 }}
      >
        <button
          onClick={() => {
            logout();
            navigate('/auth/login', { replace: true });
          }}
          className="w-full bg-white border border-red-100 text-red-500 hover:bg-red-50 rounded-2xl py-3.5 px-4 flex items-center justify-center gap-2 font-medium transition-colors cursor-pointer"
        >
          <LogOut size={18} /> Đăng xuất
        </button>
      </motion.div>
    </div>
  );
}
