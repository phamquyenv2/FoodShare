import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { Clock, AlertCircle } from 'lucide-react';
import { motion } from 'framer-motion';

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
    <div className="min-h-screen bg-gray-50 flex flex-col justify-center pb-[15vh] py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
          className="bg-white py-8 px-4 shadow-xl shadow-gray-200/50 sm:rounded-3xl sm:px-10 text-center border border-gray-100">
          
          <div className="mx-auto flex items-center justify-center h-20 w-20 rounded-full mb-6"
            style={{ backgroundColor: isRejected ? '#fee2e2' : '#fef3c7' }}>
            {isRejected ? (
              <AlertCircle className="h-10 w-10 text-red-600" />
            ) : (
              <Clock className="h-10 w-10 text-amber-600" />
            )}
          </div>
          
          <h2 className="text-2xl font-bold text-gray-900 mb-2">
            {isRejected ? 'Hồ sơ bị từ chối' : 'Hồ sơ đang chờ duyệt'}
          </h2>
          
          <p className="text-gray-500 text-sm mb-8 leading-relaxed">
            {isRejected 
              ? 'Rất tiếc, hồ sơ đăng ký Nhà Cung Cấp của bạn đã bị từ chối bởi Quản trị viên. Vui lòng liên hệ bộ phận hỗ trợ để biết thêm chi tiết.'
              : 'Thông tin đăng ký của bạn đã được ghi nhận và đang chờ Quản trị viên phê duyệt. Quá trình này thường mất từ 1-2 ngày làm việc.'}
          </p>

          <button onClick={() => logout()}
            className="w-full flex justify-center py-3 px-4 border border-transparent rounded-xl shadow-sm text-sm font-semibold text-white bg-[#2db84c] hover:bg-[#259e40] transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-[#2db84c]">
            Đăng xuất
          </button>
          
        </motion.div>
      </div>
    </div>
  );
}
