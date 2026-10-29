import { expect, expectDataState, expectNoSeriousViolations, SEED_NOW, test, type ApiClient } from '../../fixtures';

/** V1 polls (A36/A37c): bot administrates the zuerich-stadt VOTING group; seed clock 2026-11-02 09:00 UTC. */
const poll = (n: number) => `00000045-0001-4000-8000-${String(n).padStart(12, '0')}`;
const HOUR = 3_600_000;
const at = (offsetHours: number) => new Date(Date.parse(SEED_NOW) + offsetHours * HOUR).toISOString();
/** datetime-local value in Europe/Zurich (the browser time zone of every project). */
const local = (iso: string) =>
  new Date(iso).toLocaleString('sv-SE', { timeZone: 'Europe/Zurich' }).replace(' ', 'T').slice(0, 16);

async function createPoll(api: ApiClient, regionId: string, type: string, options: string[]): Promise<string> {
  const response = await api.post('/api/polls', {
    regionId, name: `Abstimmung ${type}`, type, scope: 'FOODSAVERS', startsAt: at(2), endsAt: at(24),
    options, shuffleOptions: type === 'THUMBS', notifyVoters: false,
  });
  expect(response.status(), await response.text()).toBe(201);
  return ((await response.json()) as { id: string }).id;
}

test.use({ seedReset: 'test' });

