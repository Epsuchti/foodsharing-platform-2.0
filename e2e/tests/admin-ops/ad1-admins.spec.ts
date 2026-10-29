import { expect, seedRegion, test } from '../../fixtures';

/** AD1: admins list (plan §14.4) — every ambassador / working-group admin with units, last login, active flag (ORGA). */
test.use({ seedReset: 'test' });

interface AdminEntry {
  user: { id: string; displayName: string };
  email: string;
  active: boolean;
  lastLoginAt?: string;
  units: { region: { id: string; name: string; type: string } }[];
}
interface AdminPage {
  items: AdminEntry[];
  page: { page: number; size: number; totalItems: number; totalPages: number };
}

const ZH = seedRegion('zuerich-stadt').id;

test('AD1 orga lists all admins with their units, filters and pages them; admins can be disabled @AD1', async ({ as, apiOf, dev }) => {
  await as('bot'); // a login sets lastLoginAt
  const orga = apiOf(await as('orga'));
  const bot = await dev.user('bot');

  const all = await orga.getJson<AdminPage>('/api/admin/admins?size=100');
  expect(all.page.totalItems).toBeGreaterThan(1);
  const names = all.items.map((e) => e.user.displayName);
  const lower = names.map((n) => n.toLowerCase());
  expect(lower).toEqual([...lower].sort());
  const ambassador = all.items.find((e) => e.user.id === bot.id)!;
  expect(ambassador.active).toBe(true);
  expect(ambassador.email).toBe(bot.email);
  expect(ambassador.units.map((u) => u.region.id)).toContain(ZH);
  expect(ambassador.lastLoginAt).toBeTruthy();

  // filters: name/email, unit kind, paging
  const byEmail = await orga.getJson<AdminPage>(`/api/admin/admins?q=${encodeURIComponent(bot.email.toUpperCase())}`);
  expect(byEmail.items.map((e) => e.user.id)).toEqual([bot.id]);
  const groups = await orga.getJson<AdminPage>('/api/admin/admins?unitKind=WORKING_GROUP&size=100');
  for (const entry of groups.items) {
    expect(entry.units.length).toBeGreaterThan(0);
    expect(entry.units.every((u) => u.region.type === 'WORKING_GROUP')).toBe(true);
  }
  const regions = await orga.getJson<AdminPage>('/api/admin/admins?unitKind=REGION&size=100');
  expect(regions.items.every((e) => e.units.every((u) => u.region.type !== 'WORKING_GROUP'))).toBe(true);
  const first = await orga.getJson<AdminPage>('/api/admin/admins?size=1&page=1');
  expect(first.items).toHaveLength(1);
  expect(first.items[0].user.id).toBe(all.items[1].user.id);
  expect((await orga.fetch('GET', '/api/admin/admins?size=0')).status()).toBe(400);

  // disable an admin through the existing user endpoint; the active filter finds them
  const disabled = await orga.fetch('PUT', `/api/admin/users/${bot.id}/active`, { active: false, reason: 'E2E AD1' });
  expect(disabled.status(), await disabled.text()).toBe(200);
  const inactive = await orga.getJson<AdminPage>('/api/admin/admins?active=false&size=100');
  expect(inactive.items.map((e) => e.user.id)).toContain(bot.id);
  expect((await orga.getJson<AdminPage>('/api/admin/admins?active=true&size=100')).items.map((e) => e.user.id)).not.toContain(bot.id);

  // ORGA only
  for (const role of ['jumper', 'foodsaver'] as const) {
    const client = apiOf(await as(role));
    expect((await client.fetch('GET', '/api/admin/admins')).status()).toBe(403);
  }
});
