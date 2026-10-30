import {
  OAUTH_CLIENTS,
  SEED_OAUTH_REDIRECT,
  acceptPendingConsents,
  authorizeLocation,
  authorizeUrl,
  callbackParams,
  devGetLastOAuthCode,
  expect,
  expectDataState,
  expectErrorState,
  exchangeCode,
  injectApiError,
  pkce,
  refreshToken,
  regionId,
  roleEmail,
  test,
  userinfo,
  type TokenResponse,
} from '../../fixtures';
import { acceptConfirm } from '../auth/_auth';

/**
 * C13 OAuth provider (plan-m7 Q14, A44/A56): ORGA registers a client on `/admin/oauth-clients`; a user signs in to it
 * through `/oauth/authorize` → `/oauth/consent` → the client's redirect URI (`/dev/oauth-callback`, served by the
 * backend in e2e), the client exchanges the code (PKCE) and reads userinfo; a remembered consent skips the consent
 * page; the user revokes the connection on `/account/connections`.
 */
test.use({ seedReset: 'test' });

const ZUERICH_STADT = regionId('zuerich-stadt');

test('C13 orga creates PKCE client; user logs in, consents with remember, callback receives code, token and userinfo return profile and regions; second authorize skips consent; user revokes connection @C13 @page', async ({
  page,
  as,
  apiOf,
  baseURL,
}) => {
  const redirectUri = `${baseURL}/dev/oauth-callback`;

  // --- ORGA registers a public (PKCE) client ---
  const orga = await as('orga');
  await orga.goto('/admin/oauth-clients');
  await expectDataState(orga, 'oauth.clients.state', 'success');
  await orga.getByTestId('oauth.clients.create').click();
  await orga.getByTestId('oauth.clients.form.name').fill('E2E Partner App');
  await expect(orga.getByTestId('oauth.clients.form.clientId')).toHaveValue('e2e-partner-app');
  await orga.getByTestId('oauth.clients.form.kind.public').click();
  await orga.getByTestId('oauth.clients.form.redirectUris.input').first().fill('http://partner.example/callback');
  await expect(orga.getByTestId('oauth.clients.form.redirectUris.error')).toBeVisible(); // http only for localhost
  await orga.getByTestId('oauth.clients.form.redirectUris.input').first().fill(redirectUri);
  await expect(orga.getByTestId('oauth.clients.form.redirectUris.error')).toHaveCount(0);
  await orga.getByTestId('oauth.clients.form.scope.email').click();
  await orga.getByTestId('oauth.clients.form.scope.regions').click();
  await orga.getByTestId('oauth.clients.form.save').click();
  await expect(orga.getByTestId('oauth.clients.dialog')).toBeHidden();
  const row = orga.locator('[data-testid="oauth.clients.row"][data-client="e2e-partner-app"]');
  await expect(row).toBeVisible();
  // a public client has no secret to show
  await expect(orga.getByTestId('oauth.clients.secretDialog')).toHaveCount(0);
  await expect(row.locator('[data-testid="oauth.clients.rowScopes.item"]')).toHaveCount(4);

  // --- the user starts at the client: logged out → login with returnUrl ---
  const first = pkce();
  const authorize = authorizeUrl('e2e-partner-app', {
    redirectUri,
    scope: 'openid profile email regions',
    state: 'state-1',
    codeChallenge: first.challenge,
  });
  await page.goto(authorize);
  await expect(page).toHaveURL(/\/login\?returnUrl=/);
  expect(decodeURIComponent(new URL(page.url()).searchParams.get('returnUrl') ?? '')).toContain('/oauth/authorize?');
  const user = apiOf(page);
  await user.loginAs('foodsaver');
  await acceptPendingConsents(user);

  // --- consent page: client, scopes, remember, approve ---
  await page.goto(authorize);
  await expect(page).toHaveURL(/\/oauth\/consent\?request=/);
  await expectDataState(page, 'oauth.consent.state', 'success');
  await expect(page.getByTestId('oauth.consent.title')).toContainText('E2E Partner App');
  await expect(page.getByTestId('oauth.consent.scopes.item')).toHaveCount(4);
  await page.getByTestId('oauth.consent.remember').click();
  await page.getByTestId('oauth.consent.approve').click();

  // --- the client's callback receives the code (+ state) ---
  await page.waitForURL(/\/dev\/oauth-callback\?/);
  await expect(page.getByTestId('dev.oauth-callback.result')).toContainText('code received');
  const callback = callbackParams(page.url());
  expect(callback.state).toBe('state-1');
  expect(callback.code).toBeTruthy();
  expect((await devGetLastOAuthCode(user)).code).toBe(callback.code);

  // --- token exchange with the PKCE verifier, userinfo ---
  const exchanged = await exchangeCode(page.request, {
    code: callback.code!,
    clientId: 'e2e-partner-app',
    redirectUri,
    codeVerifier: first.verifier,
  });
  expect(exchanged.status()).toBe(200);
  const tokens = (await exchanged.json()) as TokenResponse;
  expect(tokens.token_type).toBe('Bearer');
  expect(tokens.id_token).toBeTruthy();
  expect(tokens.refresh_token).toBeTruthy();
  const claims = await userinfo(page.request, tokens.access_token);
  expect(claims['email']).toBe(roleEmail('foodsaver'));
  expect(claims['name']).toBeTruthy();
  expect(String(claims['preferred_username'])).toContain('#');
  expect(claims['region_ids']).toContain(ZUERICH_STADT);
  // a code is single use
  const replay = await exchangeCode(page.request, {
    code: callback.code!,
    clientId: 'e2e-partner-app',
    redirectUri,
    codeVerifier: first.verifier,
  });
  expect(replay.status()).toBe(400);

  // --- the remembered consent skips the consent page ---
  const second = pkce();
  await page.goto(authorizeUrl('e2e-partner-app', { redirectUri, scope: 'openid profile', state: 'state-2', codeChallenge: second.challenge }));
  await expect(page).toHaveURL(/\/dev\/oauth-callback\?/);
  expect(callbackParams(page.url()).state).toBe('state-2');
  const secondTokens = (await (
    await exchangeCode(page.request, { code: callbackParams(page.url()).code!, clientId: 'e2e-partner-app', redirectUri, codeVerifier: second.verifier })
  ).json()) as TokenResponse;

  // --- the user revokes the connection ---
  await page.goto('/account/connections');
  await expectDataState(page, 'account.connections.state', 'success');
  const connection = page.locator('[data-testid="account.connections.item"][data-client="e2e-partner-app"]');
  await expect(connection.getByTestId('account.connections.name')).toHaveText('E2E Partner App');
  await expect(page.locator('[data-testid="account.connections.item"][data-client="wiki"]')).toBeVisible(); // seed consent
  await connection.getByTestId('account.connections.revoke').click();
  await acceptConfirm(page);
  await expect(connection).toHaveCount(0);
  await expect(page.locator('[data-testid="account.connections.item"][data-client="wiki"]')).toBeVisible();

  // revoked: refresh tokens no longer work and the next authorize asks again
  expect((await refreshToken(page.request, 'e2e-partner-app', secondTokens.refresh_token!)).status()).toBe(400);
  const third = pkce();
  const location = await authorizeLocation(
    page.request,
    authorizeUrl('e2e-partner-app', { redirectUri, scope: 'openid', codeChallenge: third.challenge }),
  );
  expect(location).toContain('/oauth/consent?request=');

  // ORGA sees the consent count drop back to 0
  await orga.reload();
  await expect(row.getByTestId('oauth.clients.rowConsents')).toHaveText('0');
});