test.describe('bot', () => {
  test.use({ role: 'bot' });

  test('V1 voting admin creates polls of all four types; options validation; lead time @V1 @page', async ({ page, api, region }) => {
    const zs = region.id('zuerich-stadt');
    await page.goto(`/regions/${zs}`);
    await page.getByTestId('regions.page.tab.polls').click();
    await expectDataState(page, 'polls.list.state', 'success');
    await expect(page.locator('[data-testid="polls.list.group"][data-status="RUNNING"]')).toBeVisible();
    await page.getByTestId('polls.list.new').click();
    await expect(page).toHaveURL(/\/polls\/new\?regionId=/);

    await page.getByTestId('polls.form.name').fill('Neuer Treffpunkt?');
    const inputs = page.getByTestId('polls.form.optionInput');
    await inputs.nth(0).fill('Ja');
    await inputs.nth(1).fill('ja ');
    await page.getByTestId('polls.form.startsAt').fill(local(at(0.5)));
    await page.getByTestId('polls.form.endsAt').fill(local(at(48)));
    await page.getByTestId('polls.form.endsAt').blur();
    await page.getByTestId('polls.form.submit').scrollIntoViewIfNeeded();
    await page.getByTestId('polls.form.submit').click();
    await expect(page.getByTestId('polls.form.optionsError')).toHaveAttribute('data-code', 'duplicate');
    await inputs.nth(1).fill('Nein');
    await page.getByTestId('polls.form.addOption').click();
    await inputs.nth(2).fill('Egal');
    await page.getByTestId('polls.form.removeOption').nth(2).click();
    await expect(inputs).toHaveCount(2);
    // the server clock decides the lead time (start ≥ now + 1 h)
    await page.getByTestId('polls.form.submit').click();
    await expect(page.getByTestId('polls.form.serverError')).toHaveAttribute('data-code', 'POLL_DATES_INVALID');
    await page.getByTestId('polls.form.startsAt').fill(local(at(3)));
    await expectNoSeriousViolations(page);
    await page.getByTestId('polls.form.submit').click();
    await expect(page).toHaveURL(/\/polls\/[0-9a-f-]{36}$/);
    await expect(page.getByTestId('polls.poll.status')).toBeVisible();
    await expect(page.getByTestId('polls.poll.upcoming')).toBeVisible();

    await page.getByTestId('polls.poll.edit').click();
    await expect(page.getByTestId('polls.form.name')).toHaveValue('Neuer Treffpunkt?');
    await page.getByTestId('polls.form.name').fill('Neuer Treffpunkt im Dezember?');
    await page.getByTestId('polls.form.submit').click();
    await expect(page.locator('h1')).toContainText('Neuer Treffpunkt im Dezember?');

    for (const type of ['SELECT_MULTIPLE', 'THUMBS', 'SCORE']) await createPoll(api, zs, type, ['A', 'B', 'C']);
    await page.goto(`/regions/${zs}/polls`);
    await expect(page.locator('[data-testid="polls.list.group"][data-status="UPCOMING"] [data-testid="polls.list.item"]')).toHaveCount(5);
    await expect(page.getByTestId('shell.toast').locator('.p-toast-message')).toHaveCount(0, { timeout: 15_000 });
  });

  test('V1 author cancels poll @V1', async ({ page }) => {
    await page.goto(`/polls/${poll(1)}`);
    await page.getByTestId('polls.poll.cancel').click();
    await page.locator('.p-confirmdialog .p-confirmdialog-accept-button').click();
    await expect(page.getByTestId('polls.poll.facts')).toHaveAttribute('data-status', 'CANCELLED');
    await expect(page.getByTestId('polls.poll.cancelled')).toBeVisible();
    await expect(page.getByTestId('polls.poll.cancel')).toHaveCount(0);
  });
});

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('V1 eligible member votes once per type, double vote refused, ineligible forbidden, results only after end @V1 @page', async ({ page, as, apiOf, clock, region }) => {
    const zs = region.id('zuerich-stadt');
    // SELECT_ONE (seed 01, running, foodsaver has not voted)
    await page.goto(`/polls/${poll(1)}`);
    await expect(page.getByTestId('polls.poll.results')).toHaveCount(0);
    const submit = page.getByTestId('polls.ballot.submit');
    await expect(submit.locator('button')).toBeDisabled();
    await page.getByTestId('polls.ballot.option').first().check();
    await submit.click();
    await page.locator('.p-confirmdialog .p-confirmdialog-accept-button').click();
    await expect(page.getByTestId('polls.poll.voted')).toBeVisible();
    const again = await apiOf(page).post(`/api/polls/${poll(1)}/vote`, { values: { '0': 1 } });
    expect(again.status()).toBe(409);

    // the other types: created by bot, started by moving the server clock
    const bot = apiOf(await as('bot'));
    const ids: Record<string, string> = {};
    for (const type of ['SELECT_MULTIPLE', 'THUMBS', 'SCORE']) ids[type] = await createPoll(bot, zs, type, ['A', 'B', 'C']);
    await clock.fixServer(at(3));

    await page.goto(`/polls/${ids.SELECT_MULTIPLE}`);
    const options = page.getByTestId('polls.ballot.option');
    await options.nth(0).check();
    await options.nth(2).check();
    await submit.click();
    await page.locator('.p-confirmdialog .p-confirmdialog-accept-button').click();
    await expect(page.getByTestId('polls.poll.voted')).toBeVisible();

    for (const [type, value] of [['THUMBS', '1'], ['SCORE', '-2']] as const) {
      await page.goto(`/polls/${ids[type]}`);
      const scales = page.getByTestId('polls.ballot.option');
      await expect(scales).toHaveCount(3);
      await scales.nth(0).locator(`input[data-value="${value}"]`).check();
      await expect(submit.locator('button')).toBeDisabled();
      await scales.nth(1).locator('input[data-value="0"]').check();
      await scales.nth(2).locator(`input[data-value="${value}"]`).check();
      await submit.click();
      await page.locator('.p-confirmdialog .p-confirmdialog-accept-button').click();
      await expect(page.getByTestId('polls.poll.voted')).toBeVisible();
    }
    await expect(page.getByTestId('shell.toast').locator('.p-toast-message')).toHaveCount(0, { timeout: 15_000 });
    await expectNoSeriousViolations(page);

    // results once ended
    await clock.fixServer(at(25));
    await page.goto(`/polls/${ids.SCORE}`);
    await expect(page.getByTestId('polls.poll.results')).toBeVisible();
    await expect(page.getByTestId('polls.poll.result')).toHaveCount(3);
    await page.goto(`/polls/${poll(2)}`);
    await expect(page.getByTestId('polls.poll.results')).toBeVisible();

    const outsider = await as('profile');
    await outsider.goto(`/polls/${poll(1)}`);
    await expectDataState(outsider, 'polls.poll.state', 'forbidden');
    await clock.fixServer();
  });
});
