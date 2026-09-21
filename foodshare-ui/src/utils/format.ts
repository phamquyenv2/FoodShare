export function formatVND(amount: number): string {
  if (amount === 0) return 'Miễn phí';
  return amount.toLocaleString('vi-VN') + '₫';
}

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

export function formatNumber(n: number): string {
  return n.toLocaleString('vi-VN');
}

export function formatDisplayName(fullName?: string | null, fallback = 'bạn'): string {
  if (!fullName) return fallback;
  const cleaned = fullName.replace(/\s*\([^)]*\)/g, '').trim();
  return cleaned || fallback;
}

export function formatTimeRemaining(dateStr?: string | null): { text: string; isUrgent: boolean } {
  if (!dateStr) return { text: '', isUrgent: false };
  const diff = new Date(dateStr).getTime() - Date.now();
  if (diff <= 0) return { text: 'Hết hạn', isUrgent: true };
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return { text: `Còn ${Math.max(1, mins)}p`, isUrgent: true };
  const hrs = Math.floor(mins / 60);
  const remainingMins = mins % 60;
  if (hrs < 6) {
    return { 
      text: remainingMins > 0 ? `Còn ${hrs}h${remainingMins}p` : `Còn ${hrs}h`, 
      isUrgent: true 
    };
  }
  if (hrs < 24) return { text: `Còn ${hrs}h`, isUrgent: false };
  const days = Math.floor(hrs / 24);
  return { text: `Còn ${days} ngày`, isUrgent: false };
}

