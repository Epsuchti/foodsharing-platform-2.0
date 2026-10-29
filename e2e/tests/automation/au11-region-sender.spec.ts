import { expect, seedRegion, test, zurichPickupAt } from '../../fixtures';
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
