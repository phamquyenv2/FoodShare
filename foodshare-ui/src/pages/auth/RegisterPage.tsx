import { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, Building, Eye, EyeOff, RotateCw, ShieldCheck, Store, User } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { useToast } from '../../contexts/ToastContext';
import { apiFetch } from '../../services/api';
import { getAuthenticatedHome } from '../../utils/roleHome';
import type { UserRole } from '../../types';

type RegisterStep = 'PHONE' | 'OTP' | 'ROLE' | 'ACCOUNT';

interface OtpChallengeResponse {
  challengeToken: string;
  maskedPhone: string;
  expiresInSeconds: number;
}

interface PhoneVerificationResponse {
  registrationToken: string;
  phone: string;
  expiresInSeconds: number;
}

const ROLES: { value: UserRole; label: string; desc: string; icon: React.ComponentType<any> }[] = [
  { value: 'SUPPLIER', label: 'Nhà cung cấp', desc: 'Chia sẻ thực phẩm dư thừa', icon: Store },
  { value: 'RECIPIENT', label: 'Người nhận', desc: 'Nhận thực phẩm miễn phí / giá rẻ', icon: User },
  { value: 'ORGANIZATION', label: 'Tổ chức', desc: 'Thu gom & phân phối quy mô lớn', icon: Building },
];

const STEP_NUMBER: Record<RegisterStep, number> = { PHONE: 1, OTP: 2, ROLE: 3, ACCOUNT: 4 };

function RegistrationHeader({ step, title, description, back }: {
  step: RegisterStep;
  title: string;
  description: string;
  back?: () => void;
}) {
  return (
    <>
      {back && (
        <button type="button" onClick={back} className="mb-3 flex items-center gap-1 text-sm text-gray-400 hover:text-gray-600">
          <ArrowLeft size={15} /> Quay lại
        </button>
      )}
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{title}</h1>
          <p className="mt-1 text-sm text-gray-500">{description}</p>
        </div>
        <span className="shrink-0 rounded-full bg-green-50 px-2.5 py-1 text-xs font-semibold text-[#2db84c]">
          {STEP_NUMBER[step]}/4
        </span>
      </div>
      <div className="mb-5 grid grid-cols-4 gap-1.5">
        {[1, 2, 3, 4].map(number => (
          <div key={number} className={`h-1 rounded-full ${number <= STEP_NUMBER[step] ? 'bg-[#2db84c]' : 'bg-gray-100'}`} />
        ))}
      </div>
    </>
  );
}

