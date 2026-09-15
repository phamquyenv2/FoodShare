import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Store, Heart, Building2, Paperclip, X, ArrowLeft, RotateCw } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { useToast } from '../../contexts/ToastContext';
import { getRoleHome } from '../../utils/roleHome';
import { apiFetch } from '../../services/api';
import LocationField from '../../components/shared/LocationField';
import Logo from '../../components/shared/Logo';

const ROLES = [
  { value: 'SUPPLIER', label: 'Nhà cung cấp', desc: 'Chia sẻ thực phẩm dư thừa', icon: Store, color: '#f59e0b' },
  { value: 'RECIPIENT', label: 'Người nhận', desc: 'Nhận thực phẩm miễn phí', icon: Heart, color: '#ef4444' },
  { value: 'ORGANIZATION', label: 'Tổ chức', desc: 'Quản lý phân phối', icon: Building2, color: '#3b82f6' },
] as const;

const SUPPLIER_TYPES = [
  { value: 'INDIVIDUAL', label: 'Cá nhân' },
  { value: 'RESTAURANT', label: 'Nhà hàng' },
  { value: 'BAKERY', label: 'Tiệm bánh' },
  { value: 'GROCERY_STORE', label: 'Cửa hàng thực phẩm' },
  { value: 'SUPERMARKET', label: 'Siêu thị' },
  { value: 'OTHER', label: 'Khác' },
] as const;

const ORGANIZATION_TYPES = [
  { value: 'CHARITY', label: 'Tổ chức từ thiện' },
  { value: 'ORPHANAGE', label: 'Trại trẻ mồ côi' },
  { value: 'NURSING_HOME', label: 'Viện dưỡng lão' },
  { value: 'RELIGIOUS', label: 'Tổ chức tôn giáo' },
  { value: 'COMMUNITY_GROUP', label: 'Nhóm cộng đồng' },
  { value: 'OTHER', label: 'Khác' },
] as const;

const MAX_DOCUMENTS = Number(import.meta.env.VITE_MAX_DOCUMENTS) || 5;
const MAX_DOCUMENT_SIZE_MB = Number(import.meta.env.VITE_MAX_DOCUMENT_SIZE_MB) || 10;
const MAX_DOCUMENT_SIZE = MAX_DOCUMENT_SIZE_MB * 1024 * 1024;
const ALLOWED_DOCUMENT_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif']);

