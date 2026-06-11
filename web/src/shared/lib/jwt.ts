export interface JwtPayload {
  sub: string;
  role: string;
  exp: number;
  iat: number;
  tenant_id?: string;
}

export function parseJwtPayload(token: string): JwtPayload | null {
  try {
    const [, payloadB64] = token.split('.');
    const json = atob(payloadB64.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}

export function isJwtExpired(token: string): boolean {
  const payload = parseJwtPayload(token);
  if (!payload?.exp) return true;
  return payload.exp * 1000 <= Date.now();
}
