export const APP_NAME = 'FoodShare';
export const APP_TAGLINE = 'Chia sẻ thực phẩm – Kết nối yêu thương';

/** Primary green palette — inspired by Bách Hóa Xanh */
export const COLORS = {
  primary:     '#2db84c',
  primaryDark: '#1a8f38',
  primaryLight:'#e6f7eb',
  accent:      '#f59e0b', // amber for badges
  white:       '#ffffff',
  bg:          '#f5f7f5',
  textPrimary: '#1a1a1a',
  textSecondary:'#6b7280',
  border:      '#e5e7eb',
  danger:      '#ef4444',
  success:     '#22c55e',
};

export const ORDER_STATUS_MAP: Record<string, { label: string; color: string; bg: string }> = {
  PENDING:          { label: 'Chờ xác nhận',  color: '#d97706', bg: '#fef3c7' },
  ACCEPTED:         { label: 'Đã chấp nhận',  color: '#0891b2', bg: '#cffafe' },
  READY_FOR_PICKUP: { label: 'Sẵn sàng lấy',  color: '#7c3aed', bg: '#ede9fe' },
  DELIVERED:        { label: 'Đã giao',        color: '#2563eb', bg: '#dbeafe' },
  COMPLETED:        { label: 'Hoàn thành',     color: '#16a34a', bg: '#dcfce7' },
  CANCELLED:        { label: 'Đã huỷ',         color: '#dc2626', bg: '#fee2e2' },
  REJECTED:         { label: 'Từ chối',        color: '#dc2626', bg: '#fee2e2' },
};

export const POST_STATUS_MAP: Record<string, { label: string; color: string; bg: string }> = {
  AVAILABLE:       { label: 'Đang hiển thị', color: '#16a34a', bg: '#dcfce7' },
  HIDDEN:       { label: 'Đã ẩn',         color: '#6b7280', bg: '#f3f4f6' },
  OUT_OF_STOCK: { label: 'Hết hàng',      color: '#d97706', bg: '#fef3c7' },
  EXPIRED:      { label: 'Hết hạn',       color: '#dc2626', bg: '#fee2e2' },
};

export const ROLE_MAP: Record<string, string> = {
  SUPPLIER:     'Nhà cung cấp',
  RECIPIENT:    'Người nhận',
  ORGANIZATION: 'Tổ chức',
  ADMIN:        'Quản trị viên',
};
