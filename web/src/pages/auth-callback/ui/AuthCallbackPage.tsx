// Handles the backend's OAuth2 success redirect:
//   /auth/callback?access_token=<jwt>&refresh_token=<opaque>
//
// Reads the tokens from URL search params, stores them via authStore,
// then navigates to the original redirect destination (or /schedule).

import { useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/entities/user/model/authStore';

const REDIRECT_KEY = 'teamhub_redirect';

export default function AuthCallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  useEffect(() => {
    const accessToken = searchParams.get('access_token');
    const refreshToken = searchParams.get('refresh_token');

    if (!accessToken || !refreshToken) {
      navigate('/login?error=missing_tokens', { replace: true });
      return;
    }

    useAuthStore.getState().setTokens(accessToken, refreshToken);

    const stored = sessionStorage.getItem(REDIRECT_KEY);
    sessionStorage.removeItem(REDIRECT_KEY);
    navigate(stored ?? '/schedule', { replace: true });
  }, [navigate, searchParams]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-brand-bg font-sans">
      <p className="text-brand-muted text-sm">Signing you in…</p>
    </div>
  );
}
