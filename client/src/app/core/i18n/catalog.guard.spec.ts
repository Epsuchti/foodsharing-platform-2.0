import { isStartUrl } from './catalog.guard';

describe('catalogGuard', () => {
  it('lets the start-up pages render with the start-up catalog', () => {
    for (const url of ['/', '/en', '/gws/', '/login', '/login/magic?x=1', '/onboarding', '/onboarding/profile', '/legal/privacy-policy', '/forbidden', '/bells', '/?ref=a']) {
      expect(isStartUrl(url), url).toBe(true);
    }
  });

  it('makes every other route wait for the complete catalog', () => {
    for (const url of ['/dashboard', '/stores/1/automation', '/regions/1', '/en/stores', '/loginx', '/rescue', '/messages', '/pages/about',
      // public region pages and the region finder use the regions/intro-pickups catalogs (design review M7 R1: raw keys on a deep link)
      '/r/zuerich', '/r/zuerich-stadt/intro-pickups', '/find-region', '/find-region?postcode=8004']) {
      expect(isStartUrl(url), url).toBe(false);
    }
  });
});
