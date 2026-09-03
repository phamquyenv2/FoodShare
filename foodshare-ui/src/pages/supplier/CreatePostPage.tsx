import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft, ImagePlus, Loader2, X } from 'lucide-react';
import { apiFetch } from '../../services/api';

const CATEGORIES = [
  { id: 1, name: 'Cơm' }, { id: 2, name: 'Phở / Bún' }, { id: 3, name: 'Bánh mì' },
  { id: 4, name: 'Đồ uống' }, { id: 5, name: 'Trái cây' }, { id: 6, name: 'Rau củ' },
  { id: 7, name: 'Đồ khô' }, { id: 8, name: 'Khác' },
];

export default function CreatePostPage() {
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [images, setImages] = useState<{ file: File, preview: string }[]>([]);
  const [form, setForm] = useState({
    name: '',
    description: '',
    categoryId: 1,
    totalQuantity: 1,
    postType: 'FREE' as 'FREE' | 'PAID',
    unitPrice: 0,
  originalPrice: 0,
    pickupAddress: '',
    pickupStartAt: '',
    pickupEndAt: '',
    expiresAt: '',
  });

  const update = (key: string, value: any) => setForm(p => ({ ...p, [key]: value }));

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
    setError('');
    setIsLoading(true);

    try {
      const uploadedUrls: string[] = [];
      for (const img of images) {
        const formData = new FormData();
        formData.append('file', img.file);
        const uploadRes = await apiFetch<{ url: string }>('/media/upload', {
          method: 'POST',
          body: formData,
        });
        uploadedUrls.push(uploadRes.url);
      }

      const body = {
        name: form.name,
        description: form.description,
        categoryId: form.categoryId,
        totalQuantity: form.totalQuantity,
        postType: form.postType,
        unitPrice: form.postType === 'FREE' ? 0 : form.unitPrice,
        originalPrice: form.postType === 'FREE' ? 0 : form.originalPrice,
        pickupAddress: form.pickupAddress,
        pickupStartAt: new Date(form.pickupStartAt).toISOString(),
        pickupEndAt: new Date(form.pickupEndAt).toISOString(),
        expiresAt: new Date(form.expiresAt).toISOString(),
        images: uploadedUrls,
        isDraft: (e.nativeEvent as any).submitter?.name === 'draft',
      };

      await apiFetch('/food-posts', {
        method: 'POST',
        body: JSON.stringify(body),
      });

      navigate('/supplier/posts');
    } catch (err: any) {
      setError(err.message || 'Tạo bài đăng thất bại');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="p-4 md:p-6 max-w-5xl mx-auto h-[calc(100vh-80px)] flex flex-col overflow-hidden">
      <div className="flex items-center justify-between mb-4 flex-shrink-0">
        <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-sm text-gray-500 hover:text-gray-700 cursor-pointer">
          <ArrowLeft size={16} /> Quay lại
        </button>
      </div>

      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="bg-white rounded-2xl border border-gray-100 p-5 md:p-6 flex-1 flex flex-col min-h-0 shadow-sm">
        <h1 className="text-xl font-bold text-gray-900 mb-4 flex-shrink-0">Tạo bài đăng mới</h1>
        
        {error && <div className="p-3 bg-red-50 text-red-600 rounded-xl text-sm border border-red-100 mb-4 flex-shrink-0">{error}</div>}

        <form onSubmit={handleSubmit} className="flex flex-col flex-1 min-h-0">
          
          <div className="flex-1 overflow-y-auto min-h-0 pr-1 md:pr-4 custom-scrollbar">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-5">
              
              {/* CỘT TRÁI: Thông tin cơ bản */}
              <div className="flex flex-col gap-5">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Hình ảnh</label>
                  <div className="flex flex-wrap gap-2">
                    {images.map((img, i) => (
                      <div key={i} className="relative w-16 h-16 rounded-xl overflow-hidden border border-gray-200">
                        <img src={img.preview} alt="" className="w-full h-full object-cover" />
                        <button type="button" onClick={() => removeImage(i)} className="absolute top-1 right-1 w-4 h-4 bg-red-500 text-white rounded-full flex items-center justify-center cursor-pointer"><X size={10} /></button>
                      </div>
                    ))}
                    <label className="w-16 h-16 rounded-xl border-2 border-dashed border-gray-300 flex flex-col items-center justify-center cursor-pointer hover:border-[#2db84c] hover:bg-green-50/50 transition-colors">
                      <ImagePlus size={16} className="text-gray-400" />
                      <span className="text-[9px] text-gray-400 mt-0.5">Thêm</span>
                      <input type="file" accept="image/*" multiple onChange={handleImageUpload} className="hidden" />
                    </label>
                  </div>
                </div>
                
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">Tên món ăn <span className="text-red-500">*</span></label>
                  <input type="text" required value={form.name} onChange={e => update('name', e.target.value)} placeholder="VD: Cơm gà xối mỡ"
                    className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all" />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">Mô tả</label>
                  <textarea value={form.description} onChange={e => update('description', e.target.value)} rows={3} placeholder="Mô tả chi tiết về món ăn..."
                    className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all resize-none" />
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1.5">Danh mục <span className="text-red-500">*</span></label>
                    <select value={form.categoryId} onChange={e => update('categoryId', Number(e.target.value))} className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all bg-white">
                      {CATEGORIES.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1.5">Số lượng <span className="text-red-500">*</span></label>
                    <input type="number" min={1} required value={form.totalQuantity} onChange={e => update('totalQuantity', Number(e.target.value))}
                      className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all" />
                  </div>
                </div>
              </div>

              {/* CỘT PHẢI: Bán hàng & Giao nhận */}
              <div className="flex flex-col gap-5">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Loại bài đăng <span className="text-red-500">*</span></label>
                  <div className="flex gap-3">
                    {['FREE', 'PAID'].map(type => (
                      <button key={type} type="button" onClick={() => update('postType', type)}
                        className={`flex-1 py-3 rounded-xl border-2 text-sm font-semibold transition-all cursor-pointer ${form.postType === type ? 'border-[#2db84c] bg-[#2db84c]/5 text-[#2db84c]' : 'border-gray-200 text-gray-500 hover:border-gray-300'}`}>
                        {type === 'FREE' ? 'Miễn phí' : 'Có phí'}
                      </button>
                    ))}
                  </div>
                </div>

                {form.postType === 'PAID' && (
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1.5">Giá gốc (VNĐ)</label>
                      <input type="number" min={0} value={form.originalPrice} onChange={e => update('originalPrice', Number(e.target.value))} placeholder="20000"
                        className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all" />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1.5">Giá bán (VNĐ) <span className="text-red-500">*</span></label>
                      <input type="number" min={0} required value={form.unitPrice} onChange={e => update('unitPrice', Number(e.target.value))} placeholder="10000"
                        className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all" />
                    </div>
                  </div>
                )}

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">Địa điểm nhận <span className="text-red-500">*</span></label>
                  <input type="text" required value={form.pickupAddress} onChange={e => update('pickupAddress', e.target.value)} placeholder="Số 1 Đại Cồ Việt..."
                    className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all" />
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1.5">Bắt đầu <span className="text-red-500">*</span></label>
                    <input type="datetime-local" required value={form.pickupStartAt} onChange={e => update('pickupStartAt', e.target.value)}
                      className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all" />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1.5">Kết thúc <span className="text-red-500">*</span></label>
                    <input type="datetime-local" required value={form.pickupEndAt} onChange={e => update('pickupEndAt', e.target.value)}
                      className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all" />
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">Hạn sử dụng <span className="text-red-500">*</span></label>
                  <input type="datetime-local" required value={form.expiresAt} onChange={e => update('expiresAt', e.target.value)}
                    className="w-full px-4 py-3 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all" />
                </div>
              </div>
            </div>
          </div>

          <div className="pt-4 mt-auto border-t border-gray-100 flex gap-3 flex-shrink-0">
            <button
              type="submit" name="draft" disabled={isLoading}
              className="flex-1 py-3 rounded-xl bg-gray-50 border border-gray-200 text-gray-700 font-semibold text-sm cursor-pointer hover:bg-gray-100 active:scale-[0.98] transition-all disabled:opacity-70 flex items-center justify-center gap-2"
            >
              Lưu nháp
            </button>
            <button
              type="submit" name="publish" disabled={isLoading}
              className="flex-[2] py-3 rounded-xl bg-[#2db84c] text-white font-semibold text-sm cursor-pointer hover:bg-[#259e40] active:scale-[0.98] transition-all shadow-md shadow-green-500/20 disabled:opacity-70 flex items-center justify-center gap-2"
            >
              {isLoading ? <Loader2 size={16} className="animate-spin" /> : null}
              {isLoading ? 'Đang xử lý...' : 'Đăng bài'}
            </button>
          </div>
        </form>
      </motion.div>
      <style dangerouslySetInnerHTML={{__html: `
        .custom-scrollbar::-webkit-scrollbar { width: 4px; }
        .custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
        .custom-scrollbar::-webkit-scrollbar-thumb { background: #E5E7EB; border-radius: 4px; }
        .custom-scrollbar:hover::-webkit-scrollbar-thumb { background: #D1D5DB; }
      `}} />
    </div>
  );
}
