import fs from 'node:fs';
import path from 'node:path';
import { expect, test } from '../../fixtures';

/**
 * C10 upload bytes in the database (decision 158): an upload comes back byte-identical, every download carries a
 * strong ETag (quoted sha256) and a conditional GET answers 304 without a body; public uploads are cached for a week
 * (`public, max-age=604800, immutable`), private ones only by the browser and revalidated (`private, no-cache`).
 */
test.use({ seedReset: 'test', role: 'foodsaver' });

const PNG = fs.readFileSync(path.join(__dirname, '..', '..', 'screenshots', 'fixtures', 'tile.png'));
const PDF = Buffer.from('%PDF-1.4\n% c10 upload storage\n');

test('C10 upload bytes round-trip with ETag, 304 and Cache-Control per visibility @C10', async ({ api }) => {
  // a private file (unclaimed: owner only)
  const created = await api.fetch('POST', '/api/uploads', undefined, {
    multipart: { file: { name: 'c10-storage.pdf', mimeType: 'application/pdf', buffer: PDF } },
  });
  expect(created.status(), await created.text()).toBe(201);
  const file = (await created.json()) as { id: string };

  const download = await api.get(`/api/uploads/${file.id}`);
  expect(download.status()).toBe(200);
  expect(Buffer.compare(await download.body(), PDF)).toBe(0);
  expect(download.headers()['cache-control']).toBe('private, no-cache');
  const etag = download.headers()['etag'];
  expect(etag).toMatch(/^"[0-9a-f]{64}"$/);

  const revalidated = await api.get(`/api/uploads/${file.id}`, { headers: { 'If-None-Match': etag } });
  expect(revalidated.status()).toBe(304);
  expect(revalidated.headers()['etag']).toBe(etag);
  expect((await revalidated.body()).length).toBe(0);
  const changed = await api.get(`/api/uploads/${file.id}`, { headers: { 'If-None-Match': '"other"' } });
  expect(changed.status()).toBe(200);

  // a public file: the profile photo (USER_PHOTO), original and variants with their own tags
  const photoUpload = await api.fetch('POST', '/api/uploads', undefined, {
    multipart: { file: { name: 'c10-photo.png', mimeType: 'image/png', buffer: PNG } },
  });
  expect(photoUpload.status(), await photoUpload.text()).toBe(201);
  const photo = (await photoUpload.json()) as { id: string };
  expect((await api.put('/api/users/me/photo', { uploadId: photo.id })).status()).toBe(200);

  const original = await api.get(`/api/uploads/${photo.id}`);
  expect(original.status()).toBe(200);
  expect(Buffer.compare(await original.body(), PNG)).toBe(0);
  expect(original.headers()['cache-control']).toBe('public, max-age=604800, immutable');
  const md = await api.get(`/api/uploads/${photo.id}?size=md`);
  expect(md.status()).toBe(200);
  expect(md.headers()['content-type']).toBe('image/jpeg');
  expect(md.headers()['etag']).not.toBe(original.headers()['etag']);
  const mdAgain = await api.get(`/api/uploads/${photo.id}?size=md`, { headers: { 'If-None-Match': md.headers()['etag'] } });
  expect(mdAgain.status()).toBe(304);

  // deleting removes the bytes with the row
  expect((await api.delete('/api/users/me/photo')).status()).toBeLessThan(300);
  expect((await api.delete(`/api/uploads/${file.id}`)).status()).toBe(204);
  expect((await api.get(`/api/uploads/${file.id}`)).status()).toBe(404);
});