export default function CompleteProfilePage() {
  const { user, checkAuth, logout } = useAuth();
  const { showError, showSuccess } = useToast();
  const navigate = useNavigate();
  const [step, setStep] = useState(2);
  const [selectedRole, setSelectedRole] = useState(user?.role || 'RECIPIENT');
  const [form, setForm] = useState({
    phone: '',
    specificAddress: user?.specificAddress || '',
    latitude: user?.latitude ?? null,
    longitude: user?.longitude ?? null,
    businessName: user?.profile?.name || user?.fullName || '',
    description: user?.profile?.description || '',
    taxCode: user?.profile?.taxCode || '',
    supplierType: user?.profile?.supplierType || 'INDIVIDUAL',
    organizationType: user?.profile?.organizationType || 'OTHER',
  });
  const [documents, setDocuments] = useState<File[]>([]);
  const [documentsInvalid, setDocumentsInvalid] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [phoneOtpDigits, setPhoneOtpDigits] = useState(['', '', '', '']);
  const [phoneChallengeToken, setPhoneChallengeToken] = useState('');
  const [phoneRegistrationToken, setPhoneRegistrationToken] = useState('');
  const [resendSeconds, setResendSeconds] = useState(0);
  const phoneOtp = phoneOtpDigits.join('');

  useEffect(() => {
    if (resendSeconds <= 0) return;
    const timer = window.setInterval(() => {
      setResendSeconds(current => (current > 0 ? current - 1 : 0));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [resendSeconds]);

  useEffect(() => {
    const businessProfileMissingDocuments =
      (user?.role === 'SUPPLIER' || user?.role === 'ORGANIZATION')
      && (user.profile?.licenseUrls?.length ?? 0) === 0;
    if (user?.profileCompleted && !businessProfileMissingDocuments) {
      const fallback = getRoleHome(user.role);
      navigate(fallback);
    }
  }, [user, navigate]);

  const isBusinessProfile = selectedRole === 'SUPPLIER' || selectedRole === 'ORGANIZATION';

  const sendPhoneOtp = async (event: React.FormEvent) => {
    event.preventDefault();
    setIsLoading(true);
    try {
      const response = await apiFetch<{ challengeToken: string; maskedPhone: string }>('/auth/phone-otp/send', {
        method: 'POST',
        body: JSON.stringify({ phone: form.phone }),
      });
      setPhoneChallengeToken(response.challengeToken);
      setPhoneOtpDigits(['', '', '', '']);
      setResendSeconds(60);
      showSuccess(`Mã OTP đã được gửi đến ${response.maskedPhone}`);
    } catch (error) {
      showError(error instanceof Error ? error.message : 'Không thể gửi OTP');
    } finally {
      setIsLoading(false);
    }
  };

  const verifyPhoneOtp = async (event: React.FormEvent) => {
    event.preventDefault();
    setIsLoading(true);
    try {
      const response = await apiFetch<{ registrationToken: string; phone: string }>('/auth/phone-otp/verify', {
        method: 'POST',
        body: JSON.stringify({ challengeToken: phoneChallengeToken, otp: phoneOtp }),
      });
      setForm(current => ({ ...current, phone: response.phone }));
      setPhoneRegistrationToken(response.registrationToken);
      showSuccess('Số điện thoại đã được xác minh');
    } catch (error) {
      showError(error instanceof Error ? error.message : 'OTP không đúng hoặc đã hết hạn');
    } finally {
      setIsLoading(false);
    }
  };

  const focusPhoneOtpDigit = (index: number) => {
    if (typeof document !== 'undefined') document.getElementById(`profile-phone-otp-${index}`)?.focus();
  };

  const updatePhoneOtpDigit = (index: number, value: string) => {
    const numbers = value.replace(/\D/g, '');
    if (!numbers) {
      setPhoneOtpDigits(current => current.map((digit, position) => position === index ? '' : digit));
      return;
    }
    setPhoneOtpDigits(current => {
      const next = [...current];
      numbers.slice(0, 4 - index).split('').forEach((digit, offset) => {
        next[index + offset] = digit;
      });
      return next;
    });
    focusPhoneOtpDigit(Math.min(index + numbers.length, 3));
  };

  const handleDocumentChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFiles = Array.from(event.target.files || []);
    event.target.value = '';
    if (documents.length + selectedFiles.length > MAX_DOCUMENTS) {
      showError(`Chỉ được tải lên tối đa ${MAX_DOCUMENTS} giấy tờ`);
      return;
    }
    const invalidFile = selectedFiles.find(file =>
      !ALLOWED_DOCUMENT_TYPES.has(file.type) || file.size > MAX_DOCUMENT_SIZE
    );
    if (invalidFile) {
      showError(`Giấy tờ phải là ảnh JPEG, PNG, WebP hoặc GIF và không vượt quá ${MAX_DOCUMENT_SIZE_MB} MB`);
      return;
    }
    setDocuments(current => [...current, ...selectedFiles]);
    setDocumentsInvalid(false);
  };

  const uploadDocuments = async () => Promise.all(documents.map(async file => {
    const body = new FormData();
    body.append('file', file);
    const response = await apiFetch<{ url: string }>('/media/upload/business-document', {
      method: 'POST',
      body,
    });
    return response.url;
  }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isBusinessProfile && documents.length === 0) {
      setDocumentsInvalid(true);
      showError('Vui lòng tải lên ít nhất 1 giấy tờ xác minh');
      return;
    }
    setIsLoading(true);

    try {
      const licenseUrls = isBusinessProfile ? await uploadDocuments() : [];
      await apiFetch('/users/me/profile', {
        method: 'PUT',
        body: JSON.stringify({
          phone: form.phone || user?.phone,
          phoneRegistrationToken: phoneRegistrationToken || undefined,
          specificAddress: form.specificAddress,
          role: selectedRole,
          latitude: form.latitude,
          longitude: form.longitude,
          ...(selectedRole === 'SUPPLIER' || selectedRole === 'ORGANIZATION'
            ? {
              name: form.businessName,
              description: form.description || null,
              taxCode: form.taxCode || null,
              licenseUrls,
            } : {}),
          ...(selectedRole === 'SUPPLIER' ? { supplierType: form.supplierType } : {}),
          ...(selectedRole === 'ORGANIZATION' ? { organizationType: form.organizationType } : {})
        }),
      });

      // Update auth context
      await checkAuth();
      const fallback = getRoleHome(selectedRole);
      navigate(fallback);
    } catch (err: any) {
      showError(err.message || 'Cập nhật hồ sơ thất bại');
    } finally {
      setIsLoading(false);
    }
  };

  if (!user?.phone && !phoneRegistrationToken) {
    return (
      <div className="mx-auto w-full max-w-md rounded-2xl border border-gray-100 bg-white p-6 shadow-sm">
        <div className="mb-5 flex items-center gap-3">
          <div className="shrink-0 rounded-xl bg-green-50 p-2"><Logo size="md" showText={false} /></div>
          <div>
            <h1 className="text-xl font-bold text-gray-900">Xác minh số điện thoại</h1>
            <p className="mt-0.5 text-sm text-gray-500">Bắt buộc trước khi hoàn thiện hồ sơ</p>
          </div>
        </div>
        {!phoneChallengeToken ? (
          <form noValidate onSubmit={sendPhoneOtp} className="flex flex-col gap-4">
            <input type="tel" value={form.phone} autoFocus required maxLength={12}
              pattern="(?:\+84|0)(3|5|7|8|9)[0-9]{8}" autoComplete="tel"
              onChange={event => setForm(current => ({ ...current, phone: event.target.value }))}
              placeholder="0912345678"
              className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm focus:border-[#2db84c] focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30" />
            <button disabled={isLoading} className="rounded-xl bg-[#2db84c] py-3 text-sm font-semibold text-white disabled:opacity-60">
              {isLoading ? 'Đang gửi...' : 'Gửi mã OTP'}
            </button>
          </form>
        ) : (
          <form noValidate onSubmit={verifyPhoneOtp} className="flex flex-col gap-4">
            <div className="flex justify-center gap-3">
              {phoneOtpDigits.map((digit, index) => (
                <input key={index} id={`profile-phone-otp-${index}`} type="text" inputMode="numeric"
                  autoFocus={index === 0} required maxLength={1} value={digit}
                  aria-label={`Mã OTP số ${index + 1}`} autoComplete={index === 0 ? 'one-time-code' : 'off'}
                  onChange={event => updatePhoneOtpDigit(index, event.target.value)}
                  onPaste={event => {
                    event.preventDefault();
                    updatePhoneOtpDigit(index, event.clipboardData.getData('text'));
                  }}
                  onKeyDown={event => {
                    if (event.key === 'Backspace' && !phoneOtpDigits[index] && index > 0) focusPhoneOtpDigit(index - 1);
                  }}
                  className="h-14 w-14 rounded-xl border border-gray-200 text-center text-xl font-semibold focus:border-[#2db84c] focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30" />
              ))}
            </div>
            <p className="text-center text-xs text-gray-500">
              Mã đã được gửi đến <span className="font-semibold text-gray-700">{form.phone}</span>
            </p>

            <button disabled={isLoading || phoneOtp.length !== 4} className="rounded-xl bg-[#2db84c] py-3 text-sm font-semibold text-white shadow-md shadow-green-500/20 disabled:opacity-60 transition-all">
              {isLoading ? 'Đang xác minh...' : 'Xác minh OTP'}
            </button>
            <div className="flex items-center justify-between text-xs">
              <button type="button" onClick={() => setPhoneChallengeToken('')} className="text-gray-500 hover:text-gray-700">Đổi số điện thoại</button>
              {resendSeconds > 0 ? (
                <span className="text-gray-400">
                  Gửi lại sau <span className="font-semibold text-gray-600 tabular-nums">{resendSeconds}s</span>
                </span>
              ) : (
                <button
                  type="button"
                  disabled={isLoading}
                  onClick={e => sendPhoneOtp(e)}
                  className="inline-flex items-center gap-1 font-medium text-[#2db84c] hover:underline disabled:opacity-50"
                >
                  <RotateCw size={12} className={isLoading ? "animate-spin" : ""} />
                  Gửi lại mã OTP
                </button>
              )}
            </div>
          </form>
        )}
        <button type="button" onClick={logout} className="mt-5 w-full text-center text-sm text-gray-400 hover:text-gray-600">Đăng xuất</button>
      </div>
    );
  }

  return (
    <div className={`bg-white rounded-2xl shadow-sm border border-gray-100 mx-auto w-full ${step === 2 ? 'p-5 max-w-5xl' : 'p-8 max-w-md'}`}>
      <div className="flex items-center gap-3 mb-4">
        <div className="shrink-0 rounded-xl bg-green-50 p-2">
          <Logo size="md" showText={false} />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-gray-900 leading-tight">Hoàn thiện hồ sơ</h1>
          <p className="mt-1 text-sm text-gray-500">
            {step === 1 ? 'Chọn vai trò của bạn trên FoodShare' : 'Cập nhật thông tin để tiếp tục'}
          </p>
        </div>
      </div>

      {/* Step indicator */}
      <div className="flex gap-2 mb-4">
        {[1, 2].map(s => (
          <div
            key={s}
            className={`flex-1 h-1.5 rounded-full transition-all ${s <= step ? 'bg-[#2db84c]' : 'bg-gray-200'
              }`}
          />
        ))}
      </div>



      {step === 1 && (
        <div className="flex flex-col gap-3">
          {ROLES.map(({ value, label, desc, icon: Icon, color }) => (
            <button
              key={value}
              type="button"
              onClick={() => setSelectedRole(value)}
              className={`flex items-center gap-4 p-4 rounded-xl border-2 text-left transition-all cursor-pointer ${selectedRole === value
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
              <div className={`ml-auto w-5 h-5 rounded-full border-2 flex items-center justify-center flex-shrink-0 ${selectedRole === value ? 'border-[#2db84c]' : 'border-gray-300'
                }`}>
                {selectedRole === value && <div className="w-2.5 h-2.5 rounded-full bg-[#2db84c]" />}
              </div>
            </button>
          ))}

          <div className="flex flex-col gap-2 mt-2">
            <button
              type="button"
              onClick={() => setStep(2)}
              className="w-full py-3 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20"
            >
              Tiếp tục
            </button>

            <button
              type="button"
              onClick={() => {
                logout();
                navigate('/auth/login', { replace: true });
              }}
              className="w-full py-2.5 rounded-xl border border-gray-200 text-gray-600 font-semibold text-sm cursor-pointer hover:bg-gray-50 transition-all flex items-center justify-center gap-1.5"
            >
              <ArrowLeft size={16} /> Quay lại đăng nhập
            </button>
          </div>
        </div>
      )}

      {step === 2 && (
        <form noValidate onSubmit={handleSubmit} className="grid grid-cols-1 lg:grid-cols-2 gap-x-5 gap-y-3">
          {/* Show selected role */}
          <div className="flex items-center gap-3 px-3 py-2.5 bg-gray-50 rounded-xl lg:col-span-2">
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
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Số điện thoại đã xác minh</label>
              <input
                type="tel"
                value={form.phone}
                readOnly
                className="w-full px-4 py-3 rounded-xl border border-green-200 bg-green-50 text-sm font-medium text-green-700 focus:outline-none"
              />
            </div>
          )}

          <div>
            <LocationField
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
              placeholder="928 Lê Văn Lương, Xã Nhà Bè, Thành Phố Hồ Chí Minh"
              required
            />
          </div>

          {isBusinessProfile && (
            <>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">
                  Tên hồ sơ kinh doanh <span className="text-red-500">*</span>
                </label>
                <input
                  value={form.businessName}
                  onChange={event => setForm(current => ({ ...current, businessName: event.target.value }))}
                  required
                  maxLength={150}
                  placeholder="Tên cửa hàng hoặc tổ chức"
                  className="w-full px-4 py-2.5 rounded-xl border border-gray-200 text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c]"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">
                  Loại hình <span className="text-red-500">*</span>
                </label>
                <select
                  value={selectedRole === 'SUPPLIER' ? form.supplierType : form.organizationType}
                  onChange={event => selectedRole === 'SUPPLIER'
                    ? setForm(current => ({
                      ...current,
                      supplierType: event.target.value as typeof current.supplierType,
                    }))
                    : setForm(current => ({
                      ...current,
                      organizationType: event.target.value as typeof current.organizationType,
                    }))}
                  className="w-full px-4 py-2.5 rounded-xl border border-gray-200 text-sm text-gray-900 bg-white focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c]"
                >
                  {(selectedRole === 'SUPPLIER' ? SUPPLIER_TYPES : ORGANIZATION_TYPES).map(type => (
                    <option key={type.value} value={type.value}>{type.label}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">Mã số thuế</label>
                <input
                  value={form.taxCode}
                  onChange={event => setForm(current => ({ ...current, taxCode: event.target.value }))}
                  maxLength={50}
                  placeholder="Nhập mã số thuế (nếu có)"
                  className="w-full px-4 py-2.5 rounded-xl border border-gray-200 text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c]"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">Mô tả hoạt động</label>
                <textarea
                  value={form.description}
                  onChange={event => setForm(current => ({ ...current, description: event.target.value }))}
                  maxLength={2000}
                  rows={2}
                  placeholder="Mô tả ngắn về hoạt động của bạn"
                  className="w-full px-4 py-2.5 rounded-xl border border-gray-200 text-sm text-gray-900 resize-none focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c]"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">
                  Giấy phép/giấy tờ xác minh <span className="text-red-500">*</span>
                </label>
                <label className={`flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl border-2 border-dashed text-sm text-gray-600 hover:border-[#2db84c] hover:text-[#2db84c] cursor-pointer transition-colors ${documentsInvalid ? 'border-red-500 ring-2 ring-red-500/10' : 'border-gray-200'}`}>
                  <Paperclip size={18} />
                  Chọn ảnh giấy tờ ({documents.length}/{MAX_DOCUMENTS})
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp,image/gif"
                    multiple
                    onChange={handleDocumentChange}
                    className="hidden"
                  />
                </label>
                <p className="mt-1.5 text-xs text-gray-500">JPEG, PNG, WebP hoặc GIF; tối đa {MAX_DOCUMENT_SIZE_MB} MB mỗi ảnh.</p>
                {documents.length > 0 && (
                  <ul className="mt-2 grid grid-cols-2 gap-1 max-h-16 overflow-y-auto">
                    {documents.map((file, index) => (
                      <li key={`${file.name}-${file.lastModified}-${index}`} className="flex min-w-0 items-center gap-1 rounded-lg bg-gray-50 px-2 py-1 text-xs text-gray-700">
                        <Paperclip size={14} className="shrink-0" />
                        <span className="truncate">{file.name}</span>
                        <button
                          type="button"
                          onClick={() => setDocuments(current => current.filter((_, itemIndex) => itemIndex !== index))}
                          className="ml-auto text-gray-400 hover:text-red-500"
                          aria-label={`Xóa ${file.name}`}
                        >
                          <X size={15} />
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </>
          )}

          <div className="flex gap-3 lg:col-span-2">
            <button
              type="button"
              onClick={() => setStep(1)}
              className="flex-1 py-2.5 rounded-xl border border-gray-200 text-gray-600 font-semibold text-sm cursor-pointer hover:bg-gray-50 transition-all"
            >
              Quay lại
            </button>
            <button
              type="submit"
              disabled={isLoading}
              className="flex-1 py-2.5 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 disabled:opacity-70"
            >
              {isLoading ? 'Đang xử lý...' : 'Hoàn tất'}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
