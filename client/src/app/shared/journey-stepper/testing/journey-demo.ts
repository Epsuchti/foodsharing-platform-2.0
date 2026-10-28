import { Journey, JourneyStepKey, JourneyStepPreview, JourneyStepStatus, Role } from '../../../api';

/** Fixed journeys for the dev gallery and specs (shapes as `getMyJourney` returns them). */
export const DEMO_JOURNEY_IN_PROGRESS: Journey = {
  complete: false,
  currentStep: 'INTRO_PICKUPS',
  steps: [
    { key: 'ACCOUNT', status: JourneyStepStatus.Done, titleKey: 'onboarding.step.ACCOUNT.title', estimatedDuration: 'PT5M' },
    { key: 'PROFILE_REGION', status: JourneyStepStatus.Done, titleKey: 'onboarding.step.PROFILE_REGION.title', estimatedDuration: 'PT10M' },
    { key: 'LEARN', status: JourneyStepStatus.Done, titleKey: 'onboarding.step.LEARN.title', estimatedDuration: 'PT45M' },
    { key: 'QUIZ', status: JourneyStepStatus.Done, titleKey: 'onboarding.step.QUIZ.title', estimatedDuration: 'PT30M' },
    { key: 'AGREEMENT', status: JourneyStepStatus.Done, titleKey: 'onboarding.step.AGREEMENT.title', estimatedDuration: 'PT10M' },
    {
      key: 'INTRO_PICKUPS',
      status: JourneyStepStatus.InProgress,
      titleKey: 'onboarding.step.INTRO_PICKUPS.title',
      estimatedDuration: 'P2W',
      progress: { done: 1, booked: 1, required: 3, fairteilerDone: false },
      nextAction: { labelKey: 'onboarding.action.bookIntroPickup', route: '/intro-pickups' },
      blockedReason: 'FAIRTEILER_VISIT_REQUIRED',
    },
    { key: 'ID_CHECK', status: JourneyStepStatus.Locked, titleKey: 'onboarding.step.ID_CHECK.title', estimatedDuration: 'P1W' },
    { key: 'PASSPORT', status: JourneyStepStatus.Locked, titleKey: 'onboarding.step.PASSPORT.title', estimatedDuration: 'PT5M' },
  ],
  extraSteps: [],
};

export const DEMO_JOURNEY_WAITING: Journey = {
  ...DEMO_JOURNEY_IN_PROGRESS,
  currentStep: 'ID_CHECK',
  steps: DEMO_JOURNEY_IN_PROGRESS.steps.map((step) => {
    if (step.key === 'INTRO_PICKUPS') {
      return {
        ...step,
        status: JourneyStepStatus.Done,
        progress: undefined,
        nextAction: undefined,
        blockedReason: undefined,
      };
    }
    if (step.key === 'ID_CHECK') {
      return {
        ...step,
        status: JourneyStepStatus.WaitingForOthers,
        waitingFor: {
          labelKey: 'onboarding.waiting.verification',
          typicalDays: 5,
          contacts: [
            {
              id: '00000001-0001-4000-8000-000000000011',
              displayName: 'Reto Baumann',
              role: Role.Ambassador,
              verified: true,
            },
          ],
        },
      };
    }
    return step;
  }),
};

export const DEMO_JOURNEY_PREVIEW: JourneyStepPreview[] = (
  ['ACCOUNT', 'PROFILE_REGION', 'LEARN', 'QUIZ', 'AGREEMENT', 'INTRO_PICKUPS', 'ID_CHECK', 'PASSPORT'] as JourneyStepKey[]
).map((key, index) => ({
  key,
  titleKey: `onboarding.step.${key}.title`,
  descriptionKey: `onboarding.step.${key}.description`,
  estimatedDuration: ['PT5M', 'PT10M', 'PT45M', 'PT30M', 'PT10M', 'P2W', 'P1W', 'PT5M'][index],
}));
