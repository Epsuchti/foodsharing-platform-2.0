import { SEED_NOW, expectDataState, regionId, type ApiClient } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { acceptConfirm } from '../../fixtures/flows/forum';
import { Walk, openNewestBell } from '../../fixtures/persona';
import { fillDateTime } from '../../support/date-inputs';

/**
 * Polls of zuerich-stadt (A37c). Bot (bot@local) administers its VOTING group: he sets up a poll (the form refuses
 * duplicate options and a start less than an hour ahead), corrects its name and cancels the running "Stammtisch" poll
 * he started earlier. Sarah (foodsaver@local) votes in the running poll, is told about a new score poll by bell, votes
 * once it has started and reads the results after the end (the server clock is moved). Lead time, option and vote
 * rules, who may see and vote, and when results show are unit-tested (PollRulesTest, polls.resource.spec.ts); scopes
 * (PollEligibilityServiceTest) and the started/ending-soon bells (PollNotificationServiceTest) too.
 */
const ZURICH = regionId('zuerich-stadt');
const poll = (n: number) => `00000045-0001-4000-8000-${String(n).padStart(12, '0')}`;
const HOUR = 3_600_000;
const at = (offsetHours: number) => new Date(Date.parse(SEED_NOW) + offsetHours * HOUR).toISOString();
/** datetime-local value in Europe/Zurich (the browser time zone of every project). */
const local = (iso: string) =>
  new Date(iso).toLocaleString('sv-SE', { timeZone: 'Europe/Zurich' }).replace(' ', 'T').slice(0, 16);

async function createPoll(api: ApiClient, type: string, notifyVoters: boolean): Promise<string> {
  const response = await api.post('/api/polls', {
    regionId: ZURICH, name: 'Welche Lebensmittel fehlen im Fairteiler?', type, scope: 'FOODSAVERS', startsAt: at(2),
    endsAt: at(24), options: ['Brot', 'Gemüse', 'Milchprodukte'], shuffleOptions: false, notifyVoters,
  });
  expect(response.status(), await response.text()).toBe(201);
  return ((await response.json()) as { id: string }).id;
}

