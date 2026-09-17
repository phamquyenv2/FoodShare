import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowLeft,
  ImagePlus,
  Loader2,
  X,
  MapPin,
  Calendar,
  Clock,
  Info,
  Store,
  Check
} from 'lucide-react';
import { apiFetch } from '../../services/api';
import { useAuth } from '../../contexts/AuthContext';
import { useToast } from '../../contexts/ToastContext';
import { getCategories, DEFAULT_CATEGORIES, type Category } from '../../services/categoryApi';
import { autocompleteAddress, type LocationSuggestion } from '../../services/locationApi';

export interface PostFormData {
  name: string;
  description: string;
  categoryId: number;
  totalQuantity: number;
  postType: 'FREE' | 'PAID';
  unitPrice: number;
  originalPrice: number;
  pickupAddress: string;
  pickupLatitude?: number;
  pickupLongitude?: number;
  pickupStartAt: string;
  pickupEndAt: string;
  expiresAt: string;
  images: string[];
}

export interface PostFormProps {
  mode: 'create' | 'edit';
  title?: string;
  subtitle?: string;
  initialData?: Partial<PostFormData>;
  initialImages?: string[];
  postStatus?: string;
  isSubmitting: boolean;
  onSubmit: (data: PostFormData, isDraft: boolean) => Promise<void>;
}