export default function RegisterPage() {
  const [step, setStep] = useState<RegisterStep>('PHONE');
  const [phone, setPhone] = useState('');
  const [verifiedPhone, setVerifiedPhone] = useState('');
  const [challengeToken, setChallengeToken] = useState('');
  const [registrationToken, setRegistrationToken] = useState('');
  const [otpDigits, setOtpDigits] = useState(['', '', '', '']);
  const [resendSeconds, setResendSeconds] = useState(0);
  const [role, setRole] = useState<UserRole>('RECIPIENT');
  const [form, setForm] = useState({ name: '', email: '', password: '' });
  const [showPw, setShowPw] = useState(false);
  const [invalidField, setInvalidField] = useState<keyof typeof form | 'phone' | 'otp' | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const { login } = useAuth();
  const { showError, showSuccess } = useToast();
  const navigate = useNavigate();
  const otp = otpDigits.join('');

  useEffect(() => {
    if (resendSeconds <= 0) return;
    const timer = window.setInterval(() => setResendSeconds(current => Math.max(0, current - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [resendSeconds]);

  const updateField = (field: keyof typeof form, value: string) => {
    setForm(current => ({ ...current, [field]: value }));
    if (invalidField === field) setInvalidField(null);
  };

  const sendOtp = async () => {
    setIsLoading(true);
    try {
      const response = await apiFetch<OtpChallengeResponse>('/auth/phone-otp/send', {
        method: 'POST',
        body: JSON.stringify({ phone }),
      });
      setChallengeToken(response.challengeToken);
      setOtpDigits(['', '', '', '']);
      setInvalidField(null);
      setResendSeconds(60);
      setStep('OTP');
      showSuccess(`Mã OTP đã được gửi đến ${response.maskedPhone}`);
    } catch (error) {
      setInvalidField('phone');
      showError(error instanceof Error ? error.message : 'Không thể gửi OTP');
    } finally {
      setIsLoading(false);
    }
  };

  const handlePhoneSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    await sendOtp();
  };

  const handleOtpSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setIsLoading(true);
    try {
      const response = await apiFetch<PhoneVerificationResponse>('/auth/phone-otp/verify', {
        method: 'POST',
        body: JSON.stringify({ challengeToken, otp }),
      });
      setVerifiedPhone(response.phone);
      setRegistrationToken(response.registrationToken);
      setInvalidField(null);
      setStep('ROLE');
      showSuccess('Số điện thoại đã được xác minh');
    } catch (error) {
      setInvalidField('otp');
      showError(error instanceof Error ? error.message : 'OTP không đúng hoặc đã hết hạn');
    } finally {
      setIsLoading(false);
    }
  };

  const focusOtpDigit = (index: number) => {
    if (typeof document !== 'undefined') document.getElementById(`register-otp-${index}`)?.focus();
  };

  const updateOtpDigit = (index: number, value: string) => {
    const numbers = value.replace(/\D/g, '');
    if (!numbers) {
      setOtpDigits(current => current.map((digit, position) => position === index ? '' : digit));
      return;
    }

    setOtpDigits(current => {
      const next = [...current];
      numbers.slice(0, 4 - index).split('').forEach((digit, offset) => {
        next[index + offset] = digit;
      });
      return next;
    });
    setInvalidField(null);
    focusOtpDigit(Math.min(index + numbers.length, 3));
  };

  const handleOtpKeyDown = (index: number, event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Backspace' && !otpDigits[index] && index > 0) focusOtpDigit(index - 1);
    if (event.key === 'ArrowLeft' && index > 0) focusOtpDigit(index - 1);
    if (event.key === 'ArrowRight' && index < 3) focusOtpDigit(index + 1);
  };

  const handleRegister = async (event: React.FormEvent) => {
    event.preventDefault();
    setIsLoading(true);
    try {
      const response = await apiFetch<any>('/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          registrationToken,
          phone: verifiedPhone,
          password: form.password,
          fullName: form.name,
          email: form.email || undefined,
          role,
        }),
      });

      login(response.accessToken, response);
      navigate(getAuthenticatedHome(response));
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Đăng ký thất bại';
      const normalized = message.toLocaleLowerCase('vi');
      if (normalized.includes('email')) setInvalidField('email');
      else if (normalized.includes('password') || normalized.includes('mật khẩu')) setInvalidField('password');
      else if (normalized.includes('họ tên')) setInvalidField('name');
      else if (normalized.includes('điện thoại') || normalized.includes('xác minh')) {
        setRegistrationToken('');
        setStep('PHONE');
        setInvalidField('phone');
      }
      showError(message);
    } finally {
      setIsLoading(false);
    }
  };

  const goBackToPhone = () => {
    setStep('PHONE');
    setChallengeToken('');
    setRegistrationToken('');
    setOtpDigits(['', '', '', '']);
    setInvalidField(null);
  };

  return (
    <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm">
      {step === 'PHONE' && (
        <>
          <RegistrationHeader step={step} title="Đăng ký FoodShare" description="Nhập số điện thoại để bắt đầu" />
          <form noValidate onSubmit={handlePhoneSubmit} className="flex flex-col gap-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium text-gray-700">Số điện thoại</label>
              <input
                autoFocus type="tel" value={phone}
                onChange={event => { setPhone(event.target.value); setInvalidField(null); }}
                placeholder="0912345678" required maxLength={12}
                pattern="(?:\+84|0)(3|5|7|8|9)[0-9]{8}"
                className={`w-full rounded-xl border px-4 py-3 text-sm text-gray-900 focus:outline-none focus:ring-2 ${invalidField === 'phone' ? 'border-red-500 focus:border-red-500 focus:ring-red-100' : 'border-gray-200 focus:border-[#2db84c] focus:ring-[#2db84c]/30'}`}
              />
              <p className="mt-1.5 text-xs text-gray-400">Chúng tôi sẽ gửi mã OTP gồm 4 chữ số.</p>
            </div>
            <button disabled={isLoading} className="w-full rounded-xl bg-[#2db84c] py-3 text-sm font-semibold text-white shadow-md shadow-green-500/20 disabled:opacity-60">
              {isLoading ? 'Đang gửi...' : 'Gửi mã OTP'}
            </button>
          </form>
        </>
      )}

      {step === 'OTP' && (
        <>
          <RegistrationHeader step={step} title="Xác minh số điện thoại" description="Nhập mã OTP gồm 4 số" back={goBackToPhone} />
          <form noValidate onSubmit={handleOtpSubmit} className="flex flex-col gap-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium text-gray-700">Mã OTP</label>
              <div className="flex justify-center gap-3">
                {otpDigits.map((digit, index) => (
                  <input
                    key={index} id={`register-otp-${index}`} autoFocus={index === 0}
                    type="text" inputMode="numeric" value={digit}
                    onChange={event => updateOtpDigit(index, event.target.value)}
                    onPaste={event => {
                      event.preventDefault();
                      updateOtpDigit(index, event.clipboardData.getData('text'));
                    }}
                    onKeyDown={event => handleOtpKeyDown(index, event)}
                    maxLength={1} required aria-label={`Mã OTP số ${index + 1}`}
                    aria-invalid={invalidField === 'otp'}
                    autoComplete={index === 0 ? 'one-time-code' : 'off'}
                    className={`h-14 w-14 rounded-xl border text-center text-xl font-semibold text-gray-900 focus:outline-none focus:ring-2 ${invalidField === 'otp' ? 'border-red-400 focus:border-red-400 focus:ring-red-200' : 'border-gray-200 focus:border-[#2db84c] focus:ring-[#2db84c]/30'}`}
                  />
                ))}
              </div>
              <p className="mt-2 text-center text-xs text-gray-500">
                Mã đã được gửi đến <span className="font-semibold text-gray-700">{phone}</span>
              </p>
            </div>
            <button disabled={isLoading || otp.length !== 4} className="w-full rounded-xl bg-[#2db84c] py-3 text-sm font-semibold text-white shadow-md shadow-green-500/20 disabled:opacity-60 transition-all">
              {isLoading ? 'Đang xác minh...' : 'Xác minh OTP'}
            </button>
            <div className="text-center">
              {resendSeconds > 0 ? (
                <p className="text-xs text-gray-400">
                  Chưa nhận được mã? Gửi lại sau <span className="font-semibold text-gray-600 tabular-nums">{resendSeconds}s</span>
                </p>
              ) : (
                <button
                  type="button"
                  disabled={isLoading}
                  onClick={sendOtp}
                  className="inline-flex items-center gap-1.5 text-sm font-medium text-[#2db84c] hover:underline disabled:opacity-50"
                >
                  <RotateCw size={13} className={isLoading ? "animate-spin" : ""} />
                  Gửi lại mã OTP
                </button>
              )}
            </div>
          </form>
        </>
      )}

      {step === 'ROLE' && (
        <>
          <RegistrationHeader step={step} title="Chọn vai trò" description="Bạn muốn tham gia FoodShare với vai trò nào?" back={() => setStep('OTP')} />
          <div className="mb-5 flex flex-col gap-2.5">
            {ROLES.map(item => (
              <button key={item.value} type="button" onClick={() => setRole(item.value)}
                className={`flex w-full items-center gap-3 rounded-xl border-2 p-3 text-left transition-all ${role === item.value ? 'border-[#2db84c] bg-green-50' : 'border-gray-100 hover:border-gray-200'}`}>
                <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${role === item.value ? 'bg-[#2db84c] text-white' : 'bg-gray-100 text-gray-500'}`}>
                  <item.icon size={19} />
                </div>
                <div><p className="text-sm font-semibold text-gray-900">{item.label}</p><p className="mt-0.5 text-xs text-gray-400">{item.desc}</p></div>
              </button>
            ))}
          </div>
          <button type="button" onClick={() => setStep('ACCOUNT')} className="w-full rounded-xl bg-[#2db84c] py-3 text-sm font-semibold text-white">Tiếp tục</button>
        </>
      )}

      {step === 'ACCOUNT' && (
        <>
          <RegistrationHeader step={step} title="Thông tin tài khoản" description={`Số điện thoại ${verifiedPhone} đã được xác minh`} back={() => setStep('ROLE')} />
          <div className="mb-3 flex items-center gap-2 rounded-xl bg-green-50 px-3 py-2 text-xs font-medium text-green-700">
            <ShieldCheck size={16} /> Số điện thoại đã xác minh
          </div>
          <form noValidate onSubmit={handleRegister} className="flex flex-col gap-3">
            <input type="text" value={form.name} onChange={event => { updateField('name', event.target.value); setInvalidField(null); }}
              placeholder="Họ và tên" maxLength={100} aria-label="Họ và tên" aria-invalid={invalidField === 'name'}
              className={`w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:ring-2 ${invalidField === 'name' ? 'border-red-500 focus:border-red-500 focus:ring-red-100' : 'border-gray-200 focus:border-[#2db84c] focus:ring-[#2db84c]/30'}`} />
            <div>
              <input type="email" value={form.email} onChange={event => { updateField('email', event.target.value); setInvalidField(null); }}
                placeholder="Email nhận thông báo (có thể thêm sau)" maxLength={100} aria-label="Email" aria-invalid={invalidField === 'email'}
                className={`w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:ring-2 ${invalidField === 'email' ? 'border-red-500 focus:border-red-500 focus:ring-red-100' : 'border-gray-200 focus:border-[#2db84c] focus:ring-[#2db84c]/30'}`} />
              <p className="mt-1 text-[11px] text-gray-400">Bạn có thể bổ sung email trong hồ sơ sau.</p>
            </div>
            <div className="relative">
              <input type={showPw ? 'text' : 'password'} value={form.password} onChange={event => { updateField('password', event.target.value); setInvalidField(null); }}
                placeholder="Mật khẩu từ 8 ký tự" maxLength={72} aria-label="Mật khẩu" aria-invalid={invalidField === 'password'}
                className={`w-full rounded-xl border px-4 py-2.5 pr-12 text-sm focus:outline-none focus:ring-2 ${invalidField === 'password' ? 'border-red-500 focus:border-red-500 focus:ring-red-100' : 'border-gray-200 focus:border-[#2db84c] focus:ring-[#2db84c]/30'}`} />
              <button type="button" onClick={() => setShowPw(current => !current)} aria-label="Hiện hoặc ẩn mật khẩu"
                className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400">
                {showPw ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            <button disabled={isLoading} className="mt-1 w-full rounded-xl bg-[#2db84c] py-3 text-sm font-semibold text-white shadow-md shadow-green-500/20 disabled:opacity-60">
              {isLoading ? 'Đang tạo tài khoản...' : 'Tạo tài khoản'}
            </button>
          </form>
        </>
      )}

      <p className="mt-5 text-center text-sm text-gray-500">
        Đã có tài khoản? <Link to="/auth/login" className="font-semibold text-[#2db84c] hover:underline">Đăng nhập</Link>
      </p>
    </div>
  );
}
