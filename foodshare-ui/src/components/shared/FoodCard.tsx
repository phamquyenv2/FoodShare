import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { UtensilsCrossed, ChevronRight, ShoppingCart, Plus, Minus, Check } from 'lucide-react';
import { formatVND } from '../../utils/format';
import { addToCart, loadCart } from '../../services/cartService';
import { useToast } from '../../contexts/ToastContext';

export interface FoodCardPost {
  id: number;
  name: string;
  images?: string[];
  category?: { name: string };
  description?: string;
  totalQuantity?: number;
  availableQuantity: number;
  unitPrice: number;
  originalPrice?: number;
  postType: string;
  pickupAddress?: string;
  supplier?: {
    name?: string;
    businessProfileId?: number;
    description?: string;
  };
  [key: string]: any;
}

interface FoodCardProps {
  post: FoodCardPost;
  rolePath: 'recipient' | 'organization';
  showDirectQuantity?: boolean;
}

export default function FoodCard({ post, rolePath, showDirectQuantity = true }: FoodCardProps) {
  const navigate = useNavigate();
  const { showSuccess } = useToast();
  const isOrganization = rolePath === 'organization';

  const maxAvailable = post.availableQuantity || 1;
  const [selectedQty, setSelectedQty] = useState<number>(1);
  const [cartQty, setCartQty] = useState<number>(0);
  const [justAdded, setJustAdded] = useState(false);

  // Sync with current cart state
  useEffect(() => {
    if (!isOrganization) return;
    const sync = () => {
      const items = loadCart();
      const existing = items.find(c => c.post.id === post.id);
      setCartQty(existing ? existing.quantity : 0);
    };
    sync();
    window.addEventListener('cart:updated', sync);
    window.addEventListener('storage', sync);
    return () => {
      window.removeEventListener('cart:updated', sync);
      window.removeEventListener('storage', sync);
    };
  }, [post.id, isOrganization]);

  const isFree = post.postType === 'FREE' || post.unitPrice === 0;
  const hasDiscount = post.originalPrice != null && post.originalPrice > post.unitPrice;
  const discountPercent = hasDiscount && post.originalPrice ? Math.round((1 - post.unitPrice / post.originalPrice) * 100) : 0;

  const handleAddToCart = (e: React.MouseEvent) => {
    e.stopPropagation();
    addToCart(
      {
        id: post.id,
        name: post.name,
        imageUrl: post.images?.[0] || '',
        unitPrice: post.unitPrice || 0,
        postType: post.postType,
        availableQuantity: post.availableQuantity,
        supplierName: post.supplier?.name,
        pickupAddress: post.pickupAddress,
      },
      selectedQty
    );
    setJustAdded(true);
    showSuccess(`Đã thêm ${selectedQty} phần "${post.name}" vào giỏ hàng`);
    setTimeout(() => setJustAdded(false), 2000);
  };

  const handleQuickPreset = (e: React.MouseEvent, count: number) => {
    e.stopPropagation();
    const targetQty = count === -1 ? maxAvailable : Math.min(count, maxAvailable);
    setSelectedQty(targetQty);
  };

  return (
    <motion.div
      whileHover={{ y: -3 }}
      onClick={() => navigate(`/${rolePath}/posts/${post.id}`)}
      className="bg-white rounded-2xl border border-gray-100 hover:border-green-300 shadow-xs hover:shadow-md transition-all overflow-hidden flex flex-col cursor-pointer group"
    >
      {/* Food Image */}
      <div className="relative h-44 bg-gray-100 overflow-hidden">
        {post.images && post.images.length > 0 ? (
          <img
            src={post.images[0]}
            alt={post.name}
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-gray-300">
            <UtensilsCrossed size={32} />
          </div>
        )}

        {/* Top-Left Badges */}
        {isFree ? (
          <div className="absolute top-2.5 left-2.5 bg-[#2db84c] text-white text-xs font-bold px-2.5 py-1 rounded-lg shadow-sm">
            Miễn phí
          </div>
        ) : hasDiscount ? (
          <div className="absolute top-2.5 left-2.5 bg-red-500 text-white text-xs font-bold px-2 py-0.5 rounded-lg shadow-sm">
            -{discountPercent}%
          </div>
        ) : null}

        {/* Top-Right In-Cart Badge */}
        {isOrganization && cartQty > 0 && (
          <div className="absolute top-2.5 right-2.5 bg-emerald-600/90 backdrop-blur-xs text-white text-[11px] font-bold px-2 py-0.5 rounded-lg shadow-sm flex items-center gap-1 border border-white/20">
            <Check size={12} />
            Trong giỏ: {cartQty}
          </div>
        )}

        {/* Quantity Remaining Pill */}
        <div className="absolute bottom-2.5 right-2.5 bg-black/60 backdrop-blur-xs text-white text-[11px] font-medium px-2 py-0.5 rounded-md">
          Còn lại: <strong className="font-bold text-white">{post.availableQuantity}</strong> phần
        </div>
      </div>

      {/* Food Info */}
      <div className="p-4 flex-1 flex flex-col justify-between gap-3">
        <div>
          <h3 className="font-bold text-gray-900 text-base line-clamp-1 group-hover:text-[#2db84c] transition-colors">
            {post.name}
          </h3>
          <div className="flex items-center gap-2 mt-1 text-xs text-gray-400">
            <span className="px-2 py-0.5 rounded-md bg-gray-100 text-gray-600 font-medium text-[11px]">
              {post.category?.name || 'Món ăn'}
            </span>
          </div>
          {post.description && (
            <p className="text-xs text-gray-500 mt-2 line-clamp-2 leading-relaxed">
              {post.description}
            </p>
          )}
        </div>

        {/* Price & Action Section */}
        <div className="pt-2.5 border-t border-gray-100 flex flex-col gap-2.5">
          <div className="flex items-center justify-between">
            <div>
              {isFree ? (
                <p className="font-bold text-lg text-[#2db84c] leading-none">Miễn phí</p>
              ) : (
                <div className="flex items-baseline gap-1.5">
                  <p className="font-bold text-lg text-gray-900 leading-none">{formatVND(post.unitPrice)}</p>
                  {hasDiscount && (
                    <span className="text-xs text-gray-400 line-through leading-none">{formatVND(post.originalPrice!)}</span>
                  )}
                </div>
              )}
            </div>

            {!isOrganization && (
              <button
                type="button"
                onClick={e => {
                  e.stopPropagation();
                  navigate(`/${rolePath}/posts/${post.id}`);
                }}
                className="px-3.5 py-1.5 rounded-xl bg-[#2db84c] text-white text-xs font-semibold hover:bg-[#259e40] transition-colors flex items-center gap-1 shadow-sm shadow-green-500/20 cursor-pointer"
              >
                <span>Xem & Đặt</span>
                <ChevronRight size={13} />
              </button>
            )}
          </div>

          {/* Organization Multi-Quantity Selector & Add to Cart */}
          {isOrganization && showDirectQuantity && (
            <div
              className="mt-1 p-2.5 rounded-xl bg-gray-50/80 border border-gray-100 flex flex-col gap-2"
              onClick={e => e.stopPropagation()}
            >
              {/* Presets if available >= 5 */}
              {maxAvailable >= 5 && (
                <div className="flex items-center gap-1.5 text-[11px]">
                  <span className="text-gray-400 font-medium mr-0.5">Chọn nhanh:</span>
                  {[5, 10, 20].filter(n => n <= maxAvailable).map(n => (
                    <button
                      key={n}
                      type="button"
                      onClick={e => handleQuickPreset(e, n)}
                      className={`px-2 py-0.5 rounded-md border text-[10px] font-bold cursor-pointer transition-colors ${
                        selectedQty === n
                          ? 'bg-[#2db84c] text-white border-[#2db84c]'
                          : 'bg-white text-gray-600 border-gray-200 hover:bg-gray-100'
                      }`}
                    >
                      {n}
                    </button>
                  ))}
                  <button
                    type="button"
                    onClick={e => handleQuickPreset(e, -1)}
                    className={`px-2 py-0.5 rounded-md border text-[10px] font-bold cursor-pointer transition-colors ${
                      selectedQty === maxAvailable
                        ? 'bg-[#2db84c] text-white border-[#2db84c]'
                        : 'bg-white text-[#2db84c] border-[#2db84c]/40 hover:bg-green-50'
                    }`}
                  >
                    Tất cả ({maxAvailable})
                  </button>
                </div>
              )}

              {/* Stepper + Add Button */}
              <div className="flex items-center gap-2">
                <div className="flex items-center border border-gray-200 rounded-lg bg-white overflow-hidden shadow-xs">
                  <button
                    type="button"
                    onClick={() => setSelectedQty(q => Math.max(1, q - 1))}
                    disabled={selectedQty <= 1}
                    className="w-8 h-8 flex items-center justify-center hover:bg-gray-100 disabled:opacity-30 cursor-pointer text-gray-600"
                    title="Giảm"
                  >
                    <Minus size={13} />
                  </button>
                  <input
                    type="number"
                    min={1}
                    max={maxAvailable}
                    value={selectedQty}
                    onChange={e => {
                      const val = parseInt(e.target.value, 10);
                      if (isNaN(val)) setSelectedQty(1);
                      else setSelectedQty(Math.max(1, Math.min(maxAvailable, val)));
                    }}
                    className="w-11 h-8 text-center text-xs font-bold text-gray-900 border-x border-gray-200 focus:outline-none focus:bg-green-50/50"
                  />
                  <button
                    type="button"
                    onClick={() => setSelectedQty(q => Math.min(maxAvailable, q + 1))}
                    disabled={selectedQty >= maxAvailable}
                    className="w-8 h-8 flex items-center justify-center hover:bg-gray-100 disabled:opacity-30 cursor-pointer text-gray-600"
                    title="Tăng"
                  >
                    <Plus size={13} />
                  </button>
                </div>

                <button
                  type="button"
                  onClick={handleAddToCart}
                  className={`flex-1 h-8 px-3 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-1.5 cursor-pointer shadow-xs active:scale-95 ${
                    justAdded
                      ? 'bg-emerald-600 text-white'
                      : 'bg-[#2db84c] text-white hover:bg-[#259e40] shadow-green-500/20'
                  }`}
                >
                  {justAdded ? (
                    <>
                      <Check size={14} />
                      <span>Đã thêm</span>
                    </>
                  ) : (
                    <>
                      <ShoppingCart size={13} />
                      <span>Thêm {selectedQty} phần</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </motion.div>
  );
}
