import { RouterProvider } from 'react-router-dom';
import { useEffect } from 'react';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { setupIonicReact, IonApp } from '@ionic/react';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { ToastProvider, useToast } from './contexts/ToastContext';
import { listenForForegroundMessages } from './services/deviceService';
import { apiFetch } from './services/api';
import { router } from './routes';

import '@ionic/react/css/core.css';

setupIonicReact({
  mode: 'md',
});

const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID || '1047185566085-dummy.apps.googleusercontent.com';

function PushNotificationBridge() {
  const { isAuthenticated } = useAuth();
  const { showInfo } = useToast();

  useEffect(() => {
    if (!isAuthenticated) return;
    let cleanup: () => void = () => undefined;
    void listenForForegroundMessages((payload) => {
      const notification = payload?.notification || {};
      showInfo(notification.body || 'Bạn có thông báo mới', notification.title || 'Thông báo mới');
      window.dispatchEvent(new Event('notifications:refresh'));
    }).then((unsubscribe) => { cleanup = unsubscribe; });
    return () => cleanup();
  }, [isAuthenticated, showInfo]);
  return null;
}

function MomoRedirectBridge() {
  const { isAuthenticated } = useAuth();
  const { showInfo } = useToast();
  useEffect(() => {
    if (!isAuthenticated) return;
    const params = new URLSearchParams(window.location.search);
    const orderId = params.get('orderId');
    const resultCode = params.get('resultCode');
    if (!orderId || !resultCode) return;
    const amountValue = params.get('amount');
    void apiFetch(`/payments/momo/result?orderId=${encodeURIComponent(orderId)}&resultCode=${encodeURIComponent(resultCode)}${amountValue ? `&amount=${amountValue}` : ''}`, { method: 'POST' })
      .then(() => showInfo('Thanh toán MoMo thành công'))
      .catch(() => showInfo('Thanh toán MoMo chưa thành công'))
      .finally(() => window.history.replaceState({}, document.title, window.location.pathname));
  }, [isAuthenticated, showInfo]);
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const transactionToken = params.get('zptranstoken');
    const returnCode = params.get('returncode');
    if (transactionToken && returnCode) {
      const returnMessage = params.get('returnmessage');
      void apiFetch(`/payments/zalopay/callback?zptranstoken=${encodeURIComponent(transactionToken)}&returncode=${encodeURIComponent(returnCode)}${returnMessage ? `&returnmessage=${encodeURIComponent(returnMessage)}` : ''}`, { method: 'GET' })
        .then(() => showInfo(
          returnCode === '1'
            ? 'Thanh toán ZaloPay thành công'
            : returnCode === '3'
              ? 'Thanh toán ZaloPay đang được xử lý'
              : 'Thanh toán ZaloPay chưa thành công',
        ))
        .catch(() => showInfo('Không thể xác nhận kết quả thanh toán ZaloPay'))
        .finally(() => window.history.replaceState({}, document.title, window.location.pathname));
      return;
    }
    if (!isAuthenticated) return;
    const appTransId = params.get('apptransid');
    const status = params.get('status');
    if (!appTransId || !status) return;
    const amount = params.get('amount');
    void apiFetch(`/payments/zalopay/result?apptransid=${encodeURIComponent(appTransId)}&status=${encodeURIComponent(status)}${amount ? `&amount=${amount}` : ''}`, { method: 'POST' })
      .then(() => showInfo('Thanh toán ZaloPay thành công'))
      .catch(() => showInfo('Thanh toán ZaloPay chưa thành công'))
      .finally(() => window.history.replaceState({}, document.title, window.location.pathname));
  }, [isAuthenticated, showInfo]);
  return null;
}

export default function App() {
  return (
    <IonApp>
      <GoogleOAuthProvider clientId={GOOGLE_CLIENT_ID}>
        <ToastProvider>
          <AuthProvider>
            <PushNotificationBridge />
            <MomoRedirectBridge />
            <RouterProvider router={router} />
          </AuthProvider>
        </ToastProvider>
      </GoogleOAuthProvider>
    </IonApp>
  );
}
