import { useEffect, useRef, useState } from 'react';
import apiClient from '@/shared/api/client';

// ── Types ────────────────────────────────────────────────────────────────────

export interface AddressValue {
  address: string;
  city: string;
  state: string;
  zip: string;
  lat: number | null;
  lon: number | null;
}

interface GeocodeResponse {
  city: string;
  state: string;
  zip: string;
  lat: number;
  lon: number;
}

interface GmpxPlaceChangeEvent extends Event {
  target: HTMLElement & {
    value: {
      formattedAddress?: string;
      location?: { lat: () => number; lng: () => number };
    } | null;
  };
}

interface Props {
  value?: string;
  placeholder?: string;
  disabled?: boolean;
  onChange: (value: AddressValue) => void;
  'aria-label'?: string;
  id?: string;
}

// Track whether the ECL script has been injected (module-level singleton)
let eclScriptLoaded = false;

function injectEclScript() {
  if (eclScriptLoaded) return;
  const existing = document.querySelector(
    'script[src*="extended-component-library"]',
  );
  if (existing) {
    eclScriptLoaded = true;
    return;
  }
  const script = document.createElement('script');
  script.type = 'module';
  script.src =
    'https://ajax.googleapis.com/ajax/libs/@googlemaps/extended-component-library/0.6.11/index.min.js';
  document.head.appendChild(script);
  eclScriptLoaded = true;
}

// ── Component ────────────────────────────────────────────────────────────────

export function AddressAutocomplete({
  value,
  placeholder = 'Enter an address',
  disabled,
  onChange,
  'aria-label': ariaLabel,
  id,
}: Props) {
  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;
  const hasApiKey = Boolean(apiKey && apiKey !== 'REPLACE_ME');

  const pickerRef = useRef<HTMLElement>(null);
  const [geocodeError, setGeocodeError] = useState<string | null>(null);

  // React reserves `key` and will not forward it as a DOM attribute. ECL requires
  // the Google Maps API key on <gmpx-api-loader key="...">, so set it via ref.
  const setApiLoaderRef = (el: HTMLElement | null) => {
    if (el && apiKey) {
      el.setAttribute('key', apiKey);
    }
  };

  // Inject ECL script once when the API key is available
  useEffect(() => {
    if (!hasApiKey) return;
    injectEclScript();
  }, [hasApiKey]);

  // Attach the gmpx-placechange event listener via ref
  useEffect(() => {
    if (!hasApiKey) return;
    const el = pickerRef.current;
    if (!el) return;

    const handlePlaceChange = async (evt: Event) => {
      const e = evt as GmpxPlaceChangeEvent;
      const place = e.target?.value;
      if (!place) return;

      const formattedAddress = place.formattedAddress ?? '';
      setGeocodeError(null);

      try {
        const { data } = await apiClient.post<GeocodeResponse>('/api/v1/geocode', {
          address: formattedAddress,
        });
        onChange({
          address: formattedAddress,
          city: data.city,
          state: data.state,
          zip: data.zip,
          lat: data.lat,
          lon: data.lon,
        });
      } catch {
        setGeocodeError('Address not found');
        onChange({
          address: formattedAddress,
          city: '',
          state: '',
          zip: '',
          lat: null,
          lon: null,
        });
      }
    };

    el.addEventListener('gmpx-placechange', handlePlaceChange as EventListener);
    return () => {
      el.removeEventListener('gmpx-placechange', handlePlaceChange as EventListener);
    };
  }, [hasApiKey, onChange]);

  // ── Fallback: plain text input ──────────────────────────────────────────────
  if (!hasApiKey) {
    return (
      <input
        id={id}
        type="text"
        className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
        placeholder={placeholder}
        value={value ?? ''}
        disabled={disabled}
        aria-label={ariaLabel}
        data-testid="address-autocomplete-fallback"
        onChange={(e) =>
          onChange({
            address: e.target.value,
            city: '',
            state: '',
            zip: '',
            lat: null,
            lon: null,
          })
        }
      />
    );
  }

  // ── Google Maps ECL picker ──────────────────────────────────────────────────
  return (
    <div className="w-full">
      <gmpx-api-loader
        ref={setApiLoaderRef}
        solution-channel="GMP_GE_mapsandplacesautocomplete_v2"
      />
      <gmpx-place-picker
        ref={pickerRef}
        placeholder={placeholder}
        style={{
          width: '100%',
          fontFamily: 'inherit',
          fontSize: '14px',
        }}
        aria-label={ariaLabel}
        id={id}
      />
      {geocodeError && (
        <p className="mt-1 text-xs text-destructive" role="alert">
          {geocodeError}
        </p>
      )}
    </div>
  );
}
