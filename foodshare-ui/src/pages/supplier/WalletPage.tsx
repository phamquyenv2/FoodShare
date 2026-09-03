import { useState, useEffect, useCallback, useRef } from 'react';
import { motion } from 'framer-motion';
import { Wallet, ArrowDownCircle, ArrowUpCircle, CreditCard, Loader2 } from 'lucide-react';
import { apiFetch } from '../../services/api';
import { formatVND } from '../../utils/format';

interface WalletSummary {
  totalEarned: number;
  totalPending: number;
  totalCompleted: number;
  pendingCount: number;
  platformFeePercentage: number;
  transactions: {
    content: any[];
    totalElements: number;
    totalPages: number;
  };
}

const stagger = { hidden: {}, show: { transition: { staggerChildren: 0.06 } } };
const fadeUp = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.22 } } };

export default function WalletPage() {
  const [walletData, setWalletData] = useState<WalletSummary | null>(null);
  const [txList, setTxList] = useState<any[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [isFetchingMore, setIsFetchingMore] = useState(false);
  const observerTarget = useRef<HTMLDivElement | null>(null);

  const fetchWallet = useCallback(async (pageNum: number) => {
    if (pageNum === 0) setIsLoading(true);
    else setIsFetchingMore(true);

    try {
      const res = await apiFetch<WalletSummary>(`/payouts/my/wallet?page=${pageNum}`);
      if (pageNum === 0) {
        setWalletData(res);
        setTxList(res.transactions.content || []);
      } else {
        setTxList(prev => [...prev, ...(res.transactions.content || [])]);
      }
      setTotalPages(res.transactions.totalPages || 0);
    } catch (error) {
      console.error('Failed to fetch wallet summary:', error);
    } finally {
      setIsLoading(false);
      setIsFetchingMore(false);
    }
  }, []);

  useEffect(() => {
    fetchWallet(page);
  }, [fetchWallet, page]);

  useEffect(() => {
    const observer = new IntersectionObserver(
      entries => {
        if (entries[0].isIntersecting && !isLoading && !isFetchingMore && page < totalPages - 1) {
          setPage(prev => prev + 1);
        }
      },
      { threshold: 1.0 }
    );
    if (observerTarget.current) {
      observer.observe(observerTarget.current);
    }
    return () => observer.disconnect();
  }, [isLoading, isFetchingMore, page, totalPages]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-16">
        <Loader2 size={32} className="animate-spin text-[#2db84c]" />
      </div>
    );
  }

  const { 
    totalEarned = 0, 
    totalPending = 0, 
    totalCompleted = 0, 
    pendingCount = 0,
    platformFeePercentage = 0,
    transactions 
  } = walletData || {};
  
  const totalElements = transactions?.totalElements || 0;

  return (
    <div className="p-4 md:p-6 max-w-4xl mx-auto flex flex-col gap-5">
      <div>
        <h1 className="text-xl md:text-2xl font-bold text-gray-900">Ví tiền</h1>
        <p className="text-sm text-gray-500 mt-0.5">Quản lý doanh thu và rút tiền</p>
      </div>

      {/* Balance card */}
      <motion.div
        initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
        className="bg-gradient-to-br from-[#2db84c] to-[#1a8f38] rounded-2xl p-6 text-white shadow-lg shadow-green-500/20"
      >
        <div className="flex items-center gap-2 mb-2 opacity-80">
          <Wallet size={18} />
          <span className="text-sm">Số dư khả dụng</span>
        </div>
        <p className="text-3xl md:text-4xl font-bold mb-4">{totalEarned === 0 ? '0đ' : formatVND(totalEarned)}</p>
        <div className="flex gap-6 text-sm">
          <div>
            <p className="opacity-70 text-xs">Đang chờ</p>
            <p className="font-semibold">{totalPending === 0 ? '0đ' : formatVND(totalPending)}</p>
          </div>
          <div>
            <p className="opacity-70 text-xs">Đã rút</p>
            <p className="font-semibold">{totalCompleted === 0 ? '0đ' : formatVND(totalCompleted)}</p>
          </div>
        </div>
        <button className="mt-5 px-6 py-2.5 rounded-xl bg-white text-[#2db84c] font-semibold text-sm cursor-pointer hover:bg-gray-50 transition-colors active:scale-[0.98]">
          Rút tiền
        </button>
      </motion.div>

      {/* Quick stats */}
      <motion.div
        className="grid grid-cols-3 gap-3"
        variants={stagger} initial="hidden" animate="show"
      >
        <motion.div variants={fadeUp} className="bg-white rounded-xl border border-gray-100 p-4 text-center">
          <ArrowDownCircle size={20} className="text-green-500 mx-auto mb-2" />
          <p className="text-lg font-bold text-gray-900">{totalElements}</p>
          <p className="text-xs text-gray-500">Tổng giao dịch</p>
        </motion.div>
        <motion.div variants={fadeUp} className="bg-white rounded-xl border border-gray-100 p-4 text-center">
          <ArrowUpCircle size={20} className="text-amber-500 mx-auto mb-2" />
          <p className="text-lg font-bold text-gray-900">{pendingCount}</p>
          <p className="text-xs text-gray-500">Đang chờ</p>
        </motion.div>
        <motion.div variants={fadeUp} className="bg-white rounded-xl border border-gray-100 p-4 text-center">
          <CreditCard size={20} className="text-blue-500 mx-auto mb-2" />
          <p className="text-lg font-bold text-gray-900">{platformFeePercentage}%</p>
          <p className="text-xs text-gray-500">Phí nền tảng</p>
        </motion.div>
      </motion.div>

      {/* Transactions */}
      <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
        <div className="p-4 md:px-6 border-b border-gray-100">
          <h2 className="font-semibold text-gray-900 text-sm md:text-base">Lịch sử giao dịch</h2>
        </div>
        <div className="divide-y divide-gray-50">
          {txList.map((p: any, i: number) => (
            <motion.div
              key={p.id}
              initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: (i % 10) * 0.05 }}
              className="flex items-center gap-4 p-4 md:px-6 hover:bg-gray-50/50 transition-colors"
            >
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${p.status === 'SUCCESS' ? 'bg-green-50' : 'bg-amber-50'}`}>
                {p.status === 'SUCCESS'
                  ? <ArrowDownCircle size={18} className="text-green-500" />
                  : <ArrowUpCircle size={18} className="text-amber-500" />
                }
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-900">{p.payoutCode}</p>
                <p className="text-xs text-gray-400 mt-0.5">
                  Phí nền tảng: {formatVND(p.platformFee)}
                </p>
              </div>
              <div className="text-right flex-shrink-0">
                <p className={`text-sm font-bold ${p.status === 'SUCCESS' ? 'text-green-600' : 'text-amber-600'}`}>
                  +{formatVND(p.netAmount)}
                </p>
                <span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${p.status === 'SUCCESS' ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700'}`}>
                  {p.status === 'SUCCESS' ? 'Đã nhận' : 'Đang chờ'}
                </span>
              </div>
            </motion.div>
          ))}
          {isFetchingMore && (
            <div className="py-4 flex justify-center">
              <Loader2 size={24} className="animate-spin text-gray-400" />
            </div>
          )}
          <div ref={observerTarget} className="h-4" />
        </div>
      </div>
    </div>
  );
}
