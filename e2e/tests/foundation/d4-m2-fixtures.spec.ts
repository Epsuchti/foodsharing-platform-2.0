import { expect, test } from '../../fixtures';

/** The M2 e2e helpers (plan-m2 Part B): seed regions + joinRegion via the API, and PDF text assertions. */

test.use({ seedReset: 'test' });

interface UnitMembership {
  id: string;
  slug: string;
  member: boolean;
  homeRegion: boolean;
}

test('D4.3 region fixture resolves seed slugs and joins a region through the API @D4.3', async ({ region, as, apiOf }) => {
  const zuerich = region.bySlug('zuerich-stadt');
  expect(zuerich.id).toMatch(/^00000010-/);
  expect(region.id('zuerich-stadt')).toBe(zuerich.id);
  expect(region.id(zuerich.id)).toBe(zuerich.id);

  // newbie has no region in the seed (journey step 2)
  const page = await as('newbie');
  const api = apiOf(page);
  const before = await api.getJson<UnitMembership[]>('/api/regions/mine');
  expect(before.some((unit) => unit.id === zuerich.id)).toBe(false);

  await region.joinRegion('newbie', 'zuerich-stadt');

  const after = await api.getJson<UnitMembership[]>('/api/regions/mine');
  const joined = after.find((unit) => unit.id === zuerich.id);
  expect(joined?.member).toBe(true);
  // the first region becomes the home region
  expect(joined?.homeRegion).toBe(true);
});

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

test('D4.3 pdf fixture extracts and asserts PDF text @D4.3', async ({ pdf }) => {
  const file = minimalPdf(['foodsharing Ausweis', 'Sarah Mueller 100006']);
  const result = await pdf.expectPdfContains(file, ['foodsharing Ausweis', /Mueller\s+100006/], { pages: 1 });
  expect(result.pages).toHaveLength(1);
  await expect(pdf.expectPdfContains(file, ['not in there'])).rejects.toThrow();
  await expect(pdf.pdfText(Buffer.from('<html>no pdf</html>'))).rejects.toThrow(/not a PDF/);
});
