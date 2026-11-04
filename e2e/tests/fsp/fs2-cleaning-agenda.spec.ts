import { FSPS, acceptPendingConsents, expect, expectDataState, test } from '../../fixtures';

/**
 * FS2 follow-ups (decision 154): a cleaning shift shows in "My pickups", the agenda, the dashboard card and the
 * calendar feed. Seed: guide has an unconfirmed sign-up at Wiedikon on Saturday 2026-11-07, 10:00 in Zürich.
 */
const WIEDIKON_NAME = 'Fairteiler Wiedikon';

test.describe('my cleaning shifts', () => {
  test.use({ seedReset: 'test', role: 'guide' });

  test('FS2 cleaning shift in my pickups, agenda and dashboard, sign-out removes it @FS2 @PU8 @A19 @page', async ({ page, apiOf }) => {
    await acceptPendingConsents(apiOf(page));

    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.pickups.cleaning').first()).toBeVisible();

    await page.goto('/pickups');
    await expectDataState(page, 'pickups.mine.state', 'success');
    const shift = page.locator(`[data-testid="pickups.mine.cleaning"][data-fsp="${WIEDIKON_NAME}"]`);
    await expect(shift).toHaveAttribute('data-confirmed', 'false');
    await expect(shift.getByRole('link')).toHaveAttribute('href', `/food-share-points/${FSPS.wiedikon}/cleaning`);

    await page.goto('/pickups/agenda/2026-11-07');
    await expectDataState(page, 'pickups.agenda.state', 'success');
    await expect(page.locator(`[data-testid="pickups.agenda.cleaning"][data-fsp="${WIEDIKON_NAME}"]`)).toBeVisible();

    await page.goto('/pickups');
    await shift.getByTestId('pickups.mine.cleaning.signOut').click();
    await page.locator('.p-confirmdialog-accept-button').click();
    await expect(shift).toHaveCount(0);
  });

  test('FS2 calendar switch for cleaning shifts changes the feed link @FS2 @A11', async ({ page, apiOf }) => {
    await acceptPendingConsents(apiOf(page));
    await page.goto('/account/settings');
    // guide has no calendar link in the seed
    await expectDataState(page, 'account.settings.calendar.state', 'empty');
    await page.getByTestId('account.settings.calendar.create').click();
    const url = page.getByTestId('account.settings.calendar.url');
    await expect(url).toHaveValue(/\.ics$/);
    const feed = await page.request.get(new URL(await url.inputValue()).pathname, { headers: { Accept: 'text/calendar' } });
    expect((await feed.text()).replace(/\r\n /g, '')).toContain(`SUMMARY:Putzeinsatz ${WIEDIKON_NAME} (unbestätigt)`);

    await page.locator('label[for="cal-cleaningShifts"]').click();
    await expect(url).toHaveValue(/cleaningShifts=false/);
    const without = await page.request.get(new URL(await url.inputValue()).pathname + new URL(await url.inputValue()).search);
    expect(await without.text()).not.toContain('Putzeinsatz');
  });
});

test.describe('automatic confirmation and time zone', () => {
  test.use({ seedReset: 'test', role: 'bieb' });

  test('FS2 auto-confirm confirms a sign-up at once and the zone is saved @FS2 @page', async ({ page, as, apiOf }) => {
    await acceptPendingConsents(apiOf(page));
    await page.goto(`/food-share-points/${FSPS.wiedikon}/edit`);
    await expectDataState(page, 'foodSharePoints.form.state', 'success');
    await expect(page.getByTestId('foodSharePoints.form.timeZone')).toContainText('Europe/Zurich');
    await page.locator('label[for="ff-auto-confirm"]').click();
    await page.getByTestId('foodSharePoints.form.timeZone').click();
    await page.getByRole('option', { name: 'Europe/Paris' }).click();
    await page.getByTestId('foodSharePoints.form.submit').click();
    await expect(page).toHaveURL(new RegExp(`/food-share-points/${FSPS.wiedikon}$`));

    const foodsaver = await as('foodsaver');
    const api = apiOf(foodsaver);
    const shifts = await (await api.get(`/api/food-share-points/${FSPS.wiedikon}/cleaning/shifts`)).json();
    const free = shifts.find((shift: { occupiedSlots: unknown[]; slotCount: number }) => shift.occupiedSlots.length < shift.slotCount);
    const signup = await api.post(`/api/food-share-points/${FSPS.wiedikon}/cleaning/shifts/${free.shiftAt}/signups`);
    expect(signup.ok()).toBe(true);
    expect((await signup.json()).confirmed).toBe(true);
    await foodsaver.goto('/pickups');
    await expect(foodsaver.locator('[data-testid="pickups.mine.cleaning"]').first()).toHaveAttribute('data-confirmed', 'true');
  });
});
