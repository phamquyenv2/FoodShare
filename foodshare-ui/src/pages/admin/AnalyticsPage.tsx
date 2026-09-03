import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import {
  Users, ShoppingBag, Leaf, TrendingUp, Loader2, Calendar,
  CreditCard, FileText, AlertTriangle,
} from 'lucide-react';
import {
  LineChart, Line, BarChart, Bar, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, Legend, PieChart, Pie, Cell,
} from 'recharts';
import { apiFetch } from '../../services/api';
import { formatVND } from '../../utils/format';

interface DashStats {
  totalUsers: number;
  totalOrders: number;
  totalFoodPosts: number;
  totalRevenue: number;
  totalFoodSaved: number;
  userGrowthPercent?: number;
  orderGrowthPercent?: number;
}

const stagger = { hidden: {}, show: { transition: { staggerChildren: 0.06 } } };
const fadeUp = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.25 } } };

const PIE_COLORS = ['#2db84c', '#0891b2', '#7c3aed', '#f59e0b', '#ef4444'];

function ChartTooltip({ active, payload, label }: any) {
  if (!active || !payload) return null;
  return (
    <div className="bg-white border border-gray-200 rounded-xl p-3 shadow-lg">
      <p className="text-gray-500 text-xs mb-1">{label}</p>
      {payload.map((p: any) => (
        <p key={p.name} className="text-xs font-medium" style={{ color: p.color }}>
          {p.name}: {p.value}
        </p>
      ))}
    </div>
  );
}

