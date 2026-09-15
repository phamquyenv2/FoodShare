import logoImg from '../../assets/logo.png';

interface LogoProps {
  size?: 'sm' | 'md' | 'lg' | 'xl';
  showText?: boolean;
  layout?: 'col' | 'row';
  className?: string;
}

const sizes = {
  sm: { img: 'w-7 h-auto', text: 'text-lg font-bold', colGap: 'gap-0.5', rowGap: 'gap-2' },
  md: { img: 'w-8 h-auto', text: 'text-xl font-bold', colGap: 'gap-1', rowGap: 'gap-2.5' },
  lg: { img: 'w-16 h-auto', text: 'text-3xl font-extrabold', colGap: 'gap-1.5', rowGap: 'gap-3' },
  xl: { img: 'w-24 h-auto', text: 'text-4xl font-extrabold', colGap: 'gap-2', rowGap: 'gap-4' },
};

export default function Logo({ size = 'md', showText = true, layout = 'col', className = '' }: LogoProps) {
  const s = sizes[size];
  const isCol = layout === 'col';
  const gap = isCol ? s.colGap : s.rowGap;

  return (
    <div className={`flex ${isCol ? 'flex-col items-center text-center' : 'items-center'} ${gap} ${className}`}>
      <img
        src="/logo.png"
        alt="FoodShare Logo"
        className={`${s.img} max-w-full object-contain flex-shrink-0 drop-shadow-sm`}
        onError={(e) => {
          if (e.currentTarget.src !== logoImg) {
            e.currentTarget.src = logoImg;
          }
        }}
      />
      {showText && (
        <span className={`${s.text} text-gray-900 tracking-tight leading-none`}>
          Food<span className="text-[#2db84c]">Share</span>
        </span>
      )}
    </div>
  );
}
