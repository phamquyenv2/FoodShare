import { useState, useEffect, useCallback, useRef } from 'react';
import { motion } from 'framer-motion';
import {
  Wallet,
  ArrowDownCircle,
  ArrowUpCircle,
  CreditCard,
  Loader2,
  Plus,
  X,
  ChevronDown,
  Check,
} from 'lucide-react';
import { apiFetch } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';
import { formatVND } from '../../utils/format';

const formatBankDisplay = (bankName?: string, bankCode?: string) => {
  if (!bankName) return bankCode || 'Ngân hàng';
  const match = bankName.match(/\(([^)]+)\)/);
  if (match && match[1]) {
    return match[1].trim();
  }
  return bankName
    .replace(/^(Ngân hàng thương mại cổ phần|Ngân hàng TMCP|Ngân hàng)\s+/i, '')
    .trim();
};

const POPULAR_BANKS = [
  { code: 'VCB', name: 'Vietcombank' },
  { code: 'ICB', name: 'VietinBank' },
  { code: 'BIDV', name: 'BIDV' },
  { code: 'MB', name: 'MBBank' },
  { code: 'TCB', name: 'Techcombank' },
  { code: 'ACB', name: 'ACB' },
  { code: 'VPB', name: 'VPBank' },
  { code: 'TPB', name: 'TPBank' },
  { code: 'STB', name: 'Sacombank' },
  { code: 'HDB', name: 'HDBank' },
  { code: 'VBA', name: 'Agribank' },
  { code: 'NAB', name: 'NamABank' },
  { code: 'VIB', name: 'VIB' },
  { code: 'SHB', name: 'SHB' },
  { code: 'OCB', name: 'OCB' },
  { code: 'MSB', name: 'MSB' },
  { code: 'LPB', name: 'LPBank' },
  { code: 'OTHER', name: 'Ngân hàng khác...' },
];

type PayoutStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'CANCELLED';

interface PayoutTransaction {
  id: number;
  payoutCode: string;
  requestedAmount: number;
  netAmount: number;
  status: PayoutStatus;
  bankName: string;
  accountNumber: string;
  rejectionReason?: string;
  createdAt?: string;
}

interface PayoutAccount {
  id: number;
  bankCode: string;
  bankName: string;
  accountNumber: string;
  accountHolderName: string;
  isDefault: boolean;
}

interface WalletSummary {
  totalEarned: number;
  earnedBalance: number;
  rawAvailableBalance: number;
  availableBalance: number;
  totalPending: number;
  totalCompleted: number;
  pendingCount: number;
  platformFeePercentage: number;
  minPayoutAmount: number;
  maxPayoutAmount: number;
  transactions: {
    content: PayoutTransaction[];
    totalElements: number;
    totalPages: number;
  };
}

const stagger = { hidden: {}, show: { transition: { staggerChildren: 0.06 } } };
const fadeUp = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.22 } } };

const statusLabel: Record<PayoutStatus, string> = {
  PENDING: 'Đang chờ', SUCCESS: 'Đã nhận', FAILED: 'Từ chối', CANCELLED: 'Đã hủy',
};

const statusTone: Record<PayoutStatus, string> = {
  PENDING: 'bg-amber-100 text-amber-700',
  SUCCESS: 'bg-green-100 text-green-700',
  FAILED: 'bg-red-100 text-red-700',
  CANCELLED: 'bg-gray-100 text-gray-600',
};

