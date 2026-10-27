import { Observable, switchMap } from 'rxjs';

import { AuthService, SessionInfo } from '../../api';
import { WebAuthnClient } from '../../core/auth/webauthn.client';

/**
 * Passkey login followed by the session bootstrap (POST /login/webauthn only answers `{authenticated}`).
 * Used by the login page's button (inside runMutation) and by the autofill flow (conditional mediation).
 */
export function passkeyLoginWithSession(
  webauthn: WebAuthnClient,
  authApi: AuthService,
  options: { mediation?: CredentialMediationRequirement; signal?: AbortSignal } = {},
): Observable<SessionInfo> {
  return webauthn.login(options).pipe(switchMap(() => authApi.getSession()));
}
