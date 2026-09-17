import { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiFetch } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';
import PostForm, { type PostFormData } from '../../components/supplier/PostForm';

export default function CreatePostPage() {
  const navigate = useNavigate();
  const { showError } = useToast();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const isSubmittingRef = useRef(false);

  const handleSubmit = async (data: PostFormData, isDraft: boolean) => {
    if (isSubmittingRef.current || isSubmitting) return;
    isSubmittingRef.current = true;
    setIsSubmitting(true);
    try {
      await apiFetch('/food-posts', {
        method: 'POST',
        body: JSON.stringify({
          name: data.name,
          description: data.description,
          categoryId: data.categoryId,
          totalQuantity: data.totalQuantity,
          postType: data.postType,
          unitPrice: data.unitPrice,
          originalPrice: data.originalPrice,
          pickupAddress: data.pickupAddress,
          pickupLatitude: data.pickupLatitude,
          pickupLongitude: data.pickupLongitude,
          pickupStartAt: new Date(data.pickupStartAt).toISOString(),
          pickupEndAt: new Date(data.pickupEndAt).toISOString(),
          expiresAt: new Date(data.expiresAt).toISOString(),
          images: data.images,
          isDraft,
        }),
      });
      navigate('/supplier/posts');
    } catch (err: any) {
      showError(err.message || 'Tạo bài đăng thất bại');
    } finally {
      isSubmittingRef.current = false;
      setIsSubmitting(false);
    }
  };

  return (
    <PostForm
      mode="create"
      title="Tạo bài đăng mới"
      subtitle="Chia sẻ thực phẩm an toàn đến người cần"
      isSubmitting={isSubmitting}
      onSubmit={handleSubmit}
    />
  );
}