test('C13 region-restricted client refuses a user outside the region @C13', async ({ as, apiOf }) => {
  // newbie is not a member of zuerich-stadt, which the seed client "wiki" requires
  const newbie = await as('newbie');
  await acceptPendingConsents(apiOf(newbie));
  await newbie.goto(authorizeUrl(OAUTH_CLIENTS.wiki.clientId, { redirectUri: SEED_OAUTH_REDIRECT, scope: 'openid profile', state: 'w1' }));
  await expect(newbie).toHaveURL(/\/oauth\/consent\?request=/);
  await expectDataState(newbie, 'oauth.consent.state', 'success');
  await expect(newbie.getByTestId('oauth.consent.regionRequired')).toBeVisible();
  await expect(newbie.getByTestId('oauth.consent.approve')).toHaveCount(0);
  await expect(newbie.getByTestId('oauth.consent.back')).toBeVisible();
  // the server refuses an approval anyway
  const requestId = new URL(newbie.url()).searchParams.get('request')!;
  const approve = await apiOf(newbie).post(`/api/oauth/authorize-requests/${requestId}/decision`, { approve: true, remember: true });
  expect(approve.status()).toBe(403);
  expect((await approve.json()).code).toBe('OAUTH_REGION_REQUIRED');

  // a member (foodsaver, remembered seed consent) goes straight back to the client with a code
  const member = await as('foodsaver');
  await acceptPendingConsents(apiOf(member));
  const location = await authorizeLocation(
    member.request,
    authorizeUrl(OAUTH_CLIENTS.wiki.clientId, { redirectUri: SEED_OAUTH_REDIRECT, scope: 'openid profile', state: 'w2' }),
  );
  expect(location.startsWith(`${SEED_OAUTH_REDIRECT}?code=`)).toBe(true);
  expect(callbackParams(location).state).toBe('w2');
});

