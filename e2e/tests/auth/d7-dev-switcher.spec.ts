import { ROLES, expect, roleEmail, test } from '../../fixtures';
import { skipPasskeyNudge } from './_auth';

/** D7.3: the dev user switcher on /login (local/e2e only) logs in as every seed user with one click. */

test('D7.3 dev switcher logs in as every seed user @D7.3', async ({ page, dev, apiOf }) => {
  test.slow();
  await skipPasskeyNudge(page);
  const users = await dev.users();
  const api = apiOf(page);

  for (const role of ROLES) {
    const user = users.find((candidate) => candidate.email === roleEmail(role))!;
    await page.goto('/login');
    const entry = page.getByTestId(`auth.login.devSwitcher.${roleEmail(role)}`);
    await expect(entry).toContainText(user.displayName);
    await entry.click();

    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByTestId('dashboard.page.name')).toHaveText(user.displayName);
    const session = await api.session();
    expect(session.user?.id).toBe(user.id);
    expect(session.user?.role).toBe(user.role);
    await api.logout();
  }
});
