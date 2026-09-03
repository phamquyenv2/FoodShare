import { Leaf } from 'lucide-react';

interface LogoProps {
  size?: 'sm' | 'md' | 'lg';
  showText?: boolean;
}

const sizes = {
  sm: { box: 'w-8 h-8', icon: 16, text: 'text-lg' },
  md: { box: 'w-10 h-10', icon: 20, text: 'text-xl' },
  lg: { box: 'w-14 h-14', icon: 28, text: 'text-3xl' },
};

export default function Logo({ size = 'md', showText = true }: LogoProps) {
  const s = sizes[size];
  return (
    <div className="flex items-center gap-2.5">
      <div className={`${s.box} rounded-xl bg-[#2db84c] flex items-center justify-center shadow-md shadow-green-500/20`}>
        <Leaf size={s.icon} className="text-white" />
      </div>
      {showText && (
        <span className={`${s.text} font-bold text-gray-900 tracking-tight`}>
          Food<span className="text-[#2db84c]">Share</span>
        </span>
      )}
    </div>
  );
}
