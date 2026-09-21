const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const SYSTEM_ERROR_MESSAGE = 'Lỗi hệ thống, xin lỗi vì sự bất tiện này.';

const TOKEN_KEY = 'foodshare_token';

let isRefreshing = false;
let refreshSubscribers: {
  resolve: (token: string) => void;
  reject: (err: any) => void;
}[] = [];

let accessToken: string | null = typeof window !== 'undefined' ? localStorage.getItem(TOKEN_KEY) : null;

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string) {
  accessToken = token;
  try {
    localStorage.setItem(TOKEN_KEY, token);
  } catch (e) {
  }
}

export function clearAccessToken() {
  accessToken = null;
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch (e) {
  }
}

function onRefreshed(token: string) {
  refreshSubscribers.forEach((sub) => sub.resolve(token));
  refreshSubscribers = [];
}

function onRefreshFailed(err: any) {
  refreshSubscribers.forEach((sub) => sub.reject(err));
  refreshSubscribers = [];
}

export async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const token = accessToken;
  const headers = new Headers(options?.headers);
  
  if (!headers.has('Content-Type') && !(options?.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const config = { ...options, headers };
  config.credentials = 'include';

  let res = await fetch(`${API_BASE}${path}`, config);

  if (res.status === 401 && !path.startsWith('/auth/')) {
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
        const newToken = data?.data?.accessToken || data?.accessToken;
        if (!newToken) throw new Error('No access token received');

        setAccessToken(newToken);
        isRefreshing = false;
        onRefreshed(newToken);
        
        headers.set('Authorization', `Bearer ${newToken}`);
        res = await fetch(`${API_BASE}${path}`, { ...config, headers });
      } catch (err) {
        isRefreshing = false;
        onRefreshFailed(err);
        clearAccessToken();
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('auth:expired'));
        }
        throw err;
      }
    } else {
      return new Promise((resolve, reject) => {
        refreshSubscribers.push({
          resolve: async (newToken: string) => {
            try {
              headers.set('Authorization', `Bearer ${newToken}`);
              const retryRes = await fetch(`${API_BASE}${path}`, { ...config, headers });
              if (!retryRes.ok) {
                let msg = SYSTEM_ERROR_MESSAGE;
                if (retryRes.status < 500) {
                  try {
                    const errData = await retryRes.json();
                    if (errData.message) msg = Array.isArray(errData.message) ? errData.message.join('\n') : errData.message;
                  } catch (e) {
                  }
                }
                throw new Error(msg);
              }
              if (retryRes.status === 204) return resolve({} as T);
              const json = await retryRes.json();
              resolve(json.data !== undefined ? json.data : json);
            } catch (e) {
              reject(e);
            }
          },
          reject: (err: any) => {
            reject(err);
          },
        });
      });
    }
  }

  if (!res.ok) {
    let errorMessage = SYSTEM_ERROR_MESSAGE;
    let errorMessages: string[] | undefined;
    try {
      const errorData = await res.json();
      if (Array.isArray(errorData.message)) {
        const list = errorData.message.filter(Boolean).map(String);
        errorMessages = list;
        errorMessage = list.join('\n');
      } else if (errorData.message) {
        errorMessage = errorData.message;
      } else if (errorData.error) {
        errorMessage = String(errorData.error);
      }
    } catch (e) {
    }
    const err: any = new Error(errorMessage);
    if (errorMessages) err.messages = errorMessages;
    throw err;
  }
  
  if (res.status === 204) return {} as T;
  
  const json = await res.json();
  return json.data !== undefined ? json.data : json;
}
