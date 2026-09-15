import { apiFetch } from './api';

export interface LocationSuggestion {
  formattedAddress: string;
  latitude: number;
  longitude: number;
  placeId?: string;
}

export function autocompleteAddress(text: string, signal?: AbortSignal) {
  return apiFetch<LocationSuggestion[]>(
    `/locations/autocomplete?text=${encodeURIComponent(text)}`,
    { signal },
  );
}

export function reverseGeocode(latitude: number, longitude: number) {
  const params = new URLSearchParams({
    latitude: String(latitude),
    longitude: String(longitude),
  });
  return apiFetch<LocationSuggestion>(`/locations/reverse?${params.toString()}`);
}
