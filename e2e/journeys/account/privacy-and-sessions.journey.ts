import fs from 'node:fs';
import zlib from 'node:zlib';
import { expectDataState, injectBounce, roleEmail } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { acceptConfirm, sessionIdOf, skipPasskeyNudge } from '../../fixtures/flows/auth';
import { Walk } from '../../fixtures/persona';

/**
 * Foodsaver Sarah (foodsaver@local) looks after her account's privacy: her address bounced and she clears the warning
 * (A18, the bounce arrives through the platform mailbox, MB5; ORGA's list of bounced addresses), she signs her phone
 * out — the phone shows the "session expired" dialog and logs in again through the dev user switcher (A2, D7.3) —
 * and then everything else (A22, D8.4), reads her security log and the logins in her activity (D10.3), downloads
 * her data (A21), changes her email address with the links of both mails (A3, N6) and finally deletes her account
 * (A13): she is logged out, cannot log in again, her forum post stays as "Gelöschtes Mitglied" and ORGA sees the
 * archive flag with her reason. Seed: Sarah wrote post `0043-0002-…02` in the zuerich-stadt welcome thread.
 */
test.use({ role: 'foodsaver' });

const FOODSAVER_ID = '00000001-0001-4000-8000-000000000006';
const WELCOME_THREAD = '00000043-0001-4000-8000-000000000001';
const FOODSAVER_POST = '00000043-0002-4000-8000-000000000002';
const ABORTED_ADDRESS = 'sarah.m@local.test';
const NEW_ADDRESS = 'sarah.mueller@local.test';

/** Entries of a zip file (central directory), with the inflated content of each file. */
function unzip(buffer: Buffer): Map<string, Buffer> {
  const entries = new Map<string, Buffer>();
  let end = buffer.length - 22;
  while (end >= 0 && buffer.readUInt32LE(end) !== 0x06054b50) end--;
  expect(end, 'zip end of central directory').toBeGreaterThanOrEqual(0);
  const count = buffer.readUInt16LE(end + 10);
  let offset = buffer.readUInt32LE(end + 16);
  for (let i = 0; i < count; i++) {
    expect(buffer.readUInt32LE(offset), 'central directory header').toBe(0x02014b50);
    const method = buffer.readUInt16LE(offset + 10);
    const size = buffer.readUInt32LE(offset + 20);
    const nameLength = buffer.readUInt16LE(offset + 28);
    const extraLength = buffer.readUInt16LE(offset + 30);
    const commentLength = buffer.readUInt16LE(offset + 32);
    const local = buffer.readUInt32LE(offset + 42);
    const name = buffer.toString('utf8', offset + 46, offset + 46 + nameLength);
    const start = local + 30 + buffer.readUInt16LE(local + 26) + buffer.readUInt16LE(local + 28);
    const data = buffer.subarray(start, start + size);
    entries.set(name, method === 8 ? zlib.inflateRawSync(data) : Buffer.from(data));
    offset += 46 + nameLength + extraLength + commentLength;
  }
  return entries;
}

