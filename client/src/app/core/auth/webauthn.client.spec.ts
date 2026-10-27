import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, vi } from 'vitest';

import { toApiErrorView } from '../data/api-error';
import { provideDataStateTesting } from '../testing/data-state-testing';
import {
  WebAuthnClient,
  authenticationBody,
  base64UrlDecode,
  base64UrlEncode,
  describeUserAgent,
  isPasskeyCancelled,
  registrationBody,
  suggestPasskeyLabel,
  toCreationOptions,
  toRequestOptions,
} from './webauthn.client';

const bytes = (...values: number[]) => new Uint8Array(values).buffer;

function fakeAttestationCredential(): PublicKeyCredential {
  return {
    id: 'cred-1',
    rawId: bytes(1, 2, 3),
    type: 'public-key',
    authenticatorAttachment: 'platform',
    getClientExtensionResults: () => ({ credProps: { rk: true } }),
    response: {
      attestationObject: bytes(4, 5),
      clientDataJSON: bytes(6),
      getTransports: () => ['internal', 'hybrid'],
    },
  } as unknown as PublicKeyCredential;
}

function fakeAssertionCredential(): PublicKeyCredential {
  return {
    id: 'cred-1',
    rawId: bytes(1, 2, 3),
    type: 'public-key',
    authenticatorAttachment: 'platform',
    getClientExtensionResults: () => ({}),
    response: {
      authenticatorData: bytes(7),
      clientDataJSON: bytes(8),
      signature: bytes(9, 10),
      userHandle: bytes(11),
    },
  } as unknown as PublicKeyCredential;
}

const CREATION_OPTIONS = {
  rp: { id: 'localhost', name: 'foodsharing' },
  user: { id: base64UrlEncode(bytes(42)), name: 'anna@local', displayName: 'Anna' },
  challenge: base64UrlEncode(bytes(255, 254, 253)),
  pubKeyCredParams: [{ type: 'public-key', alg: -7 }],
  excludeCredentials: [{ type: 'public-key', id: base64UrlEncode(bytes(1)) }],
  authenticatorSelection: { residentKey: 'required' },
  attestation: 'none',
};

describe('webauthn helpers', () => {
  it('round-trips base64url without padding', () => {
    const encoded = base64UrlEncode(bytes(251, 255, 191, 0));
    expect(encoded).toBe('-_-_AA');
    expect([...new Uint8Array(base64UrlDecode(encoded))]).toEqual([251, 255, 191, 0]);
    expect(base64UrlEncode(null)).toBe('');
  });

  it('converts the JSON options into the binary form the browser expects', () => {
    const creation = toCreationOptions(CREATION_OPTIONS);
    expect([...new Uint8Array(creation.challenge as ArrayBuffer)]).toEqual([255, 254, 253]);
    expect([...new Uint8Array(creation.user.id as ArrayBuffer)]).toEqual([42]);
    expect([...new Uint8Array(creation.excludeCredentials![0].id as ArrayBuffer)]).toEqual([1]);

    const request = toRequestOptions({
      challenge: base64UrlEncode(bytes(9)),
      rpId: 'localhost',
      allowCredentials: [],
    });
    expect([...new Uint8Array(request.challenge as ArrayBuffer)]).toEqual([9]);
    expect(request.rpId).toBe('localhost');
  });

  it('builds the Spring Security request bodies', () => {
    expect(registrationBody(fakeAttestationCredential(), 'Laptop')).toEqual({
      publicKey: {
        credential: {
          id: 'cred-1',
          rawId: 'AQID',
          type: 'public-key',
          response: {
            attestationObject: 'BAU',
            clientDataJSON: 'Bg',
            transports: ['internal', 'hybrid'],
          },
          clientExtensionResults: { credProps: { rk: true } },
          authenticatorAttachment: 'platform',
        },
        label: 'Laptop',
      },
    });
    expect(authenticationBody(fakeAssertionCredential())).toEqual({
      id: 'cred-1',
      rawId: 'AQID',
      type: 'public-key',
      response: {
        authenticatorData: 'Bw',
        clientDataJSON: 'CA',
        signature: 'CQo',
        userHandle: 'Cw',
      },
      clientExtensionResults: {},
      authenticatorAttachment: 'platform',
    });
  });

  it('names devices from the user agent', () => {
    const mac =
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36';
    const iphone =
      'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1';
    expect(describeUserAgent(mac)).toEqual({ browser: 'Chrome', os: 'macOS', mobile: false });
    expect(describeUserAgent(iphone)).toEqual({ browser: 'Safari', os: 'iOS', mobile: true });
    expect(describeUserAgent(undefined)).toEqual({ browser: null, os: null, mobile: false });
    expect(suggestPasskeyLabel(mac)).toBe('Chrome · macOS');
    expect(suggestPasskeyLabel('')).toBe('Passkey');
  });
});