export default function AnalyticsPage() {
  const [stats, setStats] = useState<DashStats | null>(null);
  const [userChart, setUserChart] = useState<any[]>([]);
  const [orderChart, setOrderChart] = useState<any[]>([]);
  const [orderByStatus, setOrderByStatus] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [dateRange, setDateRange] = useState('7d');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  useEffect(() => {
    const fetchAll = async () => {
      setIsLoading(true);
      try {
        let dateParams = '';
        if (dateRange === 'custom' && startDate && endDate) {
          dateParams = `?fromDate=${startDate}T00:00:00Z&toDate=${endDate}T23:59:59Z`;
        } else {
          const now = new Date();
          const days = dateRange === '7d' ? 7 : dateRange === '30d' ? 30 : 90;
          const from = new Date(now.getTime() - days * 86400000);
          dateParams = `?fromDate=${from.toISOString()}&toDate=${now.toISOString()}`;
        }

        const res = await apiFetch<any>(`/admin/statistics/dashboard${dateParams}`);
        
        if (res && res.overview) {
          setStats({
            totalUsers: res.overview.totalUsers || 0,
            totalOrders: res.overview.totalOrders || 0,
            totalFoodPosts: res.overview.totalFoodPosts || 0,
            totalRevenue: res.overview.totalRevenue || 0,
            totalFoodSaved: 0,
          });

          // Compute status for pie chart
          const completed = res.overview.completedOrders || 0;
          const cancelled = res.overview.cancelledOrders || 0;
          const total = res.overview.totalOrders || 0;
          const pending = Math.max(0, total - completed - cancelled);
          setOrderByStatus([
            { name: 'COMPLETED', value: completed },
            { name: 'PENDING', value: pending },
            { name: 'CANCELLED', value: cancelled }
          ].filter(s => s.value > 0));
        }

        if (res && res.chartData) {
          if (res.chartData.userRegistrations) {
            setUserChart(res.chartData.userRegistrations.map((u: any) => ({
              date: u.date,
              users: u.value
            })));
          }
          if (res.chartData.orderCounts) {
            setOrderChart(res.chartData.orderCounts.map((o: any) => ({
              date: o.date,
              ORDERS: o.value
            })));
          }
        }
      } catch (err) {
        console.error('Failed to load analytics:', err);
      } finally {
        setIsLoading(false);
      }
    };
    fetchAll();
  }, [dateRange, startDate, endDate]);

  const STAT_CARDS = stats ? [
    { icon: Users, label: 'Tổng người dùng', value: stats.totalUsers.toLocaleString(), change: stats.userGrowthPercent ? `+${stats.userGrowthPercent}%` : '', color: '#2563eb', bg: '#dbeafe' },
    { icon: ShoppingBag, label: 'Tổng đơn hàng', value: stats.totalOrders.toLocaleString(), change: stats.orderGrowthPercent ? `+${stats.orderGrowthPercent}%` : '', color: '#0891b2', bg: '#cffafe' },
    { icon: FileText, label: 'Bài đăng', value: stats.totalFoodPosts.toLocaleString(), change: '', color: '#7c3aed', bg: '#ede9fe' },
    { icon: CreditCard, label: 'Doanh thu', value: formatVND(stats.totalRevenue), change: '', color: '#f59e0b', bg: '#fef3c7' },
  ] : [];

  const STATUS_LABELS: Record<string, string> = {
    COMPLETED: 'Hoàn thành', PENDING: 'Chờ', CANCELLED: 'Huỷ', ACCEPTED: 'Chấp nhận', REJECTED: 'Từ chối',
  };

  if (isLoading) {
    return <div className="flex items-center justify-center py-20"><Loader2 size={24} className="animate-spin text-[#2db84c]" /></div>;
  }

  return (
    <div className="p-4 md:p-6 max-w-6xl mx-auto flex flex-col gap-5">
      <div className="flex items-start justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-gray-900">Tổng quan hệ thống</h1>
          <p className="text-sm text-gray-500 mt-0.5">Thống kê hoạt động nền tảng FoodShare</p>
        </div>
        {/* Date Range Filter */}
        <div className="flex items-center gap-2 flex-wrap">
          {[
            { key: '7d', label: '7 ngày' },
            { key: '30d', label: '30 ngày' },
            { key: '90d', label: '90 ngày' },
            { key: 'custom', label: 'Tùy chọn' },
          ].map(r => (
            <button key={r.key} onClick={() => setDateRange(r.key)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium cursor-pointer transition-all ${dateRange === r.key ? 'bg-[#2db84c] text-white' : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'}`}>
              {r.label}
            </button>
          ))}
        </div>
      </div>

      {dateRange === 'custom' && (
        <div className="flex gap-3 items-center bg-white rounded-xl border border-gray-100 p-3">
          <Calendar size={16} className="text-gray-400" />
          <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)}
            className="text-sm border border-gray-200 rounded-lg px-2 py-1.5 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30" />
          <span className="text-gray-400 text-sm">—</span>
          <input type="date" value={endDate} onChange={e => setEndDate(e.target.value)}
            className="text-sm border border-gray-200 rounded-lg px-2 py-1.5 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30" />
        </div>
      )}

      {/* Stat Cards */}
      <motion.div className="grid grid-cols-2 md:grid-cols-4 gap-3 md:gap-4" variants={stagger} initial="hidden" animate="show">
        {STAT_CARDS.map(s => (
          <motion.div key={s.label} variants={fadeUp}
            className="bg-white rounded-2xl border border-gray-100 p-4 md:p-5 hover:shadow-md transition-shadow">
            <div className="w-10 h-10 rounded-xl flex items-center justify-center mb-3" style={{ backgroundColor: s.bg }}>
              <s.icon size={20} style={{ color: s.color }} />
            </div>
            <p className="text-xs text-gray-500 mb-1">{s.label}</p>
            <div className="flex items-end gap-2">
              <p className="text-xl md:text-2xl font-bold text-gray-900">{s.value}</p>
              {s.change && <span className="text-xs text-green-600 font-medium mb-0.5">{s.change}</span>}
            </div>
          </motion.div>
        ))}
      </motion.div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* User Growth Line Chart */}
        <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
          className="bg-white rounded-2xl border border-gray-100 p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-semibold text-gray-900">Người dùng mới</h3>
            <TrendingUp size={16} className="text-green-500" />
          </div>
          {userChart.length > 0 ? (
            <ResponsiveContainer width="100%" height={220}>
              <LineChart data={userChart}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
                <XAxis dataKey="date" tick={{ fill: '#9ca3af', fontSize: 11 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: '#9ca3af', fontSize: 11 }} axisLine={false} tickLine={false} />
                <Tooltip content={<ChartTooltip />} />
                <Line type="monotone" dataKey="users" stroke="#2db84c" strokeWidth={2.5}
                  dot={{ fill: '#2db84c', r: 4 }} activeDot={{ r: 6 }} name="Người dùng" />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <div className="h-[220px] flex items-center justify-center text-gray-400 text-sm">Chưa có dữ liệu</div>
          )}
        </motion.div>

        {/* Orders Bar Chart */}
        <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}
          className="bg-white rounded-2xl border border-gray-100 p-5">
          <h3 className="text-sm font-semibold text-gray-900 mb-4">Tổng đơn hàng theo ngày</h3>
          {orderChart.length > 0 ? (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={orderChart} barSize={10}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
                <XAxis dataKey="date" tick={{ fill: '#9ca3af', fontSize: 11 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: '#9ca3af', fontSize: 11 }} axisLine={false} tickLine={false} />
                <Tooltip content={<ChartTooltip />} />
                <Legend wrapperStyle={{ fontSize: '11px', color: '#6b7280' }} />
                <Bar dataKey="ORDERS" name="Tổng đơn hàng" fill="#0891b2" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <div className="h-[220px] flex items-center justify-center text-gray-400 text-sm">Chưa có dữ liệu</div>
          )}
        </motion.div>
      </div>

      {/* Pie Chart - Order Distribution */}
      {orderByStatus.length > 0 && (
        <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 }}
          className="bg-white rounded-2xl border border-gray-100 p-5">
          <h3 className="text-sm font-semibold text-gray-900 mb-4">Phân bố trạng thái đơn hàng</h3>
          <div className="flex flex-col md:flex-row items-center gap-6">
            <ResponsiveContainer width="100%" height={200} className="max-w-[240px]">
              <PieChart>
                <Pie data={orderByStatus} cx="50%" cy="50%" innerRadius={50} outerRadius={80}
                  paddingAngle={3} dataKey="value">
                  {orderByStatus.map((_, i) => (
                    <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
            <div className="flex flex-wrap gap-3">
              {orderByStatus.map((item, i) => (
                <div key={item.name} className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded-full" style={{ backgroundColor: PIE_COLORS[i % PIE_COLORS.length] }} />
                  <span className="text-xs text-gray-600">{STATUS_LABELS[item.name] || item.name}: <strong>{item.value}</strong></span>
                </div>
              ))}
            </div>
          </div>
        </motion.div>
      )}
    </div>
  );
}
