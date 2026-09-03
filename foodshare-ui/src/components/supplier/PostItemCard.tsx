import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Package, Loader2 } from 'lucide-react';
import { formatVND } from '../../utils/format';

export interface PostItem {
  id: number;
  name: string;
  description: string;
  images: string[];
  category: {
    id: number;
    name: string;
  };
  totalQuantity: number;
  availableQuantity: number;
  unitPrice: number;
  originalPrice?: number;
  postType: string;
  postStatus: string;
  pickupAddress: string;
  expiresAt: string;
  createdAt: string;
}

const STATUS_COLORS: Record<string, { bg: string; text: string; label: string }> = {
  AVAILABLE:       { bg: 'bg-green-100',  text: 'text-green-700',  label: 'Đang hoạt động' },
  HIDDEN:       { bg: 'bg-gray-100',   text: 'text-gray-600',   label: 'Đã ẩn' },
  OUT_OF_STOCK: { bg: 'bg-amber-100',  text: 'text-amber-700',  label: 'Hết hàng' },
  EXPIRED:      { bg: 'bg-red-100',    text: 'text-red-600',    label: 'Hết hạn' },
  DRAFT:        { bg: 'bg-blue-100',   text: 'text-blue-600',   label: 'Nháp' },
  CANCELLED:    { bg: 'bg-red-100',    text: 'text-red-600',    label: 'Đã hủy' },
};

interface PostItemCardProps {
  post: PostItem;
  variants?: any;
  actionLoading?: number | null;
  onToggleVisibility: (post: PostItem) => void;
}

export default function PostItemCard({ post, variants, actionLoading, onToggleVisibility }: PostItemCardProps) {
  const navigate = useNavigate();

  return (
    <motion.div
      variants={variants}
      className="bg-white rounded-2xl border border-gray-100 overflow-hidden hover:shadow-md transition-shadow p-3 flex gap-3"
    >
      {/* Image & Badge */}
      <div className="relative w-28 h-28 shrink-0 rounded-xl overflow-hidden bg-gray-100 border border-gray-100">
        {post.images && post.images.length > 0 ? (
          <img src={post.images[0]} alt={post.name} className="w-full h-full object-cover" />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-gray-400">
            <Package size={24} />
          </div>
        )}
        {post.originalPrice && post.originalPrice > post.unitPrice && (
          <div className="absolute bottom-0 left-0 bg-[#2db84c] text-white text-[10px] font-bold px-1.5 py-0.5 rounded-tr-lg shadow-sm">
            Giảm {Math.round((1 - post.unitPrice / post.originalPrice) * 100)}%
          </div>
        )}
      </div>

      {/* Content & Actions */}
      <div className="flex-1 flex flex-col justify-between min-w-0">
        <div>
          <div className="flex items-start justify-between gap-2 mb-1">
            <h3 className="font-semibold text-gray-900 text-sm line-clamp-1">{post.name}</h3>
            {STATUS_COLORS[post.postStatus] && (
              <span className={`px-2 py-0.5 rounded-full text-[9px] font-semibold whitespace-nowrap shrink-0 ${STATUS_COLORS[post.postStatus].bg} ${STATUS_COLORS[post.postStatus].text}`}>
                {STATUS_COLORS[post.postStatus].label}
              </span>
            )}
          </div>

          <div className="flex items-center gap-2 text-[11px] text-gray-500 mb-1.5">
            <span className="truncate max-w-[80px]">{post.category?.name || 'Khác'}</span>
            <span>·</span>
            <span>SL: {post.availableQuantity}/{post.totalQuantity}</span>
          </div>

          <div className="flex items-center gap-1.5 mb-2">
            {post.postType === 'FREE' ? (
              <span className="font-semibold text-[#2db84c] text-sm">Miễn phí</span>
            ) : (
              <div className="flex items-baseline gap-1.5 flex-wrap">
                <span className="font-bold text-[#2db84c] text-sm">{formatVND(post.unitPrice)}</span>
                {post.originalPrice && post.originalPrice > post.unitPrice && (
                  <span className="line-through text-gray-400 text-[11px]">{formatVND(post.originalPrice)}</span>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Actions */}
        <div className="flex gap-2 mt-auto">
          <button
            onClick={() => navigate(`/supplier/posts/${post.id}/edit`)}
            className="flex-1 flex items-center justify-center py-1.5 rounded-lg border border-gray-200 text-[11px] font-medium text-gray-600 hover:bg-gray-50 transition-colors"
          >
            Sửa
          </button>
          <button
            onClick={() => onToggleVisibility(post)}
            disabled={actionLoading === post.id}
            className="flex-1 flex items-center justify-center py-1.5 rounded-lg border border-[#2db84c]/30 text-[#2db84c] hover:bg-[#2db84c]/10 text-[11px] font-medium transition-colors disabled:opacity-50"
          >
            {actionLoading === post.id ? (
              <Loader2 size={12} className="animate-spin" />
            ) : post.postStatus === 'HIDDEN' ? (
              'Hiện'
            ) : (
              'Ẩn'
            )}
          </button>
        </div>
      </div>
    </motion.div>
  );
}
