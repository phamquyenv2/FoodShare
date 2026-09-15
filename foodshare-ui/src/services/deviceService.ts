import { getToken, onMessage } from 'firebase/messaging';
import { getFirebaseMessaging, firebaseVapidKey } from '../config/firebase';
import { apiFetch } from './api';

const DEVICE_TOKEN_KEY = 'foodshare_fcm_token';

function deviceName() {
  if (typeof navigator === 'undefined') return 'Web browser';
  return `${navigator.userAgent.includes('Chrome') ? 'Chrome' : 'Web browser'} on ${navigator.platform || 'desktop'}`;
}

export async function registerBrowserDevice(): Promise<boolean> {
  if (!firebaseVapidKey || typeof Notification === 'undefined') return false;
  if (Notification.permission !== 'granted') {
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return false;
  }
  const messaging = await getFirebaseMessaging();
  if (!messaging) return false;
  const params = new URLSearchParams({
    apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
    authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
    projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
    storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
    messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
    appId: import.meta.env.VITE_FIREBASE_APP_ID,
  });
  const registration = await navigator.serviceWorker.register(`/firebase-messaging-sw.js?${params}`);
  const token = await getToken(messaging, { vapidKey: firebaseVapidKey, serviceWorkerRegistration: registration });
  if (!token) return false;
  await apiFetch('/devices', {
    method: 'POST',
    body: JSON.stringify({ fcmToken: token, deviceType: 'WEB', deviceName: deviceName() }),
  });
  localStorage.setItem(DEVICE_TOKEN_KEY, token);
  return true;
}

export async function unregisterBrowserDevice(): Promise<void> {
  try {
    await apiFetch('/devices/current', { method: 'DELETE' });
  } finally {
    localStorage.removeItem(DEVICE_TOKEN_KEY);
  }
}

export async function listenForForegroundMessages(handler: (payload: any) => void): Promise<() => void> {
  const messaging = await getFirebaseMessaging();
  if (!messaging) return () => undefined;
  return onMessage(messaging, handler);
}
