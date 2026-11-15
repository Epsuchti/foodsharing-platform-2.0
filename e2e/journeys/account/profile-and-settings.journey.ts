import type { Page } from '@playwright/test';
import { LANGUAGES, PHONE_MAX, expectDataState, expectNoMissingKeys, type ApiClient, type Language, type Theme } from '../../fixtures';
import { checkpoint, checkpointElement, expect, journey as test } from '../../fixtures/checkpoint';
import { NAMES, ZURICH, memberRow, openStore } from '../../fixtures/flows/stores';
import { Walk } from '../../fixtures/persona';
import { fillDate } from '../../support/date-inputs';

/**
 * Foodsaver Sarah (foodsaver@local) keeps her account up to date: she edits her profile (the mobile number is
 * required, A4), replaces her photo with a cropped one (A5, variants C10), goes on holiday with a temporary sleep mode
 * that her store manager Nina sees on the profile, in the member list and the store team (A9), tries every language
 * (A12) and theme (A17), switches mention mails off (A20), sets up her calendar feed (A11, E3) and downloads her
 * business card (A14). Seed: Sarah has the calendar token `0123456789abcdef` ×4, a photo upload, future pickups and
 * the invitation to "Stammtisch November" (docs/seed-ids.md).
 */
test.use({ role: 'foodsaver' });

const FOODSAVER = '00000001-0001-4000-8000-000000000006';
const SEED_TOKEN = '0123456789abcdef'.repeat(4);
const HTML_LANG: Record<Language, string> = { de: 'de', en: 'en', gws: 'de-CH', fr: 'fr', it: 'it' };
const HEADER_CODE: Record<Language, string> = { de: 'DE', en: 'EN', gws: 'CH', fr: 'FR', it: 'IT' };
/** `lang.<code>`: every language is named in its own language. */
const LANGUAGE_NAME: Record<Language, string> = { de: 'Deutsch', en: 'English', gws: 'Schwiizerdütsch', fr: 'Français', it: 'Italiano' };

/** A 900×600 PNG drawn in the page (non-square, so the crop matters). */
async function picture(page: Page): Promise<Buffer> {
  const dataUrl = await page.evaluate(() => {
    const canvas = document.createElement('canvas');
    canvas.width = 900;
    canvas.height = 600;
    const context = canvas.getContext('2d')!;
    context.fillStyle = '#25834a';
    context.fillRect(0, 0, 900, 600);
    context.fillStyle = '#f6d79b';
    context.beginPath();
    context.arc(450, 300, 180, 0, Math.PI * 2);
    context.fill();
    return canvas.toDataURL('image/png');
  });
  return Buffer.from(dataUrl.split(',')[1], 'base64');
}

/** Path + query of the absolute feed URL (the server's base URL may differ from the e2e origin). */
async function feed(api: ApiClient, url: string): Promise<{ status: number; text: string }> {
  const parsed = new URL(url);
  const response = await api.get(parsed.pathname + parsed.search, { headers: { Accept: 'text/calendar' } });
  return { status: response.status(), text: await response.text() };
}

function savedSettings(page: Page) {
  return page.waitForResponse(
    (response) => response.url().endsWith('/api/users/me/settings') && response.request().method() === 'PATCH',
  );
}

