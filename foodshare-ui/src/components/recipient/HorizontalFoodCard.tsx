import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Package, Sparkles } from 'lucide-react';
import { formatVND } from '../../utils/format';

interface HorizontalFoodCardProps {
  post: any;
  onClick?: () => void;
}

export default function HorizontalFoodCard({ post, onClick }: HorizontalFoodCardProps) {
  const navigate = useNavigate();

  const handleClick = () => {
    if (onClick) onClick();
    else navigate(`/recipient/posts/${post.id}`);
  };

  return (
    <motion.div
      whileHover={{ scale: 1.02 }}
      onClick={handleClick}
      className="bg-white rounded-2xl border border-gray-100 overflow-hidden hover:shadow-md transition-all p-3 flex gap-3 cursor-pointer shrink-0 snap-start"
      style={{ width: 'min(85vw, 320px)' }}
    >
      {/* Image & Badge */}
      <div className="relative w-24 h-24 shrink-0 rounded-xl overflow-hidden bg-gray-100 border border-gray-100">
        {post.images && post.images.length > 0 ? (
          <img src={post.images[0]} alt={post.name} className="w-full h-full object-cover" />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-gray-400">
            <Package size={24} />
          </div>
        )}
        
        {/* Discount Badge */}
        {post.originalPrice && post.originalPrice > post.unitPrice && (
          <div className="absolute bottom-0 left-0 bg-[#2db84c] text-white text-[10px] font-bold px-1.5 py-0.5 rounded-tr-lg shadow-sm">
            Giảm {Math.round((1 - post.unitPrice / post.originalPrice) * 100)}%
          </div>
        )}
        
        {/* Free Badge */}
        {post.postType === 'FREE' && (
          <div className="absolute bottom-0 left-0 bg-[#2db84c] text-white text-[10px] font-bold px-1.5 py-0.5 rounded-tr-lg shadow-sm">
            Miễn phí
          </div>
        )}
      </div>

      {/* Content */}
      <div className="flex-1 flex flex-col justify-between min-w-0 py-0.5">
        <div>
          <h3 className="font-semibold text-gray-900 text-sm line-clamp-2 leading-snug group-hover:text-[#2db84c] transition-colors">{post.name}</h3>

          <div className="flex items-center gap-2 text-[11px] text-gray-500 mt-1 mb-1.5">
            <span className="truncate max-w-[80px]">{post.category?.name || 'Khác'}</span>
            <span>·</span>
            <span>SL: {post.availableQuantity}{post.totalQuantity ? `/${post.totalQuantity}` : ''}</span>
          </div>

          <div className="flex items-center gap-1.5 mt-1">
            {post.postType === 'FREE' ? (
              <span className="font-semibold text-[#2db84c] text-sm">0đ</span>
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

        {/* Match Score (Optional) */}
        {post.matchScore != null && post.matchScore > 0 && (
          <div className="mt-1.5 inline-flex items-center gap-1 text-[10px] font-bold text-[#2db84c] bg-[#2db84c]/10 px-2 py-1 rounded-md self-start border border-[#2db84c]/20">
            <Sparkles size={10} /> Phù hợp {Math.round(post.matchScore)}%
          </div>
        )}
      </div>
    </motion.div>
  );
}
