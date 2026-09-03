import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft, Star, Loader2, CheckCircle } from 'lucide-react';
import { apiFetch } from '../../services/api';

export default function WriteReviewPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [rating, setRating] = useState(0);
  const [hoverRating, setHoverRating] = useState(0);
  const [comment, setComment] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (rating === 0) {
      setError('Vui lòng chọn số sao đánh giá');
      return;
    }
    setError('');
    setIsLoading(true);
    try {
      await apiFetch('/reviews', {
        method: 'POST',
        body: JSON.stringify({
          orderId: Number(id),
          rating,
          comment: comment.trim(),
        }),
      });
      setSuccess(true);
    } catch (err: any) {
      setError(err.message || 'Gửi đánh giá thất bại');
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
          <h2 className="text-xl font-bold text-gray-900 mb-2">Cảm ơn bạn! 🙏</h2>
          <p className="text-sm text-gray-500 mb-6">
            Đánh giá của bạn đã được gửi thành công.
          </p>
          <div className="flex gap-0.5 justify-center mb-6">
            {[1, 2, 3, 4, 5].map(s => (
              <Star key={s} size={24} className={s <= rating ? 'text-amber-400 fill-amber-400' : 'text-gray-300'} />
            ))}
          </div>
          <button
            onClick={() => navigate('/recipient/orders')}
            className="w-full py-3 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] transition-all shadow-md shadow-green-500/20"
          >
            Quay lại đơn hàng
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
        <h1 className="text-xl font-bold text-gray-900 mb-1">Đánh giá nhà cung cấp</h1>
        <p className="text-sm text-gray-500 mb-6">Chia sẻ trải nghiệm của bạn để giúp cộng đồng tốt hơn</p>

        {error && (
          <div className="p-3 bg-red-50 text-red-600 rounded-xl text-sm border border-red-100 mb-4">{error}</div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          {/* Star Rating */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-3">Đánh giá <span className="text-red-500">*</span></label>
            <div className="flex gap-2 justify-center">
              {[1, 2, 3, 4, 5].map(s => (
                <button
                  key={s}
                  type="button"
                  onClick={() => setRating(s)}
                  onMouseEnter={() => setHoverRating(s)}
                  onMouseLeave={() => setHoverRating(0)}
                  className="cursor-pointer transition-transform hover:scale-110 active:scale-95"
                >
                  <Star
                    size={36}
                    className={`transition-colors ${
                      s <= (hoverRating || rating)
                        ? 'text-amber-400 fill-amber-400'
                        : 'text-gray-200'
                    }`}
                  />
                </button>
              ))}
            </div>
            <p className="text-center text-sm text-gray-500 mt-2">
              {rating === 0 && 'Chọn số sao'}
              {rating === 1 && 'Rất tệ 😞'}
              {rating === 2 && 'Tệ 😕'}
              {rating === 3 && 'Bình thường 😐'}
              {rating === 4 && 'Tốt 😊'}
              {rating === 5 && 'Tuyệt vời! 🤩'}
            </p>
          </div>

          {/* Comment */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Nhận xét</label>
            <textarea
              value={comment}
              onChange={e => setComment(e.target.value)}
              rows={4}
              placeholder="Chia sẻ cảm nhận của bạn về chất lượng món ăn, thái độ phục vụ..."
              className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all resize-none"
            />
          </div>

          {/* Submit */}
          <button
            type="submit"
            disabled={isLoading || rating === 0}
            className="w-full py-3.5 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 disabled:opacity-70 flex items-center justify-center gap-2"
          >
            {isLoading ? <Loader2 size={16} className="animate-spin" /> : <Star size={16} />}
            {isLoading ? 'Đang gửi...' : 'Gửi đánh giá'}
          </button>
        </form>
      </motion.div>
    </div>
  );
}
