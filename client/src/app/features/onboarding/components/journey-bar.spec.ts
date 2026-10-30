import { journeyBarAllowedOn } from './journey-bar.component';

describe('journeyBarAllowedOn', () => {
  it('hides the journey bar on the consent page only', () => {
    for (const url of ['/consent', '/consent?returnUrl=%2Fdashboard', '/consent#top']) {
      expect(journeyBarAllowedOn(url), url).toBe(false);
    }
    for (const url of ['/dashboard', '/onboarding', '/onboarding/profile', '/quizzes/foodsaver', '/consents-overview', '/legal/privacy-policy']) {
      expect(journeyBarAllowedOn(url), url).toBe(true);
    }
  });
});
