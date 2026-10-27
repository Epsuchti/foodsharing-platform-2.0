#!/usr/bin/env node
// Regenerates the closure table seed from the region seed (docs/conventions.md A6, plan-m2 T11).
//
//   node scripts/gen-region-closure.mjs
//
// Reads server/src/main/resources/db/seed/region/region.csv (id, parent_id) and writes region_closure.csv with one
// row per (region, ancestor) pair including the region itself at depth 0. Run it after editing region.csv.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SEED_DIR = path.join(REPO_DIR, 'server/src/main/resources/db/seed/region');

/** Minimal RFC 4180 parser (quoted fields may contain commas, quotes and line breaks). */
function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = '';
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') {
        field += '"';
        i++;
      } else if (c === '"') {
        quoted = false;
      } else {
        field += c;
      }
    } else if (c === '"') {
      quoted = true;
    } else if (c === ',') {
      row.push(field);
      field = '';
    } else if (c === '\n') {
      row.push(field);
      rows.push(row);
      row = [];
      field = '';
    } else if (c !== '\r') {
      field += c;
    }
  }
  if (field !== '' || row.length) {
    row.push(field);
    rows.push(row);
  }
  return rows;
}

const [header, ...records] = parseCsv(fs.readFileSync(path.join(SEED_DIR, 'region.csv'), 'utf8'));
const idIndex = header.indexOf('id');
const parentIndex = header.indexOf('parent_id');
const parentOf = new Map(records.map((record) => [record[idIndex], record[parentIndex] === 'NULL' ? null : record[parentIndex]]));

const lines = ['region_id,ancestor_id,depth'];
for (const id of parentOf.keys()) {
  let current = id;
  let depth = 0;
  const seen = new Set();
  while (current) {
    if (seen.has(current)) throw new Error(`cycle at ${current}`);
    if (!parentOf.has(current)) throw new Error(`unknown parent ${current} of ${id}`);
    seen.add(current);
    lines.push(`${id},${current},${depth}`);
    current = parentOf.get(current);
    depth++;
  }
}
fs.writeFileSync(path.join(SEED_DIR, 'region_closure.csv'), `${lines.join('\n')}\n`);
console.log(`region_closure.csv: ${lines.length - 1} rows for ${parentOf.size} regions`);
