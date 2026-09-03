import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Search, Filter, MapPin, Clock, Sparkles, Loader2,
  UtensilsCrossed, X, ShoppingCart, Plus, Check,
} from 'lucide-react';
import { apiFetch } from '../../services/api';
import { formatVND } from '../../utils/format';

interface FoodPostItem {
  id: number;
  name: string;
  description: string;
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

export interface CartItem {
  post: FoodPostItem;
  quantity: number;
}

const CATEGORIES = [
  { id: 0, name: 'Tất cả' },
  { id: 1, name: 'Cơm' }, { id: 2, name: 'Phở / Bún' }, { id: 3, name: 'Bánh mì' },
  { id: 4, name: 'Đồ uống' }, { id: 5, name: 'Trái cây' }, { id: 6, name: 'Rau củ' },
  { id: 7, name: 'Đồ khô' }, { id: 8, name: 'Khác' },
];

function getTimeLeft(expiresAt: string): string {
  const diff = new Date(expiresAt).getTime() - Date.now();
  if (diff <= 0) return 'Hết hạn';
  const hrs = Math.floor(diff / 3600000);
  const mins = Math.floor((diff % 3600000) / 60000);
  if (hrs > 24) return `${Math.floor(hrs / 24)} ngày`;
  if (hrs > 0) return `${hrs}h ${mins}p`;
  return `${mins} phút`;
}

// Simple localStorage cart for Organization batch ordering
function loadCart(): CartItem[] {
  try { return JSON.parse(localStorage.getItem('org_cart') || '[]'); } catch { return []; }
}
function saveCart(items: CartItem[]) {
  localStorage.setItem('org_cart', JSON.stringify(items));
}

export default function OrgExplorePage() {
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
  const [cart, setCart] = useState<CartItem[]>(loadCart);

  const fetchPosts = useCallback(async () => {
    setIsLoading(true);
    try {
      let url = `/food-posts?page=${page}&size=20`;
      if (search.trim()) url += `&keyword=${encodeURIComponent(search.trim())}`;
      if (categoryId > 0) url += `&categoryId=${categoryId}`;
      if (typeFilter !== 'all') url += `&postType=${typeFilter}`;
      const res = await apiFetch<any>(url);
      setPosts(res.content || []);
      setTotalPages(res.totalPages || 0);
    } catch (err) { console.error(err); } finally { setIsLoading(false); }
  }, [page, search, categoryId, typeFilter]);

  const fetchRecommendations = useCallback(async () => {
    try {
      const res = await apiFetch<any>('/matching/recommendations?size=6');
      setRecommendations(res.content || res || []);
    } catch { setRecommendations([]); }
  }, []);

  useEffect(() => { fetchPosts(); }, [fetchPosts]);
  useEffect(() => { fetchRecommendations(); }, [fetchRecommendations]);

  const addToCart = (post: FoodPostItem) => {
    setCart(prev => {
      const exists = prev.find(c => c.post.id === post.id);
      const next = exists
        ? prev.map(c => c.post.id === post.id ? { ...c, quantity: Math.min(c.quantity + 1, post.availableQuantity) } : c)
        : [...prev, { post, quantity: 1 }];
      saveCart(next);
      return next;
    });
  };

  const isInCart = (id: number) => cart.some(c => c.post.id === id);
  const cartCount = cart.reduce((s, c) => s + c.quantity, 0);

  const handleSearch = (e: React.FormEvent) => { e.preventDefault(); setPage(0); };

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
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-gray-900">Khám phá món ăn</h1>
          <p className="text-sm text-gray-500 mt-0.5">Chọn nhiều món từ nhiều nhà cung cấp</p>
        </div>
      </div>

      {/* Search */}
      <form onSubmit={handleSearch} className="flex gap-2">
        <div className="flex-1 relative">
          <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400" />
          <input type="text" value={search} onChange={e => setSearch(e.target.value)}
            placeholder="Tìm kiếm món ăn..." className="w-full pl-10 pr-4 py-3 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all bg-white" />
          {search && <button type="button" onClick={() => { setSearch(''); setPage(0); }} className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 cursor-pointer"><X size={14} /></button>}
        </div>
        <button type="button" onClick={() => setShowFilters(!showFilters)}
          className={`px-4 py-3 rounded-xl border text-sm font-medium cursor-pointer transition-all flex items-center gap-1.5 ${showFilters || categoryId > 0 || typeFilter !== 'all' ? 'border-[#2db84c] bg-[#2db84c]/5 text-[#2db84c]' : 'border-gray-200 text-gray-600 hover:bg-gray-50'}`}>
          <Filter size={16} /><span className="hidden sm:inline">Bộ lọc</span>
        </button>
      </form>

      {/* Filters */}
      {showFilters && (
        <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} className="bg-white rounded-2xl border border-gray-100 p-4 flex flex-col gap-4 overflow-hidden">
          <div>
            <p className="text-xs font-semibold text-gray-500 mb-2 uppercase tracking-wide">Danh mục</p>
            <div className="flex flex-wrap gap-2">
              {CATEGORIES.map(c => (
                <button key={c.id} onClick={() => { setCategoryId(c.id); setPage(0); }}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium cursor-pointer transition-all ${categoryId === c.id ? 'bg-[#2db84c] text-white' : 'bg-gray-50 text-gray-600 hover:bg-gray-100'}`}>{c.name}</button>
              ))}
            </div>
          </div>
          <div>
            <p className="text-xs font-semibold text-gray-500 mb-2 uppercase tracking-wide">Loại</p>
            <div className="flex gap-2">
              {[{ key: 'all', label: 'Tất cả' }, { key: 'FREE', label: '🎁 Miễn phí' }, { key: 'PAID', label: '💰 Có phí' }].map(t => (
                <button key={t.key} onClick={() => { setTypeFilter(t.key); setPage(0); }}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium cursor-pointer transition-all ${typeFilter === t.key ? 'bg-[#2db84c] text-white' : 'bg-gray-50 text-gray-600 hover:bg-gray-100'}`}>{t.label}</button>
              ))}
            </div>
          </div>
        </motion.div>
      )}

