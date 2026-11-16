import { expectPdfContains, pdfText } from '../../fixtures';
import { expect, journey as test } from '../../fixtures/checkpoint';

/**
 * The journey fixtures themselves (decision 180), journey-less: every journey starts from the seed (seed reset per
 * test), the region fixture joins a region through the API, and the PDF fixture fails when a text is missing (the
 * journeys only ever see it pass). The two tests run in this order on one worker.
 */

interface UnitMembership {
  id: string;
  member: boolean;
  homeRegion: boolean;
}

/** A minimal one-page PDF (uncompressed, Helvetica) with the given lines. */
function minimalPdf(lines: string[]): Buffer {
  const text = lines.map((line, i) => `BT /F1 18 Tf 72 ${720 - i * 28} Td (${line}) Tj ET`).join('\n');
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${Buffer.byteLength(text, 'latin1')} >>\nstream\n${text}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>',
  ];
  let body = '%PDF-1.4\n';
  const offsets: number[] = [];
  objects.forEach((object, i) => {
    offsets.push(Buffer.byteLength(body, 'latin1'));
    body += `${i + 1} 0 obj\n${object}\nendobj\n`;
  });
  const xref = Buffer.byteLength(body, 'latin1');
  body += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  body += offsets.map((offset) => `${String(offset).padStart(10, '0')} 00000 n \n`).join('');
  body += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(body, 'latin1');
}

test('region and PDF fixtures: join a region through the API, fail on a missing PDF text @infra', async ({ region, as, apiOf }) => {
  const zuerich = region.bySlug('zuerich-stadt');
  expect(region.id('zuerich-stadt')).toBe(zuerich.id);
  expect(region.id(zuerich.id)).toBe(zuerich.id);

  // newbie has no region in the seed; the first region becomes his home region
  const api = apiOf(await as('newbie'));
  expect((await api.getJson<UnitMembership[]>('/api/regions/mine')).some((unit) => unit.id === zuerich.id)).toBe(false);
  await region.joinRegion('newbie', 'zuerich-stadt');
  const joined = (await api.getJson<UnitMembership[]>('/api/regions/mine')).find((unit) => unit.id === zuerich.id);
  expect(joined).toMatchObject({ member: true, homeRegion: true });

  const file = minimalPdf(['foodsharing Ausweis', 'Sarah Mueller 100006']);
  const result = await expectPdfContains(file, ['foodsharing Ausweis', /Mueller\s+100006/], { pages: 1 });
  expect(result.pages).toHaveLength(1);
  await expect(expectPdfContains(file, ['not in there'])).rejects.toThrow();
  await expect(pdfText(Buffer.from('<html>no pdf</html>'))).rejects.toThrow(/not a PDF/);
});

test('the next test starts from the seed again @infra', async ({ region, as, apiOf }) => {
  const api = apiOf(await as('newbie'));
  const mine = await api.getJson<UnitMembership[]>('/api/regions/mine');
  expect(mine.some((unit) => unit.id === region.id('zuerich-stadt'))).toBe(false);
});
