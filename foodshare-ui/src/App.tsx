import { RouterProvider } from 'react-router-dom';
import { useEffect } from 'react';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { setupIonicReact, IonApp } from '@ionic/react';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { ToastProvider, useToast } from './contexts/ToastContext';
import { listenForForegroundMessages } from './services/deviceService';
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

function PaymentRedirectInterceptor() {
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const isPaymentResult = window.location.pathname.startsWith('/payment/result')
      || window.location.pathname.startsWith('/payment/callback')
      || window.location.pathname.startsWith('/recipient/payment/result');

    if (isPaymentResult) return;

    const hasZalo = (params.get('apptransid') && params.get('status')) || (params.get('zptranstoken') && params.get('returncode'));
    const hasMomo = params.get('orderId') && params.get('resultCode');

    if (hasZalo || hasMomo) {
      window.location.replace(`/payment/result${window.location.search}`);
    }
  }, []);

  return null;
}

export default function App() {
  return (
    <IonApp>
      <GoogleOAuthProvider clientId={GOOGLE_CLIENT_ID}>
        <ToastProvider>
          <AuthProvider>
            <PushNotificationBridge />
            <PaymentRedirectInterceptor />
            <RouterProvider router={router} />
          </AuthProvider>
        </ToastProvider>
      </GoogleOAuthProvider>
    </IonApp>
  );
}
