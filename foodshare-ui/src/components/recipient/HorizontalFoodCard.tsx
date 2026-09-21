import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Package, Sparkles, ChevronRight } from 'lucide-react';
import { formatVND } from '../../utils/format';

interface HorizontalFoodCardProps {
  post: any;
  onClick?: () => void;
  fullWidth?: boolean;
}

export default function HorizontalFoodCard({ post, onClick, fullWidth }: HorizontalFoodCardProps) {
  const navigate = useNavigate();

  const handleClick = () => {
    if (onClick) onClick();
    else navigate(`/recipient/posts/${post.id}`);
  };

  const isFree = post.postType === 'FREE' || post.unitPrice === 0;

  return (
    <motion.div
      whileHover={{ y: -2 }}
      onClick={handleClick}
      className={`bg-white rounded-2xl border border-gray-100 hover:border-green-200 overflow-hidden hover:shadow-md transition-all p-3 flex gap-3 cursor-pointer snap-start group ${
        fullWidth ? 'w-full' : 'w-[260px] sm:w-[280px] shrink-0'
      }`}
    >
      <div className="relative w-20 h-20 sm:w-24 sm:h-24 shrink-0 rounded-xl overflow-hidden bg-gray-100 border border-gray-100">
        {post.images && post.images.length > 0 ? (
          <img
            src={post.images[0]}
            alt={post.name}
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-gray-400">
            <Package size={22} />
          </div>
        )}

        {isFree ? (
          <div className="absolute top-1.5 left-1.5 bg-[#2db84c] text-white text-[10px] font-bold px-1.5 py-0.5 rounded-md shadow-xs">
            Miễn phí
          </div>
        ) : post.originalPrice && post.originalPrice > post.unitPrice ? (
          <div className="absolute top-1.5 left-1.5 bg-red-500 text-white text-[10px] font-bold px-1.5 py-0.5 rounded-md shadow-xs">
            -{Math.round((1 - post.unitPrice / post.originalPrice) * 100)}%
          </div>
        ) : null}
      </div>

      <div className="flex-1 flex flex-col justify-between min-w-0 py-0.5">
        <div>
          <h3 className="font-bold text-gray-900 text-sm sm:text-base truncate leading-snug group-hover:text-[#2db84c] transition-colors">
            {post.name}
          </h3>

          <div className="flex items-center gap-1.5 text-xs text-gray-500 mt-1 mb-1.5">
            <span className="px-1.5 py-0.5 rounded bg-gray-100 text-[11px] font-medium text-gray-600 truncate max-w-[85px]">
              {post.category?.name || 'Món ăn'}
            </span>
            <span className="text-gray-300">•</span>
            <span className="text-xs whitespace-nowrap">Còn <strong className="text-gray-800 font-semibold">{post.availableQuantity}</strong>{post.totalQuantity ? `/${post.totalQuantity}` : ''}</span>
          </div>
        </div>

        <div className="flex items-center justify-between mt-auto pt-1">
          {isFree ? (
            <span className="font-bold text-[#2db84c] text-base whitespace-nowrap">Miễn phí</span>
          ) : (
            <div className="flex items-baseline gap-1.5 flex-wrap min-w-0">
              <span className="font-bold text-gray-900 text-base whitespace-nowrap">{formatVND(post.unitPrice)}</span>
              {post.originalPrice && post.originalPrice > post.unitPrice && (
                <span className="line-through text-gray-400 text-xs whitespace-nowrap">{formatVND(post.originalPrice)}</span>
              )}
            </div>
          )}
          <ChevronRight size={16} className="text-gray-300 group-hover:text-[#2db84c] group-hover:translate-x-0.5 transition-all shrink-0 ml-1" />
        </div>

        {post.matchScore != null && post.matchScore > 0 && (
          <div className="mt-1.5 inline-flex items-center gap-1 text-[10px] font-bold text-[#2db84c] bg-[#2db84c]/10 px-2 py-0.5 rounded-md self-start border border-[#2db84c]/20">
            <Sparkles size={10} /> Phù hợp {Math.round(post.matchScore)}%
          </div>
        )}
      </div>
    </motion.div>
  );
}
