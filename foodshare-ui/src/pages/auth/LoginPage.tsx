import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Eye, EyeOff } from 'lucide-react';
import { GoogleLogin } from '@react-oauth/google';
import { useAuth } from '../../contexts/AuthContext';
import { useToast } from '../../contexts/ToastContext';
import { getRoleHome } from '../../utils/roleHome';
import { apiFetch, clearAccessToken, setAccessToken } from '../../services/api';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [invalidField, setInvalidField] = useState<'identifier' | 'password' | 'all' | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const { login, isAuthenticated, user } = useAuth();
  const { showError } = useToast();
  const navigate = useNavigate();

  useEffect(() => {
    if (isAuthenticated && user) {
      navigate(getRoleHome(user.role), { replace: true });
    }
  }, [isAuthenticated, user, navigate]);

  const handleSuccess = (user: any) => {
    // If profile is not completed, AuthGuard will redirect them
    navigate(getRoleHome(user.role));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    try {
      // The backend uses phone for login, but UI shows email right now.
      // Let's assume the field is either phone or email, but map it to phone for API
      const res = await apiFetch<any>('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ identifier: email, password }),
      });

      setAccessToken(res.accessToken);
      const fullUser = await apiFetch<any>('/users/me');

      login(res.accessToken, fullUser);
      handleSuccess(fullUser);
    } catch (err: any) {
      clearAccessToken();
      const message = err.message || 'Đăng nhập thất bại';
      const normalized = message.toLowerCase();
      if (normalized.includes('mật khẩu') && !normalized.includes('tài khoản')) {
        setInvalidField('password');
      } else if (normalized.includes('tài khoản') || normalized.includes('số điện thoại')) {
        setInvalidField('identifier');
      } else {
        setInvalidField('all');
      }
      showError(message);
    } finally {
      setIsLoading(false);
    }
  };

  const handleGoogleSuccess = async (credentialResponse: any) => {
    setIsLoading(true);
    try {
      const res = await apiFetch<any>('/auth/google', {
        method: 'POST',
        body: JSON.stringify({ idToken: credentialResponse.credential }),
      });

      setAccessToken(res.accessToken);
      const fullUser = await apiFetch<any>('/users/me');

      login(res.accessToken, fullUser);
      handleSuccess(fullUser);
    } catch (err: any) {
      clearAccessToken();
      showError(err.message || 'Đăng nhập Google thất bại');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-1">Đăng nhập</h1>
      <p className="text-sm text-gray-500 mb-8">Chào mừng bạn quay trở lại FoodShare</p>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Số điện thoại</label>
          <input
            type="text"
            value={email}
            onChange={e => { setEmail(e.target.value); setInvalidField(null); }}
            placeholder="0912345678"
            required
            className={`w-full px-4 py-3 rounded-xl border text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 transition-all bg-white ${
              invalidField === 'identifier' || invalidField === 'all'
                ? 'border-red-500 focus:border-red-500 focus:ring-red-100'
                : 'border-gray-200 focus:border-[#2db84c] focus:ring-[#2db84c]/30'
            }`}
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Mật khẩu</label>
          <div className="relative">
            <input
              type={showPw ? 'text' : 'password'}
              value={password}
              onChange={e => { setPassword(e.target.value); setInvalidField(null); }}
              placeholder="••••••••"
              className={`w-full px-4 py-3 rounded-xl border text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 transition-all bg-white pr-12 ${
                invalidField === 'password' || invalidField === 'all'
                  ? 'border-red-500 focus:border-red-500 focus:ring-red-100'
                  : 'border-gray-200 focus:border-[#2db84c] focus:ring-[#2db84c]/30'
              }`}
            />
            <button
              type="button"
              onClick={() => setShowPw(!showPw)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 cursor-pointer hover:text-gray-600"
            >
              {showPw ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </div>
        </div>

        <button
          type="submit"
          disabled={isLoading}
          className="w-full py-3 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 disabled:opacity-70"
        >
          {isLoading ? 'Đang xử lý...' : 'Đăng nhập'}
        </button>

        <div className="relative my-2">
          <div className="absolute inset-0 flex items-center"><div className="w-full border-t border-gray-200" /></div>
          <div className="relative flex justify-center"><span className="bg-white px-3 text-xs text-gray-400">hoặc</span></div>
        </div>

        <div className="flex justify-center">
          <GoogleLogin
            onSuccess={handleGoogleSuccess}
            onError={() => showError('Đăng nhập Google thất bại')}
            width="100%"
            text="signin_with"
            shape="pill"
          />
        </div>
      </form>

      <p className="text-center text-sm text-gray-500 mt-6">
        Chưa có tài khoản?{' '}
        <Link to="/auth/register" className="text-[#2db84c] font-semibold hover:underline">Đăng ký ngay</Link>
      </p>
    </div>
  );
}
