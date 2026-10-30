import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import { ApiErrorCode, ConsentRequest, OAuthClient, OAuthGrantType, RegionRef, UnitType } from '../../api';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { ConnectionsPageComponent } from '../account/pages/connections-page.component';
import { OAUTH_REDIRECT } from './oauth-redirect';
import {
  cleanRedirectUris,
  draftOf,
  draftProblems,
  emptyDraft,
  orderedScopes,
  redirectUriProblem,
  suggestClientId,
  toCreate,
  toUpdate,
  validClientId,
} from './oauth.util';
import oauthClientCases from '../../../../../server/src/test/resources/rule-cases/oauth-client.json';
import { ConsentPageComponent } from './pages/consent-page.component';
import { OAuthClientsPageComponent, regionNameMap } from './pages/oauth-clients-page.component';

const ZURICH: RegionRef = {
  id: '00000010-0001-4000-8000-000000000040',
  slug: 'zuerich-stadt',
  name: 'Zürich Stadt',
  type: UnitType.City,
};

const WIKI: OAuthClient = {
  clientId: 'wiki',
  name: 'Foodsharing Wiki Zürich',
  confidential: true,
  redirectUris: ['https://wiki.example.org/callback'],
  scopes: ['openid', 'profile', 'email', 'regions'],
  grantTypes: [OAuthGrantType.AuthorizationCode, OAuthGrantType.RefreshToken],
  requiredRegionIds: [ZURICH.id],
  requiredRegions: [ZURICH],
  enabled: true,
  createdAt: '2026-09-15T08:00:00Z',
  consentCount: 1,
};

const REQUEST: ConsentRequest = {
  client: { clientId: 'mobile-dev', name: 'Foodsharing Mobile' },
  scopes: ['email', 'openid', 'profile'],
  alreadyRemembered: false,
  regionAllowed: true,
  requiredRegions: [],
  expiresAt: '2026-11-02T10:10:00Z',
};

describe('oauth util (C13)', () => {
  it('copies the server redirect URI rule', () => {
    expect(redirectUriProblem('https://app.example.org/cb')).toBeNull();
    expect(redirectUriProblem('http://localhost:8080/dev/oauth-callback')).toBeNull();
    expect(redirectUriProblem('http://127.0.0.1/cb')).toBeNull();
    expect(redirectUriProblem('http://app.example.org/cb')).toBe('insecure');
    expect(redirectUriProblem('https://app.example.org/cb#frag')).toBe('invalid');
    expect(redirectUriProblem('/relative')).toBe('invalid');
    expect(redirectUriProblem('https://a b')).toBe('invalid');
    expect(redirectUriProblem('mailto:x@example.org')).toBe('invalid');
    expect(redirectUriProblem('  ')).toBe('required');
  });

  it('checks and suggests client ids', () => {
    expect(validClientId('wiki')).toBe(true);
    expect(validClientId('my.app_2-x')).toBe(true);
    expect(validClientId('ab')).toBe(false);
    expect(validClientId('Wiki')).toBe(false);
    expect(validClientId('a'.repeat(61))).toBe(false);
    expect(suggestClientId('Foodsharing Wiki Zürich!')).toBe('foodsharing-wiki-zurich');
  });

  it('runs the shared server cases (OAuthRulesTest reads the same file)', () => {
    const { redirectUris, clientIds } = oauthClientCases;
    expect(redirectUris.valid.length).toBeGreaterThan(5);
    for (const uri of redirectUris.valid) expect(redirectUriProblem(uri), uri).toBeNull();
    for (const uri of redirectUris.invalid) expect(redirectUriProblem(uri), uri).not.toBeNull();
    for (const id of clientIds.valid) expect(validClientId(id), id).toBe(true);
    for (const id of clientIds.invalid) expect(validClientId(id), id).toBe(false);
  });

  it('orders known scopes and drops unknown ones', () => {
    expect(orderedScopes(['regions', 'x', 'openid', 'email'])).toEqual(['openid', 'email', 'regions']);
  });

  it('validates the draft', () => {
    expect(draftProblems(emptyDraft(), true)).toEqual(['clientIdInvalid', 'nameRequired', 'redirectRequired']);
    const ok = { ...emptyDraft(), clientId: 'app', name: 'App', redirectUris: ['https://a.example/cb', ''] };
    expect(draftProblems(ok, true)).toEqual([]);
    expect(draftProblems({ ...ok, clientId: '' }, false)).toEqual([]);
    expect(draftProblems({ ...ok, redirectUris: ['http://a.example/cb'] }, true)).toEqual(['redirectInvalid']);
    expect(draftProblems({ ...ok, redirectUris: Array.from({ length: 11 }, (_, i) => `https://a.example/${i}`) }, true)).toEqual([
      'redirectTooMany',
    ]);
    expect(cleanRedirectUris([' https://a.example/cb ', '', 'https://a.example/cb'])).toEqual(['https://a.example/cb']);
  });

  it('builds create and update bodies', () => {
    const draft = { ...draftOf(WIKI), refreshTokens: false, scopes: ['email' as const] };
    expect(toCreate(draft)).toEqual({
      clientId: 'wiki',
      name: 'Foodsharing Wiki Zürich',
      confidential: true,
      redirectUris: ['https://wiki.example.org/callback'],
      scopes: ['openid', 'email'],
      grantTypes: [OAuthGrantType.AuthorizationCode],
      requiredRegionIds: ['00000010-0001-4000-8000-000000000040'],
      enabled: true,
    });
    const update = toUpdate(draftOf(WIKI));
    expect(update).not.toHaveProperty('clientId');
    expect(update).not.toHaveProperty('confidential');
    expect(update.grantTypes).toEqual([OAuthGrantType.AuthorizationCode, OAuthGrantType.RefreshToken]);
  });

  it('takes region names from the clients and the picker, later sources win', () => {
    const names = regionNameMap([ZURICH], undefined, [{ ...ZURICH, name: 'Zürich' }, { ...ZURICH, id: 'b', name: 'Bern' }]);
    expect(names.get(ZURICH.id)).toBe('Zürich');
    expect(names.get('b')).toBe('Bern');
    expect(regionNameMap().size).toBe(0);
  });
});

