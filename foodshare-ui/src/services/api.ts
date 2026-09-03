const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

let isRefreshing = false;
let refreshSubscribers: ((token: string) => void)[] = [];

function onRefreshed(token: string) {
  refreshSubscribers.forEach((cb) => cb(token));
  refreshSubscribers = [];
}

function addRefreshSubscriber(cb: (token: string) => void) {
  refreshSubscribers.push(cb);
}

export async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const token = localStorage.getItem('accessToken');
  const headers = new Headers(options?.headers);
  
  if (!headers.has('Content-Type') && !(options?.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const config = { ...options, headers };
  // Make sure to include credentials for cookies (refresh token)
  config.credentials = 'include';

  let res = await fetch(`${API_BASE}${path}`, config);

  if (res.status === 401) {
    if (!isRefreshing) {
      isRefreshing = true;
      try {
        const refreshRes = await fetch(`${API_BASE}/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
        });
        
        if (!refreshRes.ok) throw new Error('Session expired');
        
        const data = await refreshRes.json();
        const newToken = data.data.accessToken;
        localStorage.setItem('accessToken', newToken);
        isRefreshing = false;
        onRefreshed(newToken);
        
        // Retry original request
        headers.set('Authorization', `Bearer ${newToken}`);
        res = await fetch(`${API_BASE}${path}`, { ...config, headers });
      } catch (err) {
        isRefreshing = false;
        refreshSubscribers = [];
        localStorage.removeItem('accessToken');
        window.location.href = '/auth/login';
        throw err;
      }
    } else {
      // Wait for refresh to complete and retry
      return new Promise((resolve, reject) => {
        addRefreshSubscriber(async (newToken: string) => {
          try {
            headers.set('Authorization', `Bearer ${newToken}`);
            const retryRes = await fetch(`${API_BASE}${path}`, { ...config, headers });
            if (!retryRes.ok) {
                const errData = await retryRes.json().catch(() => ({}));
                throw new Error(errData.message || `API Error ${retryRes.status}`);
            }
            resolve(retryRes.json() as Promise<T>);
          } catch (e) {
            reject(e);
          }
        });
      });
    }
  }

  if (!res.ok) {
    let errorMessage = `API Error ${res.status}`;
    try {
        const errorData = await res.json();
        errorMessage = errorData.message || errorMessage;
    } catch (e) {
        // ignore
    }
    throw new Error(errorMessage);
  }
  
  // Some endpoints might return empty response
  if (res.status === 204) return {} as T;
  
  const json = await res.json();
  // Unwrap standard API response if present
  return json.data !== undefined ? json.data : json;
}
