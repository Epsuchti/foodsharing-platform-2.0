import { expect, expectDataState, expectErrorState, injectApiError, injectBounce as bounce, test } from '../../fixtures';

/**
 * A18 bounces (plan-m7 Q4, A48, MB5): a delivery status notification reaching a platform mailbox flags the failed
 * user address. The user sees a warning on the dashboard and in the settings; ORGA lists bounced addresses on
 * `/admin/bounced-emails` and clears the flag. Seed: no bounced user; mailbox `zuerich@local.test`.
 */
test.use({ seedReset: 'test' });

const FOODSAVER_ID = '00000001-0001-4000-8000-000000000006';

test('A18 bounced user sees warning, admin list shows and clears it @A18 @MB5', async ({ as, api }) => {
  await bounce(api, 'foodsaver@local');

  // the user: warning on the dashboard and in the settings
  const page = await as('foodsaver');
  await page.goto('/dashboard');
  await expect(page.getByTestId('account.bounce.banner')).toContainText('foodsaver@local');
  await expect(page.getByTestId('account.bounce.change')).toBeVisible();
  await page.goto('/account/settings');
  await expect(page.getByTestId('account.bounce.banner')).toBeVisible();
  await expect(page.getByTestId('account.bounce.change')).toHaveCount(0);

  // ORGA: list with the user, the user page shows the flag; clearing empties the list
  const orga = await as('orga');
  await orga.goto('/admin/bounced-emails');
  const row = orga.locator(`[data-testid="usersAdmin.bounced.row"][data-user-id="${FOODSAVER_ID}"]`);
  await expect(row).toBeVisible();
  await row.getByTestId('usersAdmin.bounced.open').click();
  await expect(orga).toHaveURL(new RegExp(`/users/${FOODSAVER_ID}$`));
  await expect(orga.getByTestId('usersAdmin.user.bouncedTag')).toBeVisible();
  await orga.goBack();
  await row.getByTestId('usersAdmin.bounced.clear').click();
  await expectDataState(orga, 'usersAdmin.bounced.state', 'empty');

  // the warning is gone for the user
  await page.goto('/dashboard');
  await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
  await expectDataState(page, 'account.bounce.state', 'empty');
  await expect(page.getByTestId('account.bounce.banner')).toHaveCount(0);

  // a new bounce: the user clears it herself once the mailbox works again
  await bounce(api, 'foodsaver@local');
  await page.goto('/account/settings');
  await page.getByTestId('account.bounce.clear').click();
  await expect(page.getByTestId('account.bounce.banner')).toHaveCount(0);
  await orga.goto('/admin/bounced-emails');
  await expectDataState(orga, 'usersAdmin.bounced.state', 'empty');
});

test('A18 bounced list error state and access @A18', async ({ as }) => {
  const orga = await as('orga');
  // GETs are retried twice by the retry interceptor
  await injectApiError(orga, { url: /\/api\/admin\/users\/bounced/, method: 'GET', status: 500, times: 3 });
  await orga.goto('/admin/bounced-emails');
  const { retry } = await expectErrorState(orga, 'usersAdmin.bounced.state');
  await retry.click();
  await expectDataState(orga, 'usersAdmin.bounced.state', 'empty');

  // members without administration rights get the forbidden state
  const member = await as('foodsaver');
  await member.goto('/admin/bounced-emails');
  await expectDataState(member, 'usersAdmin.bounced.state', 'forbidden');
});
