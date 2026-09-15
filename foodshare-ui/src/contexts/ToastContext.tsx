import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import type { CSSProperties, ReactNode } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { AlertCircle, CheckCircle2, AlertTriangle, Info, X } from 'lucide-react';

export type ToastType = 'error' | 'success' | 'warning' | 'info';

interface ToastItem {
  id: string;
  message: string;
  type: ToastType;
  duration: number;
  title?: string;
}

interface ToastContextValue {
  showToast: (message: string, type?: ToastType, options?: { title?: string; duration?: number }) => void;
  showError: (message: string | string[] | any, title?: string) => void;
  showSuccess: (message: string, title?: string) => void;
  showWarning: (message: string, title?: string) => void;
  showInfo: (message: string, title?: string) => void;
}

const TOAST_CONFIG = {
  error: {
    icon: AlertCircle,
    iconColor: 'text-red-500',
    iconBg: 'bg-red-50',
    border: 'border-red-100',
    barColor: '#f43f5e',
    defaultTitle: 'Lỗi',
  },
  success: {
    icon: CheckCircle2,
    iconColor: 'text-emerald-600',
    iconBg: 'bg-emerald-50',
    border: 'border-emerald-100',
    barColor: '#2db84c',
    defaultTitle: 'Thành công',
  },
  warning: {
    icon: AlertTriangle,
    iconColor: 'text-amber-500',
    iconBg: 'bg-amber-50',
    border: 'border-amber-100',
    barColor: '#f59e0b',
    defaultTitle: 'Cảnh báo',
  },
  info: {
    icon: Info,
    iconColor: 'text-blue-500',
    iconBg: 'bg-blue-50',
    border: 'border-blue-100',
    barColor: '#3b82f6',
    defaultTitle: 'Thông tin',
  },
} as const;

/* Inject the keyframe once into the document head */
const KEYFRAME_ID = 'toast-shrink-kf';
if (typeof document !== 'undefined' && !document.getElementById(KEYFRAME_ID)) {
  const style = document.createElement('style');
  style.id = KEYFRAME_ID;
  style.textContent = `
    @keyframes toast-shrink {
      from { transform: scaleX(1); }
      to   { transform: scaleX(0); }
    }
  `;
  document.head.appendChild(style);
}

