/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
  readonly VITE_SENTRY_DSN?: string;
  readonly VITE_GOOGLE_MAPS_API_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare const __APP_VERSION__: string;
declare const __GIT_SHA__: string;

// ── Google Maps Extended Component Library custom elements ────────────────────
declare namespace JSX {
  interface IntrinsicElements {
    'gmpx-api-loader': React.DetailedHTMLProps<
      React.HTMLAttributes<HTMLElement> & {
        'solution-channel'?: string;
        ref?: React.Ref<HTMLElement>;
      },
      HTMLElement
    >;
    'gmpx-place-picker': React.DetailedHTMLProps<
      React.HTMLAttributes<HTMLElement> & {
        placeholder?: string;
        ref?: React.Ref<HTMLElement>;
      },
      HTMLElement
    >;
  }
}
