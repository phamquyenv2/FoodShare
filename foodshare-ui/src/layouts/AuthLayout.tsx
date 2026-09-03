import { Outlet } from 'react-router-dom';
import Logo from '../components/shared/Logo';

export default function AuthLayout() {
  return (
    <div className="min-h-screen bg-[#f5f7f5] flex flex-col items-center justify-center p-4">
      <div className="mb-8">
        <Logo size="lg" />
      </div>
      <div className="w-full max-w-md">
        <Outlet />
      </div>
    </div>
  );
}
