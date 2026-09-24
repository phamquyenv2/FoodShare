import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { MapPin, ChevronRight, Star, UtensilsCrossed } from 'lucide-react';

export interface StoreSupplier {
  name: string;
  avatar?: string;
  avatarUrl?: string;
  businessProfileId?: number;
  address?: string;
  phone?: string;
  description?: string;
  rating?: number | null;
  reviewCount?: number;
}

export interface StorePostItem {
  id: number;
  name: string;
  images?: string[];
  pickupAddress?: string;
  availableQuantity?: number;
  totalQuantity?: number;
  unitPrice?: number;
  originalPrice?: number;
  postType?: string;
  expiresAt?: string;
  category?: { name: string };
  [key: string]: any;
}

interface StoreCardProps {
  supplier: StoreSupplier;
  posts: StorePostItem[];
  ratingsMap?: Record<number, { averageRating: number | null; totalReviews: number }>;
  rolePath: 'recipient' | 'organization';
}

export default function StoreCard({ supplier, posts, ratingsMap, rolePath }: StoreCardProps) {
  const navigate = useNavigate();

  const storeId = supplier.businessProfileId || posts[0]?.id || 0;
  const address = supplier.address || posts[0]?.pickupAddress || 'Tại quán';

  const bpId = supplier.businessProfileId;
  const stat = bpId && ratingsMap ? ratingsMap[bpId] : null;

  const goToStore = () => {
    navigate(`/${rolePath}/stores/${storeId}`, {
      state: {
        supplier,
        posts,
      },
    });
  };

  return (
    <motion.div
      whileHover={{ y: -2 }}
      onClick={goToStore}
      className="bg-white rounded-2xl border border-gray-100 shadow-xs hover:shadow-md hover:border-[#2db84c]/40 transition-all duration-200 p-4 sm:p-5 flex flex-col justify-between gap-3.5 cursor-pointer group"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3 min-w-0 flex-1">
          <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-[#2db84c] to-[#1a9e3a] flex items-center justify-center text-white text-base font-bold shrink-0 shadow-xs overflow-hidden">
            {(supplier.avatar || supplier.avatarUrl) ? (
              <img src={supplier.avatar || supplier.avatarUrl} className="w-full h-full object-cover" alt={supplier.name} />
            ) : (
              (supplier.name?.charAt(0) || 'Q').toUpperCase()
            )}
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="text-base font-bold text-gray-900 truncate group-hover:text-[#2db84c] transition-colors">
              {supplier.name}
            </h2>
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1 mt-1">
              {stat && stat.totalReviews > 0 ? (
                <span className="inline-flex items-center gap-1 text-[11px] font-bold text-amber-600 bg-amber-50 px-1.5 py-0.5 rounded-md border border-amber-200/50 shrink-0">
                  <Star size={11} className="fill-amber-400 text-amber-400" />
                  {stat.averageRating !== null ? stat.averageRating.toFixed(1) : '5.0'} ({stat.totalReviews})
                </span>
              ) : (
                <span className="inline-flex items-center gap-1 text-[11px] font-medium text-amber-600 bg-amber-50/70 px-1.5 py-0.5 rounded-md border border-amber-200/40 shrink-0">
                  <Star size={11} className="fill-amber-400 text-amber-400" /> Mới
                </span>
              )}
              <span className="text-xs text-gray-500 flex items-center gap-1 min-w-0 max-w-full">
                <MapPin size={12} className="text-gray-400 shrink-0" />
                <span className="truncate max-w-[200px] sm:max-w-[260px]">{address}</span>
              </span>
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            goToStore();
          }}
          className="text-xs font-semibold px-3 py-1.5 rounded-xl bg-[#2db84c]/10 text-[#2db84c] group-hover:bg-[#2db84c] group-hover:text-white transition-all whitespace-nowrap flex items-center gap-1 cursor-pointer shrink-0 mt-0.5"
        >
          <span>Xem thực đơn</span>
          <ChevronRight size={13} />
        </button>
      </div>

      <div className="pt-3 border-t border-gray-100 flex items-center justify-between gap-3">
        <div className="flex items-center gap-1.5 text-xs text-gray-600 font-medium">
          <UtensilsCrossed size={14} className="text-[#2db84c] shrink-0" />
          <span>
            Đang có <strong className="text-gray-900 font-bold">{posts.length} món</strong> sẵn sàng
          </span>
        </div>

        <div className="flex items-center -space-x-2 overflow-hidden">
          {posts.slice(0, 3).map((p, i) => (
            <div
              key={p.id || i}
              className="w-8 h-8 rounded-lg border-2 border-white shadow-xs overflow-hidden bg-gray-100 shrink-0"
              title={p.name}
            >
              {p.images && p.images[0] ? (
                <img src={p.images[0]} alt={p.name} className="w-full h-full object-cover" />
              ) : (
                <div className="w-full h-full flex items-center justify-center bg-gray-200 text-gray-400 text-[9px]">
                  🍽️
                </div>
              )}
            </div>
          ))}
          {posts.length > 3 && (
            <div className="w-8 h-8 rounded-lg border-2 border-white bg-gray-100 text-gray-600 text-[10px] font-bold flex items-center justify-center shrink-0 shadow-xs">
              +{posts.length - 3}
            </div>
          )}
        </div>
      </div>
    </motion.div>
  );
}
