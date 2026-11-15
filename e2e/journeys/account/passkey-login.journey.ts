import { addVirtualAuthenticator } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { logoutWithMenu, skipPasskeyNudge, type HistoryEntryPage } from '../../fixtures/flows/auth';
import { Walk } from '../../fixtures/persona';

/**
 * Foodsaver Sarah adds a passkey in her settings and logs in with it twice: through passkey autofill (conditional
 * mediation) and through the "log in with a passkey" button of browsers without autofill (A2, D8.3). The virtual
 * WebAuthn authenticator is a CDP feature: chromium and mobile-chrome only.
 */
test.use({ role: 'foodsaver' });

test('foodsaver registers a passkey and logs in with it @A2 @D8.3 @chromium-only', async ({ page, apiOf }, testInfo) => {
  test.setTimeout(90_000);
  const walk = new Walk(page, testInfo, 'foodsaver');
  await skipPasskeyNudge(page);
  const authenticator = await addVirtualAuthenticator(page);

  await walk.step('adds a passkey in the settings', async () => {
    await page.goto('/account/settings');
    await expect(page.getByTestId('account.settings.methods')).toHaveAttribute('data-state', 'success');
    await walk.click(page.getByTestId('account.settings.passkeys.add'));
    const label = page.getByTestId('account.settings.passkeyDialog.label');
    await expect(label).not.toHaveValue('');
    await walk.fill(label, 'Laptop');
    await checkpoint(page, 'passkey-dialog');
    await walk.click(page.getByTestId('account.settings.passkeyDialog.save'));
    await expect(page.getByTestId('account.settings.passkeys')).toContainText('Laptop');
    expect(await authenticator.credentials()).toHaveLength(1);
    await checkpoint(page, 'passkey-added');
  });

  await walk.step('logs in again through passkey autofill', async () => {
    walk.count(2);
    await logoutWithMenu(page);
    // the virtual authenticator answers the conditional request right away
    await page.goto('/login?returnUrl=%2Faccount%2Fsecurity-log');
    await expect(page).toHaveURL(/\/account\/security-log$/);
  });

  await walk.step('logs in with the passkey button', async () => {
    walk.count(2);
    await logoutWithMenu(page);
    // as in browsers without passkey autofill
    await page.addInitScript(() => {
      Object.defineProperty(PublicKeyCredential, 'isConditionalMediationAvailable', { value: undefined });
    });
    await page.goto('/login?returnUrl=%2Faccount%2Fsecurity-log');
    await expect(page.getByTestId('auth.login.passkey')).toBeVisible();
    await checkpoint(page, 'login-passkey-button');
    await walk.click(page.getByTestId('auth.login.passkey'));
    await expect(page).toHaveURL(/\/account\/security-log$/);
    await expect(page.getByTestId('account.securityLog.list')).toHaveAttribute('data-state', 'success');
    await checkpoint(page, 'security-log');

    const api = apiOf(page);
    expect((await api.session()).user?.email).toBe('foodsaver@local');
    const log = await api.getJson<HistoryEntryPage>('/api/auth/security-log');
    expect(log.items.map((entry) => entry.action)).toContain('PASSKEY_REGISTERED');
    expect(log.items.some((entry) => entry.action === 'LOGIN' && entry.metadata?.['method'] === 'PASSKEY')).toBe(true);
  });

  // measured 2026-11-15 (rework step 3): 7 clicks on desktop and phone
  walk.finish({ steps: 3, clicks: 7, fields: 1 });
});