test('foodsaver clears a bounce, signs out her devices, exports her data, changes her email and deletes her account @A18 @MB5 @A2 @D7.3 @A22 @D8.4 @D10.3 @A21 @A3 @N6 @A13', async ({
  page,
  as,
  api,
  apiOf,
  mails,
}, testInfo) => {
  test.setTimeout(240_000);
  const walk = new Walk(page, testInfo, 'foodsaver');
  await skipPasskeyNudge(page);

  // `mails` reads the captured mails through the `api` fixture, logged in as Sarah: that would be one more of her
  // sessions in the list. The dev endpoints need no login, so it logs out; the bounces arrive through it too.
  await api.logout();
  const mailServer = api;
  const orga = await as('orga');

  await walk.step('sees that her address bounced and clears the warning', async () => {
    await injectBounce(mailServer, 'foodsaver@local');
    await page.goto('/dashboard');
    await expect(page.getByTestId('account.bounce.banner')).toContainText('foodsaver@local');
    await expect(page.getByTestId('account.bounce.change')).toBeVisible();
    await checkpoint(page, 'bounce-banner');
    await walk.click(page.getByTestId('account.bounce.change'));
    await expect(page).toHaveURL(/\/account\/settings/);
    await expect(page.getByTestId('account.bounce.banner')).toBeVisible();
    await expect(page.getByTestId('account.bounce.change')).toHaveCount(0);
    await walk.click(page.getByTestId('account.bounce.clear'));
    await expect(page.getByTestId('account.bounce.banner')).toHaveCount(0);

    // a second bounce: ORGA lists the address, opens the user and clears it there
    await injectBounce(mailServer, 'foodsaver@local');
    await orga.goto('/admin/bounced-emails');
    const row = orga.locator(`[data-testid="usersAdmin.bounced.row"][data-user-id="${FOODSAVER_ID}"]`);
    await expect(row).toBeVisible();
    await checkpoint(orga, 'bounced-list');
    await row.getByTestId('usersAdmin.bounced.open').click();
    await expect(orga).toHaveURL(new RegExp(`/users/${FOODSAVER_ID}$`));
    await expect(orga.getByTestId('usersAdmin.user.bouncedTag')).toBeVisible();
    await orga.goBack();
    await row.getByTestId('usersAdmin.bounced.clear').click();
    await expectDataState(orga, 'usersAdmin.bounced.state', 'empty');
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await expectDataState(page, 'account.bounce.state', 'empty');
    await expect(page.getByTestId('account.bounce.banner')).toHaveCount(0);
  });

  const phone = await as('foodsaver');
  await skipPasskeyNudge(phone);
  const phoneSession = await sessionIdOf(apiOf(phone));

  await walk.step('signs her phone out', async () => {
    await phone.goto('/account/settings');
    await expect(phone.getByTestId('account.settings.methods')).toHaveAttribute('data-state', 'success');
    walk.count(2);
    await page.getByTestId('shell.userMenu').click();
    await page.getByTestId('nav.user.account').click();
    await walk.click(page.getByTestId('account.tabs.sessions'));
    await expect(page).toHaveURL(/\/account\/sessions$/);
    const list = page.getByTestId('account.sessions.list');
    await expect(list).toHaveAttribute('data-state', 'success');
    await expect(list.locator('[data-testid^="account.sessions.item."][data-current]')).toHaveCount(2);
    const current = page.getByTestId(`account.sessions.item.${await sessionIdOf(apiOf(page))}`);
    await expect(current).toHaveAttribute('data-current', 'true');
    await expect(current.getByTestId('account.sessions.current')).toBeVisible();
    await expect(page.getByTestId(`account.sessions.item.${phoneSession}`)).toHaveAttribute('data-current', 'false');
    await checkpoint(page, 'sessions');
    await walk.click(page.getByTestId(`account.sessions.item.${phoneSession}.revoke`));
    walk.count(1);
    await acceptConfirm(page);
    await expect(page.getByTestId(`account.sessions.item.${phoneSession}`)).toHaveCount(0);
    await expect(page.getByTestId('account.sessions.onlyThis')).toBeVisible();
    expect((await apiOf(page).session()).authenticated).toBe(true);
  });

  await walk.step('logs in again on the phone, then logs out everywhere', async () => {
    // the phone's next request ends in the "session expired" dialog
    await walk.click(phone.getByTestId('account.tabs.sessions'));
    await expect(phone.getByTestId('shell.sessionExpired.login')).toBeVisible();
    expect((await apiOf(phone).session()).authenticated).toBe(false);
    await checkpoint(phone, 'session-expired');
    await walk.click(phone.getByTestId('shell.sessionExpired.login'));
    await expect(phone).toHaveURL(/\/login\?returnUrl=%2Faccount%2Fsessions$/);
    await walk.click(phone.getByTestId(`auth.login.devSwitcher.${roleEmail('foodsaver')}`));
    await expect(phone).toHaveURL(/\/account\/sessions$/);
    await expect(phone.getByTestId('account.sessions.list')).toHaveAttribute('data-state', 'success');

    await page.reload();
    await expect(page.locator('[data-testid^="account.sessions.item."][data-current]')).toHaveCount(2);
    await walk.click(page.getByTestId('account.sessions.logoutEverywhere'));
    walk.count(1);
    await acceptConfirm(page);
    await expect(page.locator('[data-testid^="account.sessions.item."][data-current]')).toHaveCount(1);
    await expect(page.getByTestId('account.sessions.logoutEverywhere')).toHaveCount(0);
    expect((await apiOf(phone).session()).authenticated).toBe(false);
  });

  await walk.step('reads her security log and the logins in her activity', async () => {
    await walk.click(page.getByTestId('account.tabs.securityLog'));
    await expect(page).toHaveURL(/\/account\/security-log$/);
    const log = page.getByTestId('account.securityLog.list');
    await expect(log).toHaveAttribute('data-state', 'success');
    await expect(log.locator('[data-action="USER.LOGIN"]').first()).toBeVisible();
    await expect(log.locator('[data-action="USER.SESSION_REVOKED"]').first()).toBeVisible();
    await expect(log.locator('[data-action="USER.SESSIONS_REVOKED_ALL"]').first()).toBeVisible();
    await checkpoint(page, 'security-log');
    await walk.click(page.getByTestId('account.securityLog.allActivity'));
    await expect(page).toHaveURL(/\/me\/activity\?preset=logins$/);
    await expectDataState(page, 'history.myActivity.list', 'success');
    await expect(page.getByTestId('history.myActivity.preset.logins')).toHaveAttribute('aria-pressed', 'true');
    const actions = await page
      .getByTestId('history.myActivity.entry')
      .evaluateAll((nodes) => nodes.map((node) => node.getAttribute('data-action')));
    expect(actions.length).toBeGreaterThan(0);
    expect(actions.every((action) => /^USER\.(LOGIN|LOGOUT|LOGIN_FAILED|SESSION_REVOKED|SESSIONS_REVOKED_ALL|MAGIC_LINK_REQUESTED)$/.test(action ?? ''))).toBe(true);
  });

  await walk.step('downloads her data', async () => {
    await page.goto('/account/settings');
    await walk.click(page.getByTestId('account.settings.export.start'));
    await expect(page.getByTestId('account.settings.export.job')).toHaveAttribute('data-status', 'DONE');
    const [download] = await Promise.all([page.waitForEvent('download'), walk.click(page.getByTestId('account.settings.export.download'))]);
    expect(download.suggestedFilename()).toMatch(/\.zip$/);
    const zip = unzip(fs.readFileSync(await download.path()));
    for (const section of ['profile.json', 'settings.json', 'history.json', 'sessions.json']) {
      expect([...zip.keys()], `zip contains ${section}`).toContain(section);
    }
    expect(zip.get('profile.json')!.toString('utf8')).toContain('foodsaver@local');
    expect(zip.get('history.json')!.toString('utf8')).toContain('SESSIONS_REVOKED_ALL');
    // one export per 10 minutes (the clock is fixed in e2e)
    await page.reload();
    await walk.click(page.getByTestId('account.settings.export.start'));
    await expect(page.getByTestId('account.settings.export.error')).toHaveAttribute('data-code', 'TOO_MANY_REQUESTS');
  });

  await walk.step('changes her email address', async () => {
    const guest = await as(null);
    await expect(page.getByTestId('account.settings.email.current')).toHaveText('foodsaver@local');
    // an address of another member is refused below the field
    await walk.fill(page.getByTestId('account.settings.email.new'), 'bieb@local');
    await walk.click(page.getByTestId('account.settings.email.submit'));
    await expect(page.getByTestId('account.settings.email.error')).toHaveAttribute('data-code', 'EMAIL_TAKEN');

    // a typo: the notice to the old address aborts the change (logged out, on another device)
    await walk.fill(page.getByTestId('account.settings.email.new'), ABORTED_ADDRESS);
    await walk.click(page.getByTestId('account.settings.email.submit'));
    await expect(page.getByTestId('account.settings.email.pending')).toHaveAttribute('data-email', ABORTED_ADDRESS);
    await expect(page.getByTestId('account.settings.email.error')).toHaveCount(0);
    const firstNotice = await mails.waitForMail({
      to: 'foodsaver@local',
      predicate: (m) => m.links.some((l) => l.includes('/account/email/abort')),
    });
    await guest.goto(mails.extractPath(firstNotice, '/account/email/abort'));
    await expect(guest.getByTestId('account.email.abort.success')).toBeVisible();
    await page.reload();
    await expectDataState(page, 'account.settings.email.pendingState', 'empty');
    // a used abort link answers with the link error, not with a retry
    await guest.reload();
    await expect(guest.getByTestId('account.email.abort.tokenError')).toBeVisible();

    // the right address: confirmed with the link of the mail to the new address
    await walk.fill(page.getByTestId('account.settings.email.new'), NEW_ADDRESS);
    await walk.click(page.getByTestId('account.settings.email.submit'));
    await expect(page.getByTestId('account.settings.email.pending')).toHaveAttribute('data-email', NEW_ADDRESS);
    const confirmMail = await mails.waitForMail({ to: NEW_ADDRESS, predicate: (m) => m.links.some((l) => l.includes('/account/email/confirm')) });
    const notice = await mails.waitForMail({
      to: 'foodsaver@local',
      predicate: (m) => m !== firstNotice && m.id !== firstNotice.id && m.links.some((l) => l.includes('/account/email/abort')),
    });
    await page.goto(mails.extractPath(confirmMail, '/account/email/confirm'));
    await expect(page.getByTestId('account.email.confirm.success')).toBeVisible();
    await checkpoint(page, 'email-confirmed');
    await walk.click(page.getByTestId('account.email.confirm.continue'));
    await expect(page.getByTestId('account.settings.email.current')).toHaveText(NEW_ADDRESS);
    await expectDataState(page, 'account.settings.email.pendingState', 'empty');
    // the second notice link no longer works once the change is complete
    await guest.goto(mails.extractPath(notice, '/account/email/abort'));
    await expect(guest.getByTestId('account.email.abort.tokenError')).toBeVisible();
    const history = (await apiOf(page).getJson<{ items: { action: string }[] }>('/api/history/me?size=50')).items.map((e) => e.action);
    expect(history).toEqual(expect.arrayContaining(['EMAIL_CHANGE_REQUESTED', 'EMAIL_CHANGE_ABORTED', 'EMAIL_CHANGE_COMPLETED']));
  });

  await walk.step('deletes her account', async () => {
    await walk.click(page.getByTestId('account.settings.danger.open'));
    const dialog = page.getByTestId('account.settings.danger.dialog');
    await walk.fill(dialog.getByTestId('account.settings.danger.reason'), 'Ich ziehe weg');
    const confirm = page.getByTestId('account.settings.danger.confirm').locator('button');
    await walk.fill(dialog.getByTestId('account.settings.danger.retype'), 'foodsaver@local');
    await expect(confirm).toBeDisabled();
    await walk.fill(dialog.getByTestId('account.settings.danger.retype'), NEW_ADDRESS);
    await expect(confirm).toBeEnabled();
    await checkpoint(page, 'delete-dialog');
    await walk.click(confirm);

    // logged out on the start page; the old session is dead and a new login is refused
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByTestId('public.landing.hero')).toBeVisible();
    expect((await apiOf(page).get('/api/users/me')).status()).toBe(401);
    const login = await apiOf(await as(null)).post('/api/dev/login-as', { userId: FOODSAVER_ID });
    expect(login.status()).toBeGreaterThanOrEqual(400);
    expect(login.status()).toBeLessThan(500);

    // the forum post stays, written by the anonymised member
    const member = await as('bieb');
    await member.goto(`/forum/threads/${WELCOME_THREAD}`);
    const post = member.locator(`[data-testid="forum.thread.post"][data-post-id="${FOODSAVER_POST}"]`);
    await expect(post).toContainText('Gelöschtes Mitglied');
    await expect(post).not.toContainText('Sarah Müller');

    // ORGA sees the archive flag with the reason; the account can no longer be changed
    await orga.goto(`/users/${FOODSAVER_ID}`);
    await expect(orga.getByTestId('usersAdmin.user.deletedTag')).toBeVisible();
    await expect(orga.getByTestId('usersAdmin.user.archive')).toBeVisible();
    await expect(orga.getByTestId('usersAdmin.user.deleteReason')).toContainText('Ich ziehe weg');
    await expect(orga.getByTestId('usersAdmin.user.delete')).toHaveCount(0);
  });

  // measured 2026-11-15 (rework step 3): 23 clicks on desktop and phone
  walk.finish({ steps: 7, clicks: 23, fields: 6 });
});
