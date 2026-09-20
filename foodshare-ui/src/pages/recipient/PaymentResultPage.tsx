import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { 
  CheckCircle2, 
  XCircle, 
  ShoppingBag, 
  ArrowRight, 
  RotateCcw, 
  Clock, 
  Building2, 
  Receipt,
  HelpCircle
} from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { apiFetch } from '../../services/api';
import { formatVND } from '../../utils/format';

interface PaymentDetails {
  orderId?: number | string;
  orderCode?: string;
  amount?: number;
  provider?: string;
  supplierName?: string;
  paidAt?: string;
}

export default function PaymentResultPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { user } = useAuth();

  const ordersPath = user?.role === 'ORGANIZATION' ? '/organization/orders' : '/recipient/orders';
  const explorePath = user?.role === 'ORGANIZATION' ? '/organization/explore' : '/recipient/explore';

  const [loading, setLoading] = useState(true);
  const [isSuccess, setIsSuccess] = useState<boolean | null>(null);
  const [message, setMessage] = useState<string>('');
  const [paymentDetails, setPaymentDetails] = useState<PaymentDetails>({});

  useEffect(() => {
    const handleVerify = async () => {
      setLoading(true);

      // Extract MoMo parameters
      const momoOrderId = searchParams.get('orderId');
      const momoResultCode = searchParams.get('resultCode');
      const momoAmount = searchParams.get('amount');
      const momoMessage = searchParams.get('message');

      // Extract ZaloPay parameters
      const zaloAppTransId = searchParams.get('apptransid');
      const zaloStatus = searchParams.get('status');
      const zaloAmount = searchParams.get('amount');
      const transactionToken = searchParams.get('zptranstoken');
      const returnCode = searchParams.get('returncode');

      // Determine extracted order ID
      let resolvedOrderId: string | number | undefined = undefined;
      if (zaloAppTransId) {
        const parts = zaloAppTransId.split('_');
        if (parts.length >= 2) resolvedOrderId = parts[1];
      } else if (momoOrderId) {
        if (momoOrderId.startsWith('FS-')) {
          const parts = momoOrderId.split('-');
          if (parts.length >= 2) resolvedOrderId = parts[1];
        } else {
          resolvedOrderId = momoOrderId;
        }
      }
      if (!resolvedOrderId) {
        resolvedOrderId = searchParams.get('orderId') || undefined;
      }

      // Handle zptranstoken callback
      if (transactionToken && returnCode) {
        const returnMessage = searchParams.get('returnmessage');
        setPaymentDetails({ orderId: resolvedOrderId, provider: 'ZALOPAY' });
        try {
          await apiFetch(`/payments/zalopay/callback?zptranstoken=${encodeURIComponent(transactionToken)}&returncode=${encodeURIComponent(returnCode)}${returnMessage ? `&returnmessage=${encodeURIComponent(returnMessage)}` : ''}`, { method: 'GET' });
          const ok = returnCode === '1';
          setIsSuccess(ok);
          setMessage(ok ? 'Giao dịch thanh toán qua ZaloPay thành công!' : (returnMessage || 'Giao dịch ZaloPay chưa thành công.'));
        } catch {
          setIsSuccess(returnCode === '1');
        }
        setLoading(false);
        return;
      }

      // 1. ZALOPAY REDIRECT
      if (zaloAppTransId !== null && zaloStatus !== null) {
        const amountNum = zaloAmount ? Number(zaloAmount) : undefined;
        setPaymentDetails({
          orderId: resolvedOrderId,
          amount: amountNum,
          provider: 'ZALOPAY',
        });

        if (zaloStatus === '1') {
          // Success from ZaloPay
          try {
            const res = await apiFetch<any>(
              `/payments/zalopay/result?apptransid=${encodeURIComponent(zaloAppTransId)}&status=${zaloStatus}${
                amountNum ? `&amount=${amountNum}` : ''
              }`,
              { method: 'POST' }
            );
            setIsSuccess(true);
            setMessage('Giao dịch thanh toán qua ZaloPay thành công!');
            if (res) {
              setPaymentDetails(prev => ({
                ...prev,
                orderId: res.orderId || prev.orderId,
                orderCode: res.orderCode,
                amount: res.amount || prev.amount,
                supplierName: res.supplierName,
                paidAt: res.paidAt,
              }));
            }
          } catch (err: any) {
            // If already processed or verified
            setIsSuccess(true);
            setMessage('Thanh toán qua ZaloPay đã được hệ thống ghi nhận thành công.');
          }
        } else {
          // Cancelled or Failed
          setIsSuccess(false);
          setMessage('Giao dịch ZaloPay đã bị huỷ hoặc chưa hoàn tất thanh toán.');
        }
        setLoading(false);
        return;
      }

      // 2. MOMO REDIRECT
      if (momoOrderId !== null && momoResultCode !== null) {
        const amountNum = momoAmount ? Number(momoAmount) : undefined;
        setPaymentDetails({
          orderId: resolvedOrderId,
          amount: amountNum,
          provider: 'MOMO',
        });

        if (momoResultCode === '0') {
          // Success from MoMo
          try {
            const res = await apiFetch<any>(
              `/payments/momo/result?orderId=${encodeURIComponent(momoOrderId)}&resultCode=${momoResultCode}${
                amountNum ? `&amount=${amountNum}` : ''
              }`,
              { method: 'POST' }
            );
            setIsSuccess(true);
            setMessage('Giao dịch thanh toán qua MoMo thành công!');
            if (res) {
              setPaymentDetails(prev => ({
                ...prev,
                orderId: res.orderId || prev.orderId,
                orderCode: res.orderCode,
                amount: res.amount || prev.amount,
                supplierName: res.supplierName,
                paidAt: res.paidAt,
              }));
            }
          } catch (err: any) {
            setIsSuccess(true);
            setMessage('Thanh toán qua MoMo đã được hệ thống ghi nhận thành công.');
          }
        } else {
          // Cancelled or Failed
          setIsSuccess(false);
          setMessage(momoMessage || 'Giao dịch MoMo đã bị huỷ hoặc chưa hoàn tất.');
        }
        setLoading(false);
        return;
      }

      // 3. Fallback or generic query params (e.g. ?success=true&orderId=28)
      const genericSuccess = searchParams.get('success');
      if (genericSuccess !== null) {
        const ok = genericSuccess === 'true' || genericSuccess === '1';
        setIsSuccess(ok);
        setMessage(ok ? 'Thanh toán đơn hàng thành công!' : 'Thanh toán đơn hàng không thành công.');
        setPaymentDetails({
          orderId: searchParams.get('orderId') || undefined,
          amount: searchParams.get('amount') ? Number(searchParams.get('amount')) : undefined,
          provider: searchParams.get('provider') || undefined,
        });
        setLoading(false);
        return;
      }

      // Default fallback
      setIsSuccess(true);
      setMessage('Kết quả thanh toán đã được ghi nhận.');
      setPaymentDetails({ orderId: resolvedOrderId });
      setLoading(false);
    };

    handleVerify();
  }, [searchParams]);

  // Try to load additional order info if missing orderCode/supplierName
  useEffect(() => {
    if (paymentDetails.orderId && !paymentDetails.orderCode) {
      apiFetch<any>(`/orders/${paymentDetails.orderId}`)
        .then(order => {
          if (order) {
            setPaymentDetails(prev => ({
              ...prev,
              orderCode: order.orderCode,
              supplierName: order.businessProfile?.name || order.businessProfileName,
              amount: prev.amount || order.totalAmount,
            }));
          }
        })
        .catch(() => undefined);
    }
  }, [paymentDetails.orderId, paymentDetails.orderCode]);

  return (
    <div className="min-h-[80vh] flex items-center justify-center p-4 md:p-8 bg-gray-50/50">
      <motion.div
        initial={{ opacity: 0, y: 15 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.35, ease: 'easeOut' }}
        className="w-full max-w-lg bg-white rounded-3xl border border-gray-100 shadow-xl overflow-hidden"
      >
        {loading ? (
          <div className="py-20 px-6 flex flex-col items-center justify-center text-center">
            <div className="relative mb-4">
              <div className="w-16 h-16 rounded-full border-4 border-[#2db84c]/20 border-t-[#2db84c] animate-spin" />
            </div>
            <h2 className="text-lg font-bold text-gray-900 mb-1">Đang kiểm tra kết quả thanh toán...</h2>
            <p className="text-sm text-gray-500">Vui lòng chờ trong giây lát, hệ thống đang đồng bộ dữ liệu.</p>
          </div>
        ) : isSuccess ? (
          /* SUCCESS STATE */
          <div className="p-6 md:p-8 flex flex-col items-center text-center">
            {/* Animated Check Icon */}
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              transition={{ type: 'spring', stiffness: 260, damping: 20 }}
              className="w-20 h-20 rounded-full bg-emerald-50 border-4 border-emerald-100 flex items-center justify-center text-emerald-600 mb-4 shadow-sm"
            >
              <CheckCircle2 size={44} strokeWidth={2.5} />
            </motion.div>

            <h1 className="text-2xl font-black text-gray-900 mb-1 tracking-tight">Thanh toán thành công!</h1>
            <p className="text-sm text-gray-500 mb-6 max-w-sm">
              Đơn hàng của bạn đã được thanh toán và chuyển trạng thái cho nhà cung cấp chuẩn bị.
            </p>

            {/* Receipt Summary Card */}
            <div className="w-full bg-gray-50/80 border border-gray-100 rounded-2xl p-4 md:p-5 mb-6 text-left">
              {paymentDetails.amount != null && paymentDetails.amount > 0 && (
                <div className="flex items-center justify-between pb-3.5 mb-3.5 border-b border-gray-200/60">
                  <span className="text-xs text-gray-500 font-medium">Số tiền đã thanh toán</span>
                  <span className="text-xl font-black text-emerald-600">
                    {formatVND(paymentDetails.amount)}
                  </span>
                </div>
              )}

              <div className="space-y-2.5 text-xs">
                {paymentDetails.orderId && (
                  <div className="flex justify-between items-center">
                    <span className="text-gray-500 flex items-center gap-1.5">
                      <Receipt size={14} className="text-gray-400" /> Mã đơn hàng
                    </span>
                    <span className="font-semibold text-gray-900">
                      #{paymentDetails.orderId} {paymentDetails.orderCode ? `(${paymentDetails.orderCode})` : ''}
                    </span>
                  </div>
                )}

                {paymentDetails.provider && (
                  <div className="flex justify-between items-center">
                    <span className="text-gray-500">Cổng thanh toán</span>
                    {paymentDetails.provider.toUpperCase() === 'ZALOPAY' ? (
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-bold bg-blue-50 text-blue-700 border border-blue-200">
                        ZaloPay
                      </span>
                    ) : (
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-bold bg-pink-50 text-pink-700 border border-pink-200">
                        MoMo
                      </span>
                    )}
                  </div>
                )}

                {paymentDetails.supplierName && (
                  <div className="flex justify-between items-center">
                    <span className="text-gray-500 flex items-center gap-1.5">
                      <Building2 size={14} className="text-gray-400" /> Cửa hàng
                    </span>
                    <span className="font-medium text-gray-800 text-right truncate max-w-[200px]">
                      {paymentDetails.supplierName}
                    </span>
                  </div>
                )}

                <div className="flex justify-between items-center">
                  <span className="text-gray-500 flex items-center gap-1.5">
                    <Clock size={14} className="text-gray-400" /> Thời gian
                  </span>
                  <span className="text-gray-700 font-medium">
                    {paymentDetails.paidAt
                      ? new Date(paymentDetails.paidAt).toLocaleString('vi-VN')
                      : 'Vừa xong'}
                  </span>
                </div>
              </div>
            </div>

            {/* Action Buttons */}
            <div className="w-full flex flex-col gap-2.5">
              {paymentDetails.orderId ? (
                <button
                  id="btn-view-order-detail"
                  onClick={() => navigate(`${ordersPath}/${paymentDetails.orderId}`)}
                  className="w-full py-3 px-4 rounded-xl bg-[#2db84c] hover:bg-[#259e40] text-white font-bold text-sm flex items-center justify-center gap-2 shadow-md shadow-[#2db84c]/20 transition-all cursor-pointer"
                >
                  Xem chi tiết đơn hàng <ArrowRight size={16} />
                </button>
              ) : null}

              <button
                id="btn-back-to-orders"
                onClick={() => navigate(ordersPath)}
                className="w-full py-3 px-4 rounded-xl border border-gray-200 hover:bg-gray-50 text-gray-700 font-semibold text-sm flex items-center justify-center gap-2 transition-all cursor-pointer"
              >
                <ShoppingBag size={16} className="text-gray-500" /> Danh sách đơn hàng của tôi
              </button>

              <Link
                to={explorePath}
                className="mt-2 text-xs font-medium text-gray-400 hover:text-gray-600 transition-colors"
              >
                Tiếp tục khám phá món ngon
              </Link>
            </div>
          </div>
        ) : (
          /* FAILED / CANCELLED STATE */
          <div className="p-6 md:p-8 flex flex-col items-center text-center">
            {/* Animated Warning Icon */}
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              transition={{ type: 'spring', stiffness: 260, damping: 20 }}
              className="w-20 h-20 rounded-full bg-rose-50 border-4 border-rose-100 flex items-center justify-center text-rose-500 mb-4 shadow-sm"
            >
              <XCircle size={44} strokeWidth={2.5} />
            </motion.div>

            <h1 className="text-2xl font-black text-gray-900 mb-1 tracking-tight">Thanh toán chưa hoàn tất</h1>
            <p className="text-sm text-gray-500 mb-6 max-w-sm">
              {message || 'Giao dịch thanh toán qua ví điện tử đã bị huỷ hoặc xảy ra sự cố.'}
            </p>

            {/* Info Notice */}
            <div className="w-full bg-amber-50/70 border border-amber-200/80 rounded-2xl p-4 mb-6 text-left flex items-start gap-3">
              <HelpCircle size={18} className="text-amber-600 flex-shrink-0 mt-0.5" />
              <div className="text-xs text-amber-900 leading-relaxed">
                <p className="font-semibold mb-0.5">Đơn hàng của bạn vẫn được lưu lại an toàn!</p>
                <p className="text-amber-700">
                  Bạn có thể mở chi tiết đơn hàng để thực hiện thanh toán lại qua ví điện tử hoặc chọn thanh toán tiền mặt khi nhận hàng.
                </p>
              </div>
            </div>

            {/* Action Buttons */}
            <div className="w-full flex flex-col gap-2.5">
              {paymentDetails.orderId ? (
                <button
                  id="btn-retry-payment"
                  onClick={() => navigate(`${ordersPath}/${paymentDetails.orderId}`)}
                  className="w-full py-3 px-4 rounded-xl bg-[#2db84c] hover:bg-[#259e40] text-white font-bold text-sm flex items-center justify-center gap-2 shadow-md shadow-[#2db84c]/20 transition-all cursor-pointer"
                >
                  <RotateCcw size={16} /> Vào chi tiết đơn để thanh toán lại
                </button>
              ) : null}

              <button
                id="btn-back-to-orders-failed"
                onClick={() => navigate(ordersPath)}
                className="w-full py-3 px-4 rounded-xl border border-gray-200 hover:bg-gray-50 text-gray-700 font-semibold text-sm flex items-center justify-center gap-2 transition-all cursor-pointer"
              >
                <ShoppingBag size={16} className="text-gray-500" /> Quay lại danh sách đơn hàng
              </button>
            </div>
          </div>
        )}
      </motion.div>
    </div>
  );
}
