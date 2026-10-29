import { isStartUrl } from './catalog.guard';

describe('catalogGuard', () => {
  it('lets the start-up pages render with the start-up catalog', () => {
    for (const url of ['/', '/en', '/gws/', '/login', '/login/magic?x=1', '/onboarding', '/onboarding/profile', '/legal/privacy-policy', '/forbidden', '/bells', '/r/zuerich', '/find-region', '/?ref=a']) {
      expect(isStartUrl(url), url).toBe(true);
    }
  });

  it('makes every other route wait for the complete catalog', () => {
    for (const url of ['/dashboard', '/stores/1/automation', '/regions/1', '/en/stores', '/loginx', '/rescue', '/messages', '/pages/about']) {
      expect(isStartUrl(url), url).toBe(false);
    }
  });
});
