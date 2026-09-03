import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Eye, EyeOff, Store, User, Building } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { apiFetch } from '../../services/api';
import type { UserRole } from '../../types';

const ROLES: { value: UserRole; label: string; desc: string; icon: React.ComponentType<any> }[] = [
  { value: 'SUPPLIER',     label: 'Nhà cung cấp',  desc: 'Chia sẻ thực phẩm dư thừa',    icon: Store },
  { value: 'RECIPIENT',    label: 'Người nhận',     desc: 'Nhận thực phẩm miễn phí / giá rẻ', icon: User },
  { value: 'ORGANIZATION', label: 'Tổ chức',        desc: 'Thu gom & phân phối quy mô lớn',  icon: Building },
];

export default function RegisterPage() {
  const [step, setStep] = useState<1 | 2>(1);
  const [role, setRole] = useState<UserRole>('RECIPIENT');
  const [form, setForm] = useState({ name: '', email: '', phone: '', password: '' });
  const [showPw, setShowPw] = useState(false);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    try {
      const payload = {
        phone: form.phone,
        password: form.password,
        fullName: form.name,
        email: form.email || undefined,
        role: role,
      };

      const res = await apiFetch<any>('/auth/register', {
        method: 'POST',
        body: JSON.stringify(payload),
      });

      login(res.accessToken, res);
      let fallback = '/supplier';
      if (res.role === 'ADMIN') fallback = '/admin';
      else if (res.role === 'RECIPIENT') fallback = '/recipient';
      else if (res.role === 'ORGANIZATION') fallback = '/organization';
      navigate(fallback);
    } catch (err: any) {
      setError(err.message || 'Đăng ký thất bại');
    } finally {
      setIsLoading(false);
    }
  };

  if (step === 1) {
    return (
      <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-8">
        <h1 className="text-2xl font-bold text-gray-900 mb-1">Tạo tài khoản</h1>
        <p className="text-sm text-gray-500 mb-6">Bạn muốn tham gia với vai trò nào?</p>
        <div className="flex flex-col gap-3 mb-6">
          {ROLES.map(r => (
            <button
              key={r.value}
              onClick={() => setRole(r.value)}
              className={`w-full flex items-center gap-4 p-4 rounded-xl border-2 cursor-pointer transition-all text-left
                ${role === r.value
                  ? 'border-[#2db84c] bg-green-50'
                  : 'border-gray-100 hover:border-gray-200'
                }`}
            >
              <div className={`w-11 h-11 rounded-xl flex items-center justify-center ${role === r.value ? 'bg-[#2db84c] text-white' : 'bg-gray-100 text-gray-500'}`}>
                <r.icon size={20} />
              </div>
              <div>
                <p className={`font-semibold text-sm ${role === r.value ? 'text-[#2db84c]' : 'text-gray-900'}`}>{r.label}</p>
                <p className="text-xs text-gray-400 mt-0.5">{r.desc}</p>
              </div>
            </button>
          ))}
        </div>
        <button
          onClick={() => setStep(2)}
          className="w-full py-3 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20"
        >
          Tiếp tục
        </button>
        <p className="text-center text-sm text-gray-500 mt-6">
          Đã có tài khoản?{' '}
          <Link to="/auth/login" className="text-[#2db84c] font-semibold hover:underline">Đăng nhập</Link>
        </p>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-8">
      <button onClick={() => setStep(1)} className="text-sm text-gray-400 hover:text-gray-600 cursor-pointer mb-4">← Quay lại</button>
      <h1 className="text-2xl font-bold text-gray-900 mb-1">Thông tin cá nhân</h1>
      <p className="text-sm text-gray-500 mb-6">Điền thông tin để hoàn tất đăng ký</p>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {error && (
          <div className="p-3 bg-red-50 text-red-600 rounded-xl text-sm border border-red-100">
            {error}
          </div>
        )}
        
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Họ và tên</label>
          <input type="text" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })}
            placeholder="Nguyễn Văn A" className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all" />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Email</label>
          <input type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })}
            placeholder="email@example.com" className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all" />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Số điện thoại</label>
          <input type="tel" value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })}
            placeholder="0912345678" className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all" />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Mật khẩu</label>
          <div className="relative">
            <input type={showPw ? 'text' : 'password'} value={form.password} onChange={e => setForm({ ...form, password: e.target.value })}
              placeholder="Tối thiểu 8 ký tự" className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all pr-12" />
            <button type="button" onClick={() => setShowPw(!showPw)} className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 cursor-pointer">
              {showPw ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </div>
        </div>
        <button 
          type="submit" 
          disabled={isLoading}
          className="w-full py-3 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 mt-2 disabled:opacity-70"
        >
          {isLoading ? 'Đang xử lý...' : 'Đăng ký'}
        </button>
      </form>
    </div>
  );
}
