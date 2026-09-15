import { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { apiFetch } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';
import PostForm, { type PostFormData } from '../../components/supplier/PostForm';

function toLocalDatetime(iso: string) {
  if (!iso) return '';
  const d = new Date(iso);
  const localTime = new Date(d.getTime() - d.getTimezoneOffset() * 60_000);
  return localTime.toISOString().slice(0, 16);
}

export default function EditPostPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { showError } = useToast();
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const isSubmittingRef = useRef(false);
  const [postData, setPostData] = useState<{
    formData: Partial<PostFormData>;
    images: string[];
    postStatus: string;
  } | null>(null);

  useEffect(() => {
    const fetchPost = async () => {
      try {
        const post = await apiFetch<any>(`/food-posts/${id}/owner`);
        setPostData({
          formData: {
            name: post.name || '',
            description: post.description || '',
            categoryId: post.category?.id || 1,
            totalQuantity: post.totalQuantity || 1,
            postType: post.postType || 'FREE',
            unitPrice: post.unitPrice || 0,
            originalPrice: post.originalPrice || 0,
            pickupAddress: post.pickupAddress || '',
            pickupStartAt: toLocalDatetime(post.pickupStartAt),
            pickupEndAt: toLocalDatetime(post.pickupEndAt),
            expiresAt: toLocalDatetime(post.expiresAt),
          },
          images: Array.isArray(post.images) ? post.images : [],
          postStatus: post.postStatus || 'AVAILABLE',
        });
      } catch (err: any) {
        showError(err.message || 'Không thể tải thông tin bài đăng');
      } finally {
        setIsLoading(false);
      }
    };

    if (id) fetchPost();
  }, [id, showError]);

  const handleSubmit = async (data: PostFormData, isDraft: boolean) => {
    if (isSubmittingRef.current || isSubmitting) return;
    isSubmittingRef.current = true;
    setIsSubmitting(true);
    try {
      await apiFetch(`/food-posts/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({
          name: data.name,
          description: data.description,
          categoryId: data.categoryId,
          totalQuantity: data.totalQuantity,
          postType: data.postType,
          unitPrice: data.unitPrice,
          originalPrice: data.originalPrice,
          pickupAddress: data.pickupAddress,
          pickupStartAt: new Date(data.pickupStartAt).toISOString(),
          pickupEndAt: new Date(data.pickupEndAt).toISOString(),
          expiresAt: new Date(data.expiresAt).toISOString(),
          images: data.images,
          isDraft,
        }),
      });
      navigate('/supplier/posts');
    } catch (err: any) {
      showError(err.message || 'Cập nhật bài đăng thất bại');
    } finally {
      isSubmittingRef.current = false;
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20 min-h-[calc(100vh-80px)]">
        <Loader2 size={24} className="animate-spin text-[#2db84c]" />
      </div>
    );
  }

  if (!postData) return null;

  return (
    <PostForm
      mode="edit"
      title="Chỉnh sửa bài đăng"
      subtitle="Cập nhật thông tin thực phẩm và thời gian nhận món"
      initialData={postData.formData}
      initialImages={postData.images}
      postStatus={postData.postStatus}
      isSubmitting={isSubmitting}
      onSubmit={handleSubmit}
    />
  );
}
