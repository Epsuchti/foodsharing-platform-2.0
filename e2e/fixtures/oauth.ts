import { createHash, randomBytes } from 'node:crypto';
import { expect, type APIRequestContext, type APIResponse } from '@playwright/test';
import type { ApiClient } from './api';

/**
 * OAuth provider helpers (C13, A44/A56): PKCE pair, authorize URL, token exchange and userinfo through a request
 * context (no browser), and the dev endpoint that returns the last issued code. Seed clients: `wiki` (confidential,
 * secret `wiki-local-secret`, requires zuerich-stadt) and `mobile-dev` (public, PKCE); both accept
 * {@link SEED_OAUTH_REDIRECT}.
 */

export const SEED_OAUTH_REDIRECT = 'http://localhost:8080/dev/oauth-callback';
export const OAUTH_CLIENTS = {
  wiki: { clientId: 'wiki', secret: 'wiki-local-secret', name: 'Foodsharing Wiki Zürich' },
  mobileDev: { clientId: 'mobile-dev', name: 'Foodsharing Mobile (Dev)' },
} as const;

const base64url = (buffer: Buffer) => buffer.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

export interface Pkce {
  verifier: string;
  challenge: string;
}

/** A fresh PKCE pair (RFC 7636, S256). */
export function pkce(): Pkce {
  const verifier = base64url(randomBytes(48));
  return { verifier, challenge: base64url(createHash('sha256').update(verifier).digest()) };
}

export interface AuthorizeOptions {
  redirectUri: string;
  scope?: string;
  state?: string;
  /** PKCE challenge (S256); required for public clients. */
  codeChallenge?: string;
  nonce?: string;
}

/** Relative `/oauth/authorize?…` URL (response_type=code). */
export function authorizeUrl(clientId: string, options: AuthorizeOptions): string {
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: clientId,
    redirect_uri: options.redirectUri,
    scope: options.scope ?? 'openid profile',
  });
  if (options.state) params.set('state', options.state);
  if (options.nonce) params.set('nonce', options.nonce);
  if (options.codeChallenge) {
    params.set('code_challenge', options.codeChallenge);
    params.set('code_challenge_method', 'S256');
  }
  return `/oauth/authorize?${params.toString()}`;
}

/** Where `/oauth/authorize` sends the browser (without following): consent page, login or the client. */
export async function authorizeLocation(request: APIRequestContext, url: string): Promise<string> {
  const response = await request.get(url, { maxRedirects: 0 });
  expect(response.status(), `GET ${url}`).toBe(302);
  return response.headers()['location'] ?? '';
}

export interface TokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  scope: string;
  refresh_token?: string;
  id_token?: string;
}

export interface CodeExchange {
  code: string;
  clientId: string;
  redirectUri: string;
  codeVerifier?: string;
  /** Confidential clients: sent as client_secret_post. */
  clientSecret?: string;
}

/** `POST /oauth/token` (authorization_code). Returns the raw response so tests can assert errors. */
export function exchangeCode(request: APIRequestContext, exchange: CodeExchange): Promise<APIResponse> {
  const form: Record<string, string> = {
    grant_type: 'authorization_code',
    code: exchange.code,
    redirect_uri: exchange.redirectUri,
    client_id: exchange.clientId,
  };
  if (exchange.codeVerifier) form['code_verifier'] = exchange.codeVerifier;
  if (exchange.clientSecret) form['client_secret'] = exchange.clientSecret;
  return request.post('/oauth/token', { form });
}

/** `POST /oauth/token` (refresh_token, public client). */
export function refreshToken(request: APIRequestContext, clientId: string, token: string): Promise<APIResponse> {
  return request.post('/oauth/token', { form: { grant_type: 'refresh_token', refresh_token: token, client_id: clientId } });
}

/** `GET /oauth/userinfo` with the access token. */
export async function userinfo(request: APIRequestContext, accessToken: string): Promise<Record<string, unknown>> {
  const response = await request.get('/oauth/userinfo', { headers: { Authorization: `Bearer ${accessToken}` } });
  expect(response.status(), 'GET /oauth/userinfo').toBe(200);
  return (await response.json()) as Record<string, unknown>;
}

export interface DevOAuthCode {
  code: string;
  clientId: string;
  redirectUri: string;
  state?: string;
}

/** `GET /api/dev/oauth/last-code` (dev profile): the last authorization code the server issued. */
export function devGetLastOAuthCode(api: ApiClient): Promise<DevOAuthCode> {
  return api.getJson<DevOAuthCode>('/api/dev/oauth/last-code');
}

/** The `code`/`state`/`error` query of a callback URL. */
export function callbackParams(url: string): { code: string | null; state: string | null; error: string | null } {
  const params = new URL(url).searchParams;
  return { code: params.get('code'), state: params.get('state'), error: params.get('error') };
}