      {/* Recommendations */}
      {recommendations.length > 0 && !search && categoryId === 0 && typeFilter === 'all' && (
        <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
          <div className="flex items-center gap-2 mb-3">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-amber-400 to-orange-500 flex items-center justify-center"><Sparkles size={16} className="text-white" /></div>
            <div><h2 className="text-sm font-bold text-gray-900">Gợi ý cho tổ chức</h2><p className="text-xs text-gray-400">Phù hợp với nhu cầu của bạn</p></div>
          </div>
          <div className="flex gap-3 overflow-x-auto pb-2 -mx-1 px-1">
            {recommendations.map(post => (
              <div key={`rec-${post.id}`} className="min-w-[200px] max-w-[200px] bg-white rounded-2xl border border-gray-100 overflow-hidden flex-shrink-0">
                <div className="h-28 bg-gray-100 relative cursor-pointer" onClick={() => navigate(`/organization/posts/${post.id}`)}>
                  {post.images && post.images.length > 0 ? <img src={post.images[0]} alt={post.name} className="w-full h-full object-cover" /> : <div className="w-full h-full flex items-center justify-center"><UtensilsCrossed size={24} className="text-gray-300" /></div>}
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
                    <button onClick={() => addToCart(post)} className={`w-7 h-7 rounded-lg flex items-center justify-center cursor-pointer transition-all ${isInCart(post.id) ? 'bg-[#2db84c] text-white' : 'bg-gray-100 text-gray-500 hover:bg-[#2db84c]/10 hover:text-[#2db84c]'}`}>
                      {isInCart(post.id) ? <Check size={14} /> : <Plus size={14} />}
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </motion.div>
      )}

      {/* Results */}
      {isLoading ? (
        <div className="flex items-center justify-center py-16"><Loader2 size={24} className="animate-spin text-[#2db84c]" /></div>
      ) : posts.length === 0 ? (
        <div className="text-center py-16 text-gray-400"><UtensilsCrossed size={48} className="mx-auto mb-3 opacity-50" /><p className="text-sm">Không tìm thấy món ăn nào</p></div>
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
              <div className="p-4 flex overflow-x-auto gap-4 pb-4 snap-x">
                {group.posts.map(post => (
                  <div 
                    key={post.id} 
                    className="min-w-[12rem] w-48 flex-shrink-0 snap-start group flex flex-col"
                  >
                    <div className="w-full h-40 rounded-2xl bg-gray-100 overflow-hidden relative mb-3 shadow-sm border border-gray-100/50 cursor-pointer" onClick={() => navigate(`/organization/posts/${post.id}`)}>
                      {post.images && post.images.length > 0 ? (
                        <img src={post.images[0]} alt="" className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" />
                      ) : (
                        <div className="w-full h-full flex items-center justify-center"><UtensilsCrossed size={28} className="text-gray-300" /></div>
                      )}
                      
                      <div className="absolute inset-0 bg-gradient-to-t from-black/50 via-transparent to-transparent" />

                      {/* Discount Badge ONLY */}
                      {post.originalPrice && post.originalPrice > post.unitPrice && (
                        <div className="absolute top-0 left-0 bg-red-500 text-white text-[10px] font-bold px-2 py-1 rounded-br-xl shadow-sm">
                          Giảm {Math.round((1 - post.unitPrice / post.originalPrice) * 100)}%
                        </div>
                      )}
                      {post.postType === 'FREE' && (
                        <div className="absolute top-0 left-0 bg-[#2db84c] text-white text-[10px] font-bold px-2 py-1 rounded-br-xl shadow-sm">
                          Miễn phí
                        </div>
                      )}
                      
                      {/* Expiry inside image */}
                      <div className="absolute bottom-2 left-2 right-2 flex justify-between items-end">
                        <span className="px-2 py-0.5 rounded-lg bg-black/50 text-white text-[10px] font-bold backdrop-blur-md flex items-center gap-1 border border-white/20">
                          <Clock size={10} /> {getTimeLeft(post.expiresAt)}
                        </span>
                      </div>
                    </div>
                    
                    <div className="flex flex-col flex-1 px-1">
                      <h3 className="font-semibold text-gray-900 text-[15px] line-clamp-1 leading-tight cursor-pointer hover:text-[#2db84c] transition-colors" onClick={() => navigate(`/organization/posts/${post.id}`)}>{post.name}</h3>
                      <div className="flex items-baseline gap-1.5 mt-1.5 mb-2">
                        <span className={`text-sm font-bold ${post.postType === 'FREE' ? 'text-[#2db84c]' : 'text-gray-900'}`}>
                          {post.postType === 'FREE' ? '0đ' : formatVND(post.unitPrice)}
                        </span>
                        {post.originalPrice && post.originalPrice > post.unitPrice && post.postType !== 'FREE' && (
                          <span className="text-[10px] line-through text-gray-400">{formatVND(post.originalPrice)}</span>
                        )}
                      </div>
                      
                      {/* Add to Cart Button */}
                      <button onClick={() => addToCart(post)} className={`mt-auto pt-2 flex items-center justify-center gap-1.5 w-full py-1.5 rounded-lg text-xs font-semibold cursor-pointer transition-all ${isInCart(post.id) ? 'bg-[#2db84c] text-white' : 'bg-[#2db84c]/10 text-[#2db84c] hover:bg-[#2db84c]/20'}`}>
                        {isInCart(post.id) ? <><Check size={12} /> Đã thêm</> : <><Plus size={12} /> Thêm</>}
                      </button>
                    </div>
                  </div>
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
            <button key={i} onClick={() => setPage(i)} className={`w-9 h-9 rounded-xl text-sm font-medium cursor-pointer transition-all ${page === i ? 'bg-[#2db84c] text-white' : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'}`}>{i + 1}</button>
          ))}
        </div>
      )}

      {/* Floating Cart Button */}
      {cartCount > 0 && (
        <motion.button
          initial={{ scale: 0, opacity: 0 }} animate={{ scale: 1, opacity: 1 }}
          onClick={() => navigate('/organization/cart')}
          className="fixed bottom-20 md:bottom-8 right-4 md:right-8 flex items-center gap-2 px-5 py-3.5 rounded-2xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer shadow-xl shadow-green-500/30 hover:bg-[#259e40] active:scale-95 transition-all z-40"
        >
          <ShoppingCart size={18} />
          Giỏ hàng ({cartCount})
        </motion.button>
      )}
    </div>
  );
}
