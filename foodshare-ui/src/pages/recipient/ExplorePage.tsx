import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Search, Filter, MapPin, Sparkles, ChevronDown,
  Loader2, UtensilsCrossed, X,
} from 'lucide-react';
import { apiFetch } from '../../services/api';
import { formatVND } from '../../utils/format';
import HorizontalFoodCard from '../../components/recipient/HorizontalFoodCard';

interface FoodPostItem {
  id: number;
  name: string;
  description: string;
  images: string[];
  category: { name: string };
  totalQuantity: number;
  availableQuantity: number;
  unitPrice: number;
  originalPrice: number;
  postType: string;
  postStatus: string;
  pickupAddress: string;
  expiresAt: string;
  supplier: { name: string };
  supplierAvatar?: string;
  distanceKm?: number;
  matchScore?: number;
}

const CATEGORIES = [
  { id: 0, name: 'Tất cả' },
  { id: 1, name: 'Cơm' }, { id: 2, name: 'Phở / Bún' }, { id: 3, name: 'Bánh mì' },
  { id: 4, name: 'Đồ uống' }, { id: 5, name: 'Trái cây' }, { id: 6, name: 'Rau củ' },
  { id: 7, name: 'Đồ khô' }, { id: 8, name: 'Khác' },
];

const TYPE_FILTERS = [
  { key: 'all', label: 'Tất cả' },
  { key: 'FREE', label: 'Miễn phí' },
  { key: 'PAID', label: 'Có phí' },
];



