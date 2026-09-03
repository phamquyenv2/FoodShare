import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowLeft, Minus, Plus, Trash2, ShoppingBag, Loader2, X, CheckCircle,
} from 'lucide-react';
import { apiFetch } from '../../services/api';
import { formatVND } from '../../utils/format';

interface CartItem {
  post: {
    id: number; name: string; imageUrl: string; unitPrice: number;
    postType: string; availableQuantity: number; supplierName: string;
    pickupAddress: string;
  };
  quantity: number;
}

function loadCart(): CartItem[] {
  try { return JSON.parse(localStorage.getItem('org_cart') || '[]'); } catch { return []; }
}
function saveCart(items: CartItem[]) {
  localStorage.setItem('org_cart', JSON.stringify(items));
}

export default function OrgCartPage() {
  const navigate = useNavigate();
  const [cart, setCart] = useState<CartItem[]>(loadCart);
  const [isOrdering, setIsOrdering] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const updateQty = (postId: number, delta: number) => {
    setCart(prev => {
      const next = prev.map(c => {
        if (c.post.id !== postId) return c;
        const newQty = Math.max(1, Math.min(c.post.availableQuantity, c.quantity + delta));
        return { ...c, quantity: newQty };
      });
      saveCart(next);
      return next;
    });
  };

  const removeItem = (postId: number) => {
    setCart(prev => {
      const next = prev.filter(c => c.post.id !== postId);
      saveCart(next);
      return next;
    });
  };

  const clearCart = () => { setCart([]); saveCart([]); };

  // Group by supplier
  const grouped = cart.reduce<Record<string, CartItem[]>>((acc, item) => {
    const key = item.post.supplierName || 'Không rõ';
    if (!acc[key]) acc[key] = [];
    acc[key].push(item);
    return acc;
  }, {});

  const totalAmount = cart.reduce((s, c) => s + (c.post.postType === 'FREE' ? 0 : c.post.unitPrice * c.quantity), 0);
  const totalItems = cart.reduce((s, c) => s + c.quantity, 0);
  const supplierCount = Object.keys(grouped).length;

  const handleBatchOrder = async () => {
    setError('');
    setIsOrdering(true);
    try {
      // Use batch API — backend groups by supplier automatically
      const items = cart.map(c => ({ foodPostId: c.post.id, quantity: c.quantity }));
      await apiFetch('/orders/batch', {
        method: 'POST',
        body: JSON.stringify({ items }),
      });
      clearCart();
      setSuccess(true);
    } catch (err: any) {
      setError(err.message || 'Đặt hàng thất bại');
    } finally {
      setIsOrdering(false);
    }
  };

  if (success) {
    return (
      <div className="p-4 md:p-6 max-w-lg mx-auto">
        <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
          className="bg-white rounded-2xl border border-gray-100 p-8 text-center">
          <div className="w-20 h-20 rounded-full bg-[#2db84c]/10 flex items-center justify-center mx-auto mb-4">
            <CheckCircle size={32} className="text-[#2db84c]" />
          </div>
          <h2 className="text-xl font-bold text-gray-900 mb-2">Đặt hàng thành công! 🎉</h2>
          <p className="text-sm text-gray-500 mb-2">Hệ thống đã tạo {supplierCount} đơn hàng từ {supplierCount} nhà cung cấp.</p>
          <p className="text-xs text-gray-400 mb-6">Các đơn hàng đang chờ nhà cung cấp xác nhận.</p>
          <div className="flex gap-3">
            <button onClick={() => navigate('/organization/orders')}
              className="flex-1 py-3 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] transition-all shadow-md shadow-green-500/20">Xem đơn hàng</button>
            <button onClick={() => navigate('/organization')}
              className="flex-1 py-3 rounded-xl border border-gray-200 text-gray-600 font-semibold text-sm cursor-pointer hover:bg-gray-50 transition-all">Tiếp tục</button>
          </div>
        </motion.div>
      </div>
    );
  }

  return (
    <div className="p-4 md:p-6 max-w-3xl mx-auto flex flex-col gap-5">
      <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-sm text-gray-500 hover:text-gray-700 cursor-pointer self-start">
        <ArrowLeft size={16} /> Quay lại
      </button>

      <div className="flex items-center justify-between">
        <h1 className="text-xl md:text-2xl font-bold text-gray-900">Giỏ hàng</h1>
        {cart.length > 0 && (
          <button onClick={clearCart} className="text-xs text-red-500 font-medium cursor-pointer hover:underline">Xóa tất cả</button>
        )}
      </div>

      {cart.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <ShoppingBag size={48} className="mx-auto mb-3 opacity-50" />
          <p className="text-sm">Giỏ hàng trống</p>
          <button onClick={() => navigate('/organization')}
            className="mt-4 px-4 py-2 rounded-xl bg-[#2db84c] text-white text-sm font-medium cursor-pointer hover:bg-[#259e40] transition-all">Khám phá ngay</button>
        </div>
      ) : (
        <>
          {/* Grouped by supplier */}
          {Object.entries(grouped).map(([supplier, items]) => (
            <motion.div key={supplier} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
              className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
              <div className="px-4 py-3 bg-gray-50 border-b border-gray-100">
                <div className="flex items-center gap-2">
                  <div className="w-7 h-7 rounded-full bg-gradient-to-br from-[#2db84c] to-[#1a9e3a] flex items-center justify-center text-white text-[10px] font-bold">
                    {supplier.charAt(0).toUpperCase()}
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-gray-900">{supplier}</p>
                    <p className="text-[10px] text-gray-400">{items.length} món · {items[0].post.pickupAddress}</p>
                  </div>
                </div>
              </div>
              <div className="divide-y divide-gray-50">
                {items.map(item => (
                  <div key={item.post.id} className="flex items-center gap-3 p-4">
                    <div className="w-14 h-14 rounded-xl bg-gray-100 overflow-hidden flex-shrink-0">
                      {item.post.imageUrl ? <img src={item.post.imageUrl} alt="" className="w-full h-full object-cover" /> :
                        <div className="w-full h-full flex items-center justify-center"><ShoppingBag size={16} className="text-gray-300" /></div>}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-gray-900 truncate">{item.post.name}</p>
                      <p className="text-xs text-gray-500">{item.post.postType === 'FREE' ? '🎁 Miễn phí' : formatVND(item.post.unitPrice) + ' / phần'}</p>
                    </div>
                    <div className="flex items-center gap-2 flex-shrink-0">
                      <button onClick={() => updateQty(item.post.id, -1)} disabled={item.quantity <= 1}
                        className="w-8 h-8 rounded-lg border border-gray-200 flex items-center justify-center cursor-pointer hover:bg-gray-50 disabled:opacity-30"><Minus size={14} /></button>
                      <span className="text-sm font-bold w-6 text-center">{item.quantity}</span>
                      <button onClick={() => updateQty(item.post.id, 1)} disabled={item.quantity >= item.post.availableQuantity}
                        className="w-8 h-8 rounded-lg border border-gray-200 flex items-center justify-center cursor-pointer hover:bg-gray-50 disabled:opacity-30"><Plus size={14} /></button>
                      <button onClick={() => removeItem(item.post.id)}
                        className="w-8 h-8 rounded-lg text-red-400 hover:bg-red-50 flex items-center justify-center cursor-pointer transition-colors"><Trash2 size={14} /></button>
                    </div>
                  </div>
                ))}
              </div>
            </motion.div>
          ))}

          {/* Summary */}
          <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
            className="bg-white rounded-2xl border border-gray-100 p-5">
            <h3 className="font-semibold text-gray-900 mb-3">Tóm tắt đơn hàng</h3>
            <div className="flex flex-col gap-2 text-sm mb-4">
              <div className="flex justify-between"><span className="text-gray-500">Tổng món</span><span className="text-gray-900">{totalItems} phần</span></div>
              <div className="flex justify-between"><span className="text-gray-500">Nhà cung cấp</span><span className="text-gray-900">{supplierCount}</span></div>
              <div className="flex justify-between"><span className="text-gray-500">Đơn hàng sẽ tạo</span><span className="text-gray-900">{supplierCount} đơn</span></div>
              <div className="border-t border-gray-200 pt-2 mt-1 flex justify-between">
                <span className="font-semibold text-gray-900">Tổng tiền</span>
                <span className="text-lg font-bold text-gray-900">{totalAmount > 0 ? formatVND(totalAmount) : '🎁 Miễn phí'}</span>
              </div>
            </div>
            <p className="text-xs text-gray-400 mb-4">
              💡 Hệ thống sẽ tự động tạo đơn hàng riêng cho mỗi nhà cung cấp.
            </p>

            {error && <div className="p-3 bg-red-50 text-red-600 rounded-xl text-sm border border-red-100 mb-4">{error}</div>}

            <button onClick={handleBatchOrder} disabled={isOrdering}
              className="w-full py-3.5 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 disabled:opacity-70 flex items-center justify-center gap-2">
              {isOrdering ? <Loader2 size={16} className="animate-spin" /> : <ShoppingBag size={16} />}
              {isOrdering ? 'Đang xử lý...' : `Đặt hàng (${supplierCount} nhà cung cấp)`}
            </button>
          </motion.div>
        </>
      )}
    </div>
  );
}
