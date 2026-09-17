export interface CartPost {
  id: number;
  name: string;
  imageUrl?: string;
  unitPrice: number;
  postType: string;
  availableQuantity: number;
  supplierName?: string;
  pickupAddress?: string;
}

export interface CartItem {
  post: CartPost;
  quantity: number;
}

const CART_KEY = 'org_cart';

export function loadCart(): CartItem[] {
  try {
    const raw = localStorage.getItem(CART_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch (err) {
    console.error('Failed to load cart from localStorage', err);
    return [];
  }
}

export function saveCart(items: CartItem[]) {
  try {
    localStorage.setItem(CART_KEY, JSON.stringify(items));
    window.dispatchEvent(new CustomEvent('cart:updated', { detail: items }));
  } catch (err) {
    console.error('Failed to save cart to localStorage', err);
  }
}

export function getCartCount(): number {
  const items = loadCart();
  return items.reduce((sum, item) => sum + item.quantity, 0);
}

export function addToCart(
  post: {
    id: number;
    name: string;
    imageUrl?: string;
    unitPrice: number;
    postType: string;
    availableQuantity: number;
    supplier?: { name?: string };
    supplierName?: string;
    pickupAddress?: string;
  },
  quantity: number = 1
): CartItem[] {
  const cart = loadCart();
  const maxAvailable = post.availableQuantity || 1;
  const safeQty = Math.max(1, Math.min(quantity, maxAvailable));

  const supplierName = post.supplier?.name || post.supplierName || 'Chưa rõ nhà cung cấp';
  const pickupAddress = post.pickupAddress || '';

  const normalizedPost: CartPost = {
    id: post.id,
    name: post.name,
    imageUrl: post.imageUrl || '',
    unitPrice: post.unitPrice || 0,
    postType: post.postType || 'FREE',
    availableQuantity: post.availableQuantity,
    supplierName,
    pickupAddress,
  };

  const existingIndex = cart.findIndex(c => c.post.id === post.id);
  let nextCart: CartItem[];

  if (existingIndex >= 0) {
    nextCart = cart.map((c, i) => {
      if (i !== existingIndex) return c;
      const newQty = Math.min(c.quantity + safeQty, maxAvailable);
      return {
        ...c,
        post: { ...c.post, ...normalizedPost },
        quantity: newQty,
      };
    });
  } else {
    nextCart = [...cart, { post: normalizedPost, quantity: safeQty }];
  }

  saveCart(nextCart);
  return nextCart;
}

export function updateCartQuantity(postId: number, quantity: number): CartItem[] {
  const cart = loadCart();
  const next = cart.map(c => {
    if (c.post.id !== postId) return c;
    const clamped = Math.max(1, Math.min(c.post.availableQuantity, quantity));
    return { ...c, quantity: clamped };
  });
  saveCart(next);
  return next;
}

export function removeFromCart(postId: number): CartItem[] {
  const cart = loadCart();
  const next = cart.filter(c => c.post.id !== postId);
  saveCart(next);
  return next;
}

export function clearCart(): void {
  saveCart([]);
}
