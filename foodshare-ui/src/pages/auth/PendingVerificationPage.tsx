import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { Clock, AlertCircle } from 'lucide-react';

export default function PendingVerificationPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    // If somehow they are VERIFIED, send them to dashboard
    if (user?.role === 'SUPPLIER' && user?.profile?.verificationStatus === 'VERIFIED') {
      navigate('/supplier', { replace: true });
    }
  }, [user, navigate]);

  const isRejected = user?.profile?.verificationStatus === 'REJECTED';

  return (
    <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-8 text-center">
      <div
        className="mx-auto flex items-center justify-center h-16 w-16 rounded-2xl mb-5"
        style={{ backgroundColor: isRejected ? '#fee2e2' : '#fef3c7' }}
      >
        {isRejected ? (
          <AlertCircle className="h-8 w-8 text-red-600" />
        ) : (
          <Clock className="h-8 w-8 text-amber-600" />
        )}
      </div>

      <h2 className="text-xl font-bold text-gray-900 mb-2">
        {isRejected ? 'Hồ sơ bị từ chối' : 'Hồ sơ đang chờ duyệt'}
      </h2>

      <p className="text-gray-500 text-sm mb-6 leading-relaxed">
        {isRejected
          ? 'Rất tiếc, hồ sơ đăng ký Nhà Cung Cấp của bạn đã bị từ chối bởi Quản trị viên. Vui lòng liên hệ bộ phận hỗ trợ để biết thêm chi tiết.'
          : 'Thông tin đăng ký của bạn đã được ghi nhận và đang chờ Quản trị viên phê duyệt. Quá trình này thường mất từ 1-2 ngày làm việc.'}
      </p>

      <button
        type="button"
        onClick={() => {
          logout();
          navigate('/auth/login', { replace: true });
        }}
        className="w-full py-3 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20"
      >
        Đăng xuất
      </button>
    </div>
  );
}