describe('WebAuthnClient', () => {
  let http: HttpTestingController;
  let client: WebAuthnClient;
  const originalCredential = (globalThis as { PublicKeyCredential?: unknown }).PublicKeyCredential;
  const originalCredentials = navigator.credentials;

  function installCredentials(api: Partial<CredentialsContainer>): void {
    (globalThis as { PublicKeyCredential?: unknown }).PublicKeyCredential = function () {
      return undefined;
    };
    Object.defineProperty(navigator, 'credentials', { value: api, configurable: true });
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideDataStateTesting() });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(WebAuthnClient);
  });

  afterEach(() => {
    (globalThis as { PublicKeyCredential?: unknown }).PublicKeyCredential = originalCredential;
    Object.defineProperty(navigator, 'credentials', {
      value: originalCredentials,
      configurable: true,
    });
    http.verify();
  });

  it('registers: options → navigator.credentials.create → POST /webauthn/register', async () => {
    const create = vi.fn().mockResolvedValue(fakeAttestationCredential());
    installCredentials({ create });

    const done = firstValueFrom(client.register('Laptop'));
    http.expectOne({ method: 'POST', url: '/webauthn/register/options' }).flush(CREATION_OPTIONS);
    await vi.waitFor(() => expect(create).toHaveBeenCalled());
    const registration = await vi.waitFor(() =>
      http.expectOne({ method: 'POST', url: '/webauthn/register' }),
    );
    expect(registration.request.body).toEqual(
      registrationBody(fakeAttestationCredential(), 'Laptop'),
    );
    registration.flush({ success: true });
    await expect(done).resolves.toEqual({ success: true });
  });

  it('logs in: options → navigator.credentials.get → POST /login/webauthn', async () => {
    const get = vi.fn().mockResolvedValue(fakeAssertionCredential());
    installCredentials({ get });
    const abort = new AbortController();

    const done = firstValueFrom(client.login({ mediation: 'conditional', signal: abort.signal }));
    http
      .expectOne({ method: 'POST', url: '/webauthn/authenticate/options' })
      .flush({ challenge: base64UrlEncode(bytes(1)), rpId: 'localhost' });
    await vi.waitFor(() => expect(get).toHaveBeenCalled());
    expect(get.mock.calls[0][0]).toEqual(
      expect.objectContaining({ mediation: 'conditional', signal: abort.signal }),
    );
    const login = await vi.waitFor(() =>
      http.expectOne({ method: 'POST', url: '/login/webauthn' }),
    );
    login.flush({ authenticated: true, redirectUrl: '/' });
    await expect(done).resolves.toEqual({ authenticated: true, redirectUrl: '/' });
  });

  it('reports a cancelled prompt as PASSKEY_CANCELLED', async () => {
    const error = new Error('The operation either timed out or was not allowed.');
    error.name = 'NotAllowedError';
    installCredentials({ get: vi.fn().mockRejectedValue(error) });

    const done = firstValueFrom(client.login());
    http
      .expectOne('/webauthn/authenticate/options')
      .flush({ challenge: base64UrlEncode(bytes(1)) });
    const failure = await done.then(
      () => undefined,
      (thrown: unknown) => toApiErrorView(thrown),
    );
    expect(isPasskeyCancelled(failure)).toBe(true);
  });

  it('knows when WebAuthn is missing', async () => {
    (globalThis as { PublicKeyCredential?: unknown }).PublicKeyCredential = undefined;
    expect(client.isSupported()).toBe(false);
    await expect(client.isConditionalMediationAvailable()).resolves.toBe(false);
  });
});
