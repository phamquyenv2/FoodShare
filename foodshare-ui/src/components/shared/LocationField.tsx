import { useEffect, useRef, useState } from 'react';
import { Loader2, LocateFixed, MapPin } from 'lucide-react';
import {
  autocompleteAddress,
  reverseGeocode,
  type LocationSuggestion,
} from '../../services/locationApi';
import { useToast } from '../../contexts/ToastContext';

export interface LocationValue {
  address: string;
  latitude: number | null;
  longitude: number | null;
}

interface LocationFieldProps {
  value: LocationValue;
  onChange: (value: LocationValue) => void;
  label?: string;
  placeholder?: string;
  required?: boolean;
  disabled?: boolean;
}

export default function LocationField({
  value,
  onChange,
  label = 'Địa chỉ cụ thể',
  placeholder = 'Số nhà, đường, phường/xã, tỉnh/thành phố',
  required = false,
  disabled = false,
}: LocationFieldProps) {
  const [suggestions, setSuggestions] = useState<LocationSuggestion[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [isLocating, setIsLocating] = useState(false);
  const [message, setMessage] = useState('');
  const [hasLocationError, setHasLocationError] = useState(false);
  const resolvedAddress = useRef(value.address);
  const { showError } = useToast();

  useEffect(() => {
    const query = value.address.trim();
    if (value.latitude !== null && value.longitude !== null) {
      resolvedAddress.current = value.address;
    }
    if (disabled || query.length < 3 || query === resolvedAddress.current) {
      setSuggestions([]);
      setIsSearching(false);
      return;
    }

    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setIsSearching(true);
      try {
        const results = await autocompleteAddress(query, controller.signal);
        setSuggestions(results);
        if (results.length === 0) {
          setMessage('Không tìm thấy địa chỉ phù hợp. Hãy nhập địa chỉ chi tiết hơn.');
        } else {
          setHasLocationError(false);
        }
      } catch (error) {
        if ((error as Error)?.name !== 'AbortError') {
          setSuggestions([]);
          const errorMessage = (error as Error)?.message;
          const displayMessage = errorMessage === 'Failed to fetch'
            ? 'Không kết nối được backend. Hãy kiểm tra Spring Boot đang chạy.'
            : errorMessage || 'Không thể tải gợi ý địa chỉ lúc này.';
          setMessage('');
          setHasLocationError(true);
          showError(displayMessage, 'Lỗi định vị');
        }
      } finally {
        if (!controller.signal.aborted) setIsSearching(false);
      }
    }, 400);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [disabled, showError, value.address, value.latitude, value.longitude]);

  const selectSuggestion = (suggestion: LocationSuggestion) => {
    resolvedAddress.current = suggestion.formattedAddress;
    setSuggestions([]);
    setHasLocationError(false);
    setMessage('Đã xác định tọa độ cho địa chỉ này.');
    onChange({
      address: suggestion.formattedAddress,
      latitude: suggestion.latitude,
      longitude: suggestion.longitude,
    });
  };

  const handleAddressChange = (address: string) => {
    resolvedAddress.current = '';
    setMessage('');
    setHasLocationError(false);
    onChange({ address, latitude: null, longitude: null });
  };

  const locateCurrentPosition = () => {
    setMessage('');
    if (!navigator.geolocation) {
      setHasLocationError(true);
      showError('Trình duyệt này không hỗ trợ xác định vị trí.', 'Lỗi định vị');
      return;
    }

    setIsLocating(true);
    navigator.geolocation.getCurrentPosition(
      async ({ coords }) => {
        try {
          const location = await reverseGeocode(coords.latitude, coords.longitude);
          resolvedAddress.current = location.formattedAddress;
          setSuggestions([]);
          setHasLocationError(false);
          setMessage(`Đã lấy vị trí hiện tại (độ chính xác khoảng ${Math.round(coords.accuracy)} m).`);
          onChange({
            address: location.formattedAddress,
            latitude: location.latitude,
            longitude: location.longitude,
          });
        } catch (error) {
          setHasLocationError(true);
          showError((error as Error)?.message || 'Không thể chuyển vị trí thành địa chỉ.', 'Lỗi định vị');
        } finally {
          setIsLocating(false);
        }
      },
      (error) => {
        const messages: Record<number, string> = {
          1: 'Hãy cấp quyền vị trí hoặc nhập địa chỉ.',
          2: 'Thiết bị không xác định được vị trí hiện tại.',
          3: 'Quá thời gian lấy vị trí. Vui lòng thử lại.',
        };
        setHasLocationError(true);
        showError(messages[error.code] || 'Không thể lấy vị trí hiện tại.', 'Lỗi định vị');
        setIsLocating(false);
      },
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 30_000 },
    );
  };

  return (
    <div>
      <label className="flex items-center gap-2 text-sm font-medium text-gray-700 mb-1.5">
        <MapPin size={14} />
        {label} {required && <span className="text-red-500">*</span>}
      </label>
      <div className="relative">
        <div className="flex gap-2">
          <div className="relative flex-1">
            <input
              type="text"
              value={value.address}
              onChange={event => handleAddressChange(event.target.value)}
              onFocus={() => {
                if (value.address !== resolvedAddress.current) resolvedAddress.current = '';
              }}
              placeholder={placeholder}
              required={required}
              aria-invalid={hasLocationError}
              disabled={disabled}
              autoComplete="street-address"
              className="w-full px-4 py-3 pr-10 rounded-xl border border-gray-200 text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-[#2db84c]/30 focus:border-[#2db84c] transition-all disabled:bg-gray-50 disabled:text-gray-500"
            />
            {isSearching && (
              <Loader2 size={16} className="absolute right-3 top-3.5 animate-spin text-gray-400" />
            )}
          </div>
          <button
            type="button"
            onClick={locateCurrentPosition}
            disabled={disabled || isLocating}
            title="Lấy vị trí hiện tại"
            aria-label="Lấy vị trí hiện tại"
            className="w-12 h-12 shrink-0 rounded-xl border border-gray-200 flex items-center justify-center text-[#2db84c] hover:bg-green-50 hover:border-[#2db84c] transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {isLocating ? <Loader2 size={19} className="animate-spin" /> : <LocateFixed size={19} />}
          </button>
        </div>

        {suggestions.length > 0 && (
          <ul className="absolute z-30 left-0 right-14 mt-1 overflow-hidden rounded-xl border border-gray-200 bg-white shadow-lg">
            {suggestions.map((suggestion, index) => (
              <li key={suggestion.placeId || `${suggestion.latitude}-${suggestion.longitude}-${index}`}>
                <button
                  type="button"
                  onMouseDown={event => event.preventDefault()}
                  onClick={() => selectSuggestion(suggestion)}
                  className="w-full px-4 py-3 flex items-start gap-2 text-left text-sm text-gray-700 hover:bg-green-50 border-b border-gray-100 last:border-b-0"
                >
                  <MapPin size={15} className="mt-0.5 shrink-0 text-[#2db84c]" />
                  <span>{suggestion.formattedAddress}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      {message && <p className="mt-1.5 text-xs text-gray-500">{message}</p>}
      <p className="mt-1 text-[10px] text-gray-400">
        Powered by{' '}
        <a
          href="https://www.geoapify.com/"
          target="_blank"
          rel="noreferrer"
          className="hover:text-[#2db84c] hover:underline"
        >
          Geoapify
        </a>
      </p>
    </div>
  );
}
