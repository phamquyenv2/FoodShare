import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowLeft, Loader2, AlertTriangle, MapPin, Clock, XCircle, CheckCircle, CreditCard, Star, Flag, Package,
  Truck, ShoppingBag, Banknote
} from 'lucide-react';
import { apiFetch } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';
import { formatVND } from '../../utils/format';
import momoLogo from '../../assets/Momo.png';
import zaloLogo from '../../assets/zalo.png';

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
  paymentStatus?: string | null;
  paymentMethod?: string;
}

const STATUS_MAP: Record<string, { bg: string; text: string; label: string; icon: typeof Package }> = {
  PENDING: { bg: 'bg-amber-100', text: 'text-amber-700', label: 'Chờ xác nhận', icon: Clock },
  ACCEPTED: { bg: 'bg-blue-100', text: 'text-blue-700', label: 'Đã chấp nhận', icon: CheckCircle },
  READY_FOR_PICKUP: { bg: 'bg-indigo-100', text: 'text-indigo-700', label: 'Sẵn sàng lấy', icon: Package },
  DELIVERED: { bg: 'bg-cyan-100', text: 'text-cyan-700', label: 'Đã giao', icon: Truck },
  COMPLETED: { bg: 'bg-green-100', text: 'text-green-700', label: 'Hoàn thành', icon: CheckCircle },
  CANCELLED: { bg: 'bg-gray-100', text: 'text-gray-600', label: 'Đã hủy', icon: XCircle },
  REJECTED: { bg: 'bg-red-100', text: 'text-red-600', label: 'Từ chối', icon: XCircle },
};

const PAYMENT_LABELS: Record<string, string> = {
  PENDING: 'Chờ thanh toán',
  PROCESSING: 'Đang xử lý',
  SUCCESS: '✓ Đã thanh toán',
  FAILED: 'Thanh toán thất bại',
  CANCELLED: 'Thanh toán đã hủy',
  EXPIRED: 'Thanh toán hết hạn',
  REFUNDED: 'Đã hoàn tiền',
};

const STEPS = ['PENDING', 'ACCEPTED', 'READY_FOR_PICKUP', 'DELIVERED', 'COMPLETED'];

function formatDate(iso: string) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

