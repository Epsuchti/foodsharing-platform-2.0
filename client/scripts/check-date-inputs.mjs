// Enforces decision 150: no native date, datetime-local or time inputs in src/app (templates in .html files and
// inline templates in .ts files). They format by the browser locale (mm/dd/yyyy, AM/PM on an en-US browser);
// use fs-date-input, fs-datetime-input or fs-time-input from src/app/shared/date-input instead.
// Spec files are ignored (they may build native inputs as fixtures).
import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';

const ROOT = 'src/app';
const NATIVE = /type\s*=\s*["'](date|datetime-local|time)["']|\[type\]\s*=\s*["']'(date|datetime-local|time)'["']/g;

async function walk(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(
    entries.map((entry) =>
      entry.isDirectory() ? (entry.name === 'api' ? [] : walk(join(directory, entry.name))) : [join(directory, entry.name)],
    ),
  );
  return nested.flat();
}

const violations = [];
const files = (await walk(ROOT)).filter((file) => /\.(ts|html)$/.test(file) && !file.endsWith('.spec.ts'));
for (const file of files) {
  const source = await readFile(file, 'utf8');
  for (const match of source.matchAll(NATIVE)) {
    const line = source.slice(0, match.index).split('\n').length;
    violations.push(`${file}:${line}: native <input ${match[0]}>`);
  }
}

if (violations.length) {
  console.error('Native date/time inputs (decision 150): use fs-date-input, fs-datetime-input or fs-time-input.');
  for (const violation of violations) {
    console.error(`  ${violation}`);
  }
  process.exit(1);
}
console.log(`date inputs: ${files.length} files checked, no native date/time inputs.`);
