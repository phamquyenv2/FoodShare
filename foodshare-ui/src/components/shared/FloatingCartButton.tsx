import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { ShoppingCart } from 'lucide-react';
import { getCartCount } from '../../services/cartService';

export default function FloatingCartButton() {
  const navigate = useNavigate();
  const [count, setCount] = useState<number>(() => getCartCount());

  useEffect(() => {
    const handleUpdate = () => {
      setCount(getCartCount());
    };
    window.addEventListener('cart:updated', handleUpdate);
    window.addEventListener('storage', handleUpdate);
    return () => {
      window.removeEventListener('cart:updated', handleUpdate);
      window.removeEventListener('storage', handleUpdate);
    };
  }, []);

  if (count <= 0) return null;

  return (
    <AnimatePresence>
      <motion.button
        initial={{ scale: 0, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        exit={{ scale: 0, opacity: 0 }}
        onClick={() => navigate('/organization/cart')}
        className="fixed bottom-20 md:bottom-8 right-4 md:right-8 flex items-center gap-2.5 px-5 py-3.5 rounded-2xl bg-[#2db84c] text-white font-bold text-sm cursor-pointer shadow-xl shadow-green-600/30 hover:bg-[#259e40] active:scale-95 transition-all z-40"
      >
        <div className="relative">
          <ShoppingCart size={20} />
          <span className="absolute -top-1.5 -right-2 min-w-[16px] h-4 px-1 bg-red-500 rounded-full text-[10px] font-extrabold text-white flex items-center justify-center border border-white">
            {count > 99 ? '99+' : count}
          </span>
        </div>
        <span>Giỏ hàng tổ chức ({count})</span>
      </motion.button>
    </AnimatePresence>
  );
}
