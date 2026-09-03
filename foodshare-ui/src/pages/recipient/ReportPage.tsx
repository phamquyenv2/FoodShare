import { useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft, Flag, Loader2, CheckCircle, ImagePlus, X } from 'lucide-react';
import { apiFetch } from '../../services/api';

const ALL_REPORT_TYPES = {
  ORDER: [
    { key: 'FOOD_QUALITY', label: 'Chất lượng thực phẩm', desc: 'Món ăn không đúng mô tả, hư hỏng...' },
    { key: 'FRAUD', label: 'Gian lận / Lừa đảo', desc: 'Hành vi gian lận, không giao hàng...' },
    { key: 'HYGIENE', label: 'Vệ sinh an toàn', desc: 'Thực phẩm mất vệ sinh, hết hạn...' },
    { key: 'COMPLAINT', label: 'Thái độ phục vụ', desc: 'Người bán/người mua có thái độ không tốt...' },
    { key: 'OTHER', label: 'Khác', desc: 'Vấn đề khác cần báo cáo' },
  ],
  FOOD_POST: [
    { key: 'INAPPROPRIATE', label: 'Nội dung phản cảm', desc: 'Hình ảnh, từ ngữ không phù hợp...' },
    { key: 'FRAUD', label: 'Thông tin sai sự thật', desc: 'Đăng bài ảo, câu view, giả mạo...' },
    { key: 'OTHER', label: 'Khác', desc: 'Vấn đề khác cần báo cáo' },
  ],
  USER: [
    { key: 'INAPPROPRIATE', label: 'Hành vi không chuẩn mực', desc: 'Xúc phạm, quấy rối...' },
    { key: 'FRAUD', label: 'Giả mạo / Lừa đảo', desc: 'Tài khoản ảo, mạo danh...' },
    { key: 'OTHER', label: 'Khác', desc: 'Vấn đề khác cần báo cáo' },
  ],
  SYSTEM: [
    { key: 'ISSUE', label: 'Báo lỗi hệ thống', desc: 'Lỗi ứng dụng, giật lag, không tải được...' },
    { key: 'FEEDBACK', label: 'Góp ý / Đóng góp', desc: 'Góp ý tính năng, cải thiện nền tảng...' },
  ]
};

