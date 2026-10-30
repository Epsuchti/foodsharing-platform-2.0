import { isJourneyPage, journeyBarAllowedOn } from './journey-bar.component';

describe('isJourneyPage', () => {
  it('matches the journey page only, not its step pages', () => {
    for (const url of ['/onboarding', '/onboarding/', '/onboarding?from=bell', '/onboarding#steps']) {
      expect(isJourneyPage(url), url).toBe(true);
    }
    for (const url of ['/onboarding/profile', '/dashboard', '/onboarding-help', '/']) {
      expect(isJourneyPage(url), url).toBe(false);
    }
  });
});

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
