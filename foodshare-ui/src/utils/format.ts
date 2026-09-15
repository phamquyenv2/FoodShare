/** Format number as Vietnamese currency: 45000 → "45.000₫" */
export function formatVND(amount: number): string {
  if (amount === 0) return 'Miễn phí';
  return amount.toLocaleString('vi-VN') + '₫';
}

/** Short relative date: "2 giờ trước", "Hôm qua" */
export function timeAgo(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'Vừa xong';
  if (mins < 60) return `${mins} phút trước`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs} giờ trước`;
  const days = Math.floor(hrs / 24);
  if (days === 1) return 'Hôm qua';
  return `${days} ngày trước`;
}

/** Compact number: 1284 → "1.284" */
export function formatNumber(n: number): string {
  return n.toLocaleString('vi-VN');
}