test('C13 discovery document and JWKS are served @C13', async ({ request }) => {
  const discovery = await request.get('/.well-known/openid-configuration');
  expect(discovery.status()).toBe(200);
  const doc = (await discovery.json()) as Record<string, unknown>;
  const issuer = String(doc['issuer']);
  expect(issuer).toMatch(/^https?:\/\//);
  expect(doc['authorization_endpoint']).toBe(`${issuer}/oauth/authorize`);
  expect(doc['token_endpoint']).toBe(`${issuer}/oauth/token`);
  expect(doc['userinfo_endpoint']).toBe(`${issuer}/oauth/userinfo`);
  expect(doc['jwks_uri']).toBe(`${issuer}/.well-known/jwks.json`);
  expect(doc['code_challenge_methods_supported']).toEqual(['S256']);
  expect(doc['scopes_supported']).toEqual(expect.arrayContaining(['openid', 'profile', 'email', 'regions']));
  expect(doc['id_token_signing_alg_values_supported']).toEqual(['RS256']);

  const jwks = await request.get('/.well-known/jwks.json');
  expect(jwks.status()).toBe(200);
  const { keys } = (await jwks.json()) as { keys: Record<string, string>[] };
  expect(keys.length).toBeGreaterThan(0);
  expect(keys[0]).toMatchObject({ kty: 'RSA', alg: 'RS256', use: 'sig' });
  expect(keys[0]['kid']).toMatch(/^[0-9a-f]{16}$/);
  expect(keys[0]['n']).toBeTruthy();
  expect(keys[0]).not.toHaveProperty('d'); // never the private part
});

test('C13 orga rotates the secret of a confidential client, disables and deletes it @C13', async ({ as }) => {
  const orga = await as('orga');
  await orga.goto('/admin/oauth-clients');
  await expectDataState(orga, 'oauth.clients.state', 'success');
  const wiki = orga.locator('[data-testid="oauth.clients.row"][data-client="wiki"]');
  await expect(wiki.getByTestId('oauth.clients.rowRegions')).toContainText('Zürich');

  // rotate: the new secret is shown once and works at the token endpoint (wrong code, but client auth passes)
  await wiki.getByTestId('oauth.clients.rotate').click();
  await acceptConfirm(orga);
  const secret = (await orga.getByTestId('oauth.clients.secret.value').textContent())?.trim() ?? '';
  expect(secret).toHaveLength(48);
  await orga.getByTestId('oauth.clients.secret.done').click();
  await expect(orga.getByTestId('oauth.clients.secretDialog')).toHaveCount(0);
  const oldSecret = await exchangeCode(orga.request, {
    code: 'nope',
    clientId: 'wiki',
    redirectUri: SEED_OAUTH_REDIRECT,
    clientSecret: OAUTH_CLIENTS.wiki.secret,
  });
  expect((await oldSecret.json()).error).toBe('invalid_client');
  const newSecret = await exchangeCode(orga.request, { code: 'nope', clientId: 'wiki', redirectUri: SEED_OAUTH_REDIRECT, clientSecret: secret });
  expect((await newSecret.json()).error).toBe('invalid_grant');

  // disable after confirm, enable again without
  await wiki.getByTestId('oauth.clients.toggle').click();
  await acceptConfirm(orga);
  await expect(wiki).toHaveAttribute('data-enabled', 'false');
  await wiki.getByTestId('oauth.clients.toggle').click();
  await expect(wiki).toHaveAttribute('data-enabled', 'true');

  // edit: the dialog loads the client; a second redirect URI is saved
  await wiki.getByTestId('oauth.clients.edit').click();
  await expectDataState(orga, 'oauth.clients.form.state', 'success');
  await expect(orga.getByTestId('oauth.clients.form.region')).toHaveCount(1);
  await orga.getByTestId('oauth.clients.form.redirectUris.add').click();
  await orga.getByTestId('oauth.clients.form.redirectUris.input').last().fill('https://wiki.example.org/callback');
  await orga.getByTestId('oauth.clients.form.save').click();
  await expect(orga.getByTestId('oauth.clients.dialog')).toHaveCount(0);
  await expect(wiki).toContainText('https://wiki.example.org/callback');

  // delete revokes everything; the client is gone
  await wiki.getByTestId('oauth.clients.delete').click();
  await acceptConfirm(orga);
  await expect(wiki).toHaveCount(0);
  const foodsaver = await as('foodsaver');
  await foodsaver.goto('/account/connections');
  await expectDataState(foodsaver, 'account.connections.state', 'empty');
});

test('C13 consent, connections and client pages show their error states @C13', async ({ as }) => {
  const orga = await as('orga');
  await injectApiError(orga, { url: '/api/admin/oauth-clients', method: 'GET', status: 500, times: 3 });
  await orga.goto('/admin/oauth-clients');
  const clients = await expectErrorState(orga, 'oauth.clients.state');
  await clients.retry.click();
  await expectDataState(orga, 'oauth.clients.state', 'success');

  const foodsaver = await as('foodsaver');
  await injectApiError(foodsaver, { url: '/api/users/me/connections', method: 'GET', status: 500, times: 3 });
  await foodsaver.goto('/account/connections');
  const connections = await expectErrorState(foodsaver, 'account.connections.state');
  await connections.retry.click();
  await expectDataState(foodsaver, 'account.connections.state', 'success');

  // an unknown (expired, answered) request is the not-found state
  await foodsaver.goto('/oauth/consent?request=00000000-0000-4000-8000-000000000000');
  await expect(foodsaver.getByTestId('oauth.consent.state')).toHaveAttribute('data-state', 'notFound');
});