function ToastCard({ item, onClose }: { item: ToastItem; onClose: (id: string) => void }) {
  const [isPaused, setIsPaused] = useState(false);
  const barRef = useRef<HTMLDivElement>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const elapsed = useRef(0);
  const startedAt = useRef<number>(Date.now());

  const config = TOAST_CONFIG[item.type];
  const Icon = config.icon;

  /* Schedule auto-close that respects remaining time */
  const scheduleClose = useCallback((remaining: number) => {
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => onClose(item.id), remaining);
    startedAt.current = Date.now();
  }, [item.id, onClose]);

  useEffect(() => {
    scheduleClose(item.duration);
    return () => { if (timerRef.current) clearTimeout(timerRef.current); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleMouseEnter = () => {
    setIsPaused(true);
    elapsed.current += Date.now() - startedAt.current;
    if (timerRef.current) clearTimeout(timerRef.current);
  };

  const handleMouseLeave = () => {
    setIsPaused(false);
    const remaining = Math.max(0, item.duration - elapsed.current);
    scheduleClose(remaining);
  };

  /* CSS animation style for the progress bar */
  const barStyle: CSSProperties = {
    transformOrigin: 'left center',
    animation: `toast-shrink ${item.duration}ms linear forwards`,
    animationPlayState: isPaused ? 'paused' : 'running',
    background: config.barColor,
  };

  return (
    <motion.div
      layout
      role="alert"
      aria-live="assertive"
      initial={{ opacity: 0, x: 40, scale: 0.92 }}
      animate={{ opacity: 1, x: 0, scale: 1 }}
      exit={{ opacity: 0, x: 40, scale: 0.92 }}
      transition={{ type: 'spring', stiffness: 400, damping: 30 }}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      className={`relative overflow-hidden rounded-xl bg-white border ${config.border} shadow-md w-full`}
    >
      <div className="flex items-center gap-2 px-2.5 py-1.5">
        <div className={`w-5 h-5 rounded-md flex items-center justify-center shrink-0 ${config.iconBg}`}>
          <Icon size={12} className={`${config.iconColor} stroke-[2.4]`} />
        </div>

        <div className="flex-1 min-w-0">
          {item.title && (
            <p className="text-[10px] font-semibold text-gray-800 leading-none mb-0.5">{item.title}</p>
          )}
          <p className="text-[11px] text-gray-700 leading-snug break-words">{item.message}</p>
        </div>

        <button
          type="button"
          onClick={() => onClose(item.id)}
          className="shrink-0 p-1 rounded-md text-gray-300 hover:text-gray-600 hover:bg-gray-100 transition-colors"
          aria-label="Đóng"
        >
          <X size={11} />
        </button>
      </div>

      {/* Pure-CSS animated progress bar — GPU scaleX, zero JS per frame */}
      <div className="absolute bottom-0 left-0 right-0 h-[2px] bg-gray-100">
        <div ref={barRef} className="h-full w-full" style={barStyle} />
      </div>
    </motion.div>
  );
}

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const counterRef = useRef(0);
  const isAuthRoute = window.location.pathname.startsWith('/auth/');

  const dismiss = useCallback((id: string) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  }, []);

  const showToast = useCallback((
    message: string,
    type: ToastType = 'error',
    options?: { title?: string; duration?: number },
  ) => {
    const id: string = `toast-${++counterRef.current}-${Math.random()}`;
    const item: ToastItem = { id, message, type, duration: options?.duration ?? 4000, title: options?.title };
    setToasts(prev => [...prev.slice(-4), item]);
  }, []);

  const showError = useCallback((m: string | string[] | any, t?: string) => {
    if (!m) return;
    if (Array.isArray(m)) {
      m.filter(Boolean).forEach(msg => showToast(String(msg), 'error', { title: t }));
    } else if (typeof m === 'string' && m.includes('\n')) {
      m.split('\n').map(s => s.trim()).filter(Boolean).forEach(line => showToast(line, 'error', { title: t }));
    } else if (m instanceof Error && (m as any).messages && Array.isArray((m as any).messages)) {
      (m as any).messages.filter(Boolean).forEach((msg: string) => showToast(String(msg), 'error', { title: t }));
    } else {
      const msg = typeof m === 'string' ? m : m.message || String(m);
      if (typeof msg === 'string' && msg.includes('\n')) {
        msg.split('\n').map(s => s.trim()).filter(Boolean).forEach(line => showToast(line, 'error', { title: t }));
      } else {
        showToast(msg, 'error', { title: t });
      }
    }
  }, [showToast]);

  const showSuccess = useCallback((m: string, t?: string) => showToast(m, 'success', { title: t }), [showToast]);
  const showWarning = useCallback((m: string, t?: string) => showToast(m, 'warning', { title: t }), [showToast]);
  const showInfo = useCallback((m: string, t?: string) => showToast(m, 'info', { title: t }), [showToast]);

  return (
    <ToastContext.Provider value={{ showToast, showError, showSuccess, showWarning, showInfo }}>
      {children}

      <div
        aria-label="Thông báo"
        className={`fixed right-3 sm:right-5 z-[9999] flex w-[min(310px,calc(100vw-1.5rem))] flex-col gap-2 pointer-events-none ${isAuthRoute ? 'top-3 sm:top-5' : 'top-[72px] md:top-17'}`}
      >
        <AnimatePresence mode="popLayout">
          {toasts.map(item => (
            <div key={item.id} className="pointer-events-auto">
              <ToastCard item={item} onClose={dismiss} />
            </div>
          ))}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within <ToastProvider>');
  return ctx;
}
