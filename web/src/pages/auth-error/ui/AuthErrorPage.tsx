// Handles the backend's OAuth2 failure redirect:
//   /auth/error?error=NO_INVITE_FOR_EMAIL|ACCOUNT_DEACTIVATED|OAUTH2_ERROR

import { useSearchParams, Link } from 'react-router-dom';
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/shared/ui/card';

const ERROR_MESSAGES: Record<string, string> = {
  NO_INVITE_FOR_EMAIL:
    "Your Google account isn't linked to an invite. Contact your administrator to receive an invite.",
  ACCOUNT_DEACTIVATED:
    'Your account has been deactivated. Contact your administrator.',
  OAUTH2_ERROR:
    'Google sign-in failed. Please try again or use email and password.',
};

const DEFAULT_MESSAGE = ERROR_MESSAGES.OAUTH2_ERROR;

export default function AuthErrorPage() {
  const [searchParams] = useSearchParams();
  const errorCode = searchParams.get('error') ?? '';
  const message = ERROR_MESSAGES[errorCode] ?? DEFAULT_MESSAGE;

  return (
    <div className="min-h-screen flex items-center justify-center bg-brand-bg font-sans px-4">
      <Card className="w-full max-w-sm bg-brand-surface border-border rounded-lg shadow-sm">
        <CardHeader className="space-y-1">
          <CardTitle className="text-2xl font-semibold text-brand-navy">Sign-in failed</CardTitle>
        </CardHeader>

        <CardContent>
          <p className="text-sm text-brand-muted">{message}</p>
        </CardContent>

        <CardFooter>
          <Link to="/login" className="text-sm text-brand-blue hover:underline">
            Back to login
          </Link>
        </CardFooter>
      </Card>
    </div>
  );
}
