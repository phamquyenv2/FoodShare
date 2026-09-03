import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowLeft, Loader2, AlertTriangle, MapPin, Clock, User,
  XCircle, CheckCircle, CreditCard, Star, Flag, Package,
  Truck, ShoppingBag,
} from 'lucide-react';
import { apiFetch } from '../../services/api';
import { formatVND, timeAgo } from '../../utils/format';

interface OrderDetail {
  id: number;
  orderCode: string;
  orderStatus: string;
  totalAmount: number;
  createdAt: string;
  updatedAt: string;
  supplier: { name: string; avatarUrl?: string; phone?: string };
  orderDetails: {
    foodPost: { name: string; pickupAddress: string; imageUrl?: string };
    quantity: number;
    unitPrice: number;
  }[];
  rejectionReason?: string;
  deliveredAt?: string;
  completedAt?: string;
  paymentStatus?: string;
  paymentMethod?: string;
}

const STATUS_MAP: Record<string, { bg: string; text: string; label: string; icon: typeof Package }> = {
  PENDING:          { bg: 'bg-amber-100',  text: 'text-amber-700',  label: 'Chờ xác nhận',   icon: Clock },
  ACCEPTED:         { bg: 'bg-blue-100',   text: 'text-blue-700',   label: 'Đã chấp nhận',   icon: CheckCircle },
  READY_FOR_PICKUP: { bg: 'bg-indigo-100', text: 'text-indigo-700', label: 'Sẵn sàng lấy',   icon: Package },
  DELIVERED:        { bg: 'bg-cyan-100',   text: 'text-cyan-700',   label: 'Đã giao',         icon: Truck },
  COMPLETED:        { bg: 'bg-green-100',  text: 'text-green-700',  label: 'Hoàn thành',      icon: CheckCircle },
  CANCELLED:        { bg: 'bg-gray-100',   text: 'text-gray-600',   label: 'Đã hủy',          icon: XCircle },
  REJECTED:         { bg: 'bg-red-100',    text: 'text-red-600',    label: 'Từ chối',          icon: XCircle },
};

const STEPS = ['PENDING', 'ACCEPTED', 'READY_FOR_PICKUP', 'DELIVERED', 'COMPLETED'];

