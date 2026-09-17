import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { Clock, AlertCircle, RefreshCw } from 'lucide-react';
import { useToast } from '../../contexts/ToastContext';

export default function PendingVerificationPage() {
  const { user, logout, checkAuth } = useAuth();
  const { showSuccess, showError } = useToast();
  const navigate = useNavigate();
  const [isChecking, setIsChecking] = useState(false);

  useEffect(() => {
    if (user?.profile?.verificationStatus === 'VERIFIED') {
      const target = user.role === 'ORGANIZATION' ? '/organization' : '/supplier';
      navigate(target, { replace: true });
    }
  }, [user, navigate]);

  const handleRefresh = async () => {
    setIsChecking(true);
    try {
      await checkAuth();
      showSuccess('Đã cập nhật trạng thái mới nhất');
    } catch (err: any) {
      showError(err.message || 'Không thể kiểm tra trạng thái');
    } finally {
      setIsChecking(false);
    }
  };

  const isRejected = user?.profile?.verificationStatus === 'REJECTED';
  const roleTitle = user?.role === 'ORGANIZATION' ? 'Tổ Chức' : 'Nhà Cung Cấp';

  return (
    <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-8 text-center max-w-md mx-auto">
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
          ? `Rất tiếc, hồ sơ đăng ký ${roleTitle} của bạn đã bị từ chối bởi Quản trị viên. Vui lòng liên hệ bộ phận hỗ trợ để biết thêm chi tiết.`
          : `Thông tin đăng ký ${roleTitle} của bạn đã được ghi nhận và đang chờ Quản trị viên phê duyệt. Quá trình này thường mất từ 1-2 ngày làm việc.`}
      </p>

      <div className="flex flex-col gap-3">
        {!isRejected && (
          <button
            type="button"
            onClick={handleRefresh}
            disabled={isChecking}
            className="w-full py-3 rounded-xl bg-white border border-gray-200 text-gray-700 font-semibold text-sm cursor-pointer hover:bg-gray-50 active:scale-[0.98] transition-all flex items-center justify-center gap-2"
          >
            <RefreshCw size={16} className={isChecking ? 'animate-spin' : ''} />
            {isChecking ? 'Đang kiểm tra...' : 'Kiểm tra lại trạng thái'}
          </button>
        )}

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
    </div>
  );
}
