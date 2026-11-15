// Self-test of the coverage gate (decision 180): `node --test e2e/scripts/feature-coverage.test.mjs`.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { test } from 'node:test';
import { evaluate, readCoverageMap } from './feature-coverage.mjs';

const row = (id, status = 'done', kind = 'feature') => ({ id, title: id, status, kind, notes: '' });
const journey = (tags, status = 'expected') => ({ file: 'journeys/x.journey.ts', title: 't', tags, project: 'chromium', status });

test('an ID with a passing journey tag passes', () => {
  const { problems, lines } = evaluate([row('S1')], [journey(['S1'])], { strict: true, mode: 'results' });
  assert.deepEqual(problems, []);
  assert.equal(lines[0].result, 'passing');
});

test('an ID without journey tag passes strict with a coverage-map entry', () => {
  const map = new Map([['EA20', ['server/src/test/java/X.java']]]);
  const { problems, lines } = evaluate([row('EA20')], [], { strict: true, mode: 'results', map });
  assert.deepEqual(problems, []);
  assert.equal(lines[0].result, 'unit/integration');
});

test('an ID with neither fails strict and done', () => {
  const { problems } = evaluate([row('EA20')], [], { strict: true, mode: 'results' });
  assert.equal(problems.length, 2);
  assert.match(problems.join('\n'), /EA20 is done in the ledger/);
  assert.match(problems.join('\n'), /--strict/);
});

test('a failing journey does not cover a done ID', () => {
  const { problems } = evaluate([row('S1')], [journey(['S1'], 'unexpected')], { strict: false, mode: 'results' });
  assert.match(problems.join('\n'), /S1 is done/);
});

test('a map entry for an ID that is not in the ledger fails', () => {
  const map = new Map([['ZZ9', ['a.java']]]);
  const { problems } = evaluate([row('S1')], [journey(['S1'])], { strict: true, mode: 'results', map });
  assert.match(problems.join('\n'), /ZZ9 is not in docs\/ledger.md/);
});

test('readCoverageMap reports files that do not exist and skips $comment', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'coverage-map-'));
  fs.writeFileSync(path.join(dir, 'Real.java'), '');
  const file = path.join(dir, 'map.json');
  fs.writeFileSync(file, JSON.stringify({ $comment: 'x', A1: 'Real.java', A2: ['Real.java', 'Gone.java'], A3: [] }));
  const { map, problems } = readCoverageMap(file, dir);
  assert.deepEqual([...map.keys()], ['A1', 'A2']);
  assert.equal(problems.length, 2);
  assert.match(problems[0], /A2 names Gone.java/);
  assert.match(problems[1], /A3 needs a file path/);
});

test('a missing map file is an empty map', () => {
  const { map, problems } = readCoverageMap(path.join(os.tmpdir(), 'no-such-coverage-map.json'));
  assert.equal(map.size, 0);
  assert.deepEqual(problems, []);
});
