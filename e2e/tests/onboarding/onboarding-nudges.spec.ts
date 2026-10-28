import { SEED_NOW, expect, test } from '../../fixtures';
import { USERS, verifyViaApi } from '../verification/_verification';

/** §11.4: the welcome team's bell for every new region member, and the daily stalled-newcomer nudge job. */
test.use({ seedReset: 'test' });

const NEWBIE_ID = '00000001-0001-4000-8000-000000000001';

interface DevNotification {
  userId: string;
  type: string;
  channel: string;
  link?: string;
  identifier?: string;
}

interface BellDto {
  type: string;
  identifier?: string;
  link?: string;
}

test('EA8 welcome team gets a bell for a new member, removed once the member is verified @EA8 @N3', async ({ as, apiOf, region }) => {
  const welcome = apiOf(await as('welcome'));
  await region.joinRegion('newbie', 'zuerich-stadt');

  const identifier = `REGION_NEW_MEMBER-${NEWBIE_ID}`;
  await expect
    .poll(async () => (await welcome.getJson<{ items: BellDto[] }>('/api/bells?size=50')).items.map((bell) => bell.identifier))
    .toContain(identifier);

  // verification resolves the welcome task: the bell disappears
  await verifyViaApi(apiOf(await as('bot')), NEWBIE_ID);
  const after = await welcome.getJson<{ items: BellDto[] }>('/api/bells?size=50');
  expect(after.items.map((bell) => bell.identifier)).not.toContain(identifier);
});

test('EA8 stalled newcomer gets one nudge bell and mail after 30 days, waiting newcomers none @EA8 @EA21', async ({ api, clock }) => {
  const nudges = async (userId: string, channel: 'BELL' | 'MAIL'): Promise<DevNotification[]> =>
    api.getJson<DevNotification[]>(`/api/dev/notifications?userId=${userId}&type=ONBOARDING_STALLED&channel=${channel}`);
  const run = async (): Promise<void> => {
    const response = await api.fetch('POST', '/api/dev/jobs/onboarding-nudges/run');
    expect(response.status(), await response.text()).toBe(204);
  };

  // nothing is stalled on the seed date
  await run();
  expect(await nudges(NEWBIE_ID, 'BELL')).toHaveLength(0);

  // 40 days later the newbie (no progress since registration) is nudged once, with a link back to the journey
  await clock.fixServer(new Date(Date.parse(SEED_NOW) + 40 * 86_400_000).toISOString());
  await run();
  const bells = await nudges(NEWBIE_ID, 'BELL');
  expect(bells).toHaveLength(1);
  expect(bells[0].link).toBe('/onboarding');
  expect(await nudges(NEWBIE_ID, 'MAIL')).toHaveLength(1);
  // pending@local waits for the ID check by others: no nudge
  expect(await nudges(USERS.pending, 'BELL')).toHaveLength(0);

  // the next day's run does not repeat it
  await clock.fixServer(new Date(Date.parse(SEED_NOW) + 41 * 86_400_000).toISOString());
  await run();
  expect(await nudges(NEWBIE_ID, 'BELL')).toHaveLength(1);
});
