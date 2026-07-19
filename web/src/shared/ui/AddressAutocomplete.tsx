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

interface PlaceAddressComponent {
  longText?: string;
  shortText?: string;
  types?: string[];
}

interface PlaceLike {
  formattedAddress?: string | null;
  location?: { lat: () => number; lng: () => number } | null;
  addressComponents?: PlaceAddressComponent[];
  fetchFields: (options: { fields: string[] }) => Promise<unknown>;
}

interface PlacePredictionLike {
  toPlace: () => PlaceLike;
}

interface PlaceSelectEvent extends Event {
  placePrediction?: PlacePredictionLike;
}

interface PlaceAutocompleteElementLike extends HTMLElement {
  placeholder?: string;
  includedRegionCodes?: string[];
}

interface Props {
  value?: string;
  placeholder?: string;
  disabled?: boolean;
  onChange: (value: AddressValue) => void;
  'aria-label'?: string;
  id?: string;
}

declare global {
  interface Window {
    google?: {
      maps?: {
        importLibrary: (name: string) => Promise<{
          PlaceAutocompleteElement: new (options?: {
            includedRegionCodes?: string[];
          }) => PlaceAutocompleteElementLike;
        }>;
      };
    };
  }
}

// ── Google Maps loader (singleton) ───────────────────────────────────────────

let mapsLoaderPromise: Promise<void> | null = null;

function loadGoogleMaps(apiKey: string): Promise<void> {
  if (window.google?.maps?.importLibrary) {
    return Promise.resolve();
  }
  if (mapsLoaderPromise) {
    return mapsLoaderPromise;
  }

  mapsLoaderPromise = new Promise<void>((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(
      'script[data-teamhub-google-maps="true"]',
    );
    if (existing) {
      existing.addEventListener('load', () => resolve(), { once: true });
      existing.addEventListener(
        'error',
        () => reject(new Error('Failed to load Google Maps JavaScript API')),
        { once: true },
      );
      return;
    }

    const script = document.createElement('script');
    script.dataset.teamhubGoogleMaps = 'true';
    script.async = true;
    script.src =
      `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(apiKey)}` +
      '&v=weekly&libraries=places&loading=async';
    script.onload = () => resolve();
    script.onerror = () => {
      mapsLoaderPromise = null;
      reject(new Error('Failed to load Google Maps JavaScript API'));
    };
    document.head.appendChild(script);
  });

  return mapsLoaderPromise;
}

function componentText(
  components: PlaceAddressComponent[] | undefined,
  type: string,
  short = false,
): string {
  const match = components?.find((c) => c.types?.includes(type));
  if (!match) return '';
  return (short ? match.shortText : match.longText) ?? '';
}

function parsePlace(place: PlaceLike): AddressValue {
  const components = place.addressComponents;
  const city =
    componentText(components, 'locality') ||
    componentText(components, 'postal_town') ||
    componentText(components, 'sublocality') ||
    componentText(components, 'sublocality_level_1') ||
    componentText(components, 'administrative_area_level_3');
  const state = componentText(components, 'administrative_area_level_1', true);
  const zip = componentText(components, 'postal_code');
  const lat = place.location ? place.location.lat() : null;
  const lon = place.location ? place.location.lng() : null;

  return {
    address: place.formattedAddress ?? '',
    city,
    state,
    zip,
    lat,
    lon,
  };
}

function isCompleteAddress(value: AddressValue): boolean {
  return Boolean(
    value.address &&
      value.city &&
      value.state &&
      value.zip &&
      value.lat != null &&
      value.lon != null,
  );
}

// ── Component ────────────────────────────────────────────────────────────────

