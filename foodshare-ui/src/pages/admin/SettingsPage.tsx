import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Settings, Loader2, Save, CreditCard, Bell, Shield, Globe } from 'lucide-react';
import { apiFetch } from '../../services/api';

interface PlatformSettings {
  platformFeePercent: number;
  minPayoutAmount: number;
  maxPayoutAmount: number;
  maintenanceMode: boolean;
  contactEmail: string;
  supportPhone: string;
}

export default function SettingsPage() {
  const [settings, setSettings] = useState<PlatformSettings>({
    platformFeePercent: 5,
    minPayoutAmount: 50000,
    maxPayoutAmount: 20000000,
    maintenanceMode: false,
    contactEmail: 'support@foodshare.vn',
    supportPhone: '19001560',
  });
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    const fetchSettings = async () => {
      try {
        const res = await apiFetch<any[]>('/admin/configs');
        const newSettings = { ...settings };
        res.forEach(item => {
          if (item.configKey === 'PLATFORM_FEE_PERCENTAGE') newSettings.platformFeePercent = parseFloat(item.configValue) * 100;
          if (item.configKey === 'MIN_PAYOUT_AMOUNT') newSettings.minPayoutAmount = parseInt(item.configValue);
          if (item.configKey === 'MAX_PAYOUT_AMOUNT') newSettings.maxPayoutAmount = parseInt(item.configValue);
          if (item.configKey === 'MAINTENANCE_MODE') newSettings.maintenanceMode = item.configValue === 'true';
          if (item.configKey === 'CONTACT_EMAIL') newSettings.contactEmail = item.configValue;
          if (item.configKey === 'HOTLINE_SUPPORT') newSettings.supportPhone = item.configValue;
        });
        setSettings(newSettings);
      } catch (err) {
        console.error('Failed to load configs', err);
      } finally {
        setIsLoading(false);
      }
    };
    fetchSettings();
  }, []);

  const handleSave = async () => {
    setIsSaving(true);
    setSaved(false);
    try {
      const updates = [
        { key: 'PLATFORM_FEE_PERCENTAGE', value: (settings.platformFeePercent / 100).toString() },
        { key: 'MIN_PAYOUT_AMOUNT', value: settings.minPayoutAmount.toString() },
        { key: 'MAX_PAYOUT_AMOUNT', value: settings.maxPayoutAmount.toString() },
        { key: 'MAINTENANCE_MODE', value: settings.maintenanceMode.toString() },
        { key: 'CONTACT_EMAIL', value: settings.contactEmail },
        { key: 'HOTLINE_SUPPORT', value: settings.supportPhone }
      ];

      for (const update of updates) {
        await apiFetch(`/admin/configs/${update.key}`, {
          method: 'PUT',
          body: JSON.stringify({ configValue: update.value }),
        });
      }

      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch (err: any) {
      alert(err.message || 'Lưu cài đặt thất bại');
    } finally {
      setIsSaving(false);
    }
  };

  const update = (key: keyof PlatformSettings, value: any) => {
    setSettings(prev => ({ ...prev, [key]: value }));
  };

  if (isLoading) {
    return <div className="flex items-center justify-center py-20"><Loader2 size={24} className="animate-spin text-[#2db84c]" /></div>;
  }

  return (
    <div className="p-4 md:p-6 max-w-3xl mx-auto flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-gray-900">Cài đặt hệ thống</h1>
          <p className="text-sm text-gray-500 mt-0.5">Cấu hình nền tảng FoodShare</p>
        </div>
        <button onClick={handleSave} disabled={isSaving}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#2db84c] text-white text-sm font-semibold cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 disabled:opacity-70">
          {isSaving ? <Loader2 size={14} className="animate-spin" /> : saved ? <Save size={14} /> : <Save size={14} />}
          {isSaving ? 'Đang lưu...' : saved ? '✓ Đã lưu' : 'Lưu thay đổi'}
        </button>
      </div>

      {/* Financial */}
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
        className="bg-white rounded-2xl border border-gray-100 p-5">
        <div className="flex items-center gap-2 mb-4">
          <div className="w-8 h-8 rounded-lg bg-amber-100 flex items-center justify-center"><CreditCard size={16} className="text-amber-600" /></div>
          <h3 className="font-semibold text-gray-900">Tài chính</h3>
        </div>
        <div className="flex flex-col gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Phí nền tảng (%)</label>
            <input type="number" min={0} max={50} value={settings.platformFeePercent}
              onChange={e => update('platformFeePercent', Number(e.target.value))}
              className="w-full px-4 py-2.5 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c]" />
            <p className="text-xs text-gray-400 mt-1">Phần trăm nền tảng thu từ mỗi đơn hàng có phí</p>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Số dư rút tối thiểu (VNĐ)</label>
            <input type="number" min={0} value={settings.minPayoutAmount}
              onChange={e => update('minPayoutAmount', Number(e.target.value))}
              className="w-full px-4 py-2.5 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c]" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Hạn mức rút tối đa (VNĐ)</label>
            <input type="number" min={0} value={settings.maxPayoutAmount}
              onChange={e => update('maxPayoutAmount', Number(e.target.value))}
              className="w-full px-4 py-2.5 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c]" />
          </div>
        </div>
      </motion.div>

      {/* System */}
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}
        className="bg-white rounded-2xl border border-gray-100 p-5">
        <div className="flex items-center gap-2 mb-4">
          <div className="w-8 h-8 rounded-lg bg-red-100 flex items-center justify-center"><Shield size={16} className="text-red-600" /></div>
          <h3 className="font-semibold text-gray-900">Hệ thống</h3>
        </div>
        <div className="flex items-center justify-between p-3 rounded-xl bg-gray-50">
          <div>
            <p className="text-sm font-medium text-gray-900">Chế độ bảo trì</p>
            <p className="text-xs text-gray-400">Tạm ngưng truy cập cho người dùng thường</p>
          </div>
          <button onClick={() => update('maintenanceMode', !settings.maintenanceMode)}
            className={`w-12 h-7 rounded-full cursor-pointer transition-all relative ${settings.maintenanceMode ? 'bg-red-500' : 'bg-gray-300'}`}>
            <div className={`absolute top-0.5 w-6 h-6 rounded-full bg-white shadow-sm transition-all ${settings.maintenanceMode ? 'left-[22px]' : 'left-0.5'}`} />
          </button>
        </div>
      </motion.div>

      {/* Contact */}
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 }}
        className="bg-white rounded-2xl border border-gray-100 p-5">
        <div className="flex items-center gap-2 mb-4">
          <div className="w-8 h-8 rounded-lg bg-green-100 flex items-center justify-center"><Globe size={16} className="text-green-600" /></div>
          <h3 className="font-semibold text-gray-900">Liên hệ</h3>
        </div>
        <div className="flex flex-col gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Email hỗ trợ</label>
            <input type="email" value={settings.contactEmail}
              onChange={e => update('contactEmail', e.target.value)}
              className="w-full px-4 py-2.5 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c]" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Hotline</label>
            <input type="text" value={settings.supportPhone}
              onChange={e => update('supportPhone', e.target.value)}
              className="w-full px-4 py-2.5 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c]" />
          </div>
        </div>
      </motion.div>
    </div>
  );
}