function formatDate(iso: string) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionLoading, setActionLoading] = useState('');
  const [cancelModal, setCancelModal] = useState(false);
  const [paymentModal, setPaymentModal] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState('CASH');

  useEffect(() => {
    const fetchOrder = async () => {
      try {
        const data = await apiFetch<OrderDetail>(`/orders/${id}`);
        setOrder(data);
      } catch (err: any) {
        setError(err.message || 'Không thể tải đơn hàng');
      } finally {
        setIsLoading(false);
      }
    };
    if (id) fetchOrder();
  }, [id]);

  const handleAction = async (action: string, body?: any) => {
    setActionLoading(action);
    try {
      await apiFetch(`/orders/${id}/${action}`, {
        method: 'PATCH',
        ...(body ? { body: JSON.stringify(body) } : {}),
      });
      // Refresh data
      const data = await apiFetch<OrderDetail>(`/orders/${id}`);
      setOrder(data);
    } catch (err: any) {
      alert(err.message || 'Thao tác thất bại');
    } finally {
      setActionLoading('');
      setCancelModal(false);
    }
  };

  const handlePayment = async () => {
    setActionLoading('payment');
    try {
      await apiFetch(`/payments`, {
        method: 'POST',
        body: JSON.stringify({
          orderId: order!.id,
          paymentMethod: paymentMethod,
        }),
      });
      const data = await apiFetch<OrderDetail>(`/orders/${id}`);
      setOrder(data);
      setPaymentModal(false);
    } catch (err: any) {
      alert(err.message || 'Thanh toán thất bại');
    } finally {
      setActionLoading('');
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 size={24} className="animate-spin text-[#2db84c]" />
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="p-4 md:p-6 max-w-3xl mx-auto">
        <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-sm text-gray-500 hover:text-gray-700 mb-4 cursor-pointer">
          <ArrowLeft size={16} /> Quay lại
        </button>
        <div className="text-center py-16 text-gray-400">
          <AlertTriangle size={48} className="mx-auto mb-3 opacity-50" />
          <p className="text-sm">{error || 'Không tìm thấy đơn hàng'}</p>
        </div>
      </div>
    );
  }

  const orderStatus = order.orderStatus || 'PENDING';
  const status = STATUS_MAP[orderStatus] || STATUS_MAP.PENDING;
  const StatusIcon = status.icon;
  const currentStep = STEPS.indexOf(orderStatus);
  const isCancelled = orderStatus === 'CANCELLED' || orderStatus === 'REJECTED';
  const canCancel = orderStatus === 'PENDING';
  const canConfirm = orderStatus === 'DELIVERED';
  const canPay = orderStatus === 'ACCEPTED' && order.totalAmount > 0 && order.paymentStatus !== 'SUCCESS';
  const canReview = orderStatus === 'COMPLETED';
  const canReport = ['ACCEPTED', 'READY_FOR_PICKUP', 'DELIVERED', 'COMPLETED'].includes(orderStatus);
  const firstDetail = order.orderDetails?.[0];
  const foodName = firstDetail?.foodPost?.name || 'Món ăn';
  const foodImageUrl = firstDetail?.foodPost?.imageUrl;
  const quantity = firstDetail?.quantity || 1;
  const unitPrice = firstDetail?.unitPrice || 0;
  const pickupAddress = firstDetail?.foodPost?.pickupAddress;
  const supplierName = order.supplier?.name || 'Nhà cung cấp';
  const supplierAvatar = order.supplier?.avatarUrl;
  const supplierPhone = order.supplier?.phone;

  return (
    <div className="p-4 md:p-6 max-w-3xl mx-auto flex flex-col gap-5">
      <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-sm text-gray-500 hover:text-gray-700 cursor-pointer self-start">
        <ArrowLeft size={16} /> Quay lại
      </button>

      {/* Status Header */}
      <motion.div
        initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
        className={`rounded-2xl p-5 ${status.bg} border border-transparent`}
      >
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-xl bg-white/60 flex items-center justify-center">
            <StatusIcon size={24} className={status.text} />
          </div>
          <div>
            <p className={`font-bold text-lg ${status.text}`}>{status.label}</p>
            <p className="text-xs text-gray-600">Mã đơn: {order.orderCode}</p>
          </div>
        </div>

        {order.rejectionReason && (
          <div className="mt-3 p-3 rounded-xl bg-white/60 text-sm text-red-600">
            <strong>Lý do từ chối:</strong> {order.rejectionReason}
          </div>
        )}
      </motion.div>

      {/* Progress Steps (only for non-cancelled orders) */}
      {!isCancelled && currentStep >= 0 && (
        <motion.div
          initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 }}
          className="bg-white rounded-2xl border border-gray-100 p-5"
        >
          <div className="flex items-center justify-between">
            {STEPS.map((step, i) => {
              const stepStatus = STATUS_MAP[step];
              const done = i <= currentStep;
              const active = i === currentStep;
              return (
                <div key={step} className="flex items-center flex-1 last:flex-initial">
                  <div className="flex flex-col items-center">
                    <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition-all ${
                      done ? 'bg-[#2db84c] text-white' : 'bg-gray-100 text-gray-400'
                    } ${active ? 'ring-4 ring-[#2db84c]/20' : ''}`}>
                      {done ? '✓' : i + 1}
                    </div>
                    <p className={`text-[10px] mt-1 text-center max-w-[60px] ${done ? 'text-[#2db84c] font-semibold' : 'text-gray-400'}`}>
                      {stepStatus.label}
                    </p>
                  </div>
                  {i < STEPS.length - 1 && (
                    <div className={`flex-1 h-0.5 mx-1 mt-[-16px] ${i < currentStep ? 'bg-[#2db84c]' : 'bg-gray-200'}`} />
                  )}
                </div>
              );
            })}
          </div>
        </motion.div>
      )}

      {/* Order Details */}
      <motion.div
        initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}
        className="bg-white rounded-2xl border border-gray-100 p-5"
      >
        <h3 className="font-semibold text-gray-900 mb-4">Chi tiết đơn hàng</h3>

        {/* Food item */}
        <div className="flex gap-3 p-3 rounded-xl bg-gray-50 mb-4">
          <div className="w-16 h-16 rounded-xl bg-gray-200 overflow-hidden flex-shrink-0">
            {foodImageUrl ? (
              <img src={foodImageUrl} alt="" className="w-full h-full object-cover" />
            ) : (
              <div className="w-full h-full flex items-center justify-center">
                <ShoppingBag size={20} className="text-gray-400" />
              </div>
            )}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-semibold text-gray-900 truncate">{foodName}</p>
            <p className="text-xs text-gray-500 mt-0.5">Số lượng: {quantity}</p>
            <p className="text-xs text-gray-500">Đơn giá: {unitPrice > 0 ? formatVND(unitPrice) : 'Miễn phí'}</p>
          </div>
          <div className="text-right flex-shrink-0">
            <p className="text-sm font-bold text-gray-900">
              {order.totalAmount > 0 ? formatVND(order.totalAmount) : '🎁 Miễn phí'}
            </p>
          </div>
        </div>

        {/* Info Grid */}
        <div className="grid grid-cols-2 gap-3 text-sm">
          <div className="p-3 rounded-xl bg-gray-50">
            <p className="text-xs text-gray-500 mb-0.5">Ngày đặt</p>
            <p className="font-medium text-gray-900">{formatDate(order.createdAt)}</p>
          </div>
          {order.deliveredAt && (
            <div className="p-3 rounded-xl bg-gray-50">
              <p className="text-xs text-gray-500 mb-0.5">Ngày giao</p>
              <p className="font-medium text-gray-900">{formatDate(order.deliveredAt)}</p>
            </div>
          )}
          {order.completedAt && (
            <div className="p-3 rounded-xl bg-gray-50">
              <p className="text-xs text-gray-500 mb-0.5">Hoàn thành</p>
              <p className="font-medium text-gray-900">{formatDate(order.completedAt)}</p>
            </div>
          )}
          {order.paymentStatus && (
            <div className="p-3 rounded-xl bg-gray-50">
              <p className="text-xs text-gray-500 mb-0.5">Thanh toán</p>
              <p className={`font-medium ${order.paymentStatus === 'SUCCESS' ? 'text-green-600' : 'text-amber-600'}`}>
                {order.paymentStatus === 'SUCCESS' ? '✓ Đã thanh toán' : 'Chưa thanh toán'}
              </p>
            </div>
          )}
        </div>

        {/* Pickup Address */}
        {pickupAddress && (
          <div className="mt-3 p-3 rounded-xl bg-gray-50 flex items-start gap-2">
            <MapPin size={14} className="text-gray-400 mt-0.5 flex-shrink-0" />
            <div>
              <p className="text-xs text-gray-500 mb-0.5">Địa điểm nhận hàng</p>
              <p className="text-sm font-medium text-gray-900">{pickupAddress}</p>
            </div>
          </div>
        )}
      </motion.div>

      {/* Supplier Info */}
      <motion.div
        initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 }}
        className="bg-white rounded-2xl border border-gray-100 p-5"
      >
        <h3 className="font-semibold text-gray-900 mb-3">Nhà cung cấp</h3>
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-full bg-gradient-to-br from-[#2db84c] to-[#1a9e3a] flex items-center justify-center text-white text-sm font-bold flex-shrink-0 overflow-hidden">
            {supplierAvatar ? (
              <img src={supplierAvatar} className="w-full h-full object-cover" alt="" />
            ) : (
              supplierName.charAt(0).toUpperCase()
            )}
          </div>
          <div className="flex-1 min-w-0">
            <p className="font-semibold text-gray-900 text-sm">{supplierName}</p>
            {supplierPhone && (
              <p className="text-xs text-gray-400">{supplierPhone}</p>
            )}
          </div>
        </div>
      </motion.div>

      {/* Action Buttons */}
      <motion.div
        initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
        className="flex flex-col gap-2"
      >
        {canPay && (
          <button
            onClick={() => setPaymentModal(true)}
            className="w-full py-3.5 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 flex items-center justify-center gap-2"
          >
            <CreditCard size={16} /> Thanh toán {formatVND(order.totalAmount)}
          </button>
        )}

        {canConfirm && (
          <button
            onClick={() => handleAction('complete')}
            disabled={actionLoading === 'complete'}
            className="w-full py-3.5 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 disabled:opacity-70 flex items-center justify-center gap-2"
          >
            {actionLoading === 'complete' ? <Loader2 size={16} className="animate-spin" /> : <CheckCircle size={16} />}
            Xác nhận đã nhận hàng
          </button>
        )}

        {canReview && (
          <button
            onClick={() => navigate(`/recipient/orders/${order.id}/review`)}
            className="w-full py-3 rounded-xl border-2 border-amber-400 text-amber-600 font-semibold text-sm cursor-pointer hover:bg-amber-50 transition-all flex items-center justify-center gap-2"
          >
            <Star size={16} /> Đánh giá nhà cung cấp
          </button>
        )}

        {canReport && (
          <button
            onClick={() => navigate(`/recipient/orders/${order.id}/report?type=ORDER`)}
            className="w-full py-3 rounded-xl border border-gray-200 text-gray-500 font-medium text-sm cursor-pointer hover:bg-gray-50 transition-all flex items-center justify-center gap-2"
          >
            <Flag size={14} /> Báo cáo / Khiếu nại
          </button>
        )}

        {canCancel && (
          <button
            onClick={() => setCancelModal(true)}
            className="w-full py-3 rounded-xl border border-red-200 text-red-500 font-medium text-sm cursor-pointer hover:bg-red-50 transition-all flex items-center justify-center gap-2"
          >
            <XCircle size={14} /> Hủy đơn hàng
          </button>
        )}
      </motion.div>

      {/* Cancel Modal */}
      {cancelModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" onClick={() => setCancelModal(false)}>
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
            className="bg-white rounded-2xl p-6 w-full max-w-sm" onClick={e => e.stopPropagation()}
          >
            <h3 className="font-semibold text-gray-900 mb-2">Hủy đơn hàng?</h3>
            <p className="text-sm text-gray-500 mb-5">Bạn có chắc chắn muốn hủy đơn hàng này? Thao tác này không thể hoàn tác.</p>
            <div className="flex gap-3">
              <button
                onClick={() => setCancelModal(false)}
                className="flex-1 py-2.5 rounded-xl border border-gray-200 text-sm font-medium text-gray-600 cursor-pointer hover:bg-gray-50"
              >
                Giữ đơn
              </button>
              <button
                onClick={() => handleAction('cancel')}
                disabled={actionLoading === 'cancel'}
                className="flex-1 py-2.5 rounded-xl bg-red-500 text-white text-sm font-medium cursor-pointer hover:bg-red-600 disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {actionLoading === 'cancel' && <Loader2 size={14} className="animate-spin" />}
                Hủy đơn
              </button>
            </div>
          </motion.div>
        </div>
      )}

      {/* Payment Modal */}
      {paymentModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" onClick={() => setPaymentModal(false)}>
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
            className="bg-white rounded-2xl p-6 w-full max-w-sm" onClick={e => e.stopPropagation()}
          >
            <h3 className="font-semibold text-gray-900 mb-1">Thanh toán</h3>
            <p className="text-sm text-gray-500 mb-4">
              Tổng tiền: <span className="font-bold text-gray-900">{formatVND(order.totalAmount)}</span>
            </p>

            <div className="flex flex-col gap-2 mb-5">
              {[
                { key: 'CASH', label: '💵 Tiền mặt', desc: 'Thanh toán khi nhận hàng' },
                { key: 'EWALLET', label: '📱 Ví điện tử', desc: 'Momo, ZaloPay...' },
              ].map(m => (
                <button
                  key={m.key}
                  onClick={() => setPaymentMethod(m.key)}
                  className={`p-3 rounded-xl border-2 text-left cursor-pointer transition-all ${
                    paymentMethod === m.key
                      ? 'border-[#2db84c] bg-[#2db84c]/5'
                      : 'border-gray-200 hover:border-gray-300'
                  }`}
                >
                  <p className="text-sm font-medium text-gray-900">{m.label}</p>
                  <p className="text-xs text-gray-500">{m.desc}</p>
                </button>
              ))}
            </div>

            <div className="flex gap-3">
              <button
                onClick={() => setPaymentModal(false)}
                className="flex-1 py-2.5 rounded-xl border border-gray-200 text-sm font-medium text-gray-600 cursor-pointer hover:bg-gray-50"
              >
                Hủy
              </button>
              <button
                onClick={handlePayment}
                disabled={actionLoading === 'payment'}
                className="flex-1 py-2.5 rounded-xl bg-[#2db84c] text-white text-sm font-medium cursor-pointer hover:bg-[#259e40] disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {actionLoading === 'payment' && <Loader2 size={14} className="animate-spin" />}
                Xác nhận
              </button>
            </div>
          </motion.div>
        </div>
      )}
    </div>
  );
}