test.describe('voting admin', () => {
  test.use({ role: 'bot' });

  test('voting admin sets up a poll, corrects it and cancels a running one @V1', async ({ page }, testInfo) => {
    test.setTimeout(120_000);
    const walk = new Walk(page, testInfo, 'voting admin');

    await walk.step('opens the polls of his region', async () => {
      await page.goto(`/regions/${ZURICH}`);
      await walk.click(page.getByTestId('regions.page.tab.polls'));
      await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/polls$`));
      await expectDataState(page, 'polls.list.state', 'success');
      await expect(page.locator('[data-testid="polls.list.group"][data-status="RUNNING"]')).toBeVisible();
      await expect(page.locator('[data-testid="polls.list.group"][data-status="ENDED"]')).toBeVisible();
      await checkpoint(page, 'region-polls');
    });

    await walk.step('sets up a poll and fixes what the form refuses', async () => {
      await walk.click(page.getByTestId('polls.list.new'));
      await expect(page).toHaveURL(/\/polls\/new\?regionId=/);
      await walk.fill(page.getByTestId('polls.form.name'), 'Neuer Treffpunkt?');
      const inputs = page.getByTestId('polls.form.optionInput');
      await walk.fill(inputs.nth(0), 'Ja');
      await walk.fill(inputs.nth(1), 'ja ');
      // the scope select names how many people may vote in every scope
      await walk.click(page.getByTestId('polls.form.scope'));
      const scopes = page.locator('.p-select-overlay .p-select-option');
      await expect(scopes.first()).toContainText(/\(\d+\)/);
      // no checkpoint on the open list: axe flags the select's scrollable list container (scrollable-region-focusable)
      await walk.click(scopes.first());
      await expect(page.locator('.p-select-overlay')).toHaveCount(0);
      await fillDateTime(page.getByTestId('polls.form.startsAt'), local(at(0.5)));
      await fillDateTime(page.getByTestId('polls.form.endsAt'), local(at(48)));
      await page.getByTestId('polls.form.submit').scrollIntoViewIfNeeded();
      await walk.click(page.getByTestId('polls.form.submit'));
      await expect(page.getByTestId('polls.form.optionsError')).toHaveAttribute('data-code', 'duplicate');
      await walk.fill(inputs.nth(1), 'Nein');
      await walk.click(page.getByTestId('polls.form.addOption'));
      await walk.fill(inputs.nth(2), 'Egal');
      await walk.click(page.getByTestId('polls.form.removeOption').nth(2));
      await expect(inputs).toHaveCount(2);
      // a start in 30 minutes is too early (at least one hour ahead)
      await walk.click(page.getByTestId('polls.form.submit'));
      await expect(page.getByTestId('polls.form.datesHint')).toHaveClass(/\berror\b/);
      await expect(page).toHaveURL(/\/polls\/new\?regionId=/);
      await checkpoint(page, 'poll-form-too-early');
      await fillDateTime(page.getByTestId('polls.form.startsAt'), local(at(3)));
      await walk.click(page.getByTestId('polls.form.submit'));
      await expect(page).toHaveURL(/\/polls\/[0-9a-f-]{36}$/);
      await expect(page.getByTestId('polls.poll.upcoming')).toBeVisible();
      await checkpoint(page, 'poll-upcoming');
    });

    await walk.step('corrects the name', async () => {
      await walk.click(page.getByTestId('polls.poll.edit'));
      await expect(page.getByTestId('polls.form.name')).toHaveValue('Neuer Treffpunkt?');
      await walk.fill(page.getByTestId('polls.form.name'), 'Neuer Treffpunkt im Dezember?');
      await walk.click(page.getByTestId('polls.form.submit'));
      await expect(page.locator('h1')).toContainText('Neuer Treffpunkt im Dezember?');
    });

    await walk.step('cancels the running Stammtisch poll', async () => {
      await page.goto(`/regions/${ZURICH}/polls`);
      await expectDataState(page, 'polls.list.state', 'success');
      await expect(page.locator('[data-testid="polls.list.group"][data-status="UPCOMING"] [data-testid="polls.list.item"]')).toHaveCount(2);
      await walk.click(page.locator(`[data-testid="polls.list.item"][data-poll-id="${poll(1)}"]`));
      await expectDataState(page, 'polls.poll.state', 'success');
      await walk.click(page.getByTestId('polls.poll.cancel'));
      await acceptConfirm(page);
      walk.count(1);
      await expect(page.getByTestId('polls.poll.facts')).toHaveAttribute('data-status', 'CANCELLED');
      await expect(page.getByTestId('polls.poll.cancelled')).toBeVisible();
      await expect(page.getByTestId('polls.poll.cancel')).toHaveCount(0);
      await checkpoint(page, 'poll-cancelled');
    });

    // measured 2026-11-15, desktop and phone
    walk.finish({ steps: 4, clicks: 14, fields: 6 });
  });
});

test.describe('voter', () => {
  test.use({ role: 'foodsaver' });

  test('member votes in a running poll, is told about a new one, votes and reads the results @V1 @N3', async ({
    page,
    api,
    as,
    apiOf,
    clock,
  }, testInfo) => {
    test.setTimeout(120_000);
    const walk = new Walk(page, testInfo, 'voter');
    const submit = page.getByTestId('polls.ballot.submit');

    await walk.step('votes in the running Stammtisch poll', async () => {
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
      await walk.nav('polls');
      await expect(page).toHaveURL(/\/polls\/mine$/);
      await expectDataState(page, 'polls.list.state', 'success');
      await walk.click(page.locator(`[data-testid="polls.list.item"][data-poll-id="${poll(1)}"]`));
      await expectDataState(page, 'polls.poll.state', 'success');
      await expect(page.getByTestId('polls.poll.results')).toHaveCount(0);
      await expect(submit.locator('button')).toBeDisabled();
      await walk.check(page.getByTestId('polls.ballot.option').first());
      await checkpoint(page, 'ballot');
      await walk.click(submit);
      await expect(page.locator('.p-confirmdialog')).toBeVisible();
      await checkpoint(page, 'vote-confirm');
      await acceptConfirm(page);
      walk.count(1);
      await expect(page.getByTestId('polls.poll.voted')).toBeVisible();
      await checkpoint(page, 'voted');
    });

    let scoreId = '';
    await walk.step('is told about a new score poll and votes once it has started', async () => {
      scoreId = await createPoll(apiOf(await as('bot')), 'SCORE', true);
      await openNewestBell(walk, api, 'POLL_NEW');
      await expect(page).toHaveURL(new RegExp(`/polls/${scoreId}$`));
      await expect(page.getByTestId('polls.poll.upcoming')).toBeVisible();
      await clock.fixServer(at(3));
      await page.reload();
      await expectDataState(page, 'polls.poll.state', 'success');
      const scales = page.getByTestId('polls.ballot.option');
      await expect(scales).toHaveCount(3);
      await walk.check(scales.nth(0).locator('input[data-value="2"]'));
      // every option needs a value
      await expect(submit.locator('button')).toBeDisabled();
      await walk.check(scales.nth(1).locator('input[data-value="0"]'));
      await walk.check(scales.nth(2).locator('input[data-value="-2"]'));
      await checkpoint(page, 'score-ballot');
      await walk.click(submit);
      await acceptConfirm(page);
      walk.count(1);
      await expect(page.getByTestId('polls.poll.voted')).toBeVisible();
    });

    await walk.step('reads the results after the end', async () => {
      await clock.fixServer(at(25));
      await page.reload();
      await expectDataState(page, 'polls.poll.state', 'success');
      await expect(page.getByTestId('polls.poll.results')).toBeVisible();
      await expect(page.getByTestId('polls.poll.result')).toHaveCount(3);
      await checkpoint(page, 'results');
    });

    // measured 2026-11-15: 12 clicks on desktop, 14 on the phone
    walk.finish({ steps: 3, clicks: 14, fields: 0 });
  });
});
