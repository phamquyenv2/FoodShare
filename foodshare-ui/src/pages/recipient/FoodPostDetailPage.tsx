import { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Loader2, UtensilsCrossed, AlertTriangle, Flag,
  ArrowLeft, Clock, ShoppingBag, MapPin, User, Minus, Plus, Star,
  ShoppingCart, Check
} from 'lucide-react';
import { apiFetch } from '../../services/api';
import { useAuth } from '../../contexts/AuthContext';
import { useToast } from '../../contexts/ToastContext';
import { addToCart } from '../../services/cartService';
import { formatVND } from '../../utils/format';

interface PostDetail {
  id: number;
  name: string;
  description: string;
  imageUrl: string;
  images?: { id: number; imageUrl: string }[];
  category?: { name: string };
  totalQuantity: number;
  availableQuantity: number;
  unitPrice: number;
  postType: string;
  postStatus: string;
  pickupAddress: string;
  pickupStartAt: string;
  pickupEndAt: string;
  expiresAt: string;
  supplier?: {
    name: string;
    description?: string;
  };
  createdAt: string;
}

function formatDate(iso: string) {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function getTimeLeft(expiresAt: string): { text: string; urgent: boolean } {
  const diff = new Date(expiresAt).getTime() - Date.now();
  if (diff <= 0) return { text: 'Đã hết hạn', urgent: true };
  const hrs = Math.floor(diff / 3600000);
  const mins = Math.floor((diff % 3600000) / 60000);
  if (hrs > 24) return { text: `Còn ${Math.floor(hrs / 24)} ngày`, urgent: false };
  if (hrs > 0) return { text: `Còn ${hrs}h ${mins}p`, urgent: hrs < 3 };
  return { text: `Còn ${mins} phút`, urgent: true };
}

export default function FoodPostDetailPage() {
  const { user } = useAuth();
  const { showSuccess, showError } = useToast();
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [post, setPost] = useState<PostDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [quantity, setQuantity] = useState(1);
  const [isOrdering, setIsOrdering] = useState(false);
  const isOrderingRef = useRef(false);
  const [orderSuccess, setOrderSuccess] = useState(false);
  const [activeImg, setActiveImg] = useState(0);
  const [addedToCart, setAddedToCart] = useState(false);
  const location = useLocation();

  const isOrganization = user?.role === 'ORGANIZATION';
  const rolePath = isOrganization ? 'organization' : (location.pathname.split('/')[1] || 'recipient');
  const maxAllowedQuantity = isOrganization ? (post?.availableQuantity || 1) : 1;

  useEffect(() => {
    const fetchPost = async () => {
      try {
        const data = await apiFetch<PostDetail>(`/food-posts/${id}`);
        setPost(data);
      } catch (err: any) {
        showError(err.message || 'Không thể tải thông tin');
      } finally {
        setIsLoading(false);
      }
    };
    if (id) fetchPost();
  }, [id, showError]);

  const handleAddToCart = () => {
    if (!post) return;
    addToCart(post, quantity);
    setAddedToCart(true);
    showSuccess(`Đã thêm ${quantity} phần "${post.name}" vào giỏ hàng`);
    setTimeout(() => setAddedToCart(false), 2500);
  };

  const handleOrder = async () => {
    if (!post || isOrderingRef.current || isOrdering) return;
    isOrderingRef.current = true;
    setIsOrdering(true);
    try {
      await apiFetch('/orders', {
        method: 'POST',
        body: JSON.stringify({
          foodPostId: post.id,
          quantity,
        }),
      });
      setOrderSuccess(true);
    } catch (err: any) {
      showError(err.message || 'Đặt hàng thất bại');
    } finally {
      isOrderingRef.current = false;
      setIsOrdering(false);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 size={24} className="animate-spin text-[#2db84c]" />
      </div>
    );
  }

  if (!post) {
    return (
      <div className="p-4 md:p-6 max-w-3xl mx-auto">
        <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-sm text-gray-500 hover:text-gray-700 mb-4 cursor-pointer">
          <ArrowLeft size={16} /> Quay lại
        </button>
        <div className="text-center py-16 text-gray-400">
          <AlertTriangle size={48} className="mx-auto mb-3 opacity-50" />
          <p className="text-sm">Không tìm thấy bài đăng</p>
        </div>
      </div>
    );
  }

  const allImages = post.images && post.images.length > 0
    ? post.images.map(img => typeof img === 'string' ? img : (img as any).imageUrl)
    : post.imageUrl ? [post.imageUrl] : [];
  const timeLeft = getTimeLeft(post.expiresAt);
  const isAvailable = (post.postStatus === 'ACTIVE' || post.postStatus === 'AVAILABLE') && post.availableQuantity > 0 && !timeLeft.text.includes('hết hạn');
  const totalPrice = post.postType === 'FREE' ? 0 : post.unitPrice * quantity;

  // Order success overlay
  if (orderSuccess) {
    return (
      <div className="p-4 md:p-6 max-w-3xl mx-auto">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
          className="bg-white rounded-2xl border border-gray-100 p-8 text-center"
        >
          <div className="w-20 h-20 rounded-full bg-[#2db84c]/10 flex items-center justify-center mx-auto mb-4">
            <ShoppingBag size={32} className="text-[#2db84c]" />
          </div>
          <h2 className="text-xl font-bold text-gray-900 mb-2">Đặt hàng thành công! 🎉</h2>
          <p className="text-sm text-gray-500 mb-6">
            Đơn hàng của bạn đang chờ nhà cung cấp xác nhận.
          </p>
          <div className="flex gap-3">
            <button
              onClick={() => navigate(isOrganization ? '/organization/orders' : '/recipient/orders')}
              className="flex-1 py-3 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] transition-all shadow-md shadow-green-500/20"
            >
              Xem đơn hàng
            </button>
            <button
              onClick={() => navigate(isOrganization ? '/organization' : '/recipient')}
              className="flex-1 py-3 rounded-xl border border-gray-200 text-gray-600 font-semibold text-sm cursor-pointer hover:bg-gray-50 transition-all"
            >
              Tiếp tục khám phá
            </button>
          </div>
        </motion.div>
      </div>
    );
  }

  return (
    <div className="p-4 md:p-6 max-w-4xl mx-auto">
      <div className="mb-4">
        <button onClick={() => navigate(-1)} className="inline-flex items-center gap-2 text-sm text-gray-500 hover:text-gray-700 cursor-pointer">
          <ArrowLeft size={16} /> Quay lại
        </button>
      </div>

      <div className="grid md:grid-cols-[1fr_360px] gap-5">
        {/* Left: Image + Details */}
        <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="flex flex-col gap-4">
          {/* Image Gallery */}
          <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
            <div className="h-64 md:h-80 bg-gray-100 relative">
              {allImages.length > 0 ? (
                <img src={allImages[activeImg]} alt={post.name} className="w-full h-full object-cover" />
              ) : (
                <div className="w-full h-full flex items-center justify-center">
                  <UtensilsCrossed size={48} className="text-gray-300" />
                </div>
              )}
              {/* Expiry */}
              <div className="absolute top-3 right-3">
                <span className={`px-2.5 py-1 rounded-lg text-xs font-bold backdrop-blur-md flex items-center gap-1 ${timeLeft.urgent ? 'bg-red-500/90 text-white' : 'bg-black/50 text-white'
                  }`}>
                  <Clock size={12} /> {timeLeft.text}
                </span>
              </div>
            </div>
            {allImages.length > 1 && (
              <div className="flex gap-2 p-3 overflow-x-auto">
                {allImages.map((img, i) => (
                  <button
                    key={i}
                    onClick={() => setActiveImg(i)}
                    className={`w-16 h-16 rounded-lg overflow-hidden flex-shrink-0 border-2 cursor-pointer transition-all ${activeImg === i ? 'border-[#2db84c] shadow-sm' : 'border-transparent opacity-60 hover:opacity-100'
                      }`}
                  >
                    <img src={img} alt="" className="w-full h-full object-cover" />
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Description */}
          <div className="bg-white rounded-2xl border border-gray-100 p-5">
            <h1 className="text-lg md:text-xl font-bold text-gray-900 mb-1">{post.name}</h1>
            <div className="flex items-center gap-2 text-xs text-gray-400 mb-4">
              <span className="px-2 py-0.5 rounded-full bg-gray-100 text-gray-600 font-medium">{post.category?.name || 'Khác'}</span>
              <span>·</span>
              <span>Đăng {formatDate(post.createdAt)}</span>
            </div>

            {post.description && (
              <div className="mb-4">
                <h3 className="text-sm font-semibold text-gray-700 mb-1">Mô tả</h3>
                <p className="text-sm text-gray-600 leading-relaxed whitespace-pre-wrap">{post.description}</p>
              </div>
            )}

            <div className="grid grid-cols-2 gap-3 text-sm">
              <div className="p-3 rounded-xl bg-gray-50">
                <p className="text-xs text-gray-500 mb-0.5">Số lượng còn</p>
                <p className="font-semibold text-gray-900">{post.availableQuantity} / {post.totalQuantity}</p>
              </div>
              <div className="p-3 rounded-xl bg-gray-50">
                <p className="text-xs text-gray-500 mb-0.5">Giá</p>
                <p className="font-semibold text-gray-900">
                  {post.postType === 'FREE' ? 'Miễn phí' : formatVND(post.unitPrice) + ' / phần'}
                </p>
              </div>
            </div>

            <div className="mt-4 p-3 rounded-xl bg-gray-50">
              <div className="flex items-start gap-2">
                <MapPin size={14} className="text-gray-400 mt-0.5 flex-shrink-0" />
                <div>
                  <p className="text-xs text-gray-500 mb-0.5">Địa điểm nhận</p>
                  <p className="text-sm font-medium text-gray-900">{post.pickupAddress}</p>
                </div>
              </div>
            </div>

            <div className="mt-3 p-3 rounded-xl bg-gray-50">
              <div className="flex items-start gap-2">
                <Clock size={14} className="text-gray-400 mt-0.5 flex-shrink-0" />
                <div>
                  <p className="text-xs text-gray-500 mb-0.5">Thời gian nhận</p>
                  <p className="text-sm font-medium text-gray-900">
                    {formatDate(post.pickupStartAt)} — {formatDate(post.pickupEndAt)}
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Supplier Info */}
          <div className="bg-white rounded-2xl border border-gray-100 p-4">
            <h3 className="text-xs font-semibold text-gray-700 mb-2.5">Nhà cung cấp</h3>
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-[#2db84c] to-[#1a9e3a] flex items-center justify-center text-white text-base font-bold flex-shrink-0 shadow-xs">
                {post.supplier?.name?.charAt(0)?.toUpperCase() || <User size={18} />}
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <p className="font-semibold text-gray-900 text-sm truncate">{post.supplier?.name || 'Không rõ'}</p>
                  <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-md bg-amber-50 border border-amber-200/60 text-amber-700 text-[11px] font-semibold">
                    <Star size={11} className="text-amber-400 fill-amber-400 shrink-0" />
                    <span>{(post.supplier as any)?.rating ? (post.supplier as any).rating.toFixed(1) : '5.0'}</span>
                    <span className="text-[10px] text-amber-600/70 font-normal">
                      ({(post.supplier as any)?.reviewCount ?? 0} đánh giá)
                    </span>
                  </div>
                </div>
                {post.supplier?.description && (
                  <p className="text-xs text-gray-500 line-clamp-1 mt-0.5">{post.supplier.description}</p>
                )}
              </div>
            </div>
          </div>
        </motion.div>

        {/* Right: Order Panel */}
        <motion.div
          initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}
          className="md:sticky md:top-20 h-fit"
        >
          <div className="bg-white rounded-2xl border border-gray-100 p-5">
            <div className="flex items-center justify-between mb-4 pb-2.5 border-b border-gray-100">
              <h3 className="font-semibold text-gray-900">Đặt hàng</h3>
              <button
                type="button"
                onClick={() => navigate(`/${rolePath}/report/${id}?type=FOOD_POST`)}
                className="flex items-center gap-1.5 text-xs text-gray-400 hover:text-red-500 transition-colors cursor-pointer px-2 py-1 rounded-lg hover:bg-red-50"
                title="Báo cáo bài đăng"
              >
                <Flag size={13} />
                <span>Báo cáo</span>
              </button>
            </div>

            {!isAvailable ? (
              <div className="p-4 bg-gray-50 rounded-xl text-center">
                <AlertTriangle size={24} className="mx-auto mb-2 text-gray-400" />
                <p className="text-sm text-gray-500">Bài đăng này hiện không khả dụng</p>
              </div>
            ) : (
              <>
                {/* Quantity selector */}
                <div className="mb-4">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-sm text-gray-600 font-medium">Số lượng</p>
                    <span className="text-xs text-gray-400">
                      {isOrganization ? `Tối đa ${post.availableQuantity} phần` : 'Tối đa 1 phần'}
                    </span>
                  </div>
                  <div className="flex items-center gap-2.5">
                    <button
                      type="button"
                      onClick={() => setQuantity(q => Math.max(1, q - 1))}
                      disabled={isOrdering || quantity <= 1}
                      className="w-10 h-10 rounded-xl border border-gray-200 flex items-center justify-center cursor-pointer hover:bg-gray-50 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                    >
                      <Minus size={16} />
                    </button>
                    {isOrganization ? (
                      <input
                        type="number"
                        min={1}
                        max={maxAllowedQuantity}
                        disabled={isOrdering}
                        value={quantity}
                        onChange={e => {
                          const val = parseInt(e.target.value, 10);
                          if (isNaN(val)) setQuantity(1);
                          else setQuantity(Math.max(1, Math.min(maxAllowedQuantity, val)));
                        }}
                        className="w-16 h-10 text-center font-bold text-gray-900 border border-gray-200 rounded-xl focus:border-[#2db84c] focus:outline-hidden text-base"
                      />
                    ) : (
                      <span className="text-lg font-bold text-gray-900 w-12 text-center">{quantity}</span>
                    )}
                    <button
                      type="button"
                      onClick={() => setQuantity(q => Math.min(maxAllowedQuantity, q + 1))}
                      disabled={isOrdering || quantity >= maxAllowedQuantity}
                      className="w-10 h-10 rounded-xl border border-gray-200 flex items-center justify-center cursor-pointer hover:bg-gray-50 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                    >
                      <Plus size={16} />
                    </button>
                  </div>
                </div>

                {/* Price breakdown */}
                <div className="p-4 rounded-xl bg-gray-50 mb-4">
                  <div className="flex justify-between text-sm mb-2">
                    <span className="text-gray-500">Đơn giá</span>
                    <span className="text-gray-900">{post.postType === 'FREE' ? 'Miễn phí' : formatVND(post.unitPrice)}</span>
                  </div>
                  <div className="flex justify-between text-sm mb-2">
                    <span className="text-gray-500">Số lượng</span>
                    <span className="text-gray-900">x{quantity}</span>
                  </div>
                  <div className="border-t border-gray-200 pt-2 mt-2 flex justify-between">
                    <span className="text-sm font-semibold text-gray-900">Tổng cộng</span>
                    <span className={`text-lg font-bold ${post.postType === 'FREE' ? 'text-[#2db84c]' : 'text-gray-900'}`}>
                      {post.postType === 'FREE' ? 'Miễn phí' : formatVND(totalPrice)}
                    </span>
                  </div>
                </div>

                {isOrganization ? (
                  <div className="flex flex-col gap-2.5">
                    <button
                      type="button"
                      onClick={handleAddToCart}
                      disabled={isOrdering}
                      className="w-full py-3 rounded-xl border-2 border-[#2db84c] text-[#2db84c] hover:bg-green-50 font-semibold text-sm cursor-pointer active:scale-[0.98] transition-all flex items-center justify-center gap-2"
                    >
                      {addedToCart ? <Check size={16} /> : <ShoppingCart size={16} />}
                      {addedToCart ? 'Đã thêm vào giỏ hàng' : 'Thêm vào giỏ hàng'}
                    </button>

                    <button
                      type="button"
                      onClick={handleOrder}
                      disabled={isOrdering}
                      className="w-full py-3.5 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                    >
                      {isOrdering ? <Loader2 size={16} className="animate-spin" /> : <ShoppingBag size={16} />}
                      {isOrdering ? 'Đang xử lý...' : 'Đặt hàng ngay'}
                    </button>
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={handleOrder}
                    disabled={isOrdering}
                    className="w-full py-3.5 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 disabled:opacity-50 disabled:cursor-not-allowed disabled:pointer-events-none flex items-center justify-center gap-2"
                  >
                    {isOrdering ? <Loader2 size={16} className="animate-spin" /> : <ShoppingBag size={16} />}
                    {isOrdering ? 'Đang xử lý...' : 'Đặt hàng ngay'}
                  </button>
                )}
              </>
            )}
          </div>
        </motion.div>
      </div>
    </div>
  );
}
