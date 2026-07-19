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

const MAPS_LOAD_TIMEOUT_MS = 5_000;

const inputClassName =
  'flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50';

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
    const timeoutId = window.setTimeout(() => {
      mapsLoaderPromise = null;
      reject(new Error('Timed out loading Google Maps JavaScript API'));
    }, MAPS_LOAD_TIMEOUT_MS);

    const finish = (fn: () => void) => {
      window.clearTimeout(timeoutId);
      fn();
    };

    const existing = document.querySelector<HTMLScriptElement>(
      'script[data-teamhub-google-maps="true"]',
    );
    if (existing) {
      existing.addEventListener('load', () => finish(() => resolve()), { once: true });
      existing.addEventListener(
        'error',
        () =>
          finish(() => {
            mapsLoaderPromise = null;
            reject(new Error('Failed to load Google Maps JavaScript API'));
          }),
        { once: true },
      );
      return;
    }

    const script = document.createElement('script');
    script.dataset.teamhubGoogleMaps = 'true';
    script.async = true;
    script.src =
      `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(apiKey)}` +
      '&v=weekly&libraries=places';
    script.onload = () => finish(() => resolve());
    script.onerror = () =>
      finish(() => {
        mapsLoaderPromise = null;
        reject(new Error('Failed to load Google Maps JavaScript API'));
      });
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
  // Text input is shown until Places mounts successfully (ad blockers / CSP must not blank the form).
  const [placesReady, setPlacesReady] = useState(false);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    if (!hasApiKey || !apiKey) return;

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
        if (cancelled) return;

        if (!window.google?.maps?.importLibrary) {
          throw new Error('Google Maps importLibrary unavailable');
        }

        // Wait one frame so the always-mounted container ref is attached.
        await new Promise<void>((r) => requestAnimationFrame(() => r()));
        if (cancelled || !containerRef.current) {
          throw new Error('Address autocomplete container unavailable');
        }

        const { PlaceAutocompleteElement } =
          await window.google.maps.importLibrary('places');
        if (cancelled || !containerRef.current) return;

        autocompleteEl = new PlaceAutocompleteElement({
          includedRegionCodes: ['us'],
        });
        autocompleteEl.placeholder = placeholder;
        // Force light scheme so the widget matches other form inputs (avoids black/dark host chrome).
        autocompleteEl.style.setProperty('color-scheme', 'light');
        autocompleteEl.style.setProperty('background-color', 'transparent');
        autocompleteEl.style.setProperty('color', 'hsl(var(--foreground))');
        autocompleteEl.style.setProperty('border', '1px solid hsl(var(--input))');
        autocompleteEl.style.setProperty(
          'border-radius',
          'calc(var(--radius) - 2px)',
        );
        autocompleteEl.style.setProperty('font-family', 'inherit');
        autocompleteEl.style.setProperty('font-size', '0.875rem');
        autocompleteEl.style.width = '100%';
        autocompleteEl.style.height = '2.25rem';
        autocompleteEl.style.display = 'block';
        autocompleteEl.style.boxSizing = 'border-box';
        if (id) autocompleteEl.id = id;
        if (ariaLabel) autocompleteEl.setAttribute('aria-label', ariaLabel);
        if (disabled) autocompleteEl.setAttribute('disabled', '');

        containerRef.current.replaceChildren(autocompleteEl);
        autocompleteEl.addEventListener('gmp-select', handleSelect);
        if (!cancelled) {
          setPlacesReady(true);
        }
      } catch {
        if (!cancelled) {
          setPlacesReady(false);
        }
      }
    })();

    return () => {
      cancelled = true;
      autocompleteEl?.removeEventListener('gmp-select', handleSelect);
      autocompleteEl?.remove();
      autocompleteEl = null;
      setPlacesReady(false);
    };
  }, [hasApiKey, apiKey, placeholder, disabled, ariaLabel, id]);

  useEffect(() => {
    const el = containerRef.current?.firstElementChild as HTMLElement | null;
    if (!el) return;
    if (disabled) {
      el.setAttribute('disabled', '');
    } else {
      el.removeAttribute('disabled');
    }
  }, [disabled, placesReady]);

  const geocodeTypedAddress = async (address: string) => {
    const trimmed = address.trim();
    if (!trimmed || !hasApiKey) return;
    setGeocodeError(null);
    try {
      const { data } = await apiClient.post<GeocodeResponse>('/api/v1/geocode', {
        address: trimmed,
      });
      onChangeRef.current({
        address: trimmed,
        city: data.city || '',
        state: data.state || '',
        zip: data.zip || '',
        lat: data.lat,
        lon: data.lon,
      });
    } catch {
      // Keep typed address; customer/job create may still geocode server-side.
    }
  };

  return (
    <div className="w-full">
      {/* Plain input always available until Places is ready (and permanently if Maps is blocked). */}
      {!placesReady && (
        <input
          id={id}
          type="text"
          className={inputClassName}
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
          onBlur={(e) => void geocodeTypedAddress(e.target.value)}
        />
      )}

      {/* Mount target always in the DOM so Places can initialize even while fallback is shown. */}
      <div
        ref={containerRef}
        className={
          placesReady
            ? 'w-full [&_gmp-place-autocomplete]:block [&_gmp-place-autocomplete]:w-full'
            : 'hidden'
        }
        aria-hidden={!placesReady}
      />

      {placesReady && value ? (
        <p className="mt-1 truncate text-xs text-muted-foreground" title={value}>
          {value}
        </p>
      ) : null}

      {geocodeError ? (
        <p className="mt-1 text-xs text-destructive" role="alert">
          {geocodeError}
        </p>
      ) : null}
    </div>
  );
}
