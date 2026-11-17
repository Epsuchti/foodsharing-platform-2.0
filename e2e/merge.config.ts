/**
 * Config for `playwright merge-reports` in the coverage job (decision 180): chromium and mobile-chrome run inside
 * the Playwright image (/work/e2e), the other browsers on the runner, so the blob reports carry different test
 * directories. The merge maps them onto this one.
 */
export default {
  testDir: '.',
};
