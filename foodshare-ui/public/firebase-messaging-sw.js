/* Firebase Messaging service worker. Values are supplied by the app's Vite build. */
importScripts('https://www.gstatic.com/firebasejs/12.19.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/12.19.0/firebase-messaging-compat.js');

const config = Object.fromEntries(new URL(self.location.href).searchParams.entries());
firebase.initializeApp(config);

const messaging = firebase.messaging();

messaging.onBackgroundMessage((payload) => {
  const notification = payload.notification || {};
  const data = payload.data || {};
  self.registration.showNotification(notification.title || 'FoodShare', {
    body: notification.body || 'Bạn có thông báo mới',
    icon: '/favicon.png',
    data,
  });
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const data = event.notification.data || {};
  const type = data.referenceType;
  const id = data.referenceId;
  let path = '/';
  if (type === 'ORDER' && id) path = `/recipient/orders/${id}`;
  else if (type === 'FOOD_POST' && id) path = `/recipient/foods/${id}`;
  else if ((type === 'PAYMENT' || type === 'PAYOUT') && id) path = `/recipient/orders/${id}`;
  else if (type === 'REPORT' && id) path = `/recipient/reports/${id}`;
  else if (type === 'USER' && id) path = `/recipient/profile/${id}`;
  event.waitUntil(clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
    for (const client of clientList) {
      if ('focus' in client) { client.navigate(path); return client.focus(); }
    }
    return clients.openWindow(path);
  }));
});
