import type { Page } from '@playwright/test';
import {
  OAUTH_CLIENTS,
  SEED_OAUTH_REDIRECT,
  type TokenResponse,
  acceptPendingConsents,
  authorizeLocation,
  authorizeUrl,
  callbackParams,
  devGetLastOAuthCode,
  exchangeCode,
  expectDataState,
  pkce,
  refreshToken,
  regionId,
  roleEmail,
  userinfo,
} from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { acceptConfirm } from '../../fixtures/flows/auth';
import { Walk } from '../../fixtures/persona';

/**
 * C13 OAuth provider (plan-m7 Q14, A44/A56): ORGA (orga@local) registers a partner app (public client with PKCE; an
 * http redirect URI outside localhost is refused at its field). A member (foodsaver@local) starts at the app, logs in,
 * agrees once with "remember", the app gets its code, tokens and the profile with the regions; the next sign-in skips
 * the question; the member disconnects the app in the account settings. A newcomer outside Zürich Stadt cannot connect
 * the region's wiki. The ORGA rotates the wiki's secret, switches it off and on, adds a redirect URI and deletes it.
 * The partner app's own calls (token, userinfo) go through the API. Rules behind it: OAuthRulesTest (redirect URIs,
 * client ids, claims per scope), OAuthProtocolRulesTest (code single use, revoked refresh tokens, old secret, region
 * required, discovery and JWKS); client oauth.spec (consent, clients, connections).
 */
test.use({ role: 'orga' });

const ZUERICH_STADT = regionId('zuerich-stadt');
const clientRow = (page: Page, clientId: string) => page.locator(`[data-testid="oauth.clients.row"][data-client="${clientId}"]`);

