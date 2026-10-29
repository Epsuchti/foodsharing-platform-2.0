import { expect, seedRegion, seedStore, test, zurichPickupAt } from '../../fixtures';
import { S06 } from './_automation';

/**
 * AU11: region system sender. Automated store messages are sent on behalf of the store's region: mails carry the
 * From display name "foodsharing <Region>" and a footer line, Telegram messages a signature line.
 */
test.use({ seedReset: 'test', role: 'orga' });

/** RFC 2047 encoded words (`=?UTF-8?Q?...?=` / `=?UTF-8?B?...?=`) of a mail header → plain text. */
function decodeHeader(value: string): string {
  return value.replace(/=\?UTF-8\?([QB])\?([^?]*)\?=/gi, (_, encoding: string, text: string) => {
    if (encoding.toUpperCase() === 'B') return Buffer.from(text, 'base64').toString('utf8');
    return decodeURIComponent(text.replace(/%/g, '%25').replace(/_/g, ' ').replace(/=([0-9A-F]{2})/gi, '%$1'));
  });
}

test('AU11 automated sign-out summary is sent as the region (mail From + footer, Telegram signature) @AU11', async ({
  api,
  store,
  notifications,
  clock,
  mails,
}) => {
  const region = await api.getJson<{ name: string }>(`/api/regions/${seedRegion('zuerich-stadt').id}`);
  const sender = `foodsharing ${region.name}`;

  const tuesday = zurichPickupAt(1, '19:00');
  await store.signUp('sleepy', 'S-06', tuesday);
  await store.withRole('sleepy', async (client) => {
    const me = await client.getJson<{ user: { id: string } }>('/api/auth/session');
    const response = await client.fetch('DELETE', `/api/stores/${S06.id}/pickups/${encodeURIComponent(tuesday)}/signups/${me.user.id}`);
    expect(response.status(), await response.text()).toBeLessThan(300);
  });
  await clock.fixServer(zurichPickupAt(2, '05:00'));
  await notifications.runJob('signout-alerts');
  await notifications.runJob('mail-outbox');

  // the store manager's summary mail comes from the region
  const mail = await mails.waitForMail({ to: 'bieb@local', subjectContains: 'noch offen' });
  expect(decodeHeader(mail.from ?? '')).toBe(`${sender} <noreply@localhost>`);
  expect(mail.html).toContain('data-sender-region="true"');
  expect(mail.html).toContain(sender);
  expect(mail.text).toContain(`-- \n`);
  expect(mail.text).toContain(sender);

  // bieb has Telegram linked (seed): the personal alert ends with the region's signature line
  const telegram = await notifications.telegram('bieb', 'PICKUP_SIGNOUT_NOT_REPLACED');
  expect(telegram).toHaveLength(1);
  expect(telegram[0].body).toContain(`\n\n— ${sender}`);
  await clock.fixServer();
});

test('AU11 automated store request decline is sent as the store region @AU11', async ({ api, notifications, mails }) => {
  const s07 = seedStore('S-07').id;
  const detail = await api.getJson<{ region: { id: string } }>(`/api/stores/${s07}`);
  const region = await api.getJson<{ name: string }>(`/api/regions/${detail.region.id}`);
  const sender = `foodsharing ${region.name}`;

  // S-07's seeded request policy is a dry run; a live one with 0 km declines guide's open application
  await api.putJson(`/api/stores/${s07}/automation/request-policy`, {
    enabled: true,
    dryRun: false,
    distanceRuleEnabled: true,
    maximumDistanceKm: 0,
  });
  const run = await api.postJson<{ declined: number }>(`/api/stores/${s07}/automation/request-policy/run?dryRun=false`);
  expect(run.declined).toBeGreaterThan(0);
  await notifications.runJob('mail-outbox');

  const mail = await mails.waitForMail({ to: 'guide@local' });
  expect(decodeHeader(mail.from ?? '')).toBe(`${sender} <noreply@localhost>`);
  expect(mail.html).toContain('data-sender-region="true"');
  expect(mail.text).toContain(sender);
});
