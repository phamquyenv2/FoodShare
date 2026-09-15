import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowLeft, Search, MapPin, Star, Phone, UtensilsCrossed,
  Loader2, ChevronRight, Filter, X, RotateCcw
} from 'lucide-react';
import { apiFetch } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';
import { formatVND, timeAgo } from '../../utils/format';
import { getCategories, type Category } from '../../services/categoryApi';

interface ReviewItem {
  id: number;
  rating: number;
  comment: string;
  order?: { id: number; orderCode: string };
  reviewer?: { id: number; fullName: string; avatarUrl?: string };
  createdAt: string;
}

interface FoodPostItem {
  id: number;
  name: string;
  description: string;
  images: string[];
  category?: { id?: number; name: string };
  totalQuantity: number;
  availableQuantity: number;
  unitPrice: number;
  originalPrice: number;
  postType: string;
  postStatus: string;
  pickupAddress: string;
  pickupStartAt?: string;
  pickupEndAt?: string;
  expiresAt: string;
  supplier?: {
    id?: number;
    businessProfileId?: number;
    name: string;
    description?: string;
  };
  supplierAvatar?: string;
  distanceKm?: number;
  matchScore?: number;
}

export default function StoreDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const { showError } = useToast();

  // Route role prefix
  const rolePath = location.pathname.startsWith('/organization') ? 'organization' : 'recipient';

  // Initial supplier info from router state (if navigated from ExplorePage)
  const stateSupplier = location.state?.supplier;
  const initialPosts: FoodPostItem[] = location.state?.posts || [];

  const [posts, setPosts] = useState<FoodPostItem[]>(initialPosts);
  const [categories, setCategories] = useState<Category[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<number>(0);
  const [selectedType, setSelectedType] = useState<string>('all');
  const [searchKeyword, setSearchKeyword] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(initialPosts.length === 0);
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);

  // Filter panel state
  const [showFilters, setShowFilters] = useState<boolean>(false);
  const [maxPrice, setMaxPrice] = useState<number>(200000);
  const [sortOption, setSortOption] = useState<string>('newest');
  const [expiryFilter, setExpiryFilter] = useState<string>('all');

  // Supplier info state (real data from API)
  const [supplierInfo, setSupplierInfo] = useState<{
    name: string;
    avatar: string;
    address: string;
    description: string;
    phone: string;
    rating: number | null;
    reviewCount: number;
  }>({
    name: stateSupplier?.name || 'Quán ăn',
    avatar: stateSupplier?.avatar || '',
    address: stateSupplier?.address || (initialPosts[0]?.pickupAddress || ''),
    description: stateSupplier?.description || '',
    phone: stateSupplier?.phone || '',
    rating: stateSupplier?.rating ?? null,
    reviewCount: stateSupplier?.reviewCount ?? 0,
  });

  // Customer reviews state
  const [reviews, setReviews] = useState<ReviewItem[]>([]);
  const [isLoadingReviews, setIsLoadingReviews] = useState<boolean>(false);

  // Fetch real review rating summary & review list for this store
  useEffect(() => {
    if (!id || isNaN(Number(id))) return;

    // 1. Fetch real summary
    apiFetch<{ averageRating: number | null; totalReviews: number }>(`/reviews/business/${id}/summary`)
      .then(res => {
        if (res) {
          setSupplierInfo(prev => ({
            ...prev,
            rating: res.averageRating,
            reviewCount: res.totalReviews,
          }));
        }
      })
      .catch(() => { });

    // 2. Fetch actual reviews
    setIsLoadingReviews(true);
    apiFetch<any>(`/reviews/business/${id}?size=10`)
      .then(res => {
        setReviews(res.content || []);
      })
      .catch(() => {
        setReviews([]);
      })
      .finally(() => {
        setIsLoadingReviews(false);
      });
  }, [id]);

  // Fetch categories
  useEffect(() => {
    getCategories().then(cats => {
      if (cats && cats.length > 0) {
        setCategories(cats);
      }
    });
  }, []);

  // Fetch supplier posts
  const fetchStorePosts = useCallback(async () => {
    setIsLoading(true);
    try {
      // Query parameters
      let query = `/food-posts?page=${page}&size=12`;
      if (id && !isNaN(Number(id))) {
        query += `&businessProfileId=${id}`;
      }
      if (searchKeyword.trim()) {
        query += `&keyword=${encodeURIComponent(searchKeyword.trim())}`;
      }
      if (selectedCategory > 0) {
        query += `&categoryId=${selectedCategory}`;
      }
      if (selectedType !== 'all') {
        query += `&postType=${selectedType}`;
      }

      // Additional filters
      if (selectedType !== 'FREE' && maxPrice < 200000) {
        query += `&maxPrice=${maxPrice}`;
      }
      if (expiryFilter === 'today') {
        const endOfToday = new Date();
        endOfToday.setHours(23, 59, 59, 999);
        query += `&expiresTo=${endOfToday.toISOString()}`;
      }
      if (sortOption === 'price_asc') {
        query += '&sort=unitPrice,asc';
      } else if (sortOption === 'price_desc') {
        query += '&sort=unitPrice,desc';
      } else if (sortOption === 'expiring_soon') {
        query += '&sort=expiresAt,asc';
      } else {
        query += '&sort=createdAt,desc';
      }

      const res = await apiFetch<any>(query);
      const fetchedPosts: FoodPostItem[] = res.content || [];
      setPosts(fetchedPosts);
      setTotalPages(res.totalPages || 1);

      // Derive supplier info from first post if not available
      if (fetchedPosts.length > 0 && !stateSupplier) {
        const first = fetchedPosts[0];
        setSupplierInfo(prev => ({
          ...prev,
          name: first.supplier?.name || prev.name,
          avatar: first.supplierAvatar || prev.avatar,
          address: first.pickupAddress || prev.address,
          description: first.supplier?.description || prev.description,
        }));
      }
    } catch (err: any) {
      // If backend filtering by businessProfileId is not yet cached, fallback to client filter
      if (initialPosts.length > 0) {
        let filtered = [...initialPosts];
        if (searchKeyword.trim()) {
          const kw = searchKeyword.toLowerCase();
          filtered = filtered.filter(p => p.name.toLowerCase().includes(kw) || p.description?.toLowerCase().includes(kw));
        }
        if (selectedCategory > 0) {
          filtered = filtered.filter(p => p.category?.id === selectedCategory);
        }
        if (selectedType !== 'all') {
          filtered = filtered.filter(p => p.postType === selectedType);
        }
        setPosts(filtered);
      } else {
        showError(err.message || 'Không thể tải danh sách món ăn của quán');
      }
    } finally {
      setIsLoading(false);
    }
  }, [id, page, searchKeyword, selectedCategory, selectedType, maxPrice, expiryFilter, sortOption, initialPosts, stateSupplier, showError]);

  useEffect(() => {
    fetchStorePosts();
  }, [fetchStorePosts]);

  // Count active filters (excluding default values)
  const activeFilterCount = useMemo(() => {
    let count = 0;
    if (selectedCategory > 0) count++;
    if (selectedType !== 'all') count++;
    if (maxPrice < 200000) count++;
    if (sortOption !== 'newest') count++;
    if (expiryFilter !== 'all') count++;
    return count;
  }, [selectedCategory, selectedType, maxPrice, sortOption, expiryFilter]);

  const handleResetFilters = () => {
    setSearchKeyword('');
    setSelectedCategory(0);
    setSelectedType('all');
    setMaxPrice(200000);
    setSortOption('newest');
    setExpiryFilter('all');
    setPage(0);
  };

  // Client-side filtering and sorting for instant responsiveness
  const displayedPosts = useMemo(() => {
    const result = posts.filter(post => {
      if (searchKeyword.trim()) {
        const kw = searchKeyword.toLowerCase();
        const matchesName = post.name.toLowerCase().includes(kw);
        const matchesDesc = post.description?.toLowerCase().includes(kw);
        if (!matchesName && !matchesDesc) return false;
      }
      if (selectedCategory > 0 && post.category?.id && post.category.id !== selectedCategory) {
        return false;
      }
      if (selectedType !== 'all' && post.postType !== selectedType) {
        return false;
      }
      if (selectedType !== 'FREE' && maxPrice < 200000 && post.unitPrice > maxPrice) {
        return false;
      }
      if (expiryFilter === 'today') {
        const endOfToday = new Date();
        endOfToday.setHours(23, 59, 59, 999);
        if (new Date(post.expiresAt) > endOfToday) return false;
      } else if (expiryFilter === 'later') {
        const endOfToday = new Date();
        endOfToday.setHours(23, 59, 59, 999);
        if (new Date(post.expiresAt) <= endOfToday) return false;
      }
      return true;
    });

    if (sortOption === 'price_asc') {
      result.sort((a, b) => (a.unitPrice || 0) - (b.unitPrice || 0));
    } else if (sortOption === 'price_desc') {
      result.sort((a, b) => (b.unitPrice || 0) - (a.unitPrice || 0));
    } else if (sortOption === 'expiring_soon') {
      result.sort((a, b) => new Date(a.expiresAt).getTime() - new Date(b.expiresAt).getTime());
    } else {
      result.sort((a, b) => b.id - a.id);
    }

    return result;
  }, [posts, searchKeyword, selectedCategory, selectedType, maxPrice, expiryFilter, sortOption]);

  // Atmospheric food hero banner from store's first available food photo
  const heroFoodImage = useMemo(() => {
    for (const post of posts) {
      if (post.images && post.images.length > 0 && post.images[0]) {
        return post.images[0];
      }
    }
    return null;
  }, [posts]);

  return (
    <div className="p-4 md:p-6 max-w-6xl mx-auto flex flex-col gap-6 min-h-[calc(100vh-80px)]">
      {/* Top row: Back button */}
      <div className="flex items-center justify-between">
        <button
          onClick={() => navigate(-1)}
          className="inline-flex items-center gap-2 text-sm text-gray-500 hover:text-gray-800 transition-colors cursor-pointer font-medium"
        >
          <ArrowLeft size={16} /> <span>Quay lại</span>
        </button>
      </div>

      {/* 1. Store Header Card */}
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        className="bg-white rounded-3xl border border-gray-100 shadow-sm overflow-hidden"
      >
        {/* Foodie Ambient Hero Banner (Phương án 1: Dùng ảnh món ăn thật của quán) */}
        <div className="h-44 sm:h-56 md:h-64 relative overflow-hidden bg-gray-900">
          {heroFoodImage ? (
            <>
              <img
                src={heroFoodImage}
                alt={supplierInfo.name}
                className="w-full h-full object-cover object-center brightness-[0.88] transition-transform duration-500"
              />
              {/* Gradient overlay: Dark at bottom to highlight avatar & text, clear at top */}
              <div className="absolute inset-0 bg-gradient-to-t from-black/75 via-black/30 to-black/10" />
            </>
          ) : (
            <div className="w-full h-full bg-gradient-to-r from-[#2db84c] via-[#259e40] to-[#1e8235] relative flex items-center justify-center">
              <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(255,255,255,0.2),transparent_70%)]" />
            </div>
          )}
        </div>

        {/* Store Profile Info */}
        <div className="p-5 sm:p-6 pt-0 relative flex flex-col sm:flex-row gap-4 sm:gap-6 items-start">
          {/* Avatar (Overlapping banner with clean white border) */}
          <div className="-mt-12 sm:-mt-14 w-20 h-20 sm:w-24 sm:h-24 rounded-2xl bg-white p-1 shadow-lg shrink-0 border-2 border-white relative z-10">
            <div className="w-full h-full rounded-xl bg-gradient-to-br from-[#2db84c] to-[#1a9e3a] flex items-center justify-center text-white text-2xl font-bold overflow-hidden shadow-inner">
              {supplierInfo.avatar ? (
                <img src={supplierInfo.avatar} alt={supplierInfo.name} className="w-full h-full object-cover" />
              ) : (
                supplierInfo.name.charAt(0).toUpperCase()
              )}
            </div>
          </div>

          {/* Store Details */}
          <div className="flex-1 min-w-0 flex flex-col gap-2">
            <div className="flex items-center justify-between gap-3 flex-wrap">
              <div>
                <h1 className="text-xl sm:text-2xl font-bold text-gray-900 leading-tight">
                  {supplierInfo.name}
                </h1>
                {supplierInfo.description && (
                  <p className="text-xs sm:text-sm text-gray-500 mt-1 line-clamp-2">
                    {supplierInfo.description}
                  </p>
                )}
              </div>

              {/* Status Pill */}
              <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-green-50 text-[#2db84c] text-xs font-semibold border border-green-200/60 shadow-2xs">
                <span className="w-2 h-2 rounded-full bg-[#2db84c] animate-pulse" />
                Đang phục vụ
              </div>
            </div>

            {/* Badges & Address */}
            <div className="flex flex-wrap items-center gap-x-3 gap-y-2 text-xs text-gray-600 mt-1">
              <span className="inline-flex items-center gap-1.5 font-bold text-amber-700 bg-amber-50 px-2.5 py-1 rounded-lg border border-amber-200/60 shadow-2xs">
                <Star size={12} className="fill-amber-400 text-amber-400" />
                {supplierInfo.reviewCount > 0 ? (
                  <span>
                    {supplierInfo.rating !== null ? supplierInfo.rating.toFixed(1) : '5.0'} ({supplierInfo.reviewCount} đánh giá)
                  </span>
                ) : (
                  <span className="font-medium text-amber-600">Chưa có đánh giá (Mới)</span>
                )}
              </span>

              {supplierInfo.address && (
                <span className="flex items-center gap-1 text-gray-500">
                  <MapPin size={13} className="text-gray-400 shrink-0" />
                  <span className="truncate max-w-[320px] sm:max-w-[450px]">{supplierInfo.address}</span>
                </span>
              )}

              {supplierInfo.phone && (
                <span className="flex items-center gap-1 text-gray-500">
                  <Phone size={13} className="text-gray-400 shrink-0" />
                  <span>{supplierInfo.phone}</span>
                </span>
              )}

              <span className="text-gray-400">
                · <strong>{displayedPosts.length}</strong> món ăn khả dụng
              </span>
            </div>
          </div>
        </div>
      </motion.div>

      {/* 2. Search & Controls */}
      <div className="flex flex-col gap-3">
        <div className="flex gap-2.5 items-center">
          {/* Search Input */}
          <div className="relative flex-1">
            <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              placeholder="Tìm món ăn tại quán này..."
              value={searchKeyword}
              onChange={e => { setSearchKeyword(e.target.value); setPage(0); }}
              className="w-full pl-10 pr-10 py-3 bg-white border border-gray-200 rounded-2xl text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/20 focus:border-[#2db84c] transition-all shadow-2xs"
            />
            {searchKeyword && (
              <button
                type="button"
                onClick={() => { setSearchKeyword(''); setPage(0); }}
                className="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 cursor-pointer"
              >
                <X size={14} />
              </button>
            )}
          </div>

          {/* Filter Button (Image replica) */}
          <button
            type="button"
            onClick={() => setShowFilters(!showFilters)}
            className={`px-4 py-3 rounded-2xl border text-sm font-medium cursor-pointer transition-all flex items-center gap-2 flex-shrink-0 shadow-2xs ${showFilters || activeFilterCount > 0
                ? 'border-[#2db84c] bg-[#2db84c]/10 text-[#2db84c] font-semibold'
                : 'border-gray-200 bg-white text-gray-700 hover:bg-gray-50'
              }`}
          >
            <Filter size={16} />
            <span>Bộ lọc</span>
            {activeFilterCount > 0 && (
              <span className="w-2 h-2 rounded-full bg-[#2db84c]" />
            )}
          </button>
        </div>

        {/* Expandable Filter Panel */}
        {showFilters && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="bg-white rounded-2xl border border-gray-100 p-5 shadow-sm overflow-hidden"
          >
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Left Column: Sắp xếp & Loại hình */}
              <div className="flex flex-col gap-5">
                {/* Sắp xếp */}
                <div>
                  <p className="text-xs font-bold text-gray-500 mb-2.5 uppercase tracking-wide">
                    Sắp xếp theo
                  </p>
                  <div className="grid grid-cols-2 gap-2">
                    {[
                      { key: 'newest', label: 'Mới nhất' },
                      { key: 'price_asc', label: 'Giá thấp → cao' },
                      { key: 'price_desc', label: 'Giá cao → thấp' },
                      { key: 'expiring_soon', label: 'Hết hạn sớm nhất' },
                    ].map(s => (
                      <button
                        key={s.key}
                        onClick={() => { setSortOption(s.key); setPage(0); }}
                        className={`py-2 px-3 rounded-xl text-xs font-semibold cursor-pointer transition-all border text-left ${sortOption === s.key
                            ? 'bg-green-50 border-[#2db84c] text-[#2db84c]'
                            : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'
                          }`}
                      >
                        {s.label}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Loại hình */}
                <div>
                  <p className="text-xs font-bold text-gray-500 mb-2.5 uppercase tracking-wide">
                    Loại hình món
                  </p>
                  <div className="flex gap-2">
                    {[
                      { key: 'all', label: 'Tất cả' },
                      { key: 'FREE', label: 'Miễn phí' },
                      { key: 'PAID', label: 'Có phí' },
                    ].map(t => (
                      <button
                        key={t.key}
                        onClick={() => { setSelectedType(t.key); setPage(0); }}
                        className={`flex-1 py-2 rounded-xl text-xs font-semibold cursor-pointer transition-all border text-center ${selectedType === t.key
                            ? 'bg-green-50 border-[#2db84c] text-[#2db84c]'
                            : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'
                          }`}
                      >
                        {t.label}
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              {/* Right Column: Giá & Hạn sử dụng */}
              <div className="flex flex-col gap-5">
                {/* Mức giá tối đa */}
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-xs font-bold text-gray-500 uppercase tracking-wide">
                      Mức giá tối đa
                    </p>
                    <span className="text-[#2db84c] font-bold text-sm">
                      {maxPrice >= 200000 ? 'Tất cả mức giá' : formatVND(maxPrice)}
                    </span>
                  </div>
                  <input
                    type="range"
                    min="10000"
                    max="200000"
                    step="5000"
                    value={maxPrice}
                    onChange={e => { setMaxPrice(Number(e.target.value)); setPage(0); }}
                    disabled={selectedType === 'FREE'}
                    className="w-full accent-[#2db84c] h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer disabled:opacity-40"
                  />
                  <div className="flex justify-between text-[10px] text-gray-400 mt-1.5 font-medium">
                    <span>10.000đ</span>
                    <span>100.000đ</span>
                    <span>200.000đ+</span>
                  </div>
                </div>

                {/* Thời hạn sử dụng */}
                <div>
                  <p className="text-xs font-bold text-gray-500 mb-2.5 uppercase tracking-wide">
                    Thời hạn nhận món
                  </p>
                  <div className="flex gap-2">
                    {[
                      { key: 'all', label: 'Tất cả' },
                      { key: 'today', label: 'Hết hạn trong ngày' },
                      { key: 'later', label: 'Còn hạn dài (>24h)' },
                    ].map(item => (
                      <button
                        key={item.key}
                        onClick={() => { setExpiryFilter(item.key); setPage(0); }}
                        className={`flex-1 py-2 rounded-xl text-xs font-semibold cursor-pointer transition-all border text-center ${expiryFilter === item.key
                            ? 'bg-green-50 border-[#2db84c] text-[#2db84c]'
                            : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'
                          }`}
                      >
                        {item.label}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            </div>

            {/* Bottom row: Reset and Count */}
            <div className="flex items-center justify-between pt-4 mt-5 border-t border-gray-100">
              <span className="text-xs text-gray-500">
                Hiển thị <strong className="text-gray-900">{displayedPosts.length}</strong> món ăn
              </span>
              <div className="flex items-center gap-3">
                {activeFilterCount > 0 && (
                  <button
                    type="button"
                    onClick={handleResetFilters}
                    className="inline-flex items-center gap-1.5 text-xs text-red-500 hover:text-red-600 font-medium cursor-pointer transition-colors"
                  >
                    <RotateCcw size={13} />
                    <span>Đặt lại bộ lọc</span>
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => setShowFilters(false)}
                  className="px-4 py-1.5 rounded-xl bg-[#2db84c] text-white text-xs font-semibold hover:bg-[#259b3f] transition-colors cursor-pointer shadow-xs shadow-green-600/20"
                >
                  Áp dụng
                </button>
              </div>
            </div>
          </motion.div>
        )}

        {/* Category Pills (Horizontal scroll) */}
        {categories.length > 0 && (
          <div className="flex gap-2 overflow-x-auto pb-2 scrollbar-none [&::-webkit-scrollbar]:hidden [-ms-overflow-style:none] [scrollbar-width:none]">
            <button
              onClick={() => { setSelectedCategory(0); setPage(0); }}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-medium cursor-pointer transition-all whitespace-nowrap ${selectedCategory === 0
                  ? 'bg-gray-900 text-white font-semibold shadow-xs'
                  : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'
                }`}
            >
              Tất cả danh mục
            </button>
            {categories.map(cat => (
              <button
                key={cat.id}
                onClick={() => { setSelectedCategory(cat.id); setPage(0); }}
                className={`px-3.5 py-1.5 rounded-xl text-xs font-medium cursor-pointer transition-all whitespace-nowrap ${selectedCategory === cat.id
                    ? 'bg-gray-900 text-white font-semibold shadow-xs'
                    : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'
                  }`}
              >
                {cat.name}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* 3. Menu Grid */}
      {isLoading ? (
        <div className="flex items-center justify-center py-20">
          <Loader2 size={26} className="animate-spin text-[#2db84c]" />
        </div>
      ) : displayedPosts.length === 0 ? (
        <div className="text-center py-16 bg-white rounded-3xl border border-gray-100 p-8 shadow-xs">
          <UtensilsCrossed size={48} className="mx-auto mb-3 text-gray-300" />
          <h3 className="font-bold text-gray-700 text-base mb-1">Không tìm thấy món ăn nào</h3>
          <p className="text-xs text-gray-400 max-w-sm mx-auto mb-4">
            Quán hiện chưa có món nào phù hợp với bộ lọc hoặc từ khóa tìm kiếm của bạn.
          </p>
          <button
            onClick={handleResetFilters}
            className="px-4 py-2 rounded-xl bg-gray-100 hover:bg-gray-200 text-xs font-semibold text-gray-700 transition-colors cursor-pointer"
          >
            Xóa bộ lọc
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {displayedPosts.map(post => {
            const isFree = post.postType === 'FREE' || post.unitPrice === 0;
            const hasDiscount = post.originalPrice && post.originalPrice > post.unitPrice;
            const discountPercent = hasDiscount ? Math.round((1 - post.unitPrice / post.originalPrice) * 100) : 0;

            return (
              <motion.div
                key={post.id}
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

                  {/* Quantity Pill */}
                  <div className="absolute bottom-2.5 right-2.5 bg-black/60 backdrop-blur-xs text-white text-[11px] font-medium px-2 py-0.5 rounded-md">
                    Còn: <strong>{post.availableQuantity}</strong>/{post.totalQuantity || post.availableQuantity}
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

                  {/* Price & Action */}
                  <div className="pt-2 border-t border-gray-100 flex items-center justify-between">
                    <div>
                      {isFree ? (
                        <p className="font-bold text-lg text-[#2db84c] leading-none">Miễn phí</p>
                      ) : (
                        <div className="flex items-baseline gap-1.5">
                          <p className="font-bold text-lg text-gray-900 leading-none">{formatVND(post.unitPrice)}</p>
                          {hasDiscount && (
                            <span className="text-xs text-gray-400 line-through leading-none">{formatVND(post.originalPrice)}</span>
                          )}
                        </div>
                      )}
                    </div>

                    <button
                      onClick={e => { e.stopPropagation(); navigate(`/${rolePath}/posts/${post.id}`); }}
                      className="px-3.5 py-1.5 rounded-xl bg-[#2db84c] text-white text-xs font-semibold hover:bg-[#259e40] transition-colors flex items-center gap-1 shadow-sm shadow-green-500/20 cursor-pointer"
                    >
                      <span>Xem & Đặt</span>
                      <ChevronRight size={13} />
                    </button>
                  </div>
                </div>
              </motion.div>
            );
          })}
        </div>
      )}

      {/* 4. Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center gap-2 pt-2 pb-6">
          {Array.from({ length: totalPages }, (_, i) => (
            <button
              key={i}
              onClick={() => setPage(i)}
              className={`w-9 h-9 rounded-xl text-sm font-medium cursor-pointer transition-all ${page === i
                  ? 'bg-[#2db84c] text-white shadow-sm shadow-green-500/20'
                  : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'
                }`}
            >
              {i + 1}
            </button>
          ))}
        </div>
      )}

      {/* 5. Customer Reviews Section (Real Data) */}
      <div className="bg-white rounded-3xl border border-gray-100 p-5 sm:p-6 shadow-xs flex flex-col gap-4 mt-2">
        <div className="flex items-center justify-between border-b border-gray-100 pb-4">
          <div className="flex items-center gap-3">
            <h2 className="text-base sm:text-lg font-bold text-gray-900">
              Đánh giá từ khách hàng
            </h2>
            {supplierInfo.reviewCount > 0 && (
              <span className="text-xs bg-amber-50 text-amber-700 border border-amber-200 font-bold px-2 py-0.5 rounded-full">
                ⭐ {supplierInfo.rating !== null ? supplierInfo.rating.toFixed(1) : '5.0'} / 5.0 ({supplierInfo.reviewCount} lượt)
              </span>
            )}
          </div>
        </div>

        {isLoadingReviews ? (
          <div className="flex items-center justify-center py-8">
            <Loader2 size={20} className="animate-spin text-[#2db84c]" />
          </div>
        ) : reviews.length === 0 ? (
          <div className="py-8 text-center text-gray-400">
            <p className="text-xs">Chưa có đánh giá nào cho quán này.</p>
            <p className="text-[11px] text-gray-400 mt-1">Sau khi hoàn thành nhận món, bạn có thể để lại đánh giá đầu tiên!</p>
          </div>
        ) : (
          <div className="flex flex-col divide-y divide-gray-100">
            {reviews.map(r => (
              <div key={r.id} className="py-3.5 flex flex-col gap-1.5 first:pt-0 last:pb-0">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <div className="w-7 h-7 rounded-full bg-emerald-100 text-emerald-800 text-xs font-bold flex items-center justify-center overflow-hidden">
                      {r.reviewer?.avatarUrl ? (
                        <img src={r.reviewer.avatarUrl} alt="" className="w-full h-full object-cover" />
                      ) : (
                        r.reviewer?.fullName ? r.reviewer.fullName.charAt(0).toUpperCase() : 'U'
                      )}
                    </div>
                    <span className="text-xs font-semibold text-gray-800">
                      {r.reviewer?.fullName || 'Người nhận'}
                    </span>
                    {r.order?.orderCode && (
                      <span className="text-[10px] text-gray-400 bg-gray-50 px-1.5 py-0.5 rounded">
                        #{r.order.orderCode}
                      </span>
                    )}
                  </div>
                  <span className="text-[11px] text-gray-400">
                    {timeAgo(r.createdAt)}
                  </span>
                </div>
                <div className="flex items-center gap-1">
                  {[1, 2, 3, 4, 5].map(star => (
                    <Star
                      key={star}
                      size={12}
                      className={star <= r.rating ? 'fill-amber-400 text-amber-400' : 'text-gray-200'}
                    />
                  ))}
                </div>
                {r.comment && (
                  <p className="text-xs text-gray-600 leading-relaxed">
                    {r.comment}
                  </p>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
