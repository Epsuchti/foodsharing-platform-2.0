import { OAUTH_CLIENTS, SEED_OAUTH_REDIRECT, acceptPendingConsents, authorizeUrl, injectApiError, pkce } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * OAuth provider (plan-m7 Q14, C13): consent page (scopes, region-restricted), connections, client administration
 * with the create dialog, loading and error. Baselines are generated in the end batch (decision 69:
 * `e2e/scripts/screenshots.sh --update-snapshots`); one variant per project (decision 109).
 */
forEachVariant((variant) => {
  screenshotTest.describe(`oauth consent ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'foodsaver' });

    screenshotTest('consent page @C13 @screenshot', async ({ page, apiOf, visual }) => {
      await acceptPendingConsents(apiOf(page));
      const { challenge } = pkce();
      await page.goto(
        authorizeUrl(OAUTH_CLIENTS.mobileDev.clientId, {
          redirectUri: SEED_OAUTH_REDIRECT,
          scope: 'openid profile email',
          codeChallenge: challenge,
        }),
      );
      await expect(page.getByTestId('oauth.consent.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `oauth-consent-${variant.name}`);
    });

    screenshotTest('connections @C13 @screenshot', async ({ page, visual }) => {
      await page.goto('/account/connections');
      await expect(page.getByTestId('account.connections.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `oauth-connections-${variant.name}`);
    });

    screenshotTest('connections error @C13 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/users/me/connections', status: 503 });
      await page.goto('/account/connections');
      await expect(page.getByTestId('account.connections.state')).toHaveAttribute('data-state', 'error');
      await visual.expectScreen(page, `oauth-connections-error-${variant.name}`);
    });
  });

  screenshotTest.describe(`oauth region restricted ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'newbie' });

    screenshotTest('consent page region restricted @C13 @screenshot', async ({ page, apiOf, visual }) => {
      await acceptPendingConsents(apiOf(page));
      await page.goto(authorizeUrl(OAUTH_CLIENTS.wiki.clientId, { redirectUri: SEED_OAUTH_REDIRECT, scope: 'openid profile' }));
      await expect(page.getByTestId('oauth.consent.regionRequired')).toBeVisible();
      await visual.expectScreen(page, `oauth-consent-region-${variant.name}`);
    });
  });

  screenshotTest.describe(`oauth clients ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'orga' });

    screenshotTest('clients @C13 @screenshot', async ({ page, visual }) => {
      await page.goto('/admin/oauth-clients');
      await expect(page.getByTestId('oauth.clients.state')).toHaveAttribute('data-state', 'success');
      await expect(page.locator('[data-testid="oauth.clients.row"][data-client="wiki"] [data-testid="oauth.clients.rowRegions"]')).not.toContainText('…');
      await visual.expectScreen(page, `oauth-clients-${variant.name}`);
    });

    screenshotTest('client create dialog with invalid redirect @C13 @screenshot', async ({ page, visual }) => {
      await page.goto('/admin/oauth-clients');
      await expect(page.getByTestId('oauth.clients.state')).toHaveAttribute('data-state', 'success');
      await page.getByTestId('oauth.clients.create').click();
      await page.getByTestId('oauth.clients.form.name').fill('Partner App');
      await page.getByTestId('oauth.clients.form.redirectUris.input').first().fill('http://partner.example/callback');
      await expect(page.getByTestId('oauth.clients.form.redirectUris.error')).toBeVisible();
      await visual.expectScreen(page, `oauth-clients-create-${variant.name}`);
    });

    screenshotTest('clients loading @C13 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/admin/oauth-clients', delayMs: 30_000 });
      await page.goto('/admin/oauth-clients');
      await expect(page.getByTestId('oauth.clients.state')).toHaveAttribute('data-state', 'loading');
      await visual.expectScreen(page, `oauth-clients-loading-${variant.name}`, { waitForData: false });
    });
  });
});
