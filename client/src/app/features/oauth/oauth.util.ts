import { OAuthClient, OAuthClientCreate, OAuthClientUpdate, OAuthGrantType } from '../../api';

/** Scopes of the OAuth provider (A44), in the order they are explained. `openid` is always granted. */
export const OAUTH_SCOPES = ['openid', 'profile', 'email', 'regions'] as const;
export type OAuthScopeName = (typeof OAUTH_SCOPES)[number];

const SCOPE_ICONS: Record<OAuthScopeName, string> = {
  openid: 'pi pi-id-card',
  profile: 'pi pi-user',
  email: 'pi pi-envelope',
  regions: 'pi pi-map-marker',
};

export function isKnownScope(scope: string): scope is OAuthScopeName {
  return (OAUTH_SCOPES as readonly string[]).includes(scope);
}

/** Known scopes in their fixed order (unknown ones are dropped: they cannot be explained to the user). */
export function orderedScopes(scopes: readonly string[]): OAuthScopeName[] {
  return OAUTH_SCOPES.filter((scope) => scopes.includes(scope));
}

export function scopeIcon(scope: OAuthScopeName): string {
  return SCOPE_ICONS[scope];
}

export type RedirectUriProblem = 'required' | 'invalid' | 'insecure';

/**
 * Client-side copy of `OAuthClientService.validRedirectUri` (decision 92 pattern; the server still answers 400
 * OAUTH_REDIRECT_URI_INVALID): absolute, with a host, no fragment, no spaces; https, or http on localhost/127.0.0.1.
 */
export function redirectUriProblem(raw: string): RedirectUriProblem | null {
  const value = raw.trim();
  if (!value) {
    return 'required';
  }
  if (/\s/.test(value) || value.includes('#')) {
    return 'invalid';
  }
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return 'invalid';
  }
  if (!url.hostname || !/^[a-z][a-z0-9+.-]*:\/\//i.test(value)) {
    return 'invalid';
  }
  const local = url.hostname === 'localhost' || url.hostname === '127.0.0.1';
  if (url.protocol === 'https:' || (url.protocol === 'http:' && local)) {
    return null;
  }
  return url.protocol === 'http:' ? 'insecure' : 'invalid';
}

/** Server rule of `createOAuthClient`: 3–60 characters of `a-z 0-9 . _ -`. */
export function validClientId(value: string): boolean {
  return /^[a-z0-9._-]{3,60}$/.test(value.trim());
}

/** Suggests a client id from the display name (`Foodsharing Wiki Zürich` → `foodsharing-wiki-zurich`). */
export function suggestClientId(name: string): string {
  return name
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60);
}

export const MAX_REDIRECT_URIS = 10;

/** Trimmed, without empty rows and duplicates (the server de-duplicates too). */
export function cleanRedirectUris(uris: readonly string[]): string[] {
  return [...new Set(uris.map((uri) => uri.trim()).filter(Boolean))];
}

/** Draft of the create/edit dialog. */
export interface OAuthClientDraft {
  clientId: string;
  name: string;
  confidential: boolean;
  redirectUris: string[];
  scopes: OAuthScopeName[];
  refreshTokens: boolean;
  requiredRegionIds: string[];
  enabled: boolean;
}

export function emptyDraft(): OAuthClientDraft {
  return {
    clientId: '',
    name: '',
    confidential: true,
    redirectUris: [''],
    scopes: ['openid', 'profile'],
    refreshTokens: true,
    requiredRegionIds: [],
    enabled: true,
  };
}

export function draftOf(client: OAuthClient): OAuthClientDraft {
  return {
    clientId: client.clientId,
    name: client.name,
    confidential: client.confidential,
    redirectUris: client.redirectUris.length ? [...client.redirectUris] : [''],
    scopes: orderedScopes(['openid', ...client.scopes]),
    refreshTokens: client.grantTypes.includes(OAuthGrantType.RefreshToken),
    requiredRegionIds: [...client.requiredRegionIds],
    enabled: client.enabled,
  };
}

/** Problems that keep the dialog from saving (keys below `oauth.clients.form.`), empty when valid. */
export function draftProblems(draft: OAuthClientDraft, creating: boolean): string[] {
  const problems: string[] = [];
  if (creating && !validClientId(draft.clientId)) {
    problems.push('clientIdInvalid');
  }
  const name = draft.name.trim();
  if (!name || name.length > 200) {
    problems.push('nameRequired');
  }
  const uris = cleanRedirectUris(draft.redirectUris);
  if (!uris.length) {
    problems.push('redirectRequired');
  } else if (uris.length > MAX_REDIRECT_URIS) {
    problems.push('redirectTooMany');
  } else if (uris.some((uri) => redirectUriProblem(uri))) {
    problems.push('redirectInvalid');
  }
  return problems;
}

function grantTypes(draft: OAuthClientDraft): OAuthGrantType[] {
  return draft.refreshTokens
    ? [OAuthGrantType.AuthorizationCode, OAuthGrantType.RefreshToken]
    : [OAuthGrantType.AuthorizationCode];
}

export function toCreate(draft: OAuthClientDraft): OAuthClientCreate {
  return {
    clientId: draft.clientId.trim(),
    name: draft.name.trim(),
    confidential: draft.confidential,
    redirectUris: cleanRedirectUris(draft.redirectUris),
    scopes: orderedScopes(['openid', ...draft.scopes]),
    grantTypes: grantTypes(draft),
    requiredRegionIds: [...new Set(draft.requiredRegionIds)],
    enabled: draft.enabled,
  };
}

/**
 * The PATCH body. `confidential` is never sent: the server refuses turning a public client confidential (it has no
 * secret) and the dialog shows the kind read-only.
 */
export function toUpdate(draft: OAuthClientDraft): OAuthClientUpdate {
  const body = toCreate(draft);
  return {
    name: body.name,
    redirectUris: body.redirectUris,
    scopes: body.scopes,
    grantTypes: body.grantTypes,
    requiredRegionIds: body.requiredRegionIds,
    enabled: body.enabled,
  };
}
