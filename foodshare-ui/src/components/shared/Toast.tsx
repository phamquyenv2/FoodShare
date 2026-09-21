import { useEffect, useState, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { AlertCircle, CheckCircle2, AlertTriangle, Info, X } from 'lucide-react';

export type ToastType = 'error' | 'success' | 'warning' | 'info';

export interface ToastProps {
  message: string;
  type?: ToastType;
  duration?: number;
  onClose: () => void;
  title?: string;
}

const TOAST_CONFIG = {
  error: {
    icon: AlertCircle,
    iconBg: 'bg-red-50 text-red-500 ring-4 ring-red-50/70',
    border: 'border-red-100',
    shadow: 'shadow-2xl shadow-red-500/10',
    barBg: 'bg-red-100/70',
    barFill: 'bg-gradient-to-r from-red-500 to-rose-400',
    defaultTitle: 'Thông báo lỗi',
  },
  success: {
    icon: CheckCircle2,
    iconBg: 'bg-emerald-50 text-emerald-600 ring-4 ring-emerald-50/70',
    border: 'border-emerald-100',
    shadow: 'shadow-2xl shadow-emerald-500/10',
    barBg: 'bg-emerald-100/70',
    barFill: 'bg-gradient-to-r from-[#2db84c] to-emerald-400',
    defaultTitle: 'Thành công',
  },
  warning: {
    icon: AlertTriangle,
    iconBg: 'bg-amber-50 text-amber-500 ring-4 ring-amber-50/70',
    border: 'border-amber-100',
    shadow: 'shadow-2xl shadow-amber-500/10',
    barBg: 'bg-amber-100/70',
    barFill: 'bg-gradient-to-r from-amber-500 to-orange-400',
    defaultTitle: 'Cảnh báo',
  },
  info: {
    icon: Info,
    iconBg: 'bg-blue-50 text-blue-500 ring-4 ring-blue-50/70',
    border: 'border-blue-100',
    shadow: 'shadow-2xl shadow-blue-500/10',
    barBg: 'bg-blue-100/70',
    barFill: 'bg-gradient-to-r from-blue-500 to-sky-400',
    defaultTitle: 'Thông tin',
  },
};

export default function Toast({
  message,
  type = 'error',
  duration = 4000,
  onClose,
  title,
}: ToastProps) {
  const [progress, setProgress] = useState(100);
  const [isPaused, setIsPaused] = useState(false);
  const timerRef = useRef<number | null>(null);
  const startTimeRef = useRef<number>(Date.now());
  const remainingTimeRef = useRef<number>(duration);

  const config = TOAST_CONFIG[type];
  const IconComponent = config.icon;
  const displayTitle = title ?? config.defaultTitle;

  useEffect(() => {
    setProgress(100);
    remainingTimeRef.current = duration;
    startTimeRef.current = Date.now();
  }, [message, duration]);

  useEffect(() => {
    if (!message) return;

    if (isPaused) {
      if (timerRef.current) cancelAnimationFrame(timerRef.current);
      return;
    }

    startTimeRef.current = Date.now();
    const initialRemaining = remainingTimeRef.current;

    const tick = () => {
      const elapsed = Date.now() - startTimeRef.current;
      const currentRemaining = Math.max(0, initialRemaining - elapsed);
      remainingTimeRef.current = currentRemaining;

      const newProgress = Math.max(0, (currentRemaining / duration) * 100);
      setProgress(newProgress);

      if (currentRemaining <= 0) {
        onClose();
      } else {
        timerRef.current = requestAnimationFrame(tick);
      }
    };

    timerRef.current = requestAnimationFrame(tick);

    return () => {
      if (timerRef.current) cancelAnimationFrame(timerRef.current);
    };
  }, [message, isPaused, duration, onClose]);

  return (
    <AnimatePresence>
      {message && (
        <motion.div
          role="alert"
          initial={{ opacity: 0, y: -24, scale: 0.96 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: -16, scale: 0.94 }}
          transition={{ type: 'spring', stiffness: 450, damping: 32 }}
          onMouseEnter={() => setIsPaused(true)}
          onMouseLeave={() => setIsPaused(false)}
          className={`fixed top-5 left-4 right-4 sm:left-auto sm:right-6 sm:w-full sm:max-w-md z-50 overflow-hidden rounded-2xl bg-white/95 backdrop-blur-md border ${config.border} ${config.shadow} transition-shadow`}
        >
          <div className="flex items-start gap-3.5 p-4">
            <div className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${config.iconBg}`}>
              <IconComponent size={18} className="stroke-[2.2]" />
            </div>

            <div className="flex-1 min-w-0 pt-0.5">
              <h4 className="text-xs font-semibold text-gray-900 tracking-tight mb-0.5">
                {displayTitle}
              </h4>
              <p className="text-sm text-gray-600 leading-snug break-words">
                {message}
              </p>
            </div>

            <button
              type="button"
              onClick={onClose}
              className="shrink-0 -mr-1 -mt-1 p-1.5 rounded-lg text-gray-400 hover:text-gray-700 hover:bg-gray-100 transition-colors"
              aria-label="Đóng thông báo"
            >
              <X size={16} />
            </button>
          </div>

          <div className={`h-1 w-full ${config.barBg} overflow-hidden`}>
            <div
              className={`h-full ${config.barFill} transition-[width] ease-linear duration-75`}
              style={{ width: `${progress}%` }}
            />
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