export function AddressAutocomplete({
  value,
  placeholder = 'Enter an address',
  disabled,
  onChange,
  'aria-label': ariaLabel,
  id,
}: Readonly<Props>) {
  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;
  const hasApiKey = Boolean(apiKey && apiKey !== 'REPLACE_ME');

  const containerRef = useRef<HTMLDivElement>(null);
  const onChangeRef = useRef(onChange);
  const [geocodeError, setGeocodeError] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    if (!hasApiKey || !apiKey || !containerRef.current) return;

    let cancelled = false;
    let autocompleteEl: PlaceAutocompleteElementLike | null = null;

    const handleSelect = async (evt: Event) => {
      const placePrediction = (evt as PlaceSelectEvent).placePrediction;
      if (!placePrediction) return;

      setGeocodeError(null);

      try {
        const place = placePrediction.toPlace();
        await place.fetchFields({
          fields: ['formattedAddress', 'location', 'addressComponents'],
        });

        let next = parsePlace(place);

        // Prefer Places details; only call backend geocode when components are incomplete
        // and we have a non-blank address (blank address is what previously caused HTTP 400).
        if (!isCompleteAddress(next) && next.address.trim()) {
          try {
            const { data } = await apiClient.post<GeocodeResponse>('/api/v1/geocode', {
              address: next.address,
            });
            next = {
              address: next.address,
              city: next.city || data.city || '',
              state: next.state || data.state || '',
              zip: next.zip || data.zip || '',
              lat: next.lat ?? data.lat,
              lon: next.lon ?? data.lon,
            };
          } catch {
            setGeocodeError('Address not found');
          }
        } else if (!next.address.trim()) {
          setGeocodeError('Address not found');
        }

        onChangeRef.current(next);
      } catch {
        setGeocodeError('Address not found');
      }
    };

    void (async () => {
      try {
        await loadGoogleMaps(apiKey);
        if (cancelled || !containerRef.current || !window.google?.maps?.importLibrary) {
          return;
        }

        const { PlaceAutocompleteElement } =
          await window.google.maps.importLibrary('places');
        if (cancelled || !containerRef.current) return;

        autocompleteEl = new PlaceAutocompleteElement({
          includedRegionCodes: ['us'],
        });
        autocompleteEl.placeholder = placeholder;
        autocompleteEl.style.display = 'block';
        autocompleteEl.style.width = '100%';
        if (id) autocompleteEl.id = id;
        if (ariaLabel) autocompleteEl.setAttribute('aria-label', ariaLabel);
        if (disabled) autocompleteEl.setAttribute('disabled', '');

        containerRef.current.replaceChildren(autocompleteEl);
        autocompleteEl.addEventListener('gmp-select', handleSelect);
        setLoadError(null);
      } catch {
        if (!cancelled) {
          setLoadError('Address lookup unavailable');
        }
      }
    })();

    return () => {
      cancelled = true;
      autocompleteEl?.removeEventListener('gmp-select', handleSelect);
      autocompleteEl?.remove();
      autocompleteEl = null;
    };
  }, [hasApiKey, apiKey, placeholder, disabled, ariaLabel, id]);

  // Keep widget disabled state in sync after mount
  useEffect(() => {
    const el = containerRef.current?.firstElementChild as HTMLElement | null;
    if (!el) return;
    if (disabled) {
      el.setAttribute('disabled', '');
    } else {
      el.removeAttribute('disabled');
    }
  }, [disabled]);

  // ── Fallback: plain text input ──────────────────────────────────────────────
  if (!hasApiKey || loadError) {
    return (
      <div className="w-full">
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
        {loadError && (
          <output className="mt-1 block text-xs text-muted-foreground">
            {loadError}
          </output>
        )}
      </div>
    );
  }

  // ── Place Autocomplete (New) widget ─────────────────────────────────────────
  return (
    <div className="w-full">
      <div
        ref={containerRef}
        className="w-full [&_gmp-place-autocomplete]:block [&_gmp-place-autocomplete]:w-full"
      />
      {/* Preserve current free-text value for forms that bind `value` */}
      {value ? (
        <p className="mt-1 truncate text-xs text-muted-foreground" title={value}>
          {value}
        </p>
      ) : null}
      {geocodeError && (
        <p className="mt-1 text-xs text-destructive" role="alert">
          {geocodeError}
        </p>
      )}
    </div>
  );
}