test('foodsaver edits her profile, photo, sleep mode, language, theme, calendar feed and business card @A4 @A5 @C10 @A9 @A12 @A17 @A20 @A11 @E3 @A14', async ({
  page,
  as,
  apiOf,
  pdf,
}, testInfo) => {
  test.setTimeout(240_000);
  const walk = new Walk(page, testInfo, 'foodsaver');
  const api = apiOf(page);

  await walk.step('edits her profile; the mobile number is required', async () => {
    await page.goto('/account/profile');
    await expectDataState(page, 'account.profile.state', 'success');
    await expect(page.getByTestId('account.profile.homeRegion')).toHaveText('Zürich Stadt');
    const save = page.getByTestId('account.profile.save').locator('button');
    await expect(save).toBeDisabled();
    await checkpoint(page, 'profile-form');

    await walk.fill(page.getByTestId('account.profile.mobilePhone'), '');
    await walk.fill(page.getByTestId('account.profile.street'), 'Langstrasse 100');
    await expect(page.getByTestId('account.profile.mobileRequired')).toBeVisible();
    await expect(save).toBeDisabled();
    // review 2026-11-10 F2: the error below the mobile number moves neither input; both keep the same height
    const mobile = (await page.getByTestId('account.profile.mobilePhone').boundingBox())!;
    const landline = (await page.getByTestId('account.profile.landlinePhone').boundingBox())!;
    expect(Math.abs(mobile.height - landline.height)).toBeLessThanOrEqual(1);
    if (Math.abs(mobile.x - landline.x) > 1) expect(Math.abs(mobile.y - landline.y)).toBeLessThanOrEqual(1);
    await checkpoint(page, 'profile-mobile-required');

    await walk.fill(page.getByTestId('account.profile.mobilePhone'), '079 123 45 67');
    await walk.fill(page.getByTestId('account.profile.postcode'), '8004');
    await walk.fill(page.getByTestId('account.profile.city'), 'Zürich');
    await walk.fill(page.getByTestId('account.profile.aboutMePublic'), 'Ich rette gern Brot.');
    await expect(page.getByTestId('account.profile.mobileRequired')).toHaveCount(0);
    await walk.click(page.getByTestId('account.profile.save'));
    await expect(page.getByTestId('shell.toast').locator('.p-toast-message').first()).toBeVisible();

    await page.reload();
    await expectDataState(page, 'account.profile.state', 'success');
    await expect(page.getByTestId('account.profile.street')).toHaveValue('Langstrasse 100');
    await expect(page.getByTestId('account.profile.city')).toHaveValue('Zürich');
    await expect(page.getByTestId('account.profile.aboutMePublic')).toHaveValue('Ich rette gern Brot.');
    expect((await api.getJson<{ mobilePhone?: string }>('/api/users/me')).mobilePhone).toBe('+41791234567');
  });

  await walk.step('replaces her photo with a cropped picture', async () => {
    await page.goto('/account/settings');
    const avatar = page.getByTestId('account.settings.photo.avatar');
    // seed: Sarah already has a photo upload; it is replaced
    await expect(avatar).toHaveAttribute('data-has-photo', 'true');
    const before = (await api.getJson<{ avatarUrl?: string }>('/api/users/me')).avatarUrl;
    await walk.click(page.getByTestId('account.settings.photo.change'));
    await expect(page.getByTestId('account.settings.photo.crop.pick')).toBeVisible();
    walk.count(1);
    await page
      .getByTestId('account.settings.photo.crop.input')
      .setInputFiles({ name: 'ich.png', mimeType: 'image/png', buffer: await picture(page) });
    await expect(page.getByTestId('account.settings.photo.crop.stage')).toBeVisible();
    await walk.fill(page.getByTestId('account.settings.photo.crop.zoom'), '1.5');
    await checkpoint(page, 'photo-crop');
    await walk.click(page.getByTestId('account.settings.photo.crop.apply'));
    await expect(page.getByTestId('account.settings.photo.dialog')).toBeHidden();
    await expect.poll(async () => (await api.getJson<{ avatarUrl?: string }>('/api/users/me')).avatarUrl).not.toBe(before);
    const avatarUrl = (await api.getJson<{ avatarUrl: string }>('/api/users/me')).avatarUrl;
    const id = /^\/api\/uploads\/([0-9a-f-]{36})\?size=md$/.exec(avatarUrl)?.[1];
    expect(id, avatarUrl).toBeTruthy();
    await expect(avatar.locator('img')).toHaveAttribute('src', avatarUrl);
    await expect(page.locator('fs-user-menu img').first()).toHaveAttribute('src', avatarUrl);

    // the cropped original is a JPEG of at most 1 MB; its sm/md/lg variants are JPEGs readable without login
    const original = await page.request.get(`/api/uploads/${id}`);
    expect(original.status()).toBe(200);
    expect(original.headers()['content-type']).toContain('image/jpeg');
    expect((await original.body()).length).toBeLessThanOrEqual(1024 * 1024);
    const anonymous = await as(null);
    for (const size of ['sm', 'md', 'lg']) {
      const response = await anonymous.request.get(`/api/uploads/${id}?size=${size}`);
      expect(response.status(), size).toBe(200);
      expect(response.headers()['content-type'], size).toContain('image/jpeg');
    }
  });

  await walk.step('goes on holiday with a temporary sleep mode', async () => {
    await expectDataState(page, 'account.settings.sleep.state', 'success');
    await expect(page.getByTestId('account.settings.sleep.current')).toHaveAttribute('data-status', 'NONE');
    await walk.click(page.getByTestId('account.settings.sleep.status.TEMPORARY'));
    await expect(page.getByTestId('account.settings.sleep.problem')).toBeVisible();
    await expect(page.getByTestId('account.settings.sleep.save').locator('button')).toBeDisabled();
    // the last day before the first one is refused before asking the server
    walk.count(4);
    await fillDate(page.getByTestId('account.settings.sleep.from'), '2026-11-10');
    await fillDate(page.getByTestId('account.settings.sleep.until'), '2026-11-05');
    await expect(page.getByTestId('account.settings.sleep.problem')).toBeVisible();
    await fillDate(page.getByTestId('account.settings.sleep.from'), '2026-11-02');
    await fillDate(page.getByTestId('account.settings.sleep.until'), '2026-11-20');
    await walk.fill(page.getByTestId('account.settings.sleep.message'), 'Ferien im Tessin, ab dem 21. wieder dabei.');
    await expect(page.getByTestId('account.settings.sleep.problem')).toHaveCount(0);
    await walk.click(page.getByTestId('account.settings.sleep.save'));
    await expect(page.getByTestId('account.settings.sleep.current')).toHaveAttribute('data-status', 'TEMPORARY');
    await checkpointElement(page.getByTestId('account.settings.sleep'), 'sleep-mode-temporary');

    // her store manager Nina sees the moon on the profile, in the member list and in the store team
    const bieb = await as('bieb');
    await bieb.goto(`/profile/${FOODSAVER}`);
    await expect(bieb.getByTestId('profiles.page.sleep')).toBeVisible();
    await bieb.goto(`/regions/${ZURICH}/members`);
    await expectDataState(bieb, 'regions.members.list', 'success');
    await bieb.getByTestId('regions.members.search').fill('Müller');
    const member = bieb.locator(`[data-testid="regions.members.member"][data-user="${FOODSAVER}"]`);
    await expect(member.getByTestId('shared.userChip.sleep')).toBeVisible();
    await openStore(bieb, 'S-01', 'team');
    await expectDataState(bieb, 'stores.team.list', 'success');
    await expect(memberRow(bieb, NAMES.foodsaver).getByTestId('stores.team.sleeping')).toBeVisible();

    // back from holiday: the moon is gone
    await walk.click(page.getByTestId('account.settings.sleep.status.NONE'));
    await walk.click(page.getByTestId('account.settings.sleep.save'));
    await expect(page.getByTestId('account.settings.sleep.current')).toHaveAttribute('data-status', 'NONE');
    await bieb.goto(`/profile/${FOODSAVER}`);
    await expect(bieb.getByTestId('profiles.page.name')).toBeVisible();
    await expect(bieb.getByTestId('profiles.page.sleep')).toHaveCount(0);
  });

  await walk.step('tries every language and goes back to German', async () => {
    await expect(page.getByTestId('account.settings.methods')).toHaveAttribute('data-state', 'success');
    // "last used N … ago" of the login methods counts from the run's own clock
    await checkpoint(page, 'settings', { mask: [page.getByTestId('account.settings.methods').locator('.row__meta')] });
    // from the other end of the list, so every language is a real switch; German comes last
    for (const language of [...LANGUAGES].reverse()) {
      const option = page.getByTestId(`account.settings.language.${language}`);
      const saved = savedSettings(page);
      await walk.click(option);
      expect((await saved).status()).toBe(200);
      await expect(option).toHaveAttribute('aria-checked', 'true');
      await expect(page.locator('html')).toHaveAttribute('lang', HTML_LANG[language]);
      if ((page.viewportSize()?.width ?? 1280) <= PHONE_MAX) {
        await expect(page.getByTestId('shell.languageMenu')).toContainText(HEADER_CODE[language]);
      } else {
        // desktop (decision 133): the user menu's language row names the current language
        await expect(page.getByTestId('shell.userMenu.language')).toContainText(LANGUAGE_NAME[language]);
      }
      await expectNoMissingKeys(page);
      expect((await api.getJson<{ language: Language }>('/api/users/me')).language).toBe(language);
      if (language === 'it') {
        // saved with the account: a fresh load keeps it
        await page.reload();
        await expect(page.locator('html')).toHaveAttribute('lang', 'it');
        await expect(page.getByTestId('account.settings.language.it')).toHaveAttribute('aria-checked', 'true');
      }
    }
  });

  await walk.step('switches between dark, light and the device theme', async () => {
    const html = page.locator('html');
    const theme = async () => (await api.getJson<{ theme: Theme }>('/api/users/me')).theme;
    const choose = async (mode: 'light' | 'dark' | 'system') => {
      const saved = savedSettings(page);
      await walk.click(page.getByTestId(`account.settings.theme.${mode}`));
      expect((await saved).status()).toBe(200);
      await expect(page.getByTestId(`account.settings.theme.${mode}`)).toHaveAttribute('aria-checked', 'true');
    };
    await page.emulateMedia({ colorScheme: 'light' });
    await choose('dark');
    await expect(html).toHaveClass(/fs-dark/);
    expect(await theme()).toBe('DARK');
    await page.reload();
    await expect(html).toHaveClass(/fs-dark/);
    await choose('light');
    await expect(html).not.toHaveClass(/fs-dark/);
    expect(await theme()).toBe('LIGHT');
    // an explicit choice ignores the device setting
    await page.emulateMedia({ colorScheme: 'dark' });
    await expect(html).not.toHaveClass(/fs-dark/);
    await choose('system');
    expect(await theme()).toBe('SYSTEM');
    await expect(html).toHaveClass(/fs-dark/);
    await page.emulateMedia({ colorScheme: 'light' });
    await expect(html).not.toHaveClass(/fs-dark/);
    await page.reload();
    await expect(page.getByTestId('account.settings.theme.system')).toHaveAttribute('aria-checked', 'true');
    await expect(html).not.toHaveClass(/fs-dark/);
  });

  await walk.step('switches mention mails off and keeps the info mails', async () => {
    await expectDataState(page, 'account.settings.mailSwitches.state', 'success');
    await expect(page.getByTestId('account.settings.infoMail').locator('input')).toBeChecked();
    await walk.click(page.getByTestId('account.settings.mentions'));
    await expect
      .poll(async () => (await api.getJson<{ mentionNotifications: boolean }>('/api/users/me')).mentionNotifications)
      .toBe(false);
    await page.reload();
    await expect(page.getByTestId('account.settings.mentions').locator('input')).not.toBeChecked();
    await expect(page.getByTestId('account.settings.infoMail').locator('input')).toBeChecked();
    expect((await api.getJson<{ infoMailMessage: boolean }>('/api/users/me')).infoMailMessage).toBe(true);
  });

  await walk.step('sets up her calendar feed', async () => {
    const url = page.getByTestId('account.settings.calendar.url');
    await expect(url).toHaveValue(new RegExp(`/api/public/calendar/${SEED_TOKEN}\\.ics$`));
    await walk.click(page.getByTestId('account.settings.calendar.events'));
    await walk.click(page.getByRole('option').first()); // "every"
    await walk.click(page.locator('label[for="cal-reminder-15"]'));
    await walk.click(page.locator('label[for="cal-reminder-60"]'));
    await expect(url).toHaveValue(/\?events=every&reminders=15,60$/);
    await expect(page.getByTestId('account.settings.calendar.subscribe')).toHaveAttribute('href', /^webcal:\/\/.*events=every&reminders=15,60$/);
    await checkpointElement(page.getByTestId('account.settings.calendar'), 'calendar-card');
    const oldUrl = await url.inputValue();
    const withPickups = await feed(api, oldUrl);
    expect(withPickups.status).toBe(200);
    expect(withPickups.text).toContain('BEGIN:VCALENDAR');
    expect(withPickups.text).toMatch(/SUMMARY:Abholung /);
    expect(withPickups.text).toContain('SUMMARY:Stammtisch November');
    expect(withPickups.text).toContain('TRIGGER:-PT15M');
    expect(withPickups.text).toContain('TRIGGER:-PT60M');
    // without the pickups the feed has none
    await walk.click(page.locator('label[for="cal-pickups"]'));
    await expect(url).toHaveValue(/pickups=false/);
    expect((await feed(api, await url.inputValue())).text).not.toMatch(/SUMMARY:Abholung /);

    // a new link: the old one is gone, the new one keeps the filters
    await walk.click(page.getByTestId('account.settings.calendar.regenerate'));
    await walk.click(page.locator('.p-confirmdialog-accept-button'));
    await expect(url).not.toHaveValue(new RegExp(SEED_TOKEN));
    await expect(url).toHaveValue(/\/api\/public\/calendar\/[0-9a-f]{32}\.ics\?events=every&pickups=false&reminders=15,60$/);
    expect((await feed(api, oldUrl)).status).toBe(404);
    expect((await feed(api, await url.inputValue())).status).toBe(200);

    // deleted, then created again
    await walk.click(page.getByTestId('account.settings.calendar.delete'));
    await walk.click(page.locator('.p-confirmdialog-accept-button'));
    await expectDataState(page, 'account.settings.calendar.state', 'empty');
    await walk.click(page.getByTestId('account.settings.calendar.create'));
    await expect(url).toHaveValue(/\/api\/public\/calendar\/[0-9a-f]{32}\.ics/);
    expect((await feed(api, await url.inputValue())).status).toBe(200);
  });

  await walk.step('downloads her business card', async () => {
    const card = page.getByTestId('account.settings.businessCard');
    await expect(card.getByTestId('account.settings.businessCard.role')).toContainText(/Foodsaver/i);
    await expect(card.getByTestId('account.settings.businessCard.region')).toContainText('Zürich Stadt');
    const [download] = await Promise.all([page.waitForEvent('download'), walk.click(card.getByTestId('account.settings.businessCard.download'))]);
    expect(download.suggestedFilename()).toBe('business-card-zuerich-stadt.pdf');
    await pdf.expectPdfContains(download, ['Sarah Müller', 'foodsharing Zürich Stadt'], { pages: 1 });
    await expect(card.getByTestId('account.settings.businessCard.error')).toHaveCount(0);
  });

  // measured 2026-11-15 (rework step 3): 32 clicks on desktop and phone
  walk.finish({ steps: 8, clicks: 32, fields: 8 });
});
