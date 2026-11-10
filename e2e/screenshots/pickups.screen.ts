import { type ApiClient, S01_THURSDAY, S07_WEDNESDAY, injectApiError, seedStore, zurichPickupAt } from '../fixtures';
import { fillDate } from '../support/date-inputs';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Pickup screens (M3 F4): store pickups tab (day list; as manager with split pills, the "Unbestätigt (n)" chip and the
 * summary), sign-up dialog eligible / not eligible, regular editor, one-time pickup dialog, options, my pickups,
 * agenda, dashboard card, loading and error; the store rules card, the "Gesperrt" chip and its reason dialog
 * (decision 167 S2); the linked-store step of the sign-up dialog and "Verknüpfung fehlt" (S3). Baselines in the end batch
 * (decision 69). The move dialog (PU10) with its summary and the taken-time error.
 */
const S01 = seedStore('S-01').id;
const S02 = seedStore('S-02').id;
const S03 = seedStore('S-03').id;
const S07 = seedStore('S-07').id;
const S01_MONDAY = zurichPickupAt(7, '19:00');

screenshotTest.use({ seedReset: 'file' });

forEachVariant((variant) => {
  screenshotTest.describe(`pickups ${variant.name}`, () => {
    screenshotTest.describe('guide', () => {
      screenshotTest.use({ variant, role: 'guide' });

      screenshotTest('my pickups with a cleaning shift @FS2 @screenshot', async ({ page, visual }) => {
        await page.goto('/pickups');
        await expect(page.getByTestId('pickups.mine.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('pickups.mine.cleaning').first()).toBeVisible();
        await visual.expectScreen(page, `pickups-mine-cleaning-${variant.name}`);
      });
    });

    screenshotTest.describe('foodsaver', () => {
      screenshotTest.use({ variant, role: 'foodsaver' });

      screenshotTest('pickups tab day list @PU3 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `pickups-tab-${variant.name}`);
      });

      screenshotTest('slot dialog rich (co-pickup) and short (team) @PU3 @screenshot', async ({ page, visual }) => {
        // decision 165: compact day cards, "Chat öffnen", rich dialog for co-pickups, short one for the team
        await page.goto(`/stores/${S01}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'success');
        await page.locator(`[data-testid="stores.pickups.pickup"][data-pickup-at="${S01_THURSDAY}"] [data-testid="stores.pickups.slot.taken"][data-user="Marco Bernasconi"]`).click();
        await expect(page.getByTestId('stores.pickups.slotDetail.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `pickups-slot-rich-${variant.name}`);
        await page.getByTestId('stores.pickups.slotDetail.close').click();
        await page.locator(`[data-testid="stores.pickups.pickup"][data-pickup-at="${S01_MONDAY}"] [data-testid="stores.pickups.slot.taken"][data-user="Nina Schneider"]`).click();
        await expect(page.getByTestId('stores.pickups.slotDialog')).toHaveAttribute('data-mode', 'short');
        await visual.expectScreen(page, `pickups-slot-short-${variant.name}`);
      });

      screenshotTest('slot dialog rich loading @PU3 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: `/api/stores/${S01}/pickups/*/signups/*`, delayMs: 30_000 });
        await page.goto(`/stores/${S01}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'success');
        await page.locator(`[data-testid="stores.pickups.pickup"][data-pickup-at="${S01_THURSDAY}"] [data-testid="stores.pickups.slot.taken"][data-user="Marco Bernasconi"]`).click();
        await expect(page.getByTestId('stores.pickups.slotDetail.state')).toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `pickups-slot-loading-${variant.name}`, { waitForData: false });
      });

      screenshotTest('sign-up dialog eligible @PU3 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S07}/pickups`);
        await page.locator(`[data-testid="stores.pickups.pickup"][data-pickup-at="${S07_WEDNESDAY}"]`).getByTestId('stores.pickups.slot.free').first().click();
        await expect(page.getByTestId('pickups.signup.eligible')).toBeVisible();
        await visual.expectScreen(page, `pickups-signup-eligible-${variant.name}`);
      });

      screenshotTest('pickups page, agenda, dashboard @PU7 @PU8 @A19 @screenshot', async ({ page, visual }) => {
        // one page since 2026-11-04: my pickups and where can I help (baselines in the end batch, decision 69)
        await page.goto('/pickups');
        await expect(page.getByTestId('pickups.mine.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('pickups.options.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `pickups-page-${variant.name}`);
        await page.getByTestId('pickups.options.showMore').click();
        await visual.expectScreen(page, `pickups-page-more-${variant.name}`);
        await page.goto('/pickups/agenda/2026-11-05');
        await expect(page.getByTestId('pickups.agenda.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `pickups-agenda-${variant.name}`);
        await page.goto('/dashboard');
        await expect(page.getByTestId('dashboard.pickups.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `pickups-dashboard-${variant.name}`);
      });

      screenshotTest('pickups tab loading and error @PU3 @screenshot', async ({ page, visual }) => {
        const delay = await injectApiError(page, { url: `/api/stores/${S01}/pickups`, delayMs: 30_000 });
        await page.goto(`/stores/${S01}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `pickups-tab-loading-${variant.name}`, { waitForData: false });
        await delay.remove();
        await injectApiError(page, { url: `/api/stores/${S01}/pickups`, status: 500 });
        await page.reload();
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `pickups-tab-error-${variant.name}`);
      });
    });

    screenshotTest.describe('foodsaver booking', () => {
      // books a slot, so the seed is reset per test and the screens above stay on the untouched seed
      screenshotTest.use({ variant, role: 'foodsaver', seedReset: 'test' });

      screenshotTest('booked slot, unconfirmed @PU3 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S07}/pickups`);
        const row = page.locator(`[data-testid="stores.pickups.pickup"][data-pickup-at="${S07_WEDNESDAY}"]`);
        await row.getByTestId('stores.pickups.slot.free').first().click();
        await page.getByTestId('pickups.signup.confirm').click();
        await expect(row.locator('[data-testid="stores.pickups.slot.taken"][data-confirmed="false"]')).toHaveCount(1);
        await visual.expectScreen(page, `pickups-booked-${variant.name}`);
      });

      screenshotTest('help needed card with an own sign-up @PU7 @A19 @screenshot', async ({ page, api, visual }) => {
        // review fix A2: the first option of the card is the foodsaver's, shown as "Eingetragen · noch unbestätigt"
        const first = zurichPickupAt(0, '19:00');
        expect((await api.post(`/api/stores/${S01}/pickups/${encodeURIComponent(first)}/signups`)).ok()).toBe(true);
        await page.goto('/dashboard');
        const card = page.getByTestId('dashboard.help');
        await expect(async () => {
          if (!(await card.count())) await page.locator('.defer-slot.o-help').scrollIntoViewIfNeeded({ timeout: 1_000 });
          await expect(card).toBeAttached({ timeout: 1_000 });
        }).toPass();
        await card.scrollIntoViewIfNeeded();
        await expect(page.getByTestId('dashboard.help.state')).toHaveAttribute('data-state', 'success');
        await expect(card.getByTestId('dashboard.help.signedUp')).toBeVisible();
        await visual.expectElement(card, `pickups-help-signed-up-${variant.name}`);
      });
    });

    screenshotTest.describe('jumper', () => {
      screenshotTest.use({ variant, role: 'jumper' });

      screenshotTest('eligibility reasons behind the "Gesperrt" chip @PU4 @screenshot', async ({ page, visual }) => {
        // decision 167 S2: eligibility reasons use the same lock chip and reason dialog as the store rules
        await page.goto(`/stores/${S02}/pickups`);
        await page.getByTestId('stores.pickups.slot.blocked').first().click();
        await expect(page.getByTestId('shared.eligibility.reason').first()).toBeVisible();
        await visual.expectScreen(page, `pickups-signup-reasons-${variant.name}`);
      });
    });

    screenshotTest.describe('sleepy with store rules', () => {
      // S-06's live policy with a 3-day gap; sleepy has Friday (confirmed) and the seeded Tuesday, so Sunday is blocked
      screenshotTest.use({ variant, role: 'sleepy', seedReset: 'test' });

      const S06 = seedStore('S-06').id;
      const FRIDAY = zurichPickupAt(4, '19:00');
      const SUNDAY = zurichPickupAt(6, '19:00');

      async function gapRule(bieb: ApiClient): Promise<void> {
        const current = await bieb.getJson<{ pickupPolicy: Record<string, unknown> }>(`/api/stores/${S06}/automation`);
        await bieb.putJson(`/api/stores/${S06}/automation/pickup-policy`, {
          ...current.pickupPolicy,
          enabled: true,
          experienceRuleEnabled: false,
          gapRuleEnabled: true,
          minimumGapDays: 3,
        });
        for (const at of [FRIDAY, SUNDAY]) {
          await bieb.putJson(`/api/stores/${S06}/pickups/${encodeURIComponent(at)}`, { slotCount: 2 });
        }
      }

      screenshotTest('rules card, Gesperrt chip and reason dialog @AU1 @PU3 @screenshot', async ({ page, as, apiOf, store, visual }) => {
        await gapRule(apiOf(await as('bieb')));
        await store.signUp('sleepy', 'S-06', FRIDAY);
        await page.goto(`/stores/${S06}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'success');
        await page.getByTestId('stores.pickups.rules.toggle').click();
        await expect(page.getByTestId('stores.pickups.rules.line').first()).toBeVisible();
        await expect(page.getByTestId('stores.pickups.slot.blocked')).toBeVisible();
        await visual.expectScreen(page, `pickups-rules-blocked-${variant.name}`);
        await page.getByTestId('stores.pickups.slot.blocked').click();
        await expect(page.getByTestId('pickups.blocked.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('pickups.blocked.reason').first()).toBeVisible();
        await visual.expectScreen(page, `pickups-blocked-dialog-${variant.name}`);
      });

      screenshotTest('rules card loading and error @AU1 @screenshot', async ({ page, as, apiOf, visual }) => {
        await gapRule(apiOf(await as('bieb')));
        const delay = await injectApiError(page, { url: `/api/stores/${S06}/automation/rules`, delayMs: 30_000 });
        await page.goto(`/stores/${S06}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'success');
        await page.getByTestId('stores.pickups.rules.toggle').click();
        await expect(page.getByTestId('stores.pickups.rules')).toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `pickups-rules-loading-${variant.name}`, { waitForData: false });
        await delay.remove();
        await injectApiError(page, { url: `/api/stores/${S06}/automation/rules`, status: 500 });
        await page.reload();
        await expect(page.getByTestId('stores.pickups.rules')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `pickups-rules-error-${variant.name}`);
      });
    });

    screenshotTest.describe('foodsaver with a linked store', () => {
      // decision 167 S3: S-04 needs an S-01 pickup on the same day or up to 2 days after; foodsaver is in both teams
      screenshotTest.use({ variant, role: 'foodsaver', seedReset: 'test' });

      const S04 = seedStore('S-04').id;
      const SATURDAY = zurichPickupAt(5, '19:30');
      const MONDAY = zurichPickupAt(7, '19:00');
      const linkedOptions = `/api/stores/${S04}/automation/linked-options`;

      async function linkS04ToS01(orga: ApiClient): Promise<void> {
        for (const id of [S01, S04]) {
          await orga.patchJson(`/api/stores/${id}`, { useRegionPickupRule: false });
        }
        const current = await orga.getJson<{ pickupPolicy: Record<string, unknown> }>(`/api/stores/${S04}/automation`);
        await orga.putJson(`/api/stores/${S04}/automation/pickup-policy`, {
          ...current.pickupPolicy,
          enabled: true,
          gapRuleEnabled: false,
          experienceRuleEnabled: false,
          publicHolidayRuleEnabled: false,
          linkedStores: [
            { storeIds: [S01], bidirectional: false, windowMode: 'LAST_DAYS', windowDaysBefore: 0, windowDaysAfter: 2, graceMinutes: 120 },
          ],
        });
      }

      async function openStep(page: import('@playwright/test').Page): Promise<void> {
        await page.goto(`/stores/${S04}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'success');
        await page.locator(`[data-testid="stores.pickups.pickup"][data-pickup-at="${SATURDAY}"]`).getByTestId('stores.pickups.slot.free').click();
      }

      screenshotTest('linked step: choose, none free, not in the team @AU1 @PU3 @screenshot', async ({ page, as, apiOf, visual }) => {
        await linkS04ToS01(apiOf(await as('orga')));
        await openStep(page);
        await expect(page.getByTestId('pickups.signup.linked.state')).toHaveAttribute('data-state', 'success');
        await page.locator(`[data-testid="pickups.signup.linked.option"][data-at="${MONDAY}"] input`).check();
        await visual.expectScreen(page, `pickups-linked-choose-${variant.name}`);
        // the same rule without a free pickup, and with S-01 as a store foodsaver is not in
        for (const [name, patch] of [
          ['none-free', { options: [] }],
          ['not-member', { options: [], notMember: [{ id: S01, name: seedStore('S-01').name }] }],
        ] as const) {
          await page.route(`**${linkedOptions}*`, async (route) => {
            const response = await route.fetch();
            const json = await response.json();
            json.rules = json.rules.map((rule: object) => ({ ...rule, ...patch }));
            await route.fulfill({ response, json });
          });
          await page.keyboard.press('Escape');
          await openStep(page);
          await expect(page.getByTestId(name === 'none-free' ? 'pickups.signup.linked.noneFree' : 'pickups.signup.linked.notMember')).toBeVisible();
          await visual.expectScreen(page, `pickups-linked-${name}-${variant.name}`);
          await page.unroute(`**${linkedOptions}*`);
        }
      });

      screenshotTest('Verknüpfung fehlt, repair and satisfied @AU1 @PU3 @screenshot', async ({ page, as, apiOf, api, profiles, store, visual }) => {
        await linkS04ToS01(apiOf(await as('orga')));
        // a real missing link: Saturday booked together with Monday, then Monday signed out
        const booked = await api.post(`/api/stores/${S04}/pickups/${encodeURIComponent(SATURDAY)}/signups`, {
          linkedPickups: [{ storeId: S01, pickupAt: MONDAY }],
        });
        expect(booked.status()).toBe(200);
        const out = await api.fetch('DELETE', `/api/stores/${S01}/pickups/${encodeURIComponent(MONDAY)}/signups/${await profiles.userId('foodsaver')}`);
        expect(out.ok()).toBe(true);
        await page.goto(`/stores/${S04}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'success');
        const pill = page.locator(`[data-testid="stores.pickups.pickup"][data-pickup-at="${SATURDAY}"]`).getByTestId('stores.pickups.linkMissing');
        await pill.scrollIntoViewIfNeeded();
        await expect(pill).toBeVisible();
        await visual.expectScreen(page, `pickups-link-missing-${variant.name}`);
        await pill.click();
        await expect(page.getByTestId('pickups.signup.linked.repair')).toBeVisible();
        await expect(page.getByTestId('pickups.signup.linked.option').first()).toBeVisible();
        await visual.expectScreen(page, `pickups-link-repair-${variant.name}`);
        await page.keyboard.press('Escape');
        // satisfied: S-01 Thursday 12.11. is booked, S-04 Wednesday 11.11. needs nothing more
        const wednesday = zurichPickupAt(9, '18:30');
        await store.signUp('foodsaver', 'S-01', zurichPickupAt(10, '19:00'));
        await page.reload();
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'success');
        await page.locator(`[data-testid="stores.pickups.pickup"][data-pickup-at="${wednesday}"]`).getByTestId('stores.pickups.slot.free').click();
        await expect(page.getByTestId('pickups.signup.linked.satisfied')).toBeVisible();
        await expect(page.getByTestId('pickups.signup.confirm')).toBeVisible();
        await visual.expectScreen(page, `pickups-linked-satisfied-${variant.name}`);
      });

      screenshotTest('linked step loading and error @AU1 @screenshot', async ({ page, as, apiOf, visual }) => {
        await linkS04ToS01(apiOf(await as('orga')));
        const delay = await injectApiError(page, { url: linkedOptions, delayMs: 30_000 });
        await openStep(page);
        await expect(page.getByTestId('pickups.signup.linked.state')).toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `pickups-linked-loading-${variant.name}`, { waitForData: false });
        await delay.remove();
        await injectApiError(page, { url: linkedOptions, status: 500 });
        await page.keyboard.press('Escape');
        await openStep(page);
        await expect(page.getByTestId('pickups.signup.linked.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `pickups-linked-error-${variant.name}`);
      });
    });

    screenshotTest.describe('bieb', () => {
      screenshotTest.use({ variant, role: 'bieb' });

      screenshotTest('regular editor and one-time dialog @PU1 @PU2 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/pickups`);
        await page.getByTestId('stores.pickups.regular').click();
        await expect(page.getByTestId('stores.regular.row').first()).toBeVisible();
        await visual.expectScreen(page, `pickups-regular-${variant.name}`);
        await page.getByRole('button', { name: /Abbrechen|Annuler/ }).click();
        await page.getByTestId('stores.pickups.add').click();
        await expect(page.getByTestId('stores.pickups.edit.date')).toBeVisible();
        await visual.expectScreen(page, `pickups-one-time-${variant.name}`);
      });

      screenshotTest('pickups tab as manager: split pill, chip, summary @PU3 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('stores.pickups.range.pending')).toBeVisible();
        await expect(page.getByTestId('stores.pickups.slot.confirm').first()).toBeVisible();
        await expect(page.getByTestId('stores.pickups.summary').first()).toBeVisible();
        await visual.expectScreen(page, `pickups-tab-manager-${variant.name}`);
      });

      screenshotTest('slot dialog rich as manager: phone, Bestätigen, Austragen @PU3 @S9 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'success');
        await page.locator(`[data-testid="stores.pickups.pickup"][data-pickup-at="${S01_THURSDAY}"] [data-testid="stores.pickups.slot.taken"][data-user="Sarah Müller"]`).click();
        await expect(page.getByTestId('stores.pickups.slotDetail.state')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('stores.pickups.confirmSlot')).toBeVisible();
        await visual.expectScreen(page, `pickups-slot-manager-${variant.name}`);
      });
    });

    screenshotTest.describe('bot', () => {
      screenshotTest.use({ variant, role: 'bot' });

      screenshotTest('move dialog with summary and taken time @PU10 @EA16 @screenshot', async ({ page, visual }) => {
        // S-03 Friday 6.11 18:30 (regular): guide signed up, his linked EinAb booked by one newcomer
        const friday = zurichPickupAt(4, '18:30');
        await page.goto(`/stores/${S03}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'success');
        await page.locator(`[data-testid="stores.pickups.pickup"][data-pickup-at="${friday}"] [data-testid="stores.pickups.edit"]`).click();
        await expect(page.getByTestId('pickups.slot.move')).toBeVisible();
        await visual.expectScreen(page, `pickups-edit-move-${variant.name}`);
        await page.getByTestId('pickups.slot.move').click();
        await expect(page.getByTestId('pickups.move.introPickups')).toBeVisible();
        await visual.expectScreen(page, `pickups-move-${variant.name}`);
        // the store's regular Tuesday 10.11 18:30: 409 PICKUP_TIME_TAKEN in the dialog (nothing changes)
        await fillDate(page.getByTestId('pickups.move.newDate'), '2026-11-10');
        await page.getByTestId('pickups.move.save').click();
        await expect(page.getByTestId('pickups.move.error')).toBeVisible();
        await visual.expectScreen(page, `pickups-move-taken-${variant.name}`);
      });
    });
  });
});
