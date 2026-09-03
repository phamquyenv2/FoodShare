import { ORDER_STATUS_MAP, POST_STATUS_MAP } from '../../constants';
import type { OrderStatus, PostStatus } from '../../types';

export function OrderBadge({ status }: { status: OrderStatus }) {
  const c = ORDER_STATUS_MAP[status];
  return (
    <span
      className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold whitespace-nowrap"
      style={{ color: c.color, backgroundColor: c.bg }}
    >
      {c.label}
    </span>
  );
}

export function PostBadge({ status }: { status: PostStatus }) {
  const c = POST_STATUS_MAP[status];
  return (
    <span
      className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold whitespace-nowrap"
      style={{ color: c.color, backgroundColor: c.bg }}
    >
      {c.label}
    </span>
  );
}
