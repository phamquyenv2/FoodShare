import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowLeft, Minus, Plus, Trash2, ShoppingBag, Loader2, CheckCircle
} from 'lucide-react';
import { apiFetch } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';
import { formatVND } from '../../utils/format';
import {
  loadCart,
  updateCartQuantity,
  removeFromCart,
  clearCart,
  type CartItem,
} from '../../services/cartService';

export default function OrgCartPage() {
  const { showError } = useToast();
  const navigate = useNavigate();
  const [cart, setCart] = useState<CartItem[]>(loadCart);
  const [isOrdering, setIsOrdering] = useState(false);
  const [success, setSuccess] = useState(false);
  const [orderedSupplierCount, setOrderedSupplierCount] = useState(0);

  useEffect(() => {
    const handleCartSync = () => {
      setCart(loadCart());
    };
    window.addEventListener('cart:updated', handleCartSync);
    window.addEventListener('storage', handleCartSync);
    return () => {
      window.removeEventListener('cart:updated', handleCartSync);
      window.removeEventListener('storage', handleCartSync);
    };
  }, []);

  const updateQty = (postId: number, delta: number) => {
    const current = cart.find(c => c.post.id === postId);
    if (!current) return;
    const newQty = current.quantity + delta;
    setCart(updateCartQuantity(postId, newQty));
  };

  const removeItem = (postId: number) => {
    setCart(removeFromCart(postId));
  };

  const handleClearCart = () => {
    clearCart();
    setCart([]);
  };

  // Group items by supplier
  const grouped = cart.reduce<Record<string, CartItem[]>>((acc, item) => {
    const key = item.post.supplierName || 'Nhà cung cấp';
    if (!acc[key]) acc[key] = [];
    acc[key].push(item);
    return acc;
  }, {});

  const totalAmount = cart.reduce((s, c) => s + (c.post.postType === 'FREE' ? 0 : c.post.unitPrice * c.quantity), 0);
  const totalItems = cart.reduce((s, c) => s + c.quantity, 0);
  const supplierCount = Object.keys(grouped).length;

  const handleBatchOrder = async () => {
    if (cart.length === 0) return;
    setIsOrdering(true);
    setOrderedSupplierCount(supplierCount);
    try {
      const orders = cart.map(c => ({ foodPostId: c.post.id, quantity: c.quantity }));
      await apiFetch('/orders/batch', {
        method: 'POST',
        body: JSON.stringify({ orders }),
      });
      clearCart();
      setCart([]);
      setSuccess(true);
    } catch (err: any) {
      showError(err.message || 'Đặt hàng thất bại');
    } finally {
      setIsOrdering(false);
    }
  };

  if (success) {
    return (
      <div className="p-4 md:p-6 max-w-lg mx-auto">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          className="bg-white rounded-2xl border border-gray-100 p-8 text-center"
        >
          <div className="w-20 h-20 rounded-full bg-[#2db84c]/10 flex items-center justify-center mx-auto mb-4">
            <CheckCircle size={32} className="text-[#2db84c]" />
          </div>
          <h2 className="text-xl font-bold text-gray-900 mb-2">Đặt hàng thành công! 🎉</h2>
          <p className="text-sm text-gray-500 mb-2">
            Hệ thống đã tự động tạo các đơn hàng riêng cho {orderedSupplierCount} nhà cung cấp.
          </p>
          <p className="text-xs text-gray-400 mb-6">
            Các nhà cung cấp đã nhận được thông báo và sẽ chuẩn bị các phần ăn cho tổ chức.
          </p>
          <div className="flex gap-3">
            <button
              onClick={() => navigate('/organization/orders')}
              className="flex-1 py-3 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] transition-all shadow-md shadow-green-500/20"
            >
              Xem đơn hàng
            </button>
            <button
              onClick={() => navigate('/organization')}
              className="flex-1 py-3 rounded-xl border border-gray-200 text-gray-600 font-semibold text-sm cursor-pointer hover:bg-gray-50 transition-all"
            >
              Tiếp tục tìm món
            </button>
          </div>
        </motion.div>
      </div>
    );
  }

  return (
    <div className="p-4 md:p-6 max-w-3xl mx-auto flex flex-col gap-5">
      <button
        onClick={() => navigate(-1)}
        className="flex items-center gap-2 text-sm text-gray-500 hover:text-gray-700 cursor-pointer self-start"
      >
        <ArrowLeft size={16} /> Quay lại
      </button>

      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-gray-900">Giỏ hàng tổ chức</h1>
          <p className="text-xs text-gray-500 mt-0.5">Đặt món theo số lượng lớn từ nhiều quán ăn cùng lúc</p>
        </div>
        {cart.length > 0 && (
          <button
            onClick={handleClearCart}
            className="text-xs text-red-500 font-medium cursor-pointer hover:underline"
          >
            Xóa tất cả
          </button>
        )}
      </div>

      {cart.length === 0 ? (
        <div className="text-center py-16 bg-white rounded-2xl border border-gray-100 p-8 text-gray-400">
          <ShoppingBag size={48} className="mx-auto mb-3 opacity-40 text-gray-400" />
          <p className="text-base font-medium text-gray-600">Giỏ hàng hiện đang trống</p>
          <p className="text-xs text-gray-400 mt-1 max-w-sm mx-auto">
            Tổ chức có thể thêm các món ăn với số lượng lớn từ các nhà hàng vào cùng một giỏ hàng.
          </p>
          <button
            onClick={() => navigate('/organization')}
            className="mt-5 px-5 py-2.5 rounded-xl bg-[#2db84c] text-white text-sm font-medium cursor-pointer hover:bg-[#259e40] transition-all shadow-md shadow-green-500/20"
          >
            Khám phá món ăn ngay
          </button>
        </div>
      ) : (
        <>
          {/* Grouped by supplier */}
          {Object.entries(grouped).map(([supplier, items]) => (
            <motion.div
              key={supplier}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              className="bg-white rounded-2xl border border-gray-100 overflow-hidden shadow-sm"
            >
              <div className="px-4 py-3 bg-gray-50/80 border-b border-gray-100">
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-full bg-gradient-to-br from-[#2db84c] to-[#1a9e3a] flex items-center justify-center text-white text-xs font-bold">
                    {supplier.charAt(0).toUpperCase()}
                  </div>
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-gray-900 truncate">{supplier}</p>
                    <p className="text-[11px] text-gray-500 truncate">
                      {items.length} món · {items[0]?.post?.pickupAddress || 'Tại quán'}
                    </p>
                  </div>
                </div>
              </div>

              <div className="divide-y divide-gray-100">
                {items.map(item => (
                  <div key={item.post.id} className="flex items-center gap-3.5 p-4">
                    <div className="w-16 h-16 rounded-xl bg-gray-100 overflow-hidden flex-shrink-0">
                      {item.post.imageUrl ? (
                        <img src={item.post.imageUrl} alt={item.post.name} className="w-full h-full object-cover" />
                      ) : (
                        <div className="w-full h-full flex items-center justify-center">
                          <ShoppingBag size={20} className="text-gray-300" />
                        </div>
                      )}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-semibold text-gray-900 truncate">{item.post.name}</p>
                      <p className="text-xs text-gray-500 mt-0.5">
                        {item.post.postType === 'FREE' ? (
                          <span className="text-[#2db84c] font-semibold">Miễn phí</span>
                        ) : (
                          <span className="text-gray-700 font-medium">{formatVND(item.post.unitPrice)} / phần</span>
                        )}
                        <span className="text-gray-400 ml-2">
                          (Còn lại: {item.post.availableQuantity})
                        </span>
                      </p>
                    </div>
                    <div className="flex items-center gap-1.5 flex-shrink-0">
                      <button
                        onClick={() => updateQty(item.post.id, -1)}
                        disabled={item.quantity <= 1}
                        className="w-8 h-8 rounded-lg border border-gray-200 flex items-center justify-center cursor-pointer hover:bg-gray-50 disabled:opacity-30 transition-colors"
                        title="Giảm số lượng"
                      >
                        <Minus size={14} />
                      </button>
                      <input
                        type="number"
                        min={1}
                        max={item.post.availableQuantity}
                        value={item.quantity}
                        onChange={(e) => {
                          const val = parseInt(e.target.value, 10);
                          if (!isNaN(val)) {
                            setCart(updateCartQuantity(item.post.id, val));
                          }
                        }}
                        className="w-12 h-8 text-center text-sm font-bold border border-gray-200 rounded-lg focus:outline-none focus:border-[#2db84c]"
                      />
                      <button
                        onClick={() => updateQty(item.post.id, 1)}
                        disabled={item.quantity >= item.post.availableQuantity}
                        className="w-8 h-8 rounded-lg border border-gray-200 flex items-center justify-center cursor-pointer hover:bg-gray-50 disabled:opacity-30 transition-colors"
                        title="Tăng số lượng"
                      >
                        <Plus size={14} />
                      </button>
                      <button
                        onClick={() => removeItem(item.post.id)}
                        className="w-8 h-8 rounded-lg text-red-400 hover:bg-red-50 flex items-center justify-center cursor-pointer transition-colors ml-1"
                        title="Xóa khỏi giỏ"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </motion.div>
          ))}

          {/* Summary */}
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            className="bg-white rounded-2xl border border-gray-100 p-5 shadow-sm"
          >
            <h3 className="font-semibold text-gray-900 mb-3">Tóm tắt đơn hàng tổ chức</h3>
            <div className="flex flex-col gap-2.5 text-sm mb-4">
              <div className="flex justify-between">
                <span className="text-gray-500">Tổng số phần ăn</span>
                <span className="font-semibold text-gray-900">{totalItems} phần</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Số nhà cung cấp</span>
                <span className="text-gray-900">{supplierCount} nhà cung cấp</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Số đơn hàng sẽ tạo</span>
                <span className="text-gray-900 font-medium">{supplierCount} đơn hàng độc lập</span>
              </div>
              <div className="border-t border-gray-200 pt-3 mt-1 flex justify-between items-baseline">
                <span className="font-semibold text-gray-900">Tổng tiền thanh toán</span>
                <span className="text-xl font-bold text-[#2db84c]">
                  {totalAmount > 0 ? formatVND(totalAmount) : 'Miễn phí'}
                </span>
              </div>
            </div>
            <div className="p-3 bg-green-50 rounded-xl mb-4 border border-green-100 text-xs text-green-800 leading-relaxed">
              💡 <strong>Quy trình tiếp nhận:</strong> Hệ thống tự động phân tách thành từng đơn tương ứng cho mỗi nhà cung cấp. Các quán ăn sẽ duyệt và chuẩn bị riêng biệt theo địa chỉ nhận món.
            </div>

            <button
              onClick={handleBatchOrder}
              disabled={isOrdering}
              className="w-full py-3.5 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 disabled:opacity-70 flex items-center justify-center gap-2"
            >
              {isOrdering ? <Loader2 size={16} className="animate-spin" /> : <ShoppingBag size={16} />}
              {isOrdering ? 'Đang gửi yêu cầu đặt...' : `Xác nhận đặt hàng (${supplierCount} quán · ${totalItems} phần)`}
            </button>
          </motion.div>
        </>
      )}
    </div>
  );
}