export default function PostForm({
  mode,
  title = mode === 'create' ? 'Tạo bài đăng mới' : 'Chỉnh sửa bài đăng',
  subtitle = mode === 'create'
    ? 'Chia sẻ thực phẩm an toàn đến người cần'
    : 'Cập nhật thông tin thực phẩm và thời gian nhận món',
  initialData,
  initialImages = [],
  postStatus = 'AVAILABLE',
  isSubmitting,
  onSubmit,
}: PostFormProps) {
  const { user } = useAuth();
  const storeAddress = user?.specificAddress || '';
  const { showError } = useToast();
  const navigate = useNavigate();

  const [isDragging, setIsDragging] = useState(false);
  const [images, setImages] = useState<{ file?: File; preview: string }[]>(() =>
    initialImages.map(url => ({ preview: url }))
  );
  const [categories, setCategories] = useState<Category[]>(DEFAULT_CATEGORIES);

  const [form, setForm] = useState({
    name: initialData?.name || '',
    description: initialData?.description || '',
    categoryId: initialData?.categoryId || 1,
    totalQuantity: initialData?.totalQuantity || 1,
    postType: (initialData?.postType || 'FREE') as 'FREE' | 'PAID',
    unitPrice: initialData?.unitPrice || 0,
    originalPrice: initialData?.originalPrice || 0,
    pickupAddress: initialData?.pickupAddress || (mode === 'create' ? storeAddress : ''),
    pickupLatitude: initialData?.pickupLatitude,
    pickupLongitude: initialData?.pickupLongitude,
    pickupStartAt: initialData?.pickupStartAt || '',
    pickupEndAt: initialData?.pickupEndAt || '',
    expiresAt: initialData?.expiresAt || '',
  });

  const update = (key: string, value: any) => setForm(p => ({ ...p, [key]: value }));

  const [locationSuggestions, setLocationSuggestions] = useState<LocationSuggestion[]>([]);
  const [isSearchingLocation, setIsSearchingLocation] = useState(false);
  const [isLocationDropdownOpen, setIsLocationDropdownOpen] = useState(false);
  const locationDropdownRef = useRef<HTMLDivElement>(null);
  const selectedAddressRef = useRef(initialData?.pickupAddress || storeAddress || '');
  const [isProcessing, setIsProcessing] = useState(false);
  const isSubmittingRef = useRef(false);
  const isBusy = isSubmitting || isProcessing;

  // Lấy danh mục từ Database
  useEffect(() => {
    getCategories().then(list => {
      if (list && list.length > 0) {
        setCategories(list);
        if (!initialData?.categoryId) {
          setForm(prev => prev.categoryId ? prev : { ...prev, categoryId: list[0].id });
        }
      }
    });
  }, [initialData?.categoryId]);

  // Ở chế độ tạo mới: tự động điền địa chỉ quán nếu trường địa chỉ còn trống
  useEffect(() => {
    if (mode === 'create' && storeAddress && !form.pickupAddress) {
      selectedAddressRef.current = storeAddress;
      setForm(prev => prev.pickupAddress ? prev : { ...prev, pickupAddress: storeAddress });
    }
  }, [mode, storeAddress]);

  // Tìm kiếm địa chỉ tự động qua Geoapify khi người dùng nhập
  useEffect(() => {
    const query = form.pickupAddress.trim();
    if (query.length < 3 || query === selectedAddressRef.current) {
      setLocationSuggestions([]);
      setIsSearchingLocation(false);
      return;
    }

    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setIsSearchingLocation(true);
      try {
        const results = await autocompleteAddress(query, controller.signal);
        setLocationSuggestions(results || []);
        setIsLocationDropdownOpen(true);
      } catch (err: any) {
        if (err?.name !== 'AbortError') {
          setLocationSuggestions([]);
        }
      } finally {
        if (!controller.signal.aborted) {
          setIsSearchingLocation(false);
        }
      }
    }, 350);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [form.pickupAddress]);

  // Đóng dropdown khi click ra ngoài
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (locationDropdownRef.current && !locationDropdownRef.current.contains(e.target as Node)) {
        setIsLocationDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleFiles = (files: FileList | File[]) => {
    const fileArray = Array.from(files).filter(f => f.type.startsWith('image/'));
    if (!fileArray.length) return;

    const remainingSlots = 5 - images.length;
    if (remainingSlots <= 0) {
      showError('Bạn chỉ có thể tải lên tối đa 5 hình ảnh');
      return;
    }

    const added = fileArray.slice(0, remainingSlots).map(file => ({
      file,
      preview: URL.createObjectURL(file),
    }));
    setImages(prev => [...prev, ...added]);
  };

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      handleFiles(e.target.files);
      e.target.value = '';
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files) {
      handleFiles(e.dataTransfer.files);
    }
  };

  const removeImage = (index: number) => {
    setImages(prev => {
      const target = prev[index];
      if (target?.file) {
        URL.revokeObjectURL(target.preview);
      }
      return prev.filter((_, i) => i !== index);
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isBusy || isSubmittingRef.current) return;

    if (!form.name.trim()) {
      showError('Vui lòng nhập tên món ăn');
      return;
    }
    if (!form.pickupAddress.trim()) {
      showError('Vui lòng chọn hoặc nhập địa điểm nhận món');
      return;
    }
    if (!form.pickupStartAt || !form.pickupEndAt || !form.expiresAt) {
      showError('Vui lòng chọn đầy đủ thời gian nhận và hạn sử dụng');
      return;
    }
    if (new Date(form.pickupEndAt) <= new Date(form.pickupStartAt)) {
      showError('Thời gian kết thúc nhận phải sau thời gian bắt đầu nhận');
      return;
    }
    if (new Date(form.expiresAt) <= new Date(form.pickupEndAt)) {
      showError('Hạn sử dụng món ăn phải sau thời gian kết thúc nhận');
      return;
    }
    if (images.length === 0) {
      showError('Vui lòng tải lên ít nhất 1 hình ảnh món ăn');
      return;
    }

    isSubmittingRef.current = true;
    setIsProcessing(true);

    try {
      const uploadedUrls: string[] = [];
      for (const img of images) {
        if (img.file) {
          const formData = new FormData();
          formData.append('file', img.file);
          const uploadRes = await apiFetch<{ url: string }>('/media/upload', {
            method: 'POST',
            body: formData,
          });
          uploadedUrls.push(uploadRes.url);
        } else {
          uploadedUrls.push(img.preview);
        }
      }

      const isDraftSubmit = (e.nativeEvent as any).submitter?.name === 'draft';
      const isDraft = (mode === 'create' || postStatus === 'DRAFT') ? isDraftSubmit : false;

      await onSubmit({
        name: form.name.trim(),
        description: form.description.trim(),
        categoryId: form.categoryId,
        totalQuantity: form.totalQuantity,
        postType: form.postType,
        unitPrice: form.postType === 'FREE' ? 0 : form.unitPrice,
        originalPrice: form.postType === 'FREE' ? 0 : form.originalPrice,
        pickupAddress: form.pickupAddress.trim(),
        pickupLatitude: form.pickupLatitude,
        pickupLongitude: form.pickupLongitude,
        pickupStartAt: form.pickupStartAt,
        pickupEndAt: form.pickupEndAt,
        expiresAt: form.expiresAt,
        images: uploadedUrls,
      }, isDraft);
    } catch (err: any) {
      showError(err.message || 'Có lỗi xảy ra khi lưu bài đăng');
    } finally {
      isSubmittingRef.current = false;
      setIsProcessing(false);
    }
  };

  return (
    <div className="p-3 md:p-6 max-w-5xl mx-auto flex flex-col justify-center min-h-[calc(100vh-80px)]">
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        className="bg-white rounded-3xl border border-gray-100 p-5 md:p-6 shadow-sm flex flex-col"
      >
        {/* HEADER: NÚT QUAY LẠI NẰM BÊN TRÁI TIÊU ĐỀ */}
        <div className="flex items-center gap-3 pb-3 mb-4 border-b border-gray-100">
          <button
            type="button"
            onClick={() => navigate(-1)}
            disabled={isBusy}
            className="w-8 h-8 rounded-xl border border-gray-200 hover:border-gray-300 bg-white hover:bg-gray-50 text-gray-600 flex items-center justify-center transition-all cursor-pointer shadow-xs shrink-0 disabled:opacity-50 disabled:cursor-not-allowed disabled:pointer-events-none"
            title="Quay lại"
          >
            <ArrowLeft size={16} />
          </button>
          <div>
            <h1 className="text-lg font-bold text-gray-900 leading-tight">{title}</h1>
            <p className="text-xs text-gray-400 mt-0.5">{subtitle}</p>
          </div>
        </div>

        {/* FORM CHÍNH */}
        <form noValidate onSubmit={handleSubmit} className="flex flex-col">
          <div className="flex flex-col md:flex-row gap-5 items-start">
            
            {/* CỘT TRÁI: HÌNH ẢNH & MÔ TẢ (ĐỒNG BỘ 2 CARD BO TRÒN GỌN GÀNG) */}
            <div className="w-full md:w-[270px] shrink-0 flex flex-col gap-3">
              {/* Card hình ảnh vuông */}
              <div className="rounded-2xl border border-gray-100 bg-gray-50/50 p-3 flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-semibold text-gray-700">
                    Hình ảnh món ăn
                  </label>
                  <span className="text-[11px] text-gray-400 font-medium">
                    {images.length}/5 ảnh
                  </span>
                </div>

                {images.length === 0 ? (
                  <label
                    onDragOver={e => { e.preventDefault(); setIsDragging(true); }}
                    onDragLeave={() => setIsDragging(false)}
                    onDrop={handleDrop}
                    className={`w-full aspect-square flex flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed transition-all cursor-pointer p-4 text-center ${
                      isDragging
                        ? 'border-[#2db84c] bg-green-50/60 scale-[1.01]'
                        : 'border-gray-200 bg-white hover:border-[#2db84c] hover:bg-green-50/20'
                    }`}
                  >
                    <div className="w-11 h-11 rounded-xl bg-green-50 text-[#2db84c] flex items-center justify-center shadow-xs">
                      <ImagePlus size={22} />
                    </div>
                    <div>
                      <p className="text-xs font-semibold text-gray-800">Thêm hình ảnh</p>
                      <p className="text-[10px] text-gray-400 mt-0.5">Kéo thả hoặc nhấn để tải lên</p>
                    </div>
                    <span className="text-[10px] text-gray-400 bg-gray-100 px-2 py-0.5 rounded-full">
                      Tối đa 5MB / ảnh
                    </span>
                    <input type="file" accept="image/*" multiple onChange={handleImageUpload} className="hidden" />
                  </label>
                ) : (
                  <div className="flex flex-col gap-2">
                    <div className="relative w-full aspect-square overflow-hidden rounded-xl border border-gray-200 bg-gray-100 shadow-xs">
                      <img src={images[0].preview} alt="Ảnh chính" className="h-full w-full object-cover" />
                      <div className="absolute top-1.5 left-1.5 rounded bg-black/60 px-1.5 py-0.5 text-[10px] font-medium text-white">
                        Ảnh bìa
                      </div>
                      <button
                        type="button"
                        disabled={isBusy}
                        onClick={() => removeImage(0)}
                        className="absolute top-1.5 right-1.5 w-6 h-6 rounded-full bg-white/90 text-gray-600 hover:text-red-500 hover:bg-white shadow flex items-center justify-center transition-all cursor-pointer disabled:opacity-50 disabled:pointer-events-none"
                        title="Xóa ảnh này"
                      >
                        <X size={12} />
                      </button>
                    </div>

                    <div className="grid grid-cols-4 gap-1.5">
                      {images.slice(1).map((img, i) => (
                        <div key={i + 1} className="relative aspect-square overflow-hidden rounded-lg border border-gray-200 bg-gray-100">
                          <img src={img.preview} alt={`Ảnh ${i + 2}`} className="h-full w-full object-cover" />
                          <button
                            type="button"
                            disabled={isBusy}
                            onClick={() => removeImage(i + 1)}
                            className="absolute top-0.5 right-0.5 w-4 h-4 rounded-full bg-black/60 text-white hover:bg-red-500 flex items-center justify-center transition-all cursor-pointer disabled:opacity-50 disabled:pointer-events-none"
                            title="Xóa ảnh"
                          >
                            <X size={9} />
                          </button>
                        </div>
                      ))}

                      {images.length < 5 && (
                        <label
                          onDragOver={e => { if (!isBusy) { e.preventDefault(); setIsDragging(true); } }}
                          onDragLeave={() => setIsDragging(false)}
                          onDrop={e => { if (!isBusy) handleDrop(e); }}
                          className={`aspect-square rounded-lg border-2 border-dashed border-gray-300 hover:border-[#2db84c] hover:bg-green-50/40 flex flex-col items-center justify-center cursor-pointer transition-all text-gray-400 hover:text-[#2db84c] ${isBusy ? 'pointer-events-none opacity-50' : ''}`}
                          title="Thêm ảnh khác"
                        >
                          <ImagePlus size={14} />
                          <span className="text-[9px] font-medium mt-0.5">Thêm</span>
                          <input type="file" accept="image/*" multiple disabled={isBusy} onChange={handleImageUpload} className="hidden" />
                        </label>
                      )}
                    </div>
                  </div>
                )}
              </div>

              {/* Card mô tả */}
              <div className="rounded-2xl border border-gray-100 bg-gray-50/50 p-3 flex flex-col gap-1.5">
                <label className="text-xs font-semibold text-gray-700">
                  Mô tả món ăn
                </label>
                <textarea
                  value={form.description}
                  onChange={e => update('description', e.target.value)}
                  placeholder="Mô tả chi tiết nguyên liệu, tình trạng bảo quản..."
                  rows={2}
                  className="w-full px-3 py-2 rounded-xl border border-gray-200 bg-white text-xs focus:outline-none focus:ring-2 focus:ring-[#2db84c]/20 focus:border-[#2db84c] transition-all resize-none"
                />
              </div>
            </div>

            {/* CỘT PHẢI: CHI TIẾT THỰC PHẨM & GIAO NHẬN */}
            <div className="flex-1 flex flex-col gap-3 min-w-0">
              
              {/* Dòng 1: Loại bài đăng & Giá (cùng 1 hàng ngang siêu gọn) */}
              <div className="flex items-center justify-between gap-3 p-2 rounded-2xl border border-gray-100 bg-gray-50/40 flex-wrap">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-semibold text-gray-700">Hình thức:</span>
                  <div className="flex p-0.5 bg-gray-200/60 rounded-lg">
                    <button
                      type="button"
                      onClick={() => update('postType', 'FREE')}
                      className={`px-3 py-1 rounded-md text-xs font-semibold transition-all cursor-pointer ${
                        form.postType === 'FREE'
                          ? 'bg-white text-[#2db84c] shadow-xs'
                          : 'text-gray-500 hover:text-gray-800'
                      }`}
                    >
                      Miễn phí
                    </button>
                    <button
                      type="button"
                      onClick={() => update('postType', 'PAID')}
                      className={`px-3 py-1 rounded-md text-xs font-semibold transition-all cursor-pointer ${
                        form.postType === 'PAID'
                          ? 'bg-white text-orange-600 shadow-xs'
                          : 'text-gray-500 hover:text-gray-800'
                      }`}
                    >
                      Có phí
                    </button>
                  </div>
                </div>

                {form.postType === 'PAID' && (
                  <div className="flex items-center gap-2">
                    <div className="flex items-center gap-1">
                      <span className="text-[11px] text-gray-500">Giá gốc:</span>
                      <input
                        type="number"
                        min={0}
                        value={form.originalPrice || ''}
                        onChange={e => update('originalPrice', Number(e.target.value))}
                        placeholder="35.000"
                        className="w-20 px-2 py-1 text-xs rounded-lg border border-gray-200 bg-white focus:outline-none focus:ring-1 focus:ring-[#2db84c]"
                      />
                    </div>
                    <div className="flex items-center gap-1">
                      <span className="text-[11px] font-semibold text-orange-600">Giá bán:</span>
                      <input
                        type="number"
                        min={0}
                        required
                        value={form.unitPrice || ''}
                        onChange={e => update('unitPrice', Number(e.target.value))}
                        placeholder="15.000"
                        className="w-20 px-2 py-1 text-xs rounded-lg border border-orange-300 bg-white focus:outline-none focus:ring-1 focus:ring-orange-500 font-semibold text-orange-600"
                      />
                    </div>
                  </div>
                )}
              </div>

              {/* Dòng 2: Tên món ăn */}
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  Tên món ăn <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  value={form.name}
                  onChange={e => update('name', e.target.value)}
                  placeholder="VD: Cơm gà sốt nấm, Bánh mì kẹp thịt..."
                  className="w-full px-3.5 py-2 rounded-xl border border-gray-200 text-xs focus:outline-none focus:ring-2 focus:ring-[#2db84c]/20 focus:border-[#2db84c] transition-all"
                />
              </div>

              {/* Dòng 3: Danh mục & Số lượng */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    Danh mục <span className="text-red-500">*</span>
                  </label>
                  <select
                    value={form.categoryId}
                    onChange={e => update('categoryId', Number(e.target.value))}
                    className="w-full px-3 py-2 rounded-xl border border-gray-200 text-xs focus:outline-none focus:ring-2 focus:ring-[#2db84c]/20 focus:border-[#2db84c] transition-all bg-white cursor-pointer"
                  >
                    {categories.map(c => (
                      <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    Số lượng suất <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="number"
                    min={1}
                    required
                    value={form.totalQuantity}
                    onChange={e => update('totalQuantity', Number(e.target.value))}
                    className="w-full px-3.5 py-2 rounded-xl border border-gray-200 text-xs focus:outline-none focus:ring-2 focus:ring-[#2db84c]/20 focus:border-[#2db84c] transition-all"
                  />
                </div>
              </div>

              {/* Dòng 4: Địa điểm nhận món (Chọn địa chỉ quán hoặc gợi ý Geoapify) */}
              <div className="relative" ref={locationDropdownRef}>
                <div className="flex items-center justify-between mb-1">
                  <label className="block text-xs font-semibold text-gray-700">
                    Địa điểm nhận món <span className="text-red-500">*</span>
                  </label>
                  {storeAddress ? (
                    <button
                      type="button"
                      disabled={isBusy}
                      onClick={() => {
                        selectedAddressRef.current = storeAddress;
                        update('pickupAddress', storeAddress);
                        setLocationSuggestions([]);
                        setIsLocationDropdownOpen(false);
                      }}
                      className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-lg text-[11px] transition-all cursor-pointer disabled:opacity-50 disabled:pointer-events-none ${
                        form.pickupAddress.trim() === storeAddress.trim()
                          ? 'bg-emerald-50 text-[#2db84c] border border-emerald-200/80 font-semibold'
                          : 'text-gray-500 hover:text-[#2db84c] hover:bg-emerald-50/50 border border-gray-100'
                      }`}
                      title={`Địa chỉ quán: ${storeAddress}`}
                    >
                      {form.pickupAddress.trim() === storeAddress.trim() ? (
                        <Check size={12} className="text-[#2db84c]" />
                      ) : (
                        <Store size={12} className="text-gray-400" />
                      )}
                      <span>
                        {form.pickupAddress.trim() === storeAddress.trim()
                          ? 'Đang dùng địa chỉ quán'
                          : 'Dùng địa chỉ của quán'}
                      </span>
                    </button>
                  ) : null}
                </div>
                <div className="relative">
                  <MapPin size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
                  <input
                    type="text"
                    required
                    value={form.pickupAddress}
                    onChange={e => {
                      update('pickupAddress', e.target.value);
                      update('pickupLatitude', undefined);
                      update('pickupLongitude', undefined);
                      setIsLocationDropdownOpen(true);
                    }}
                    onFocus={() => {
                      if (locationSuggestions.length > 0) setIsLocationDropdownOpen(true);
                    }}
                    placeholder="VD: 928 Lê Văn Lương, Xã Nhà Bè, TP. Hồ Chí Minh..."
                    className="w-full pl-9 pr-8 py-2 rounded-xl border border-gray-200 text-xs focus:outline-none focus:ring-2 focus:ring-[#2db84c]/20 focus:border-[#2db84c] transition-all"
                  />
                  {isSearchingLocation ? (
                    <div className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none">
                      <Loader2 size={13} className="animate-spin text-[#2db84c]" />
                    </div>
                  ) : form.pickupAddress ? (
                    <button
                      type="button"
                      onClick={() => {
                        update('pickupAddress', '');
                        selectedAddressRef.current = '';
                        setLocationSuggestions([]);
                        setIsLocationDropdownOpen(false);
                      }}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 p-0.5 cursor-pointer rounded-full hover:bg-gray-100 transition-colors"
                      title="Xóa địa chỉ"
                    >
                      <X size={12} />
                    </button>
                  ) : null}
                </div>

                {/* Dropdown gợi ý tìm kiếm bằng Geoapify */}
                {isLocationDropdownOpen && locationSuggestions.length > 0 && (
                  <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded-xl shadow-xl z-50 max-h-48 overflow-y-auto divide-y divide-gray-100">
                    <div className="px-3 py-1.5 bg-gray-50/90 text-[10px] font-semibold text-gray-500 uppercase tracking-wider flex items-center justify-between sticky top-0 backdrop-blur-xs">
                      <span>Gợi ý địa chỉ từ Geoapify</span>
                      <span className="text-gray-400 lowercase font-normal">{locationSuggestions.length} kết quả</span>
                    </div>
                    {locationSuggestions.map((item, idx) => (
                      <button
                        key={idx}
                        type="button"
                        onClick={() => {
                          selectedAddressRef.current = item.formattedAddress;
                          update('pickupAddress', item.formattedAddress);
                          update('pickupLatitude', item.latitude);
                          update('pickupLongitude', item.longitude);
                          setLocationSuggestions([]);
                          setIsLocationDropdownOpen(false);
                        }}
                        className="w-full text-left px-3 py-2 text-xs text-gray-700 hover:bg-emerald-50 hover:text-[#2db84c] transition-colors flex items-start gap-2 cursor-pointer group"
                      >
                        <MapPin size={13} className="shrink-0 mt-0.5 text-gray-400 group-hover:text-[#2db84c]" />
                        <span className="line-clamp-2 leading-relaxed">{item.formattedAddress}</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>

              {/* Dòng 5: Thời gian nhận */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    Bắt đầu nhận <span className="text-red-500">*</span>
                  </label>
                  <div className="relative">
                    <Calendar size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
                    <input
                      type="datetime-local"
                      required
                      value={form.pickupStartAt}
                      onChange={e => update('pickupStartAt', e.target.value)}
                      className="w-full pl-8 pr-2.5 py-1.5 rounded-xl border border-gray-200 text-xs focus:outline-none focus:ring-2 focus:ring-[#2db84c]/20 focus:border-[#2db84c] transition-all"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    Kết thúc nhận <span className="text-red-500">*</span>
                  </label>
                  <div className="relative">
                    <Clock size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
                    <input
                      type="datetime-local"
                      required
                      value={form.pickupEndAt}
                      onChange={e => update('pickupEndAt', e.target.value)}
                      className="w-full pl-8 pr-2.5 py-1.5 rounded-xl border border-gray-200 text-xs focus:outline-none focus:ring-2 focus:ring-[#2db84c]/20 focus:border-[#2db84c] transition-all"
                    />
                  </div>
                </div>
              </div>

              {/* Dòng 6: Hạn sử dụng món ăn (kèm dòng lưu ý cân xứng 2 cột) */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 items-center">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    Hạn sử dụng món ăn <span className="text-red-500">*</span>
                  </label>
                  <div className="relative">
                    <Clock size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
                    <input
                      type="datetime-local"
                      required
                      value={form.expiresAt}
                      onChange={e => update('expiresAt', e.target.value)}
                      className="w-full pl-8 pr-2.5 py-1.5 rounded-xl border border-gray-200 text-xs focus:outline-none focus:ring-2 focus:ring-[#2db84c]/20 focus:border-[#2db84c] transition-all"
                    />
                  </div>
                </div>
                <div className="sm:pt-4 flex items-center gap-1.5 text-[11px] text-gray-400">
                  <Info size={13} className="text-gray-400 shrink-0" />
                  <span>Bài đăng tự động đóng sau hạn này để đảm bảo an toàn thực phẩm.</span>
                </div>
              </div>

            </div>
          </div>

          {/* THANH NÚT BẤM DƯỚI ĐÁY GỌN GÀNG, CÂN ĐỐI */}
          <div className="pt-4 mt-5 border-t border-gray-100 flex items-center justify-between flex-wrap gap-3">
            <p className="text-[11px] text-gray-400">
              <span className="text-red-500 font-bold">*</span> Vui lòng điền đầy đủ các thông tin bắt buộc
            </p>

            <div className="flex items-center gap-2.5 ml-auto">
              {mode === 'create' || postStatus === 'DRAFT' ? (
                <>
                  <button
                    type="submit"
                    name="draft"
                    disabled={isBusy}
                    className="px-5 py-2.5 rounded-xl border border-gray-200 bg-white hover:bg-gray-50 text-gray-700 font-semibold text-xs cursor-pointer active:scale-[0.98] transition-all disabled:opacity-50 disabled:cursor-not-allowed disabled:pointer-events-none shadow-xs"
                  >
                    Lưu nháp
                  </button>
                  <button
                    type="submit"
                    name="publish"
                    disabled={isBusy}
                    className="px-6 py-2.5 rounded-xl bg-[#2db84c] hover:bg-[#259e40] text-white font-semibold text-xs cursor-pointer active:scale-[0.98] transition-all shadow-md shadow-green-500/20 disabled:opacity-50 disabled:cursor-not-allowed disabled:pointer-events-none flex items-center gap-1.5"
                  >
                    {isBusy ? <Loader2 size={14} className="animate-spin" /> : null}
                    <span>{isBusy ? 'Đang xử lý...' : 'Đăng bài ngay'}</span>
                  </button>
                </>
              ) : (
                <button
                  type="submit"
                  name="save"
                  disabled={isBusy}
                  className="px-6 py-2.5 rounded-xl bg-[#2db84c] hover:bg-[#259e40] text-white font-semibold text-xs cursor-pointer active:scale-[0.98] transition-all shadow-md shadow-green-500/20 disabled:opacity-50 disabled:cursor-not-allowed disabled:pointer-events-none flex items-center gap-1.5"
                >
                  {isBusy ? <Loader2 size={14} className="animate-spin" /> : null}
                  <span>{isBusy ? 'Đang lưu...' : 'Lưu thay đổi'}</span>
                </button>
              )}
            </div>
          </div>
        </form>
      </motion.div>
    </div>
  );
}