export default function ExplorePage() {
  const navigate = useNavigate();
  const [posts, setPosts] = useState<FoodPostItem[]>([]);
  const [recommendations, setRecommendations] = useState<FoodPostItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [categoryId, setCategoryId] = useState(0);
  const [typeFilter, setTypeFilter] = useState('all');
  const [showFilters, setShowFilters] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  
  // New UI states
  const [distance, setDistance] = useState(5);
  const [maxPrice, setMaxPrice] = useState(50000);
  const [timeFilter, setTimeFilter] = useState('all');

  const fetchPosts = useCallback(async () => {
    setIsLoading(true);
    try {
      let url = `/food-posts?page=${page}`;
      if (search.trim()) url += `&keyword=${encodeURIComponent(search.trim())}`;
      if (categoryId > 0) url += `&categoryId=${categoryId}`;
      if (typeFilter !== 'all') url += `&postType=${typeFilter}`;
      // In a real scenario, we'd also append distance, price, time left here
      // if (distance < 20) url += `&distance=${distance}`;
      // if (typeFilter === 'PAID') url += `&maxPrice=${maxPrice}`;

      const res = await apiFetch<any>(url);
      setPosts(res.content || []);
      setTotalPages(res.totalPages || 0);
    } catch (err) {
      console.error('Failed to fetch posts:', err);
    } finally {
      setIsLoading(false);
    }
  }, [page, search, categoryId, typeFilter]);

  const fetchRecommendations = useCallback(async () => {
    try {
      const res = await apiFetch<any>('/matching/recommendations?size=6');
      setRecommendations(res.content || res || []);
    } catch {
      // Recommendations are optional
      setRecommendations([]);
    }
  }, []);

  useEffect(() => { fetchPosts(); }, [fetchPosts]);
  useEffect(() => { fetchRecommendations(); }, [fetchRecommendations]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    fetchPosts();
  };

  const stagger = { hidden: {}, show: { transition: { staggerChildren: 0.04 } } };
  const fadeUp = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.25 } } };

  const groupedPosts = useMemo(() => {
    const groups: Record<string, { supplier: { name: string; avatar?: string }; posts: FoodPostItem[] }> = {};
    for (const post of posts) {
      const supplierName = post.supplier?.name || 'Quán ăn chưa rõ';
      if (!groups[supplierName]) {
        groups[supplierName] = { supplier: { name: supplierName, avatar: post.supplierAvatar }, posts: [] };
      }
      groups[supplierName].posts.push(post);
    }
    return Object.values(groups);
  }, [posts]);

  return (
    <div className="p-4 md:p-6 max-w-6xl mx-auto flex flex-col gap-5">
      {/* Header */}
      <div>
        <h1 className="text-xl md:text-2xl font-bold text-gray-900">Khám phá món ăn</h1>
        <p className="text-sm text-gray-500 mt-0.5">Tìm kiếm thực phẩm được chia sẻ gần bạn</p>
      </div>

      {/* Search Bar */}
      <form onSubmit={handleSearch} className="flex gap-2">
        <div className="flex-1 relative">
          <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Tìm kiếm món ăn, nhà cung cấp..."
            className="w-full pl-10 pr-4 py-3 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all bg-white"
          />
          {search && (
            <button type="button" onClick={() => { setSearch(''); setPage(0); }}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 cursor-pointer">
              <X size={14} />
            </button>
          )}
        </div>
        <button
          type="button"
          onClick={() => setShowFilters(!showFilters)}
          className={`px-4 py-3 rounded-xl border text-sm font-medium cursor-pointer transition-all flex items-center gap-1.5 ${
            showFilters || categoryId > 0 || typeFilter !== 'all'
              ? 'border-[#2db84c] bg-[#2db84c]/5 text-[#2db84c]'
              : 'border-gray-200 text-gray-600 hover:bg-gray-50'
          }`}
        >
          <Filter size={16} />
          <span className="hidden sm:inline">Bộ lọc</span>
          {(categoryId > 0 || typeFilter !== 'all') && (
            <span className="w-2 h-2 rounded-full bg-[#2db84c]" />
          )}
        </button>
      </form>

      {/* Filters Panel */}
      {showFilters && (
        <motion.div
          initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }}
          className="bg-white rounded-2xl border border-gray-100 p-5 shadow-sm overflow-hidden"
        >
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Left Column */}
            <div className="flex flex-col gap-6">
              {/* Category */}
              <div>
                <p className="text-xs font-bold text-gray-500 mb-3 uppercase tracking-wide flex items-center justify-between">
                  Danh mục
                  <span className="text-gray-400 font-normal normal-case">{CATEGORIES.find(c => c.id === categoryId)?.name}</span>
                </p>
                <div className="flex gap-2 overflow-x-auto pb-2 scrollbar-hide -mx-1 px-1">
                  {CATEGORIES.map(c => (
                    <button
                      key={c.id}
                      onClick={() => { setCategoryId(c.id); setPage(0); }}
                      className={`px-3.5 py-2 rounded-xl text-xs font-semibold whitespace-nowrap cursor-pointer transition-all flex-shrink-0 ${
                        categoryId === c.id
                          ? 'bg-[#2db84c] text-white shadow-md shadow-green-500/20'
                          : 'bg-gray-50 text-gray-600 hover:bg-gray-100 border border-transparent'
                      }`}
                    >
                      {c.name}
                    </button>
                  ))}
                </div>
              </div>

              {/* Type */}
              <div>
                <p className="text-xs font-bold text-gray-500 mb-3 uppercase tracking-wide">Loại hình</p>
                <div className="flex gap-2">
                  {TYPE_FILTERS.map(t => (
                    <button
                      key={t.key}
                      onClick={() => { setTypeFilter(t.key); setPage(0); }}
                      className={`flex-1 py-2.5 rounded-xl text-xs font-semibold cursor-pointer transition-all border ${
                        typeFilter === t.key
                          ? 'bg-green-50 border-[#2db84c] text-[#2db84c]'
                          : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'
                      }`}
                    >
                      {t.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Price Range (Only show if Type is PAID or ALL) */}
              {typeFilter !== 'FREE' && (
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <p className="text-xs font-bold text-gray-500 uppercase tracking-wide">Mức giá tối đa</p>
                    <span className="text-[#2db84c] font-bold text-sm">{formatVND(maxPrice)}</span>
                  </div>
                  <input 
                    type="range" min="10000" max="200000" step="10000" 
                    value={maxPrice} onChange={(e) => setMaxPrice(Number(e.target.value))}
                    className="w-full accent-[#2db84c] h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer"
                  />
                  <div className="flex justify-between text-[10px] text-gray-400 mt-1.5 font-medium">
                    <span>10.000đ</span>
                    <span>200.000đ</span>
                  </div>
                </div>
              )}
            </div>

            {/* Right Column */}
            <div className="flex flex-col gap-6">
              {/* Distance */}
              <div>
                <div className="flex items-center justify-between mb-3">
                  <p className="text-xs font-bold text-gray-500 uppercase tracking-wide">Khoảng cách</p>
                  <span className="text-gray-900 font-bold text-sm">{distance === 20 ? 'Toàn thành phố' : `Bán kính < ${distance}km`}</span>
                </div>
                <input 
                  type="range" min="1" max="20" step="1" 
                  value={distance} onChange={(e) => setDistance(Number(e.target.value))}
                  className="w-full accent-[#2db84c] h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer"
                />
                <div className="flex justify-between text-[10px] text-gray-400 mt-1.5 font-medium">
                  <span>1 km</span>
                  <span>20 km</span>
                </div>
              </div>

              {/* Expiration Time */}
              <div>
                <p className="text-xs font-bold text-gray-500 mb-3 uppercase tracking-wide">Thời hạn sử dụng</p>
                <div className="flex flex-wrap gap-2">
                  {[
                    { key: 'all', label: 'Tất cả' },
                    { key: 'today', label: 'Hết hạn trong ngày' },
                    { key: '3days', label: 'Dưới 3 ngày' },
                    { key: '7days', label: 'Dưới 7 ngày' },
                  ].map(t => (
                    <button
                      key={t.key}
                      onClick={() => setTimeFilter(t.key)}
                      className={`px-4 py-2 rounded-xl text-xs font-semibold cursor-pointer transition-all border ${
                        timeFilter === t.key
                          ? 'bg-amber-50 border-amber-400 text-amber-700'
                          : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'
                      }`}
                    >
                      {t.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>

          {/* Action Footer */}
          <div className="mt-6 pt-4 border-t border-gray-100 flex items-center justify-between">
            <button
              onClick={() => { 
                setCategoryId(0); setTypeFilter('all'); setDistance(5); setMaxPrice(50000); setTimeFilter('all'); setPage(0); 
              }}
              className="text-sm font-semibold text-gray-500 cursor-pointer hover:text-red-500 transition-colors"
            >
              Thiết lập lại
            </button>
            <button
              onClick={() => { setShowFilters(false); fetchPosts(); }}
              className="px-6 py-2.5 rounded-xl bg-[#2db84c] text-white text-sm font-bold cursor-pointer hover:bg-[#259e40] active:scale-95 transition-all shadow-md shadow-green-500/20"
            >
              Áp dụng
            </button>
          </div>
        </motion.div>
      )}

      {/* Recommendations Section */}
      {recommendations.length > 0 && !search && categoryId === 0 && typeFilter === 'all' && (
        <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
          <div className="flex items-center gap-2 mb-3">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-amber-400 to-orange-500 flex items-center justify-center">
              <Sparkles size={16} className="text-white" />
            </div>
            <div>
              <h2 className="text-sm font-bold text-gray-900">Gợi ý cho bạn</h2>
              <p className="text-xs text-gray-400">Dựa trên vị trí và sở thích</p>
            </div>
          </div>
          <div className="flex gap-3 overflow-x-auto pb-2 -mx-1 px-1">
            {recommendations.map(post => (
              <motion.div
                key={`rec-${post.id}`}
                whileHover={{ scale: 1.02 }}
                onClick={() => navigate(`/recipient/posts/${post.id}`)}
                className="min-w-[200px] max-w-[200px] bg-white rounded-2xl border border-gray-100 overflow-hidden cursor-pointer hover:shadow-md transition-shadow flex-shrink-0"
              >
                <div className="h-28 bg-gray-100 relative">
                  {post.images && post.images.length > 0 ? (
                    <img src={post.images[0]} alt={post.name} className="w-full h-full object-cover" />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center">
                      <UtensilsCrossed size={24} className="text-gray-300" />
                    </div>
                  )}
                  {post.matchScore != null && post.matchScore > 0 && (
                    <span className="absolute top-2 right-2 px-1.5 py-0.5 rounded-md bg-amber-500/90 text-white text-[10px] font-bold backdrop-blur-sm">
                      ⚡ {Math.round(post.matchScore)}%
                    </span>
                  )}
                </div>
                <div className="p-3">
                  <p className="text-sm font-semibold text-gray-900 truncate">{post.name}</p>
                  <p className="text-xs text-gray-400 mt-0.5 truncate">{post.supplier?.name}</p>
                  <div className="flex items-center justify-between mt-2">
                    <span className={`text-xs font-bold ${post.postType === 'FREE' ? 'text-[#2db84c]' : 'text-gray-900'}`}>
                      {post.postType === 'FREE' ? '🎁 Miễn phí' : (
                        <div className="flex flex-col">
                          {post.originalPrice && post.originalPrice > post.unitPrice && (
                            <span className="text-[10px] line-through text-gray-400 font-normal">{formatVND(post.originalPrice)}</span>
                          )}
                          <span>{formatVND(post.unitPrice)}</span>
                        </div>
                      )}
                    </span>
                    <span className="text-[10px] text-gray-400">SL: {post.availableQuantity}</span>
                  </div>
                </div>
              </motion.div>
            ))}
          </div>
        </motion.div>
      )}

      {/* Results */}
      {isLoading ? (
        <div className="flex items-center justify-center py-16">
          <Loader2 size={24} className="animate-spin text-[#2db84c]" />
        </div>
      ) : posts.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <UtensilsCrossed size={48} className="mx-auto mb-3 opacity-50" />
          <p className="text-sm">Không tìm thấy món ăn nào</p>
          {search && <p className="text-xs mt-1">Thử tìm kiếm với từ khóa khác</p>}
        </div>
      ) : (
        <motion.div className="grid grid-cols-1 lg:grid-cols-2 gap-6" variants={stagger} initial="hidden" animate="show">
          {groupedPosts.map((group, idx) => (
            <motion.div key={idx} variants={fadeUp} className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden flex flex-col">
              {/* Supplier Header */}
              <div className="p-4 flex items-center gap-4 bg-gray-50/50 border-b border-gray-100">
                <div className="w-16 h-16 rounded-xl bg-gradient-to-br from-[#2db84c] to-[#1a9e3a] flex items-center justify-center text-white text-xl font-bold flex-shrink-0 shadow-inner">
                  {group.supplier.avatar ? (
                    <img src={group.supplier.avatar} className="w-full h-full rounded-xl object-cover" alt="" />
                  ) : (
                    group.supplier.name.charAt(0).toUpperCase()
                  )}
                </div>
                <div className="flex-1">
                  <h2 className="text-lg font-bold text-gray-900 line-clamp-1">{group.supplier.name}</h2>
                  <div className="flex items-center gap-2 mt-1">
                    <span className="flex items-center text-xs font-semibold text-amber-500 bg-amber-50 px-1.5 py-0.5 rounded-md">⭐ 5.0 (99+)</span>
                    <span className="text-gray-300">•</span>
                    <span className="text-xs text-gray-500 flex items-center gap-1 line-clamp-1"><MapPin size={12}/> {group.posts[0].pickupAddress}</span>
                  </div>
                </div>
                <button className="text-[#2db84c] font-medium text-xs px-3 py-1.5 rounded-lg bg-[#2db84c]/10 hover:bg-[#2db84c]/20 transition-colors whitespace-nowrap">
                  Tới quán
                </button>
              </div>

              {/* Posts Content */}
              <div className="p-4 grid grid-rows-2 grid-flow-col gap-3 overflow-x-auto pb-4 snap-x">
                {group.posts.map(post => (
                  <HorizontalFoodCard key={post.id} post={post} />
                ))}
              </div>
            </motion.div>
          ))}
        </motion.div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-2">
          {Array.from({ length: totalPages }, (_, i) => (
            <button
              key={i} onClick={() => setPage(i)}
              className={`w-9 h-9 rounded-xl text-sm font-medium cursor-pointer transition-all ${
                page === i ? 'bg-[#2db84c] text-white' : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'
              }`}
            >
              {i + 1}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