export default function OrderDetailPage() {
  const { showError } = useToast();
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState('');
  const [supplierAvatarFailed, setSupplierAvatarFailed] = useState(false);
  const [cancelModal, setCancelModal] = useState(false);
  const [paymentModal, setPaymentModal] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState('CASH');

  useEffect(() => {
    const fetchOrder = async () => {
      try {
        const data = await apiFetch<OrderDetail>(`/orders/${id}`);
        setOrder(data);
      } catch (err: any) {
        showError(err.message || 'Không thể tải đơn hàng');
      } finally {
        setIsLoading(false);
      }
    };
    if (id) fetchOrder();
  }, [id, showError]);

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
      showError(err.message || 'Thao tác thất bại');
    } finally {
      setActionLoading('');
      setCancelModal(false);
    }
  };

  const handlePayment = async () => {
    setActionLoading('payment');
    try {
      const payment = await apiFetch<{ paymentUrl?: string }>(`/payments/order/${order!.id}`, {
        method: 'POST',
        body: JSON.stringify({
          method: paymentMethod,
        }),
      });
      if (paymentMethod !== 'CASH' && payment.paymentUrl) {
        window.location.assign(payment.paymentUrl);
        return;
      }
      const data = await apiFetch<OrderDetail>(`/orders/${id}`);
      setOrder(data);
      setPaymentModal(false);
    } catch (err: any) {
      showError(err.message || 'Thanh toán thất bại');
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

  if (!order) {
    return (
      <div className="p-4 md:p-6 max-w-3xl mx-auto">
        <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-sm text-gray-500 hover:text-gray-700 mb-4 cursor-pointer">
          <ArrowLeft size={16} /> Quay lại
        </button>
        <div className="text-center py-16 text-gray-400">
          <AlertTriangle size={48} className="mx-auto mb-3 opacity-50" />
          <p className="text-sm">Không tìm thấy đơn hàng</p>
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
    <div className="p-4 md:p-6 max-w-3xl mx-auto w-full flex flex-col gap-5">
      <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-sm text-gray-500 hover:text-gray-700 cursor-pointer self-start transition-colors">
        <ArrowLeft size={16} /> Quay lại
      </button>

      {/* 1. Status Header Card */}
      <motion.div
        initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
        className={`w-full rounded-2xl p-5 ${status.bg} border border-transparent`}
      >
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-xl bg-white flex items-center justify-center shadow-xs shrink-0">
            <StatusIcon size={24} className={status.text} />
          </div>
          <div>
            <p className={`font-bold text-lg ${status.text}`}>{status.label}</p>
            <p className="text-xs text-gray-600 mt-0.5">Mã đơn: {order.orderCode}</p>
          </div>
        </div>

        {order.rejectionReason && (
          <div className="mt-3 p-3 rounded-xl bg-white/70 text-xs sm:text-sm text-red-600 flex items-start gap-2">
            <AlertTriangle size={15} className="shrink-0 mt-0.5" />
            <span><strong>Lý do từ chối:</strong> {order.rejectionReason}</span>
          </div>
        )}
      </motion.div>

      {/* 2. Progress Steps Card */}
      {!isCancelled && currentStep >= 0 && (
        <motion.div
          initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 }}
          className="w-full bg-white rounded-2xl border border-gray-100 p-5 sm:p-6 shadow-xs"
        >
          <div className="relative">
            {/* Background connecting track */}
            <div className="absolute top-4 left-[10%] right-[10%] -translate-y-1/2 h-0.5 bg-gray-200 z-0 pointer-events-none" />

            {/* Active completed track */}
            <div
              className="absolute top-4 left-[10%] -translate-y-1/2 h-0.5 bg-[#2db84c] z-0 transition-all duration-300 pointer-events-none"
              style={{
                width: `${(Math.min(Math.max(currentStep, 0), STEPS.length - 1) / (STEPS.length - 1)) * 80}%`,
              }}
            />

            {/* 5 Step Nodes */}
            <div className="grid grid-cols-5 relative z-10">
              {STEPS.map((step, i) => {
                const stepStatus = STATUS_MAP[step];
                const done = i <= currentStep;
                const active = i === currentStep;
                return (
                  <div key={step} className="flex flex-col items-center">
                    <div
                      className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition-all border-2 border-white shadow-xs ${
                        done
                          ? 'bg-[#2db84c] text-white'
                          : 'bg-gray-100 text-gray-400'
                      } ${active ? 'ring-4 ring-[#2db84c]/20' : ''}`}
                    >
                      {done ? '✓' : i + 1}
                    </div>
                    <p
                      className={`text-xs mt-2 text-center leading-tight max-w-[90px] font-medium ${
                        done ? 'text-[#2db84c] font-semibold' : 'text-gray-400'
                      }`}
                    >
                      {stepStatus.label}
                    </p>
                  </div>
                );
              })}
            </div>
          </div>
        </motion.div>
      )}

      {/* 3. Two-Column Layout: Left (Chi tiết đơn hàng), Right (Nhà cung cấp & Hủy đơn hàng / Action buttons) */}
      <div className="w-full grid grid-cols-1 md:grid-cols-[minmax(0,1fr)_260px] gap-4 sm:gap-5 items-start">
        {/* Left Column: Chi tiết đơn hàng */}
        <motion.div
          initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}
          className="w-full min-w-0 bg-white rounded-2xl border border-gray-100 p-4 sm:p-5 shadow-xs flex flex-col gap-3.5"
        >
          <h3 className="font-semibold text-gray-900 text-base">Chi tiết đơn hàng</h3>

          {/* Food item row */}
          <div className="p-3.5 rounded-xl bg-gray-50 flex items-center justify-between gap-3">
            <div className="flex items-center gap-3 min-w-0">
              <div className="w-16 h-16 rounded-xl bg-gray-200 overflow-hidden shrink-0 border border-gray-200/50">
                {foodImageUrl ? (
                  <img src={foodImageUrl} alt="" className="w-full h-full object-cover" />
                ) : (
                  <div className="w-full h-full flex items-center justify-center">
                    <ShoppingBag size={24} className="text-gray-400" />
                  </div>
                )}
              </div>
              <div className="min-w-0">
                <p className="font-medium text-gray-900 text-base truncate">{foodName}</p>
                <p className="text-xs text-gray-500 mt-0.5">Số lượng: {quantity}</p>
                <p className="text-xs text-gray-500">
                  Đơn giá: {unitPrice > 0 ? formatVND(unitPrice) : 'Miễn phí'}
                </p>
              </div>
            </div>
            <div className="text-right shrink-0 pl-3">
              <p className="text-base font-bold text-gray-900">
                {order.totalAmount > 0 ? formatVND(order.totalAmount) : 'Miễn phí'}
              </p>
            </div>
          </div>

          {/* Info cards: Ngày đặt, Ngày giao, Hoàn thành, Thanh toán */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
            <div className="p-3 rounded-xl bg-gray-50">
              <p className="text-xs text-gray-400 mb-0.5">Ngày đặt</p>
              <p className="text-sm font-semibold text-gray-800 truncate">{formatDate(order.createdAt)}</p>
            </div>
            {order.deliveredAt && (
              <div className="p-3 rounded-xl bg-gray-50">
                <p className="text-xs text-gray-400 mb-0.5">Ngày giao</p>
                <p className="text-sm font-semibold text-gray-800 truncate">{formatDate(order.deliveredAt)}</p>
              </div>
            )}
            {order.completedAt && (
              <div className="p-3 rounded-xl bg-gray-50">
                <p className="text-xs text-gray-400 mb-0.5">Hoàn thành</p>
                <p className="text-sm font-semibold text-gray-800 truncate">{formatDate(order.completedAt)}</p>
              </div>
            )}
            {order.paymentStatus && (
              <div className="p-3 rounded-xl bg-gray-50">
                <p className="text-xs text-gray-400 mb-0.5">Thanh toán</p>
                <p className={`text-sm font-semibold ${order.paymentStatus === 'SUCCESS' ? 'text-green-600' : 'text-amber-600'}`}>
                  {PAYMENT_LABELS[order.paymentStatus] || 'Chưa thanh toán'}
                </p>
              </div>
            )}
          </div>

          {/* Pickup Address */}
          {pickupAddress && (
            <div className="p-3.5 rounded-xl bg-gray-50 flex items-start gap-2">
              <MapPin size={16} className="text-gray-400 mt-0.5 shrink-0" />
              <div className="min-w-0 flex-1">
                <p className="text-xs text-gray-400 mb-0.5">Địa điểm nhận hàng</p>
                <p className="text-sm font-medium text-gray-800 leading-snug break-words">{pickupAddress}</p>
              </div>
            </div>
          )}
        </motion.div>

        {/* Right Column: Nhà cung cấp & Action buttons */}
        <div className="w-full min-w-0 flex flex-col gap-4">
          {/* Nhà cung cấp */}
          <motion.div
            initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 }}
            className="w-full bg-white rounded-2xl border border-gray-100 p-4 sm:p-5 shadow-xs"
          >
            <h3 className="font-semibold text-gray-900 mb-3 text-base">Nhà cung cấp</h3>
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-[#2db84c] flex items-center justify-center text-white text-base font-bold shrink-0 overflow-hidden shadow-xs">
                {supplierAvatar && !supplierAvatarFailed ? (
                  <img src={supplierAvatar} className="w-full h-full object-cover" alt="" onError={() => setSupplierAvatarFailed(true)} />
                ) : (
                  supplierName.charAt(0).toUpperCase()
                )}
              </div>
              <div className="flex-1 min-w-0">
                <p className="font-semibold text-gray-900 text-sm truncate">{supplierName}</p>
                {supplierPhone && (
                  <p className="text-xs text-gray-500 mt-0.5 truncate">{supplierPhone}</p>
                )}
              </div>
            </div>
          </motion.div>

          {/* Action Buttons */}
          <motion.div
            initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
            className="w-full flex flex-col gap-2.5"
          >
            {canPay && (
              <button
                onClick={() => setPaymentModal(true)}
                disabled={!!actionLoading}
                className="w-full py-3 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 disabled:opacity-50 disabled:cursor-not-allowed disabled:pointer-events-none flex items-center justify-center gap-2 px-3 text-center"
              >
                <CreditCard size={16} className="shrink-0" /> <span className="truncate">Thanh toán {formatVND(order.totalAmount)}</span>
              </button>
            )}

            {canConfirm && (
              <button
                onClick={() => handleAction('complete')}
                disabled={actionLoading === 'complete' || !!actionLoading}
                className="w-full py-3 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 disabled:opacity-50 disabled:cursor-not-allowed disabled:pointer-events-none flex items-center justify-center gap-2 px-3 text-center"
              >
                {actionLoading === 'complete' ? <Loader2 size={16} className="animate-spin shrink-0" /> : <CheckCircle size={16} className="shrink-0" />}
                <span>Xác nhận đã nhận hàng</span>
              </button>
            )}

            {canReview && (
              <button
                onClick={() => navigate(`/recipient/orders/${order.id}/review`)}
                disabled={!!actionLoading}
                className="w-full py-2.5 sm:py-3 rounded-xl border-2 border-amber-400 text-amber-600 font-semibold text-sm cursor-pointer hover:bg-amber-50 transition-all disabled:opacity-50 disabled:cursor-not-allowed disabled:pointer-events-none flex items-center justify-center gap-2 px-3 text-center"
              >
                <Star size={16} className="shrink-0" /> <span>Đánh giá nhà cung cấp</span>
              </button>
            )}

            {canReport && (
              <button
                onClick={() => navigate(`/recipient/orders/${order.id}/report?type=ORDER`)}
                disabled={!!actionLoading}
                className="w-full py-2.5 sm:py-3 rounded-xl border border-gray-200 text-gray-500 font-medium text-sm cursor-pointer hover:bg-gray-50 hover:text-red-500 hover:border-red-200 transition-all disabled:opacity-50 disabled:cursor-not-allowed disabled:pointer-events-none flex items-center justify-center gap-2 px-3 text-center"
              >
                <Flag size={14} className="shrink-0" /> <span>Báo cáo / Khiếu nại</span>
              </button>
            )}

            {canCancel && (
              <button
                onClick={() => setCancelModal(true)}
                disabled={!!actionLoading}
                className="w-full py-2.5 sm:py-3 rounded-xl border border-red-200 text-red-500 font-medium text-sm cursor-pointer hover:bg-red-50 transition-all disabled:opacity-50 disabled:cursor-not-allowed disabled:pointer-events-none flex items-center justify-center gap-2 px-3 text-center"
              >
                <XCircle size={15} className="shrink-0" /> <span>Hủy đơn hàng</span>
              </button>
            )}
          </motion.div>
        </div>
      </div>

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

            <div className="flex flex-col gap-2.5 mb-5">
              {[
                {
                  key: 'CASH',
                  label: 'Tiền mặt',
                  desc: 'Thanh toán trực tiếp khi nhận hàng',
                  icon: (
                    <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0 border border-emerald-100">
                      <Banknote size={20} />
                    </div>
                  ),
                },
                {
                  key: 'MOMO',
                  label: 'Ví MoMo',
                  desc: 'Thanh toán qua ví điện tử MoMo',
                  icon: (
                    <div className="w-10 h-10 rounded-xl bg-[#a50064]/5 p-1.5 flex items-center justify-center shrink-0 border border-[#a50064]/10">
                      <img src={momoLogo} alt="MoMo" className="w-full h-full object-contain" />
                    </div>
                  ),
                },
                {
                  key: 'ZALOPAY',
                  label: 'Ví ZaloPay',
                  desc: 'Thanh toán qua ví điện tử ZaloPay',
                  icon: (
                    <div className="w-10 h-10 rounded-xl bg-[#0068ff]/5 p-1.5 flex items-center justify-center shrink-0 border border-[#0068ff]/10">
                      <img src={zaloLogo} alt="ZaloPay" className="w-full h-full object-contain" />
                    </div>
                  ),
                },
              ].map(m => (
                <button
                  key={m.key}
                  type="button"
                  onClick={() => setPaymentMethod(m.key)}
                  className={`p-3 rounded-xl border-2 text-left cursor-pointer transition-all flex items-center gap-3 ${
                    paymentMethod === m.key
                      ? 'border-[#2db84c] bg-[#2db84c]/5 shadow-xs'
                      : 'border-gray-100 hover:border-gray-200 bg-white'
                  }`}
                >
                  {m.icon}
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-gray-900">{m.label}</p>
                    <p className="text-xs text-gray-500 truncate">{m.desc}</p>
                  </div>
                  <div className={`w-4 h-4 rounded-full border-2 flex items-center justify-center transition-all ${
                    paymentMethod === m.key
                      ? 'border-[#2db84c] bg-[#2db84c]'
                      : 'border-gray-300'
                  }`}>
                    {paymentMethod === m.key && <div className="w-1.5 h-1.5 rounded-full bg-white" />}
                  </div>
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
