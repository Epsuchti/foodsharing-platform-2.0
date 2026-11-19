import { hidesJourneyAction, isJourneyPage, journeyBarAllowedOn } from './journey-bar.component';

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
  it('hides the journey bar on the consent page and during a quiz attempt', () => {
    for (const url of ['/consent', '/consent?returnUrl=%2Fdashboard', '/consent#top', '/quizzes/foodsaver/session', '/quizzes/foodsaver/session?x=1']) {
      expect(journeyBarAllowedOn(url), url).toBe(false);
    }
    for (const url of ['/dashboard', '/onboarding', '/onboarding/profile', '/quizzes/foodsaver', '/quizzes/foodsaver/results', '/consents-overview', '/legal/privacy-policy']) {
      expect(journeyBarAllowedOn(url), url).toBe(true);
    }
  });
});

describe('hidesJourneyAction', () => {
  it('hides the action on the journey page, its step pages and the page the action leads to', () => {
    expect(hidesJourneyAction('/onboarding', '/learn')).toBe(true);
    expect(hidesJourneyAction('/onboarding/profile', '/dashboard')).toBe(true);
    expect(hidesJourneyAction('/learn', '/learn')).toBe(true);
    expect(hidesJourneyAction('/quizzes/foodsaver?from=bar', '/quizzes/foodsaver')).toBe(true);
    expect(hidesJourneyAction('/learn/', '/learn')).toBe(true);
  });

  it('hides the action on the dashboard, which shows the next step in its own card', () => {
    expect(hidesJourneyAction('/dashboard', '/onboarding/profile')).toBe(true);
    expect(hidesJourneyAction('/dashboard?tab=x', '/intro-pickups')).toBe(true);
    expect(hidesJourneyAction('/dashboard', undefined)).toBe(true);
  });

  it('keeps the action everywhere else', () => {
    expect(hidesJourneyAction('/quizzes', '/onboarding/profile')).toBe(false);
    expect(hidesJourneyAction('/learn/hygiene', '/learn')).toBe(false);
    expect(hidesJourneyAction('/quizzes', undefined)).toBe(false);
  });
});
