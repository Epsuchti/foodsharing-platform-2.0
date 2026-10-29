import { expect, test } from '../../fixtures';

/** C15: the background jobs of plan §6.3 are registered, run on demand and record their runs (ORGA job admin). */
test.use({ seedReset: 'test' });

/** §6.3 table → job names. */
const JOBS = [
  'mail-outbox', // mail outbox worker
  'mailbox-fetcher', // incoming regional mail
  'open-slot-adverts', // open-slot alerts
  'signout-alerts', // sign-out check (daily summary)
  'pickup-reminder', // pickup reminders
  'onboarding-nudges', // onboarding nudges
  'region-maintenance', // parent memberships, sleep mode wake-up, inactive accounts
  'store-maintenance', // unconfirmed slots, empty pickups, inactive store managers
  'statistics-aggregation', // statistics
  'audit-retention', // audit retention
  'hygiene-expiry', // quiz/achievement expiry
  'cleanup', // push subscriptions, tokens, bells, uploads
];

/** Nightly maintenance jobs that are safe to run against the seed. */
const MAINTENANCE = ['region-maintenance', 'store-maintenance', 'statistics-aggregation', 'audit-retention', 'hygiene-expiry', 'cleanup'];

test('C15 every §6.3 job is registered with a schedule @C15', async ({ as, apiOf }) => {
  const orga = apiOf(await as('orga'));
  const jobs = await orga.getJson<{ name: string; cron?: string; locked: boolean }[]>('/api/admin/jobs');
  const names = jobs.map((j) => j.name);
  for (const name of JOBS) {
    expect(names, name).toContain(name);
    expect(jobs.find((j) => j.name === name)!.locked, name).toBe(false);
  }
});

test('C15 maintenance jobs run on demand and record a successful manual run @C15', async ({ as, apiOf, notifications }) => {
  const orga = apiOf(await as('orga'));
  for (const name of MAINTENANCE) {
    await notifications.runJob(name);
    const runs = await orga.getJson<{ items: { jobName: string; outcome: string; trigger: string }[] }>(`/api/admin/jobs/${name}/runs`);
    expect(runs.items[0], name).toMatchObject({ jobName: name, outcome: 'OK', trigger: 'MANUAL' });
  }
  const unknown = await orga.fetch('GET', '/api/admin/jobs/no-such-job/runs');
  expect(unknown.status()).toBe(404);

  const foodsaver = apiOf(await as('foodsaver'));
  expect((await foodsaver.fetch('GET', '/api/admin/jobs')).status()).toBe(403);
});
