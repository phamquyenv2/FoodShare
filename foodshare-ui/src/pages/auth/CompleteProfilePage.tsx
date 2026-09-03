import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Store, Heart, Building2 } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { apiFetch } from '../../services/api';

const ROLES = [
  { value: 'SUPPLIER', label: 'Nhà cung cấp', desc: 'Chia sẻ thực phẩm dư thừa', icon: Store, color: '#f59e0b' },
  { value: 'RECIPIENT', label: 'Người nhận', desc: 'Nhận thực phẩm miễn phí', icon: Heart, color: '#ef4444' },
  { value: 'ORGANIZATION', label: 'Tổ chức', desc: 'Quản lý phân phối', icon: Building2, color: '#3b82f6' },
];

export default function CompleteProfilePage() {
  const { user, checkAuth } = useAuth();
  const navigate = useNavigate();
  const [step, setStep] = useState(2);
  const [selectedRole, setSelectedRole] = useState(user?.role || 'RECIPIENT');
  const [form, setForm] = useState({
    phone: '',
    specificAddress: '',
  });
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (user?.profileCompleted) {
      const fallback = user.role === 'ADMIN' ? '/admin' : '/supplier';
      navigate(fallback);
    }
  }, [user, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    try {
      await apiFetch('/users/me/profile', {
        method: 'PUT',
        body: JSON.stringify({
          phone: form.phone || user?.phone || '0900000000',
          specificAddress: form.specificAddress,
          role: selectedRole,
          latitude: 21.0285,
          longitude: 105.8542,
          name: user?.fullName || 'Tên của bạn',
          ...(selectedRole === 'SUPPLIER' ? { supplierType: 'INDIVIDUAL' } : {}),
          ...(selectedRole === 'ORGANIZATION' ? { organizationType: 'OTHER' } : {})
        }),
      });

      // Update auth context
      await checkAuth();
      const fallback = selectedRole === 'ADMIN' ? '/admin' : '/supplier';
      navigate(fallback);
    } catch (err: any) {
      setError(err.message || 'Cập nhật hồ sơ thất bại');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-8 max-w-md mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-1">Hoàn thiện hồ sơ</h1>
      <p className="text-sm text-gray-500 mb-6">
        {step === 1 ? 'Chọn vai trò của bạn trên FoodShare' : 'Cập nhật thông tin để tiếp tục'}
      </p>

      {/* Step indicator */}
      <div className="flex gap-2 mb-6">
        {[1, 2].map(s => (
          <div
            key={s}
            className={`flex-1 h-1.5 rounded-full transition-all ${
              s <= step ? 'bg-[#2db84c]' : 'bg-gray-200'
            }`}
          />
        ))}
      </div>

      {error && (
        <div className="p-3 bg-red-50 text-red-600 rounded-xl text-sm border border-red-100 mb-4">
          {error}
        </div>
      )}

      {step === 1 && (
        <div className="flex flex-col gap-3">
          {ROLES.map(({ value, label, desc, icon: Icon, color }) => (
            <button
              key={value}
              type="button"
              onClick={() => setSelectedRole(value)}
              className={`flex items-center gap-4 p-4 rounded-xl border-2 text-left transition-all cursor-pointer ${
                selectedRole === value
                  ? 'border-[#2db84c] bg-[#2db84c]/5'
                  : 'border-gray-100 hover:border-gray-200'
              }`}
            >
              <div
                className="w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0"
                style={{ backgroundColor: `${color}15` }}
              >
                <Icon size={22} style={{ color }} />
              </div>
              <div>
                <p className={`font-semibold text-sm ${selectedRole === value ? 'text-[#2db84c]' : 'text-gray-900'}`}>
                  {label}
                </p>
                <p className="text-xs text-gray-500">{desc}</p>
              </div>
              <div className={`ml-auto w-5 h-5 rounded-full border-2 flex items-center justify-center flex-shrink-0 ${
                selectedRole === value ? 'border-[#2db84c]' : 'border-gray-300'
              }`}>
                {selectedRole === value && <div className="w-2.5 h-2.5 rounded-full bg-[#2db84c]" />}
              </div>
            </button>
          ))}

          <button
            type="button"
            onClick={() => setStep(2)}
            className="w-full py-3 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 mt-2"
          >
            Tiếp tục
          </button>
        </div>
      )}

      {step === 2 && (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {/* Show selected role */}
          <div className="flex items-center gap-3 p-3 bg-gray-50 rounded-xl">
            <span className="text-sm text-gray-600">Vai trò:</span>
            <span className="text-sm font-semibold text-[#2db84c]">
              {ROLES.find(r => r.value === selectedRole)?.label}
            </span>
            <button
              type="button"
              onClick={() => setStep(1)}
              className="ml-auto text-xs text-[#2db84c] font-medium hover:underline cursor-pointer"
            >
              Thay đổi
            </button>
          </div>

          {!user?.phone && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Số điện thoại <span className="text-red-500">*</span></label>
              <input
                type="tel"
                value={form.phone}
                onChange={e => setForm({ ...form, phone: e.target.value })}
                placeholder="0912345678"
                required
                className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all"
              />
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Địa chỉ cụ thể <span className="text-red-500">*</span></label>
            <input
              type="text"
              value={form.specificAddress}
              onChange={e => setForm({ ...form, specificAddress: e.target.value })}
              placeholder="Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội"
              required
              className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all"
            />
          </div>

          <div className="flex gap-3 mt-2">
            <button
              type="button"
              onClick={() => setStep(1)}
              className="flex-1 py-3 rounded-xl border border-gray-200 text-gray-600 font-semibold text-sm cursor-pointer hover:bg-gray-50 transition-all"
            >
              Quay lại
            </button>
            <button
              type="submit"
              disabled={isLoading}
              className="flex-1 py-3 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 disabled:opacity-70"
            >
              {isLoading ? 'Đang xử lý...' : 'Hoàn tất'}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
