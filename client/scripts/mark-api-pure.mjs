// Runs after openapi-generator (npm run openapi:generate). Marks the generated client as side-effect free so the
// bundler only includes the services a chunk actually imports. Without it, every import from the `api` barrel pulls
// all ~70 services (≈650 kB) into the eager start-up chunk (D11.5.3, Lighthouse performance).
import { writeFile } from 'node:fs/promises';

await writeFile('src/app/api/package.json', `${JSON.stringify({ sideEffects: false }, null, 2)}\n`);
