import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, defer, map, switchMap } from 'rxjs';

import { ApiErrorView } from '../data/api-error';

/**
 * Passkeys (WebAuthn) through Spring Security's framework endpoints, which are not part of the OpenAPI contract
 * (listed under `x-framework-endpoints`), so this is the one place that talks to them with HttpClient:
 *
 *  - register: POST /webauthn/register/options → navigator.credentials.create() → POST /webauthn/register
 *  - log in:   POST /webauthn/authenticate/options → navigator.credentials.get() → POST /login/webauthn
 *
 * Binary fields travel as base64url. The XSRF header is added by Angular's XSRF interceptor (relative URLs).
 */

export const PASSKEY_CANCELLED = 'PASSKEY_CANCELLED';
export const PASSKEY_UNSUPPORTED = 'PASSKEY_UNSUPPORTED';

/** Options JSON as Spring Security serialises PublicKeyCredentialCreationOptions. */
export interface CreationOptionsJson {
  rp: { id?: string; name: string };
  user: { id: string; name: string; displayName: string };
  challenge: string;
  pubKeyCredParams: { type: string; alg: number }[];
  timeout?: number;
  excludeCredentials?: { type: string; id: string; transports?: string[] }[];
  authenticatorSelection?: Record<string, unknown>;
  attestation?: string;
  extensions?: Record<string, unknown>;
}

/** Options JSON as Spring Security serialises PublicKeyCredentialRequestOptions. */
export interface RequestOptionsJson {
  challenge: string;
  timeout?: number;
  rpId?: string;
  allowCredentials?: { type: string; id: string; transports?: string[] }[];
  userVerification?: string;
  extensions?: Record<string, unknown>;
}

export interface PasskeyLoginResult {
  authenticated: boolean;
  redirectUrl?: string;
}