test('orga registers a partner app, a member connects and disconnects it, the orga rotates and removes the wiki @C13', async ({
  page,
  as,
  apiOf,
  baseURL,
}, testInfo) => {
  test.setTimeout(180_000);
  await acceptPendingConsents(apiOf(page));
  const walk = new Walk(page, testInfo, 'orga');
  const redirectUri = `${baseURL}/dev/oauth-callback`;
  const partner = clientRow(page, 'e2e-partner-app');
  const wiki = clientRow(page, OAUTH_CLIENTS.wiki.clientId);

  await walk.step('registers the partner app', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('adminOauth');
    await expect(page).toHaveURL(/\/admin\/oauth-clients$/);
    await expectDataState(page, 'oauth.clients.state', 'success');
    await expect(wiki.getByTestId('oauth.clients.rowRegions')).toContainText('Zürich');
    await checkpoint(page, 'oauth-clients');
    await walk.click(page.getByTestId('oauth.clients.create'));
    await walk.fill(page.getByTestId('oauth.clients.form.name'), 'E2E Partner App');
    await expect(page.getByTestId('oauth.clients.form.clientId')).toHaveValue('e2e-partner-app');
    await walk.click(page.getByTestId('oauth.clients.form.kind.public'));
    // http only for localhost
    await walk.fill(page.getByTestId('oauth.clients.form.redirectUris.input').first(), 'http://partner.example/callback');
    await expect(page.getByTestId('oauth.clients.form.redirectUris.error')).toBeVisible();
    await checkpoint(page, 'client-create-invalid');
    await walk.fill(page.getByTestId('oauth.clients.form.redirectUris.input').first(), redirectUri);
    await expect(page.getByTestId('oauth.clients.form.redirectUris.error')).toHaveCount(0);
    await walk.click(page.getByTestId('oauth.clients.form.scope.email'));
    await walk.click(page.getByTestId('oauth.clients.form.scope.regions'));
    await walk.click(page.getByTestId('oauth.clients.form.save'));
    await expect(page.getByTestId('oauth.clients.dialog')).toBeHidden();
    await expect(partner).toBeVisible();
    // a public client has no secret to show
    await expect(page.getByTestId('oauth.clients.secretDialog')).toHaveCount(0);
    await expect(partner.locator('[data-testid="oauth.clients.rowScopes.item"]')).toHaveCount(4);
  });

  // the member, logged out, starts at the partner app
  const member = await as(null);
  const memberApi = apiOf(member);
  const user = new Walk(member, testInfo, 'member');
  const first = pkce();
  const authorize = authorizeUrl('e2e-partner-app', {
    redirectUri,
    scope: 'openid profile email regions',
    state: 'state-1',
    codeChallenge: first.challenge,
  });
  let refresh = '';

  await user.step('signs in to the partner app and agrees once', async () => {
    await member.goto(authorize);
    await expect(member).toHaveURL(/\/login\?returnUrl=/);
    expect(decodeURIComponent(new URL(member.url()).searchParams.get('returnUrl') ?? '')).toContain('/oauth/authorize?');
    // logging in itself is the account area's journey
    await memberApi.loginAs('foodsaver');
    await acceptPendingConsents(memberApi);
    await member.goto(authorize);
    await expect(member).toHaveURL(/\/oauth\/consent\?request=/);
    await expectDataState(member, 'oauth.consent.state', 'success');
    await expect(member.getByTestId('oauth.consent.title')).toContainText('E2E Partner App');
    await expect(member.getByTestId('oauth.consent.scopes.item')).toHaveCount(4);
    await checkpoint(member, 'consent');
    await user.click(member.getByTestId('oauth.consent.remember'));
    await user.click(member.getByTestId('oauth.consent.approve'));
    await member.waitForURL(/\/dev\/oauth-callback\?/);
    await expect(member.getByTestId('dev.oauth-callback.result')).toContainText('code received');
    const callback = callbackParams(member.url());
    expect(callback.state).toBe('state-1');
    expect((await devGetLastOAuthCode(memberApi)).code).toBe(callback.code);

    // the partner app exchanges the code (PKCE) and reads the profile with the regions
    const exchanged = await exchangeCode(member.request, {
      code: callback.code!,
      clientId: 'e2e-partner-app',
      redirectUri,
      codeVerifier: first.verifier,
    });
    expect(exchanged.status()).toBe(200);
    const tokens = (await exchanged.json()) as TokenResponse;
    expect(tokens.id_token).toBeTruthy();
    refresh = tokens.refresh_token!;
    const claims = await userinfo(member.request, tokens.access_token);
    expect(claims['email']).toBe(roleEmail('foodsaver'));
    expect(String(claims['preferred_username'])).toContain('#');
    expect(claims['region_ids']).toContain(ZUERICH_STADT);
  });

  await user.step('signs in again without being asked', async () => {
    const second = pkce();
    await member.goto(authorizeUrl('e2e-partner-app', { redirectUri, scope: 'openid profile', state: 'state-2', codeChallenge: second.challenge }));
    await expect(member).toHaveURL(/\/dev\/oauth-callback\?/);
    expect(callbackParams(member.url()).state).toBe('state-2');
  });

  await user.step('disconnects the partner app in the account', async () => {
    await member.goto('/dashboard');
    await expect(member.getByTestId('dashboard.page.welcome')).toBeVisible();
    await user.nav('account');
    await user.click(member.getByTestId('account.tabs.connections'));
    await expect(member).toHaveURL(/\/account\/connections$/);
    await expectDataState(member, 'account.connections.state', 'success');
    const connection = member.locator('[data-testid="account.connections.item"][data-client="e2e-partner-app"]');
    await expect(connection.getByTestId('account.connections.name')).toHaveText('E2E Partner App');
    // the seed consent of the wiki
    await expect(member.locator('[data-testid="account.connections.item"][data-client="wiki"]')).toBeVisible();
    await checkpoint(member, 'connections');
    await user.click(connection.getByTestId('account.connections.revoke'));
    await acceptConfirm(member);
    user.count(1);
    await expect(connection).toHaveCount(0);
    await expect(member.locator('[data-testid="account.connections.item"][data-client="wiki"]')).toBeVisible();
    // disconnected: the app's refresh token is dead and the next sign-in asks again
    expect((await refreshToken(member.request, 'e2e-partner-app', refresh)).status()).toBe(400);
    const location = await authorizeLocation(
      member.request,
      authorizeUrl('e2e-partner-app', { redirectUri, scope: 'openid', codeChallenge: pkce().challenge }),
    );
    expect(location).toContain('/oauth/consent?request=');
  });

  // a newcomer outside Zürich Stadt tries the region's wiki
  const newbie = await as('newbie');
  await acceptPendingConsents(apiOf(newbie));
  const newcomer = new Walk(newbie, testInfo, 'newcomer');
  await newcomer.step('cannot connect the wiki of a region she is not in', async () => {
    await newbie.goto(authorizeUrl(OAUTH_CLIENTS.wiki.clientId, { redirectUri: SEED_OAUTH_REDIRECT, scope: 'openid profile', state: 'w1' }));
    await expect(newbie).toHaveURL(/\/oauth\/consent\?request=/);
    await expectDataState(newbie, 'oauth.consent.state', 'success');
    await expect(newbie.getByTestId('oauth.consent.regionRequired')).toBeVisible();
    await expect(newbie.getByTestId('oauth.consent.approve')).toHaveCount(0);
    await expect(newbie.getByTestId('oauth.consent.back')).toBeVisible();
    await checkpoint(newbie, 'consent-region-required');
  });

  await walk.step('rotates the secret of the wiki', async () => {
    // the partner app has no remembered consent any more
    await page.reload();
    await expectDataState(page, 'oauth.clients.state', 'success');
    await expect(partner.getByTestId('oauth.clients.rowConsents')).toHaveText('0');
    await walk.click(wiki.getByTestId('oauth.clients.rotate'));
    await acceptConfirm(page);
    walk.count(1);
    const secretValue = page.getByTestId('oauth.clients.secret.value');
    const secret = (await secretValue.textContent())?.trim() ?? '';
    expect(secret).toHaveLength(48);
    // the secret is random
    await checkpoint(page, 'client-secret', { mask: [secretValue] });
    await walk.click(page.getByTestId('oauth.clients.secret.done'));
    await expect(page.getByTestId('oauth.clients.secretDialog')).toHaveCount(0);
    // the wiki's old secret fails client authentication, the new one passes it (the code is made up)
    const old = await exchangeCode(page.request, { code: 'nope', clientId: 'wiki', redirectUri: SEED_OAUTH_REDIRECT, clientSecret: OAUTH_CLIENTS.wiki.secret });
    expect((await old.json()).error).toBe('invalid_client');
    const rotated = await exchangeCode(page.request, { code: 'nope', clientId: 'wiki', redirectUri: SEED_OAUTH_REDIRECT, clientSecret: secret });
    expect((await rotated.json()).error).toBe('invalid_grant');
  });

  await walk.step('switches the wiki off and on, adds an address and deletes it', async () => {
    // switching off asks first, switching on does not
    await walk.click(wiki.getByTestId('oauth.clients.toggle'));
    await acceptConfirm(page);
    walk.count(1);
    await expect(wiki).toHaveAttribute('data-enabled', 'false');
    await walk.click(wiki.getByTestId('oauth.clients.toggle'));
    await expect(wiki).toHaveAttribute('data-enabled', 'true');

    await walk.click(wiki.getByTestId('oauth.clients.edit'));
    await expectDataState(page, 'oauth.clients.form.state', 'success');
    await expect(page.getByTestId('oauth.clients.form.region')).toHaveCount(1);
    await walk.click(page.getByTestId('oauth.clients.form.redirectUris.add'));
    await walk.fill(page.getByTestId('oauth.clients.form.redirectUris.input').last(), 'https://wiki.example.org/callback');
    await walk.click(page.getByTestId('oauth.clients.form.save'));
    await expect(page.getByTestId('oauth.clients.dialog')).toHaveCount(0);
    await expect(wiki).toContainText('https://wiki.example.org/callback');

    // deleting revokes every connection: the member's account lists none
    await walk.click(wiki.getByTestId('oauth.clients.delete'));
    await acceptConfirm(page);
    walk.count(1);
    await expect(wiki).toHaveCount(0);
    await member.reload();
    await expectDataState(member, 'account.connections.state', 'empty');
  });

  walk.finish({ steps: 3, clicks: 18, fields: 4 });
  user.finish({ steps: 3, clicks: 7, fields: 0 });
  newcomer.finish({ steps: 1, clicks: 0, fields: 0 });
});