export default function ReportPage() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const refType = searchParams.get('type') || 'ORDER';
  
  const availableTypes = ALL_REPORT_TYPES[refType as keyof typeof ALL_REPORT_TYPES] || ALL_REPORT_TYPES.SYSTEM;
  
  const navigate = useNavigate();
  const [reportType, setReportType] = useState('');
  const [reason, setReason] = useState('');
  const [images, setImages] = useState<{ file: File, preview: string }[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files) return;
    const newImages = Array.from(files).map(file => ({
      file,
      preview: URL.createObjectURL(file)
    }));
    setImages(prev => [...prev, ...newImages]);
  };

  const removeImage = (index: number) => {
    setImages(prev => {
      URL.revokeObjectURL(prev[index].preview);
      return prev.filter((_, i) => i !== index);
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reportType) {
      setError('Vui lòng chọn loại báo cáo');
      return;
    }
    if (!reason.trim()) {
      setError('Vui lòng nhập nội dung báo cáo');
      return;
    }
    setError('');
    setIsLoading(true);
    try {
      let finalEvidenceUrl = null;
      if (images.length > 0) {
        const formData = new FormData();
        formData.append('file', images[0].file);
        const uploadRes = await apiFetch<{ url: string }>('/media/upload', {
          method: 'POST',
          body: formData,
        });
        finalEvidenceUrl = uploadRes.url;
      }

      const selectedType = availableTypes.find(r => r.key === reportType);
      await apiFetch('/reports', {
        method: 'POST',
        body: JSON.stringify({
          referenceId: Number(id),
          referenceType: refType,
          reportType,
          title: `Báo cáo ${refType === 'ORDER' ? 'đơn hàng' : refType === 'USER' ? 'người dùng' : 'bài đăng'} #${id} - ${selectedType?.label || 'Khác'}`,
          content: reason.trim(),
          evidenceUrl: finalEvidenceUrl,
        }),
      });
      setSuccess(true);
    } catch (err: any) {
      setError(err.message || 'Gửi báo cáo thất bại');
    } finally {
      setIsLoading(false);
    }
  };

  if (success) {
    return (
      <div className="p-4 md:p-6 max-w-lg mx-auto">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
          className="bg-white rounded-2xl border border-gray-100 p-8 text-center"
        >
          <div className="w-20 h-20 rounded-full bg-[#2db84c]/10 flex items-center justify-center mx-auto mb-4">
            <CheckCircle size={32} className="text-[#2db84c]" />
          </div>
          <h2 className="text-xl font-bold text-gray-900 mb-2">Đã gửi báo cáo</h2>
          <p className="text-sm text-gray-500 mb-6">
            Báo cáo của bạn đã được ghi nhận. Đội ngũ quản trị sẽ xem xét và phản hồi sớm nhất.
          </p>
          <button
            onClick={() => navigate(-1)}
            className="w-full py-3 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] transition-all shadow-md shadow-green-500/20"
          >
            Quay lại
          </button>
        </motion.div>
      </div>
    );
  }

  return (
    <div className="p-4 md:p-6 max-w-lg mx-auto">
      <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-sm text-gray-500 hover:text-gray-700 mb-4 cursor-pointer">
        <ArrowLeft size={16} /> Quay lại
      </button>

      <motion.div
        initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
        className="bg-white rounded-2xl border border-gray-100 p-6"
      >
        <h1 className="text-xl font-bold text-gray-900 mb-1">
          {refType === 'SYSTEM' ? 'Báo lỗi / Góp ý' : 'Báo cáo / Khiếu nại'}
        </h1>
        <p className="text-sm text-gray-500 mb-6">Cho chúng tôi biết vấn đề bạn gặp phải</p>

        {error && (
          <div className="p-3 bg-red-50 text-red-600 rounded-xl text-sm border border-red-100 mb-4">{error}</div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          {/* Report Type */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Phân loại <span className="text-red-500">*</span></label>
            <div className="flex flex-col gap-2">
              {availableTypes.map(rt => (
                <button
                  key={rt.key}
                  type="button"
                  onClick={() => setReportType(rt.key)}
                  className={`p-3 rounded-xl border-2 text-left cursor-pointer transition-all ${
                    reportType === rt.key
                      ? 'border-[#2db84c] bg-[#2db84c]/5'
                      : 'border-gray-200 hover:border-gray-300'
                  }`}
                >
                  <p className="text-sm font-medium text-gray-900">{rt.label}</p>
                  <p className="text-xs text-gray-500">{rt.desc}</p>
                </button>
              ))}
            </div>
          </div>

          {/* Reason */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Chi tiết <span className="text-red-500">*</span></label>
            <textarea
              value={reason}
              onChange={e => setReason(e.target.value)}
              rows={4}
              placeholder="Mô tả chi tiết vấn đề bạn gặp phải..."
              className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all resize-none"
            />
          </div>

          {/* Evidence Images */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Bằng chứng (ảnh)</label>
            <div className="flex flex-wrap gap-3">
              {images.map((img, i) => (
                <div key={i} className="relative w-20 h-20 rounded-xl overflow-hidden border border-gray-200">
                  <img src={img.preview} alt="" className="w-full h-full object-cover" />
                  <button
                    type="button"
                    onClick={() => removeImage(i)}
                    className="absolute top-1 right-1 w-5 h-5 bg-red-500 text-white rounded-full flex items-center justify-center cursor-pointer"
                  >
                    <X size={12} />
                  </button>
                </div>
              ))}
              <label className="w-20 h-20 rounded-xl border-2 border-dashed border-gray-300 flex flex-col items-center justify-center cursor-pointer hover:border-[#2db84c] hover:bg-green-50/50 transition-colors">
                <ImagePlus size={20} className="text-gray-400" />
                <span className="text-[10px] text-gray-400 mt-1">Thêm ảnh</span>
                <input type="file" accept="image/*" multiple onChange={handleImageUpload} className="hidden" />
              </label>
            </div>
          </div>

          {/* Submit */}
          <button
            type="submit"
            disabled={isLoading}
            className="w-full py-3.5 rounded-xl bg-red-500 text-white font-semibold text-sm cursor-pointer hover:bg-red-600 active:scale-[0.98] transition-all shadow-md shadow-red-500/20 disabled:opacity-70 flex items-center justify-center gap-2"
          >
            {isLoading ? <Loader2 size={16} className="animate-spin" /> : <Flag size={16} />}
            {isLoading ? 'Đang gửi...' : 'Gửi báo cáo'}
          </button>
        </form>
      </motion.div>
    </div>
  );
}