describe('oauth consent page (C13)', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ConsentPageComponent>;
  const redirect = vi.fn();
  const q = (testId: string) => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null;
  const settle = () => fixture.whenStable();

  beforeEach(() => {
    redirect.mockReset();
    TestBed.configureTestingModule({
      providers: [...provideDataStateTesting(), provideOptimus(), { provide: OAUTH_REDIRECT, useValue: redirect }],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ConsentPageComponent);
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('without a request id explains that the request is missing', async () => {
    await settle();
    expect(q('oauth.consent.missing')).not.toBeNull();
  });

  it('shows client and scopes, approves with remember and leaves for the redirect URI', async () => {
    fixture.componentRef.setInput('request', 'r1');
    await settle();
    expect(q('oauth.consent.state')?.getAttribute('data-state')).toBe('loading');
    http.expectOne('/api/oauth/authorize-requests/r1').flush(REQUEST);
    await settle();
    expect(q('oauth.consent.state')?.getAttribute('data-state')).toBe('success');
    const scopes = [...fixture.nativeElement.querySelectorAll('[data-testid="oauth.consent.scopes.item"]')].map((li) =>
      (li as HTMLElement).getAttribute('data-scope'),
    );
    expect(scopes).toEqual(['openid', 'profile', 'email']);
    (q('oauth.consent.remember')?.querySelector('input') as HTMLInputElement).click();
    await settle();
    (q('oauth.consent.approve')?.querySelector('button') as HTMLButtonElement).click();
    const decision = http.expectOne('/api/oauth/authorize-requests/r1/decision');
    expect(decision.request.body).toEqual({ approve: true, remember: true });
    decision.flush({ redirectUri: 'http://localhost:8080/dev/oauth-callback?code=abc' });
    await settle();
    expect(redirect).toHaveBeenCalledWith('http://localhost:8080/dev/oauth-callback?code=abc');
  });

  it('region-restricted client: no approve button, going back declines', async () => {
    fixture.componentRef.setInput('request', 'r2');
    await settle();
    http.expectOne('/api/oauth/authorize-requests/r2').flush({ ...REQUEST, regionAllowed: false, requiredRegions: [ZURICH] });
    await settle();
    expect(q('oauth.consent.regionRequired')).not.toBeNull();
    // the required regions are named
    expect(q('oauth.consent.requiredRegion')?.getAttribute('data-region')).toBe(ZURICH.id);
    expect(q('oauth.consent.requiredRegion')?.textContent?.trim()).toBe('Zürich Stadt');
    expect(q('oauth.consent.approve')).toBeNull();
    (q('oauth.consent.back')?.querySelector('button') as HTMLButtonElement).click();
    const decision = http.expectOne('/api/oauth/authorize-requests/r2/decision');
    expect(decision.request.body).toEqual({ approve: false, remember: false });
    decision.flush({ redirectUri: 'https://app.example/cb?error=access_denied' });
    await settle();
    expect(redirect).toHaveBeenCalledWith('https://app.example/cb?error=access_denied');
  });

  it('an expired request is the not-found state', async () => {
    fixture.componentRef.setInput('request', 'gone');
    await settle();
    http
      .expectOne('/api/oauth/authorize-requests/gone')
      .flush(apiHttpError(404, ApiErrorCode.OauthRequestExpired).error, { status: 404, statusText: 'Not Found' });
    await settle();
    expect(q('oauth.consent.state')?.getAttribute('data-state')).toBe('notFound');
  });
});

describe('oauth clients and connections pages (C13)', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector) as HTMLElement | null;
  const qa = (selector: string) => harness.routeNativeElement!.querySelectorAll(selector);
  const state = (testId: string) => q(`[data-testid="${testId}"]`)?.getAttribute('data-state');
  const settle = () => harness.fixture.whenStable();
  const click = (testId: string) => (q(`[data-testid="${testId}"] button`) as HTMLButtonElement).click();
  const type = (testId: string, value: string) => {
    const input = q(`[data-testid="${testId}"]`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            { path: 'clients', component: OAuthClientsPageComponent },
            { path: 'connections', component: ConnectionsPageComponent },
          ],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('clients: empty state, create validates, then shows the secret once', async () => {
    await harness.navigateByUrl('/clients');
    http.expectOne('/api/admin/oauth-clients').flush([]);
    await settle();
    expect(state('oauth.clients.state')).toBe('empty');

    click('oauth.clients.create');
    await settle();
    (q('[data-testid="oauth.clients.form.save"] button') as HTMLButtonElement).click();
    await settle();
    expect(q('[data-testid="oauth.clients.form.nameError"]')).not.toBeNull();

    type('oauth.clients.form.name', 'Partner App');
    type('oauth.clients.form.redirectUris.input', 'https://partner.example/cb');
    await settle();
    expect((q('[data-testid="oauth.clients.form.clientId"]') as HTMLInputElement).value).toBe('partner-app');
    (q('[data-testid="oauth.clients.form.save"] button') as HTMLButtonElement).click();
    const create = http.expectOne((req) => req.method === 'POST' && req.url === '/api/admin/oauth-clients');
    expect(create.request.body).toMatchObject({
      clientId: 'partner-app',
      confidential: true,
      redirectUris: ['https://partner.example/cb'],
      scopes: ['openid', 'profile'],
      requiredRegionIds: [],
    });
    create.flush({ client: { ...WIKI, clientId: 'partner-app', name: 'Partner App', requiredRegionIds: [], requiredRegions: [] }, clientSecret: 's3cret' });
    await settle();
    expect(qa('[data-testid="oauth.clients.row"]').length).toBe(1);
    expect(q('[data-testid="oauth.clients.secret.value"]')?.textContent?.trim()).toBe('s3cret');
  });

  it('clients: a taken client id is shown in the dialog', async () => {
    await harness.navigateByUrl('/clients');
    http.expectOne('/api/admin/oauth-clients').flush([]);
    await settle();
    click('oauth.clients.create');
    await settle();
    type('oauth.clients.form.name', 'Wiki');
    type('oauth.clients.form.redirectUris.input', 'https://wiki.example/cb');
    await settle();
    (q('[data-testid="oauth.clients.form.save"] button') as HTMLButtonElement).click();
    http
      .expectOne((req) => req.method === 'POST')
      .flush(apiHttpError(409, ApiErrorCode.OauthClientExists).error, { status: 409, statusText: 'Conflict' });
    await settle();
    expect(q('[data-testid="oauth.clients.form.error"]')).not.toBeNull();
  });

  it('clients: region names come with the list (no request per region), disabling asks first', async () => {
    await harness.navigateByUrl('/clients');
    http.expectOne('/api/admin/oauth-clients').flush([WIKI]);
    await settle();
    http.expectNone((req) => req.url.startsWith('/api/regions/'));
    expect(q('[data-testid="oauth.clients.rowRegions"]')?.textContent).toContain('Zürich Stadt');

    const confirm = vi.spyOn(TestBed.inject(ConfirmationService), 'confirm');
    click('oauth.clients.toggle');
    expect(confirm).toHaveBeenCalled();
    confirm.mock.calls[0][0].accept!();
    const patch = http.expectOne('/api/admin/oauth-clients/wiki');
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ enabled: false });
    patch.flush({ ...WIKI, enabled: false });
    await settle();
    expect(q('[data-testid="oauth.clients.row"]')?.getAttribute('data-enabled')).toBe('false');
  });

  it('clients: error state with retry', async () => {
    await harness.navigateByUrl('/clients');
    http.expectOne('/api/admin/oauth-clients').flush(apiHttpError(503, ApiErrorCode.UnexpectedError).error, { status: 503, statusText: 'Error' });
    await settle();
    expect(state('oauth.clients.state')).toBe('error');
  });

  it('connections: list and revoke after confirm', async () => {
    await harness.navigateByUrl('/connections');
    expect(state('account.connections.state')).toBe('loading');
    http.expectOne('/api/users/me/connections').flush([
      { clientId: 'wiki', clientName: 'Foodsharing Wiki Zürich', scopes: ['openid', 'regions'], grantedAt: '2026-10-01T18:00:00Z' },
    ]);
    await settle();
    expect(qa('[data-testid="account.connections.item"]').length).toBe(1);
    const confirm = vi.spyOn(TestBed.inject(ConfirmationService), 'confirm');
    click('account.connections.revoke');
    confirm.mock.calls[0][0].accept!();
    http.expectOne((req) => req.method === 'DELETE' && req.url === '/api/users/me/connections/wiki').flush(null);
    await settle();
    expect(state('account.connections.state')).toBe('empty');
  });
});