export default function WalletPage() {
  const { showError, showSuccess } = useToast();
  const [walletData, setWalletData] = useState<WalletSummary | null>(null);
  const [txList, setTxList] = useState<PayoutTransaction[]>([]);
  const [accounts, setAccounts] = useState<PayoutAccount[]>([]);
  const [isWithdrawOpen, setIsWithdrawOpen] = useState(false);
  const [isAddingAccount, setIsAddingAccount] = useState(false);
  const [selectedAccountId, setSelectedAccountId] = useState('');
  const [isAccountDropdownOpen, setIsAccountDropdownOpen] = useState(false);
  const accountDropdownRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (accountDropdownRef.current && !accountDropdownRef.current.contains(e.target as Node)) {
        setIsAccountDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);
  const [withdrawAmount, setWithdrawAmount] = useState('');
  const [invalidWithdrawalField, setInvalidWithdrawalField] = useState<'account' | 'amount' | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [accountForm, setAccountForm] = useState({
    bankCode: '', bankName: '', accountNumber: '', accountHolderName: '', isDefault: true,
  });
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
      showError(error instanceof Error ? error.message : 'Không thể tải thông tin ví');
    } finally {
      setIsLoading(false);
      setIsFetchingMore(false);
    }
  }, [showError]);

  useEffect(() => {
    fetchWallet(page);
  }, [fetchWallet, page]);

  const fetchAccounts = useCallback(async () => {
    try {
      const data = await apiFetch<PayoutAccount[]>('/payouts/accounts');
      setAccounts(data);
      const preferred = data.find(account => account.isDefault) ?? data[0];
      if (preferred) setSelectedAccountId(String(preferred.id));
    } catch (error) {
      showError(error instanceof Error ? error.message : 'Không thể tải tài khoản nhận tiền');
    }
  }, [showError]);

  useEffect(() => {
    fetchAccounts();
  }, [fetchAccounts]);

  const submitWithdrawal = async (event: React.FormEvent) => {
    event.preventDefault();
    setInvalidWithdrawalField(null);
    const amount = Number(withdrawAmount);
    if (!selectedAccountId) {
      setInvalidWithdrawalField('account');
      showError('Vui lòng chọn tài khoản nhận tiền.');
      return;
    }
    if (!Number.isFinite(amount) || amount <= 0) {
      setInvalidWithdrawalField('amount');
      showError('Số tiền rút không hợp lệ.');
      return;
    }
    setIsSubmitting(true);
    try {
      await apiFetch<PayoutTransaction>('/payouts/requests', {
        method: 'POST',
        body: JSON.stringify({ payoutAccountId: Number(selectedAccountId), amount }),
      });
      showSuccess('Yêu cầu rút tiền đã được gửi và đang chờ duyệt.');
      setIsWithdrawOpen(false);
      setWithdrawAmount('');
      setPage(0);
      await fetchWallet(0);
    } catch (error) {
      showError(error instanceof Error ? error.message : 'Không thể tạo yêu cầu rút tiền.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const submitAccount = async (event: React.FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    try {
      const created = await apiFetch<PayoutAccount>('/payouts/accounts', {
        method: 'POST',
        body: JSON.stringify(accountForm),
      });
      await fetchAccounts();
      setSelectedAccountId(String(created.id));
      setAccountForm({ bankCode: '', bankName: '', accountNumber: '', accountHolderName: '', isDefault: true });
      setIsAddingAccount(false);
      showSuccess('Đã thêm tài khoản nhận tiền.');
    } catch (error) {
      showError(error instanceof Error ? error.message : 'Không thể thêm tài khoản nhận tiền.');
    } finally {
      setIsSubmitting(false);
    }
  };

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
    rawAvailableBalance = 0,
    totalPending = 0,
    totalCompleted = 0,
    pendingCount = 0,
    platformFeePercentage = 0,
    minPayoutAmount = 50000,
    maxPayoutAmount = 20000000,
    transactions
  } = walletData || {};

  const totalElements = transactions?.totalElements || 0;
  const selectedAccount = accounts.find(a => String(a.id) === selectedAccountId);

  return (
    <div className="p-4 md:p-6 max-w-4xl mx-auto flex flex-col gap-5">
      <div>
        <h1 className="text-xl md:text-2xl font-bold text-gray-900">Ví tiền</h1>
        <p className="text-sm text-gray-500 mt-0.5">Quản lý doanh thu và rút tiền</p>
      </div>

      {rawAvailableBalance < 0 && (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          Ví đang có nghĩa vụ đối soát {formatVND(Math.abs(rawAvailableBalance))}; yêu cầu rút tiền mới tạm khóa.
        </div>
      )}

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
        <button
          type="button"
          onClick={() => { setInvalidWithdrawalField(null); setIsWithdrawOpen(true); }}
          disabled={totalEarned < minPayoutAmount || rawAvailableBalance < 0}
          className="mt-5 px-6 py-2.5 rounded-xl bg-white text-[#2db84c] font-semibold text-sm cursor-pointer hover:bg-gray-50 transition-colors active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60"
        >
          Rút tiền
        </button>
        <p className="mt-2 text-xs text-white/70">
          Hạn mức mỗi lần: {formatVND(minPayoutAmount)} – {formatVND(maxPayoutAmount)}
        </p>
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
          {txList.length === 0 && (
            <p className="px-6 py-10 text-center text-sm text-gray-500">Chưa có yêu cầu rút tiền.</p>
          )}
          {txList.map((p, i) => (
            <motion.div
              key={p.id}
              initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: (i % 10) * 0.05 }}
              className="flex items-center gap-4 p-4 md:px-6 hover:bg-gray-50/50 transition-colors"
            >
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${p.status === 'SUCCESS' ? 'bg-green-50' : p.status === 'FAILED' ? 'bg-red-50' : 'bg-amber-50'}`}>
                {p.status === 'SUCCESS'
                  ? <ArrowDownCircle size={18} className="text-green-500" />
                  : <ArrowUpCircle size={18} className="text-amber-500" />
                }
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-900">{p.payoutCode}</p>
                <p className="text-xs text-gray-400 mt-0.5">
                  {p.bankName} ••••{p.accountNumber?.slice(-4)}
                </p>
                {p.rejectionReason && <p className="text-xs text-red-500 mt-1">{p.rejectionReason}</p>}
              </div>
              <div className="text-right flex-shrink-0">
                <p className={`text-sm font-bold ${p.status === 'SUCCESS' ? 'text-green-600' : p.status === 'FAILED' ? 'text-red-600' : 'text-amber-600'}`}>
                  -{formatVND(p.requestedAmount ?? p.netAmount)}
                </p>
                <span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${statusTone[p.status]}`}>
                  {statusLabel[p.status]}
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

      {isWithdrawOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true">
          <div className="w-full max-w-md rounded-2xl bg-white p-5 shadow-xl">
            <div className="mb-5 flex items-center justify-between">
              <div>
                <h2 className="font-bold text-gray-900">{isAddingAccount ? 'Thêm tài khoản nhận tiền' : 'Tạo yêu cầu rút tiền'}</h2>
                <p className="mt-1 text-xs text-gray-500">Số dư khả dụng: {formatVND(totalEarned)}</p>
              </div>
              <button type="button" onClick={() => setIsWithdrawOpen(false)} className="rounded-lg p-1 text-gray-400 hover:bg-gray-100" aria-label="Đóng">
                <X size={20} />
              </button>
            </div>

            {isAddingAccount ? (
              <form noValidate onSubmit={submitAccount} className="space-y-3.5">
                <div>
                  <label className="mb-1 block text-xs font-semibold text-gray-700">Ngân hàng</label>
                  <select
                    required
                    value={POPULAR_BANKS.some(b => b.code === accountForm.bankCode) ? accountForm.bankCode : (accountForm.bankCode ? 'OTHER' : '')}
                    onChange={e => {
                      const code = e.target.value;
                      if (code === 'OTHER') {
                        setAccountForm({ ...accountForm, bankCode: 'OTHER', bankName: '' });
                      } else {
                        const bank = POPULAR_BANKS.find(b => b.code === code);
                        setAccountForm({ ...accountForm, bankCode: code, bankName: bank ? bank.name : '' });
                      }
                    }}
                    className="w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm outline-none focus:border-green-500 bg-white"
                  >
                    <option value="">-- Chọn ngân hàng --</option>
                    {POPULAR_BANKS.map(b => (
                      <option key={b.code} value={b.code}>{b.name} ({b.code})</option>
                    ))}
                  </select>
                </div>

                {(!POPULAR_BANKS.some(b => b.code === accountForm.bankCode && b.code !== 'OTHER') || accountForm.bankCode === 'OTHER') && (
                  <div className="grid grid-cols-2 gap-2">
                    <input
                      required
                      maxLength={30}
                      placeholder="Mã (VD: VCB)"
                      value={accountForm.bankCode === 'OTHER' ? '' : accountForm.bankCode}
                      onChange={e => setAccountForm({ ...accountForm, bankCode: e.target.value.toUpperCase() })}
                      className="rounded-xl border border-gray-200 px-3 py-2 text-sm outline-none focus:border-green-500"
                    />
                    <input
                      required
                      maxLength={150}
                      placeholder="Tên ngân hàng"
                      value={accountForm.bankName}
                      onChange={e => setAccountForm({ ...accountForm, bankName: e.target.value })}
                      className="rounded-xl border border-gray-200 px-3 py-2 text-sm outline-none focus:border-green-500"
                    />
                  </div>
                )}

                <div>
                  <label className="mb-1 block text-xs font-semibold text-gray-700">Số tài khoản</label>
                  <input
                    required
                    maxLength={50}
                    placeholder="Nhập số tài khoản"
                    value={accountForm.accountNumber}
                    onChange={e => setAccountForm({ ...accountForm, accountNumber: e.target.value.replace(/\s+/g, '') })}
                    className="w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm font-mono outline-none focus:border-green-500"
                  />
                </div>

                <div>
                  <label className="mb-1 block text-xs font-semibold text-gray-700">Tên chủ tài khoản</label>
                  <input
                    required
                    maxLength={150}
                    placeholder="VD: NGUYEN VAN A"
                    value={accountForm.accountHolderName}
                    onChange={e => setAccountForm({ ...accountForm, accountHolderName: e.target.value.toUpperCase() })}
                    className="w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm font-semibold uppercase outline-none focus:border-green-500"
                  />
                </div>

                <div className="flex gap-2 pt-2">
                  <button type="button" onClick={() => setIsAddingAccount(false)} className="flex-1 rounded-xl border border-gray-200 py-2.5 text-sm font-medium hover:bg-gray-50 transition-colors">
                    Quay lại
                  </button>
                  <button disabled={isSubmitting || !accountForm.bankCode || !accountForm.accountNumber || !accountForm.accountHolderName} className="flex-1 rounded-xl bg-[#2db84c] hover:bg-green-600 py-2.5 text-sm font-semibold text-white transition-colors disabled:opacity-50">
                    {isSubmitting ? 'Đang lưu...' : 'Lưu tài khoản'}
                  </button>
                </div>
              </form>
            ) : (
              <form noValidate onSubmit={submitWithdrawal} className="space-y-4">
                <div ref={accountDropdownRef} className="relative">
                  <label className="mb-1.5 block text-sm font-medium text-gray-700">Tài khoản nhận tiền</label>
                  <button
                    type="button"
                    onClick={() => setIsAccountDropdownOpen(prev => !prev)}
                    aria-invalid={invalidWithdrawalField === 'account'}
                    className={`w-full flex items-center justify-between rounded-xl border px-3 py-2.5 text-sm bg-white text-left transition-colors outline-none cursor-pointer ${
                      invalidWithdrawalField === 'account'
                        ? 'border-red-400 focus:border-red-500 ring-2 ring-red-100'
                        : 'border-gray-200 hover:border-gray-300 focus:border-green-500'
                    }`}
                  >
                    {selectedAccount ? (
                      <div className="flex items-center gap-2.5 min-w-0">
                        <div className="w-7 h-7 rounded-lg bg-green-50 text-green-700 flex items-center justify-center shrink-0">
                          <CreditCard size={15} />
                        </div>
                        <div className="min-w-0 flex items-center gap-2 truncate">
                          <span className="font-semibold text-gray-900">
                            {formatBankDisplay(selectedAccount.bankName, selectedAccount.bankCode)}
                          </span>
                          <span className="font-mono text-gray-500 text-xs">
                            ••••{selectedAccount.accountNumber?.slice(-4)}
                          </span>
                          {selectedAccount.accountHolderName && (
                            <span className="text-gray-400 text-xs truncate uppercase hidden sm:inline">
                              • {selectedAccount.accountHolderName}
                            </span>
                          )}
                        </div>
                      </div>
                    ) : (
                      <span className="text-gray-400 text-sm">Chọn tài khoản nhận tiền</span>
                    )}
                    <ChevronDown size={16} className={`text-gray-400 shrink-0 transition-transform ${isAccountDropdownOpen ? 'rotate-180' : ''}`} />
                  </button>

                  {isAccountDropdownOpen && (
                    <div className="absolute left-0 right-0 top-full mt-1.5 z-50 max-h-56 overflow-y-auto rounded-xl border border-gray-100 bg-white p-1.5 shadow-xl animate-in fade-in zoom-in-95 duration-100">
                      {accounts.map(account => {
                        const isSelected = String(account.id) === selectedAccountId;
                        return (
                          <button
                            key={account.id}
                            type="button"
                            onClick={() => {
                              setSelectedAccountId(String(account.id));
                              setInvalidWithdrawalField(null);
                              setIsAccountDropdownOpen(false);
                            }}
                            className={`w-full flex items-center justify-between px-3 py-2.5 rounded-lg text-left text-sm transition-colors cursor-pointer ${
                              isSelected ? 'bg-green-50 text-green-900 font-medium' : 'text-gray-700 hover:bg-gray-50'
                            }`}
                          >
                            <div className="flex items-center gap-2.5 min-w-0">
                              <span className="font-bold text-gray-900 w-28 shrink-0 truncate">
                                {formatBankDisplay(account.bankName, account.bankCode)}
                              </span>
                              <span className="font-mono text-gray-600 text-xs shrink-0">
                                ••••{account.accountNumber?.slice(-4)}
                              </span>
                              {account.accountHolderName && (
                                <span className="text-gray-400 text-xs truncate uppercase hidden sm:inline">
                                  ({account.accountHolderName})
                                </span>
                              )}
                            </div>
                            {isSelected && <Check size={16} className="text-green-600 shrink-0 ml-2" />}
                          </button>
                        );
                      })}
                      {accounts.length === 0 && (
                        <div className="py-3 text-center text-xs text-gray-400">Chưa có tài khoản nào</div>
                      )}
                    </div>
                  )}

                  <button type="button" onClick={() => setIsAddingAccount(true)} className="mt-2 flex items-center gap-1 text-xs font-medium text-green-600 hover:text-green-700 transition-colors cursor-pointer">
                    <Plus size={14} /> Thêm tài khoản mới
                  </button>
                </div>
                <div>
                  <div className="mb-1.5 flex items-center justify-between">
                    <label className="text-sm font-medium text-gray-700">Số tiền</label>
                    <button type="button" onClick={() => setWithdrawAmount(String(Math.min(totalEarned, maxPayoutAmount)))} className="text-xs font-medium text-green-600">Rút tối đa</button>
                  </div>
                  <input required type="number" min={minPayoutAmount} max={Math.min(totalEarned, maxPayoutAmount)} step="1"
                    value={withdrawAmount} aria-invalid={invalidWithdrawalField === 'amount'} onChange={e => { setWithdrawAmount(e.target.value); setInvalidWithdrawalField(null); }} placeholder={String(minPayoutAmount)}
                    className="w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm outline-none focus:border-green-500" />
                  <p className="mt-1 text-xs text-gray-400">Từ {formatVND(minPayoutAmount)} đến {formatVND(Math.min(totalEarned, maxPayoutAmount))}</p>
                </div>
                <button disabled={isSubmitting || accounts.length === 0} className="w-full rounded-xl bg-[#2db84c] py-2.5 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60">
                  {isSubmitting ? 'Đang gửi...' : 'Gửi yêu cầu'}
                </button>
              </form>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
