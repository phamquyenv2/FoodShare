import { Outlet, useLocation } from 'react-router-dom';
import Logo from '../components/shared/Logo';

export default function AuthLayout() {
  const location = useLocation();
  const isCompleteProfile = location.pathname === '/auth/complete-profile';
  const usesFixedDesktopViewport = isCompleteProfile || location.pathname === '/auth/register';

  return (
    <div className={`bg-[#f5f7f5] flex flex-col items-center p-4 ${usesFixedDesktopViewport ? 'min-h-screen overflow-y-auto lg:h-screen lg:min-h-0 lg:overflow-hidden lg:py-3' : 'min-h-screen overflow-y-auto py-8'}`}>
      <div className={`my-auto flex flex-col items-center w-full ${isCompleteProfile ? 'max-w-5xl' : 'max-w-md'}`}>
        {!isCompleteProfile && (
          <div className="mb-5">
            <Logo size="lg" />
          </div>
        )}
        <div className={`w-full ${isCompleteProfile ? '' : 'pb-4'}`}>
          <Outlet />
        </div>
      </div>
    </div>
  );
}