export function base64UrlEncode(buffer: ArrayBuffer | ArrayBufferView | null | undefined): string {
  if (!buffer) {
    return '';
  }
  const bytes =
    buffer instanceof ArrayBuffer
      ? new Uint8Array(buffer)
      : new Uint8Array(buffer.buffer, buffer.byteOffset, buffer.byteLength);
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function base64UrlDecode(value: string): ArrayBuffer {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

export function toCreationOptions(json: CreationOptionsJson): PublicKeyCredentialCreationOptions {
  return {
    ...json,
    rp: json.rp,
    user: { ...json.user, id: base64UrlDecode(json.user.id) },
    challenge: base64UrlDecode(json.challenge),
    pubKeyCredParams: json.pubKeyCredParams as PublicKeyCredentialParameters[],
    excludeCredentials: (json.excludeCredentials ?? []).map((credential) => ({
      ...credential,
      type: 'public-key',
      id: base64UrlDecode(credential.id),
      transports: credential.transports as AuthenticatorTransport[] | undefined,
    })),
    authenticatorSelection: json.authenticatorSelection as AuthenticatorSelectionCriteria,
    attestation: json.attestation as AttestationConveyancePreference | undefined,
    extensions: json.extensions as AuthenticationExtensionsClientInputs | undefined,
  };
}

export function toRequestOptions(json: RequestOptionsJson): PublicKeyCredentialRequestOptions {
  return {
    ...json,
    challenge: base64UrlDecode(json.challenge),
    allowCredentials: (json.allowCredentials ?? []).map((credential) => ({
      ...credential,
      type: 'public-key',
      id: base64UrlDecode(credential.id),
      transports: credential.transports as AuthenticatorTransport[] | undefined,
    })),
    userVerification: json.userVerification as UserVerificationRequirement | undefined,
    extensions: json.extensions as AuthenticationExtensionsClientInputs | undefined,
  };
}

/** Body of POST /webauthn/register. */
export function registrationBody(credential: PublicKeyCredential, label: string): unknown {
  const response = credential.response as AuthenticatorAttestationResponse;
  return {
    publicKey: {
      credential: {
        id: credential.id,
        rawId: base64UrlEncode(credential.rawId),
        type: credential.type,
        response: {
          attestationObject: base64UrlEncode(response.attestationObject),
          clientDataJSON: base64UrlEncode(response.clientDataJSON),
          transports: typeof response.getTransports === 'function' ? response.getTransports() : [],
        },
        clientExtensionResults: credential.getClientExtensionResults(),
        authenticatorAttachment: credential.authenticatorAttachment ?? undefined,
      },
      label,
    },
  };
}

/** Body of POST /login/webauthn. */
export function authenticationBody(credential: PublicKeyCredential): unknown {
  const response = credential.response as AuthenticatorAssertionResponse;
  return {
    id: credential.id,
    rawId: base64UrlEncode(credential.rawId),
    type: credential.type,
    response: {
      authenticatorData: base64UrlEncode(response.authenticatorData),
      clientDataJSON: base64UrlEncode(response.clientDataJSON),
      signature: base64UrlEncode(response.signature),
      userHandle: response.userHandle ? base64UrlEncode(response.userHandle) : null,
    },
    clientExtensionResults: credential.getClientExtensionResults(),
    authenticatorAttachment: credential.authenticatorAttachment ?? undefined,
  };
}

/** True when the user closed the browser's passkey prompt (no error message needed). */
export function isPasskeyCancelled(view: ApiErrorView | undefined): boolean {
  return (view?.code as string | undefined) === PASSKEY_CANCELLED;
}

/** A client-side failure as ApiErrorView, so runMutation / fs-error-state can show it. */
function clientError(code: string, message: string): ApiErrorView {
  return {
    status: -1,
    code: code as ApiErrorView['code'],
    message,
    retryable: false,
    fieldErrors: [],
  };
}

/** Maps a DOMException of the browser ceremony (cancelled, timed out, not allowed) to an ApiErrorView. */
export function ceremonyError(error: unknown): ApiErrorView {
  const name = error instanceof Error ? error.name : '';
  if (name === 'NotAllowedError' || name === 'AbortError') {
    return clientError(PASSKEY_CANCELLED, 'Passkey ceremony was cancelled');
  }
  if (name === 'InvalidStateError') {
    return clientError('PASSKEY_EXISTS', 'This authenticator is already registered');
  }
  if (name === 'NotSupportedError' || name === 'SecurityError') {
    return clientError(PASSKEY_UNSUPPORTED, error instanceof Error ? error.message : name);
  }
  return clientError('UNKNOWN', error instanceof Error ? error.message : String(error));
}

@Injectable({ providedIn: 'root' })
export class WebAuthnClient {
  private readonly http = inject(HttpClient);

  /** True when the browser can create and use passkeys at all. */
  isSupported(): boolean {
    return (
      typeof window !== 'undefined' &&
      typeof window.PublicKeyCredential === 'function' &&
      !!globalThis.navigator?.credentials
    );
  }

  /** True when passkeys can be offered in the email field's autofill (conditional mediation). */
  async isConditionalMediationAvailable(): Promise<boolean> {
    if (!this.isSupported()) {
      return false;
    }
    const check = (
      PublicKeyCredential as unknown as { isConditionalMediationAvailable?: () => Promise<boolean> }
    ).isConditionalMediationAvailable;
    if (typeof check !== 'function') {
      return false;
    }
    try {
      return await check.call(PublicKeyCredential);
    } catch {
      return false;
    }
  }

  /** Creates a passkey for the logged-in user and stores it under `label`. */
  register(label: string): Observable<unknown> {
    return this.http
      .post<CreationOptionsJson>('/webauthn/register/options', null)
      .pipe(
        switchMap((options) =>
          defer(() => this.create(toCreationOptions(options))).pipe(
            switchMap((credential) =>
              this.http.post('/webauthn/register', registrationBody(credential, label)),
            ),
          ),
        ),
      );
  }

  /**
   * Logs in with a passkey. `mediation: 'conditional'` waits for the user to pick a passkey from the
   * email field's autofill; abort it with `signal` before starting a modal ceremony.
   */
  login(
    options: { mediation?: CredentialMediationRequirement; signal?: AbortSignal } = {},
  ): Observable<PasskeyLoginResult> {
    return this.http.post<RequestOptionsJson>('/webauthn/authenticate/options', null).pipe(
      switchMap((json) =>
        defer(() => this.get(toRequestOptions(json), options.mediation, options.signal)),
      ),
      switchMap((credential) =>
        this.http.post<PasskeyLoginResult>('/login/webauthn', authenticationBody(credential)),
      ),
      map((result) => result ?? { authenticated: true }),
    );
  }

  private async create(
    publicKey: PublicKeyCredentialCreationOptions,
  ): Promise<PublicKeyCredential> {
    if (!this.isSupported()) {
      throw clientError(PASSKEY_UNSUPPORTED, 'WebAuthn is not available');
    }
    try {
      const credential = await navigator.credentials.create({ publicKey });
      if (!credential) {
        throw clientError(PASSKEY_CANCELLED, 'No credential created');
      }
      return credential as PublicKeyCredential;
    } catch (error) {
      throw isView(error) ? error : ceremonyError(error);
    }
  }

  private async get(
    publicKey: PublicKeyCredentialRequestOptions,
    mediation?: CredentialMediationRequirement,
    signal?: AbortSignal,
  ): Promise<PublicKeyCredential> {
    if (!this.isSupported()) {
      throw clientError(PASSKEY_UNSUPPORTED, 'WebAuthn is not available');
    }
    try {
      const credential = await navigator.credentials.get({
        publicKey,
        ...(mediation ? { mediation } : {}),
        ...(signal ? { signal } : {}),
      });
      if (!credential) {
        throw clientError(PASSKEY_CANCELLED, 'No credential selected');
      }
      return credential as PublicKeyCredential;
    } catch (error) {
      throw isView(error) ? error : ceremonyError(error);
    }
  }
}

function isView(value: unknown): value is ApiErrorView {
  return !!value && typeof value === 'object' && 'fieldErrors' in value && 'code' in value;
}

/** Name for a new passkey derived from the user agent ("Chrome on macOS"). */
export function suggestPasskeyLabel(
  userAgent: string = globalThis.navigator?.userAgent ?? '',
): string {
  const { browser, os } = describeUserAgent(userAgent);
  return browser && os ? `${browser} · ${os}` : browser || os || 'Passkey';
}

/** Rough browser / OS names for session lists and passkey labels (no library, good enough for humans). */
export function describeUserAgent(userAgent: string | null | undefined): {
  browser: string | null;
  os: string | null;
  mobile: boolean;
} {
  const ua = userAgent ?? '';
  const browser = /Edg\//.test(ua)
    ? 'Edge'
    : /OPR\/|Opera/.test(ua)
      ? 'Opera'
      : /Firefox\/|FxiOS/.test(ua)
        ? 'Firefox'
        : /Chrome\/|CriOS/.test(ua)
          ? 'Chrome'
          : /Safari\//.test(ua)
            ? 'Safari'
            : null;
  const os = /iPhone|iPad|iPod/.test(ua)
    ? 'iOS'
    : /Android/.test(ua)
      ? 'Android'
      : /Mac OS X|Macintosh/.test(ua)
        ? 'macOS'
        : /Windows/.test(ua)
          ? 'Windows'
          : /CrOS/.test(ua)
            ? 'ChromeOS'
            : /Linux/.test(ua)
              ? 'Linux'
              : null;
  return { browser, os, mobile: /Mobile|Android|iPhone|iPad/.test(ua) };
}
