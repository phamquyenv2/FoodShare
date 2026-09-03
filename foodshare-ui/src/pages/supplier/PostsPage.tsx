import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Plus, Package, Loader2, Search } from 'lucide-react';
import { apiFetch } from '../../services/api';
import PostItemCard, { type PostItem } from '../../components/supplier/PostItemCard';

const TABS = [
  { key: 'all', label: 'Tất cả' },
  { key: 'AVAILABLE', label: 'Hoạt động' },
  { key: 'HIDDEN', label: 'Đã ẩn' },
  { key: 'EXPIRED', label: 'Hết hạn' },
  { key: 'DRAFT', label: 'Bản nháp' },
];

export default function PostsPage() {
  const navigate = useNavigate();
  const [posts, setPosts] = useState<PostItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isFetchingMore, setIsFetchingMore] = useState(false);
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  const [tab, setTab] = useState('all');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isDesktop, setIsDesktop] = useState(window.innerWidth >= 768);
  const observerTarget = useRef<HTMLDivElement | null>(null);
  const [keyword, setKeyword] = useState('');
  const [searchInput, setSearchInput] = useState('');

  useEffect(() => {
    const handleResize = () => setIsDesktop(window.innerWidth >= 768);
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // Debounce logic for mobile
  useEffect(() => {
    if (!isDesktop) {
      const timer = setTimeout(() => {
        setKeyword(searchInput);
        setPage(0);
      }, 500);
      return () => clearTimeout(timer);
    }
  }, [searchInput, isDesktop]);

  const handleSearchKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (isDesktop && e.key === 'Enter') {
      setKeyword(searchInput);
      setPage(0);
    }
  };

  const fetchPosts = useCallback(async (pageNum: number, desktopMode: boolean) => {
    if (pageNum === 0) setIsLoading(true);
    else setIsFetchingMore(true);

    try {
      const statusParam = tab !== 'all' ? `&status=${tab}` : '';
      const keywordParam = keyword ? `&keyword=${encodeURIComponent(keyword)}` : '';
      const res = await apiFetch<any>(`/food-posts/my?page=${pageNum}&size=9${statusParam}${keywordParam}`);
      setPosts(prev => (pageNum === 0 || desktopMode) ? res.content || [] : [...prev, ...(res.content || [])]);
      setTotalPages(res.totalPages || 0);
    } catch (err) {
      console.error('Failed to fetch posts:', err);
    } finally {
      setIsLoading(false);
      setIsFetchingMore(false);
    }
  }, [tab, keyword]);

  useEffect(() => { 
    fetchPosts(page, isDesktop); 
  }, [fetchPosts, page, isDesktop]);

  useEffect(() => {
    if (isDesktop) return;

    const observer = new IntersectionObserver(
      entries => {
        if (entries[0].isIntersecting && !isLoading && !isFetchingMore && page < totalPages - 1) {
          setPage(p => p + 1);
        }
      },
      { threshold: 0.1 }
    );

    if (observerTarget.current) {
      observer.observe(observerTarget.current);
    }

    return () => observer.disconnect();
  }, [isLoading, isFetchingMore, page, totalPages, isDesktop]);

  const handleToggleVisibility = async (post: PostItem) => {
    setActionLoading(post.id);
    try {
      const endpoint = post.postStatus === 'HIDDEN'
        ? `/food-posts/${post.id}/unhide`
        : `/food-posts/${post.id}/hide`;
      await apiFetch(endpoint, { method: 'PATCH' });
      setPage(0);
      if (page === 0) fetchPosts(0, isDesktop);
    } catch (err: any) {
      alert(err.message || 'Thao tác thất bại');
    } finally {
      setActionLoading(null);
    }
  };

  const filtered = posts;

  const stagger = { hidden: {}, show: { transition: { staggerChildren: 0.05 } } };
  const fadeUp = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.2 } } };

  return (
    <div className="p-4 md:p-6 max-w-6xl mx-auto flex flex-col gap-5 min-h-[calc(100vh-80px)] relative">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <h1 className="text-xl md:text-2xl font-bold text-gray-900">Bài đăng của tôi</h1>
        
        <div className="flex items-center gap-3 w-full md:w-auto">
          {/* Search Bar */}
          <div className="relative w-full md:w-64">
            <input
              type="text"
              placeholder={"Tìm tên món ăn..."}
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              onKeyDown={handleSearchKeyDown}
              className="w-full pl-10 pr-4 py-2 bg-white border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/20 focus:border-[#2db84c] transition-all"
            />
            <Search size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          </div>

          <button
            onClick={() => navigate('/supplier/posts/create')}
            className="flex items-center gap-2 px-4 py-2 bg-[#2db84c] text-white text-sm font-semibold rounded-xl cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 whitespace-nowrap"
          >
            <Plus size={16} /> Tạo bài đăng
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 overflow-x-auto pb-1">
        {TABS.map(t => (
          <button
            key={t.key}
            onClick={() => { setTab(t.key); setPage(0); }}
            className={`px-4 py-2 rounded-xl text-sm font-medium whitespace-nowrap cursor-pointer transition-all ${
              tab === t.key
                ? 'bg-[#2db84c] text-white shadow-sm'
                : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Loading */}
      {isLoading ? (
        <div className="flex items-center justify-center py-16">
          <Loader2 size={24} className="animate-spin text-[#2db84c]" />
        </div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <Package size={48} className="mx-auto mb-3 opacity-50" />
          <p className="text-sm">Chưa có bài đăng nào</p>
        </div>
      ) : (
        <motion.div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3" variants={stagger} initial="hidden" animate="show">
          {filtered.map(post => (
            <PostItemCard
              key={post.id}
              post={post}
              variants={fadeUp}
              actionLoading={actionLoading}
              onToggleVisibility={handleToggleVisibility}
            />
          ))}
        </motion.div>
      )}

      {/* Infinite Scroll Target (Mobile Only) */}
      {!isDesktop && page < totalPages - 1 && (
        <div ref={observerTarget} className="flex justify-center py-6 w-full">
          <Loader2 size={24} className="animate-spin text-[#2db84c]" />
        </div>
      )}
      
      {!isDesktop && page >= totalPages - 1 && posts.length > 0 && (
        <div className="text-center py-6 text-gray-400 text-sm">
          Đã tải hết bài đăng
        </div>
      )}

      {/* Pagination (Desktop Only) - Fixed at bottom */}
      {isDesktop && totalPages > 1 && filtered.length > 0 && (
        <div className="hidden md:flex sticky bottom-0 -mx-4 md:-mx-6 px-4 md:px-6 py-4 justify-center gap-2 mt-auto z-40">
          {Array.from({ length: totalPages }, (_, i) => (
            <button
              key={i} onClick={() => setPage(i)}
              className={`w-10 h-10 rounded-xl text-sm font-semibold cursor-pointer transition-all ${
                page === i 
                  ? 'bg-[#2db84c] text-white shadow-md shadow-green-500/20' 
                  : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50 hover:border-gray-300'
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
