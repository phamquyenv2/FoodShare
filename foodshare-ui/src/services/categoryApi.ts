import { apiFetch } from './api';

export interface Category {
  id: number;
  name: string;
}

export const DEFAULT_CATEGORIES: Category[] = [
  { id: 1, name: 'Cơm và món chính' },
  { id: 2, name: 'Rau củ và trái cây' },
  { id: 3, name: 'Bánh và đồ ăn nhẹ' },
  { id: 4, name: 'Đồ uống' },
  { id: 5, name: 'Thực phẩm khô' },
  { id: 6, name: 'Sữa và chế phẩm từ sữa' },
  { id: 7, name: 'Khác' },
];

let cachedCategories: Category[] | null = null;

export async function getCategories(): Promise<Category[]> {
  if (cachedCategories && cachedCategories.length > 0) {
    return cachedCategories;
  }

  try {
    const res = await apiFetch<Category[]>('/categories');
    if (Array.isArray(res) && res.length > 0) {
      cachedCategories = res;
      return res;
    }
  } catch (err) {
    console.error('Không thể tải danh mục từ máy chủ, dùng danh mục mặc định:', err);
  }

  return DEFAULT_CATEGORIES;
}
