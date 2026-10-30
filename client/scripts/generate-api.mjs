// `npm run openapi:generate`: regenerates the Angular client in src/app/api from the server's OpenAPI contract.
//
// Deterministic on purpose:
// 1. src/app/api is deleted first. openapi-generator never removes files it no longer writes, so a run with a different
//    generator version (e.g. an ad-hoc `npx openapi-generator-cli` from server/, which creates server/openapitools.json
//    pinned to the *latest* generator) left files like query.params.ts behind that the pinned version does not know,
//    and `tsc` broke until the folder was deleted by hand.
// 2. The generator version comes only from client/openapitools.json (passed explicitly with --openapitools, so the
//    working directory never matters). It must equal `openapi-generator.version` in server/pom.xml (the Java side);
//    this script fails if they drift apart.
import { spawnSync } from 'node:child_process';
import { readFileSync, rmSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const CLIENT_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const API_DIR = path.join(CLIENT_DIR, 'src/app/api');
const PIN_FILE = path.join(CLIENT_DIR, 'openapitools.json');
const POM = path.resolve(CLIENT_DIR, '../server/pom.xml');
const SPEC = path.resolve(CLIENT_DIR, '../server/src/main/resources/openapi/openapi.yaml');

const pinned = JSON.parse(readFileSync(PIN_FILE, 'utf8'))['generator-cli']?.version;
const pomVersion = readFileSync(POM, 'utf8').match(/<openapi-generator\.version>([^<]+)</)?.[1];
if (!pinned || pinned !== pomVersion) {
  console.error(
    `openapi-generator version mismatch: client/openapitools.json pins ${pinned}, server/pom.xml uses ${pomVersion}. ` +
      'Change both together.',
  );
  process.exit(1);
}

rmSync(API_DIR, { recursive: true, force: true });

// the CLI's main.js runs on this Node directly: no .cmd shim, no shell, same behaviour on Windows and Linux
const cli = path.join(CLIENT_DIR, 'node_modules/@openapitools/openapi-generator-cli/main.js');
const result = spawnSync(
  process.execPath,
  [cli, '--openapitools', PIN_FILE, 'generate', '-i', SPEC, '-g', 'typescript-angular', '-o', API_DIR, '-c', path.join(CLIENT_DIR, 'openapi-generator.json')],
  { cwd: CLIENT_DIR, stdio: 'inherit' },
);
if (result.status !== 0) {
  console.error(`openapi-generator failed (exit ${result.status ?? result.error?.message})`);
  process.exit(result.status || 1);
}
