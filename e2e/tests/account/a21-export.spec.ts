import fs from 'node:fs';
import zlib from 'node:zlib';

import { acceptPendingConsents, expect, injectApiError, test } from '../../fixtures';

/**
 * A21 data export (plan-m7 Q4, A49): the settings card starts the export job, shows its progress and offers the zip
 * (`profile`, `settings`, `history` plus one file per module section and `uploads/`). One export per user and 10
 * minutes; the clock is fixed in e2e, so the second start is refused.
 */
test.use({ seedReset: 'test' });

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

test('A21 data export job produces zip with profile and history sections @A21', async ({ as }) => {
  const page = await as('foodsaver');
  await page.goto('/account/settings');
  await page.getByTestId('account.settings.export.start').click();
  await expect(page.getByTestId('account.settings.export.job')).toHaveAttribute('data-status', 'DONE');
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByTestId('account.settings.export.download').click(),
  ]);
  expect(download.suggestedFilename()).toMatch(/\.zip$/);
  const zip = unzip(fs.readFileSync(await download.path()));
  for (const section of ['profile.json', 'settings.json', 'history.json']) {
    expect([...zip.keys()], `zip contains ${section}`).toContain(section);
  }
  expect(zip.get('profile.json')!.toString('utf8')).toContain('foodsaver@local');
  // the export itself is part of the own history
  expect(zip.get('history.json')!.toString('utf8').length).toBeGreaterThan(2);

  // one export per 10 minutes
  await page.reload();
  await page.getByTestId('account.settings.export.start').click();
  await expect(page.getByTestId('account.settings.export.error')).toHaveAttribute('data-code', 'TOO_MANY_REQUESTS');
});

test('A21 a failing start is shown below the button @A21', async ({ as, apiOf }) => {
  // another user: the 10-minute cooldown is kept in memory and survives the seed reset
  const page = await as('bieb');
  await acceptPendingConsents(apiOf(page));
  await injectApiError(page, { url: '/api/users/me/export', method: 'POST', status: 503, times: 1 });
  await page.goto('/account/settings');
  await page.getByTestId('account.settings.export.start').click();
  await expect(page.getByTestId('account.settings.export.error')).toHaveAttribute('data-code', /UNEXPECTED_ERROR|SERVICE_UNAVAILABLE/);
  // the next try reaches the server
  await page.getByTestId('account.settings.export.start').click();
  await expect(page.getByTestId('account.settings.export.job')).toHaveAttribute('data-status', 'DONE');
});
