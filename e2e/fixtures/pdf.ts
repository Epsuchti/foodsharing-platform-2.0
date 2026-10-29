import fs from 'node:fs';
import path from 'node:path';
import { expect, type APIResponse, type Download } from '@playwright/test';

/** Anything a PDF can come from in a test. */
export type PdfSource = Buffer | Uint8Array | APIResponse | Download | string;

interface TextItem {
  str?: string;
}
interface PdfJs {
  getDocument(options: {
    data: Uint8Array;
    useSystemFonts?: boolean;
    isEvalSupported?: boolean;
    standardFontDataUrl?: string;
    verbosity?: number;
  }): {
    promise: Promise<{
      numPages: number;
      getPage(page: number): Promise<{ getTextContent(): Promise<{ items: TextItem[] }> }>;
    }>;
    destroy(): Promise<void>;
  };
}

// A real dynamic import: pdfjs-dist is ESM-only and this project compiles to CommonJS.
const importEsm = new Function('specifier', 'return import(specifier)') as (specifier: string) => Promise<PdfJs>;
let pdfjs: Promise<PdfJs> | undefined;
/** Metrics of the 14 standard PDF fonts shipped with pdfjs-dist (needed for Helvetica & co. without embedding). */
// pdf.js insists on a trailing '/' (not path.sep), so use forward slashes on Windows too; fs reads them fine.
const STANDARD_FONTS =
  path.join(path.dirname(require.resolve('pdfjs-dist/package.json')), 'standard_fonts').split(path.sep).join('/') + '/';

async function bytesOf(source: PdfSource): Promise<Uint8Array> {
  if (typeof source === 'string') return new Uint8Array(fs.readFileSync(source));
  if (source instanceof Uint8Array) return new Uint8Array(source);
  if ('body' in source && typeof source.body === 'function') return new Uint8Array(await source.body());
  const file = await (source as Download).path();
  return new Uint8Array(fs.readFileSync(file));
}

export interface PdfText {
  pages: string[];
  /** All pages joined with a newline. */
  text: string;
}

/** Text of every page (pdf.js text layer; whitespace collapsed). */
export async function pdfText(source: PdfSource): Promise<PdfText> {
  const data = await bytesOf(source);
  expect(Buffer.from(data.subarray(0, 5)).toString('latin1'), 'not a PDF (missing %PDF- header)').toBe('%PDF-');
  pdfjs ??= importEsm('pdfjs-dist/legacy/build/pdf.mjs');
  const lib = await pdfjs;
  const task = lib.getDocument({
    data,
    useSystemFonts: false,
    isEvalSupported: false,
    standardFontDataUrl: STANDARD_FONTS,
    verbosity: 0,
  });
  try {
    const document = await task.promise;
    const pages: string[] = [];
    for (let number = 1; number <= document.numPages; number++) {
      const content = await (await document.getPage(number)).getTextContent();
      pages.push(
        content.items
          .map((item) => item.str ?? '')
          .join(' ')
          .replace(/\s+/g, ' ')
          .trim(),
      );
    }
    return { pages, text: pages.join('\n') };
  } finally {
    await task.destroy();
  }
}

/**
 * Asserts that a PDF (response, download, buffer or file path) contains every expected text (string = substring,
 * RegExp = match), e.g. `await pdf.expectPdfContains(await api.get('/api/users/me/passport.pdf'), ['Sarah Müller'])`.
 * Returns the extracted text for further checks.
 */
export async function expectPdfContains(source: PdfSource, expected: (string | RegExp)[], options: { pages?: number } = {}): Promise<PdfText> {
  const result = await pdfText(source);
  if (options.pages !== undefined) expect(result.pages, 'PDF page count').toHaveLength(options.pages);
  for (const item of expected) {
    if (typeof item === 'string') expect(result.text, `PDF text should contain "${item}"`).toContain(item);
    else expect(result.text, `PDF text should match ${item}`).toMatch(item);
  }
  return result;
}

export const pdf = { pdfText, expectPdfContains };
export type PdfHelpers = typeof pdf;
