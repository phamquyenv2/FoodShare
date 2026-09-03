// ─── User ────────────────────────────────────────────────────────────────────
export type UserRole = 'SUPPLIER' | 'RECIPIENT' | 'ORGANIZATION' | 'ADMIN';

export interface User {
  id: string;
  email: string;
  fullName: string;
  phone: string;
  role: UserRole;
  avatar?: string;
  isActive: boolean;
  createdAt: string;
  orderCount?: number;
  profileCompleted?: boolean;
}

// ─── Food Post ───────────────────────────────────────────────────────────────
export type PostStatus = 'AVAILABLE' | 'HIDDEN' | 'OUT_OF_STOCK' | 'EXPIRED';
export type PostType = 'FREE' | 'PAID';

export interface FoodPost {
  id: string;
  name: string;
  description: string;
  imageUrl: string;
  category: string;
  totalQuantity: number;
  availableQuantity: number;
  unitPrice: number;
  postType: PostType;
  postStatus: PostStatus;
  pickupAddress: string;
  pickupStartAt: string;
  pickupEndAt: string;
  expiresAt: string;
  expiresInMin: number;
  distanceKm: number;
  matchScore: number;
  supplierName: string;
  supplierAvatar: string;
  reportCount: number;
}

// ─── Order ───────────────────────────────────────────────────────────────────
export type OrderStatus =
  | 'PENDING'
  | 'ACCEPTED'
  | 'READY_FOR_PICKUP'
  | 'DELIVERED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REJECTED';

export interface Order {
  id: string;
  orderCode: string;
  status: OrderStatus;
  totalAmount: number;
  createdAt: string;
  recipientName: string;
  recipientPhone: string;
  foodPostName: string;
  quantity: number;
  unitPrice: number;
  rejectionReason?: string;
}

// ─── Payout ──────────────────────────────────────────────────────────────────
export type PayoutStatus = 'PENDING' | 'COMPLETED';

export interface PayoutTransaction {
  id: string;
  orderId: string;
  orderCode: string;
  grossAmount: number;
  platformFee: number;
  netAmount: number;
  status: PayoutStatus;
  createdAt: string;
}

// ─── Admin Report ────────────────────────────────────────────────────────────
export type ReportStatus = 'PENDING' | 'REVIEWING' | 'RESOLVED';
export type ReportTargetType = 'FOODPOST' | 'USER' | 'ORDER';

export interface AdminReport {
  id: string;
  reporterName: string;
  targetName: string;
  targetType: ReportTargetType;
  reason: string;
  status: ReportStatus;
  createdAt: string;
  postStatus?: PostStatus;
  reportCount: number;
}

// ─── Navigation ──────────────────────────────────────────────────────────────
export interface NavItem {
  key: string;
  label: string;
  icon: React.ComponentType<{ size?: number; className?: string }>;
  path: string;
}
