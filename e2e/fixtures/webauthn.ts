import type { CDPSession, Page } from '@playwright/test';

export interface VirtualAuthenticatorOptions {
  protocol?: 'ctap2' | 'u2f';
  transport?: 'internal' | 'usb' | 'nfc' | 'ble' | 'hybrid';
  hasResidentKey?: boolean;
  hasUserVerification?: boolean;
  isUserVerified?: boolean;
  automaticPresenceSimulation?: boolean;
}

export interface VirtualCredential {
  credentialId: string;
  isResidentCredential: boolean;
  rpId?: string;
  userHandle?: string;
  signCount: number;
}

export interface VirtualAuthenticator {
  authenticatorId: string;
  cdp: CDPSession;
  credentials(): Promise<VirtualCredential[]>;
  /** Simulate the user approving / not approving the next ceremony. */
  setUserVerified(verified: boolean): Promise<void>;
  remove(): Promise<void>;
}

/**
 * Adds a CDP virtual authenticator (platform passkey, user verification on) to the page — Chromium only.
 * Passkey tests carry the `@chromium-only` tag; the config filters that tag out of the Firefox/WebKit projects,
 * so tests never skip themselves.
 */
export async function addVirtualAuthenticator(
  page: Page,
  options: VirtualAuthenticatorOptions = {},
): Promise<VirtualAuthenticator> {
  const browserName = page.context().browser()?.browserType().name();
  if (browserName !== 'chromium') {
    throw new Error(`Virtual WebAuthn authenticators need Chromium (got ${browserName}); tag the test @chromium-only`);
  }
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('WebAuthn.enable', { enableUI: false });
  const { authenticatorId } = await cdp.send('WebAuthn.addVirtualAuthenticator', {
    options: {
      protocol: options.protocol ?? 'ctap2',
      transport: options.transport ?? 'internal',
      hasResidentKey: options.hasResidentKey ?? true,
      hasUserVerification: options.hasUserVerification ?? true,
      isUserVerified: options.isUserVerified ?? true,
      automaticPresenceSimulation: options.automaticPresenceSimulation ?? true,
    },
  });
  return {
    authenticatorId,
    cdp,
    credentials: async () => (await cdp.send('WebAuthn.getCredentials', { authenticatorId })).credentials,
    setUserVerified: async (isUserVerified) => {
      await cdp.send('WebAuthn.setUserVerified', { authenticatorId, isUserVerified });
    },
    remove: async () => {
      await cdp.send('WebAuthn.removeVirtualAuthenticator', { authenticatorId });
    },
  };
}

export const webauthn = { addVirtualAuthenticator };
export type WebAuthnHelpers = typeof webauthn;
